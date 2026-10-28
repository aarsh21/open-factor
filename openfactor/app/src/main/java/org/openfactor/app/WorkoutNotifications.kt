package org.openfactor.app

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

/** System-owned reminders continue when the activity and its process are gone. */
object WorkoutNotifications {
    const val OPEN_WORKOUT = "org.openfactor.app.OPEN_WORKOUT"
    internal const val REST_FINISHED = "org.openfactor.app.REST_FINISHED"
    internal const val WORKOUT_ID = "workout_id"
    internal const val REST_DEADLINE = "rest_deadline"
    private const val ACTIVE_CHANNEL = "active_workout"
    private const val REST_CHANNEL = "rest_timer"
    private const val ACTIVE_NOTIFICATION = 1001
    private const val REST_NOTIFICATION = 1002
    private const val ALARM_REQUEST = 1003
    private data class AlarmPlan(val workoutId: String?, val deadline: Long, val exact: Boolean)
    private var scheduledAlarm: AlarmPlan? = null
    private data class ActiveNotice(val id: String, val name: String, val sets: Int, val startedAt: Long, val pausedAt: Long?, val pausedDuration: Long)
    private var activeNotice: ActiveNotice? = null
    private var lastRestState: Pair<String, Long>? = null
    private var channelsCreated = false

    fun notificationsAllowed(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()

    fun preciseTimersAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Optional special access. Logging and approximate reminders work without it. */
    fun preciseTimerSettings(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= 31) Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
        else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    fun sync(context: Context, data: AppData, forceRefresh: Boolean = false) {
        try {
            if (forceRefresh) {
                activeNotice = null
                scheduledAlarm = null
                lastRestState = null
            }
            syncInternal(context, data)
        } catch (error: RuntimeException) {
            // Reminders are best effort; a system/OEM notification failure cannot undo a saved set.
            android.util.Log.w("WorkoutNotifications", "Could not refresh workout reminders", error)
        }
    }

    private fun syncInternal(context: Context, data: AppData) {
        val manager = context.getSystemService(NotificationManager::class.java)
        createChannels(manager)
        val workout = data.active
        scheduleRest(context, workout)
        if (workout == null) {
            manager.cancel(ACTIVE_NOTIFICATION)
            manager.cancel(REST_NOTIFICATION)
            activeNotice = null
            lastRestState = null
            return
        }
        val restState = workout.id to workout.restUntil
        // A new or cleared timer must never leave an old "Rest complete" alert behind.
        if (restState != lastRestState) {
            if (workout.restUntil == 0L || workout.restUntil > System.currentTimeMillis()) manager.cancel(REST_NOTIFICATION)
            lastRestState = restState
        }
        val notice = ActiveNotice(workout.id, workout.name, workout.completedSets(), workout.startedAt, workout.pausedAt, workout.pausedDurationMs)
        if (notice == activeNotice) return
        if (!notificationsAllowed(context)) {
            activeNotice = notice
            return
        }
        val count = workout.completedSets()
        val text = if (workout.pausedAt != null) "Paused · Tap to open" else "$count ${if (count == 1) "set" else "sets"} logged · Tap to resume"
        val notification = Notification.Builder(context, ACTIVE_CHANNEL)
            .setSmallIcon(R.drawable.ic_workout_notification)
            .setContentTitle(workout.name)
            .setContentText(text)
            .setContentIntent(openWorkout(context))
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(workout.pausedAt == null)
            .setWhen(workout.startedAt + workout.pausedDurationMs)
            .setUsesChronometer(workout.pausedAt == null)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .build()
        // Revoking notification permission while the app is open must not break a saved set.
        runCatching { manager.notify(ACTIVE_NOTIFICATION, notification) }.onSuccess { activeNotice = notice }
    }

    private fun createChannels(manager: NotificationManager) {
        if (channelsCreated) return
        manager.createNotificationChannel(NotificationChannel(ACTIVE_CHANNEL, "Active workout", NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel(REST_CHANNEL, "Rest timer", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Alerts when a workout rest timer finishes"
            enableVibration(true)
        })
        channelsCreated = true
    }

    private fun openWorkout(context: Context): PendingIntent = PendingIntent.getActivity(
        context, ACTIVE_NOTIFICATION,
        Intent(context, MainActivity::class.java).setAction(OPEN_WORKOUT)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun alarmIntent(context: Context, workout: Workout? = null): PendingIntent = PendingIntent.getBroadcast(
        context, ALARM_REQUEST,
        Intent(context, WorkoutRestReceiver::class.java).setAction(REST_FINISHED)
            .putExtra(WORKOUT_ID, workout?.id).putExtra(REST_DEADLINE, workout?.restUntil ?: 0L),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun scheduleRest(context: Context, workout: Workout?) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val deadline = workout?.restUntil?.takeIf { it > System.currentTimeMillis() } ?: 0L
        if (scheduledAlarm?.let { it.workoutId == workout?.id && it.deadline == deadline } == true) return
        val exact = preciseTimersAllowed(context)
        val plan = AlarmPlan(workout?.id, deadline, exact)
        // Editing a weight or note must not reschedule an identical alarm on each keystroke.
        // Keep this cache in memory: force-stop clears OS alarms, so a new process must schedule again.
        if (scheduledAlarm == plan) return
        val pending = alarmIntent(context, workout)
        alarms.cancel(pending)
        if (deadline > 0) {
            // Exact access can be revoked between the check and schedule; inexact is the safe fallback.
            val scheduled = exact && runCatching {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadline, pending)
            }.isSuccess
            if (!scheduled) alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadline, pending)
        }
        scheduledAlarm = plan
    }

    internal fun deliverRest(context: Context, workoutId: String?, deadline: Long, now: Long = System.currentTimeMillis()) {
        val data = WorkoutStore(context).load()
        if (!restAlarmMatches(data.active, workoutId, deadline, now)) return
        if (!notificationsAllowed(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        createChannels(manager)
        val notification = Notification.Builder(context, REST_CHANNEL)
            .setSmallIcon(R.drawable.ic_workout_notification)
            .setContentTitle("Rest complete")
            .setContentText(data.active!!.name)
            .setContentIntent(openWorkout(context))
            .setCategory(Notification.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .build()
        runCatching { manager.notify(REST_NOTIFICATION, notification) }
    }

    internal fun reschedule(context: Context) {
        // System alarms are cleared on reboot and when exact-alarm access changes.
        scheduledAlarm = null
        activeNotice = null
        lastRestState = null
        sync(context, WorkoutStore(context).load())
    }
}

/** Reject broadcasts from timers that were skipped, replaced, or belong to an ended workout. */
internal fun restAlarmMatches(workout: Workout?, workoutId: String?, deadline: Long, now: Long): Boolean =
    workout != null && workout.id == workoutId && deadline > 0 && workout.restUntil == deadline && now >= deadline

class WorkoutRestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WorkoutNotifications.REST_FINISHED) return
        runCatching {
            WorkoutNotifications.deliverRest(context, intent.getStringExtra(WorkoutNotifications.WORKOUT_ID), intent.getLongExtra(WorkoutNotifications.REST_DEADLINE, 0L))
        }.onFailure { android.util.Log.w("WorkoutNotifications", "Could not deliver rest reminder", it) }
    }
}

class WorkoutReminderRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(Intent.ACTION_BOOT_COMPLETED, AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED))
            WorkoutNotifications.reschedule(context)
    }
}
