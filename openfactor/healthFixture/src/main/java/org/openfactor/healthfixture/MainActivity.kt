package org.openfactor.healthfixture

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import androidx.lifecycle.lifecycleScope
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.launch

/** Test-only external data origin. Never a dependency of the production app. */
class MainActivity : ComponentActivity() {
    private lateinit var status: TextView
    private lateinit var seed: Button
    private val required = setOf(
        HealthPermission.getWritePermission(WeightRecord::class),
        HealthPermission.getWritePermission(BodyFatRecord::class),
        HealthPermission.getWritePermission(StepsRecord::class),
    )
    private val permissions = registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        if (granted.containsAll(required)) writeFixture() else status.text = "Test permissions not granted. No fixture written."
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply {
            text = "TEST DATA ONLY\nExternal-origin Health Connect fixture\n\nThree dates: today and the previous two days. Today: 72.6 kg, 19% body fat and 4,321 steps. All records are labeled test fixtures and remain in this emulator's Health Connect store."
            textSize = 18f
        }
        seed = Button(this).apply { text = "Write test records"; setOnClickListener {
            lifecycleScope.launch {
                if (HealthConnectClient.getSdkStatus(this@MainActivity) != HealthConnectClient.SDK_AVAILABLE) {
                    status.text = "Health Connect unavailable on this test device."
                } else if (HealthConnectClient.getOrCreate(this@MainActivity).permissionController.getGrantedPermissions().containsAll(required)) writeFixture()
                else permissions.launch(required)
            }
        } }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 80, 32, 32)
            addView(status); addView(seed)
        })
    }
    private fun writeFixture() {
        seed.isEnabled = false
        lifecycleScope.launch {
            runCatching {
                val zone = ZoneId.systemDefault()
                val records = (0L..2L).flatMap { offset ->
                    val date = LocalDate.now().minusDays(offset)
                    val at = date.atStartOfDay(zone).plusHours(1)
                    listOf(
                        WeightRecord(at.toInstant(), at.offset, Mass.kilograms(72.6 + offset * .2), Metadata.manualEntry("test-weight:$date", 1)),
                        BodyFatRecord(at.toInstant(), at.offset, Percentage(19.0), Metadata.manualEntry("test-fat:$date", 1)),
                        StepsRecord(at.toInstant(), at.offset, at.plusMinutes(30).toInstant(), at.offset,
                            4321 + offset, Metadata.autoRecorded(device = Device(type = Device.TYPE_PHONE), clientRecordId = "test-steps:$date", clientRecordVersion = 1L)),
                    )
                }
                HealthConnectClient.getOrCreate(this@MainActivity).insertRecords(records)
            }.onSuccess { status.text = "TEST FIXTURE WRITTEN\n9 records, 3 dates\nToday: 72.6 kg · 19% · 4,321 steps\nOrigin: org.openfactor.healthfixture" }
                .onFailure { status.text = "Test fixture failed: ${it.javaClass.simpleName}: ${it.message}" }
            seed.isEnabled = true
        }
    }
}
