package org.openfactor.app

/** App-owned adapter: navigation owns the workflow; core commands own durable training changes. */
internal class ViewModelNavigationWorkouts(private val vm: WorkoutViewModel) : NavigationWorkoutActions {
    override val hasActiveWorkout get() = vm.data.value.active != null
    override fun prepareWorkout() { vm.commands.prepareWorkout() }
    override fun startEmptySession() { vm.commands.start() }
    override fun addSessionExercise(exerciseId: String) { vm.commands.addExercise(exerciseId) }
    override fun addDraftExercise(exerciseId: String) { vm.commands.addDraftExercise(exerciseId) }
    override fun addProgramExercise(dayId: String, exerciseId: String) {
        vm.commands.editProgramDraft { plan ->
            plan.copy(days = plan.days.map { day ->
                if (day.id == dayId) day.addExercise(
                    exerciseId, plan.cycleCount,
                    "Workout ${('A'.code + plan.days.count { it.exercises.isNotEmpty() }).toChar()}",
                ) else day
            })
        }
    }
}
