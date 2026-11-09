package com.chantingscheduler.app.playback

/** One step of a routine: loop [soundUri] (null = silence) for [durationMinutes]. */
data class RoutineStepPlan(val soundUri: String?, val durationMinutes: Int)

/**
 * Sanitized, ready-to-run routine definition. Steps with a non-positive
 * duration are dropped; an empty plan means the schedule runs in playlist
 * mode (today's behavior).
 */
class RoutinePlan(steps: List<RoutineStepPlan>) {

    val steps: List<RoutineStepPlan> = steps.filter { it.durationMinutes > 0 }

    val isRoutine: Boolean get() = steps.isNotEmpty()

    val stepCount: Int get() = steps.size

    /** Steps that actually render audio (silence steps excluded). */
    val playableStepCount: Int get() = steps.count { it.soundUri != null }

    val totalDurationMinutes: Int get() = steps.sumOf { it.durationMinutes }

    fun isSilence(index: Int): Boolean = steps[index].soundUri == null

    fun soundUri(index: Int): String? = steps[index].soundUri

    fun durationMinutes(index: Int): Int = steps[index].durationMinutes

    fun durationMillis(index: Int): Long = steps[index].durationMinutes * 60_000L

    /** Index of the step after [index], or -1 when the routine is finished. */
    fun indexAfter(index: Int): Int = if (index + 1 < stepCount) index + 1 else -1

    companion object {
        /** Builds a plan from the service's parallel-array intent extras. */
        fun fromExtras(uris: List<String>, minutes: List<Int>): RoutinePlan = RoutinePlan(
            uris.zip(minutes) { uri, mins ->
                RoutineStepPlan(soundUri = uri.ifBlank { null }, durationMinutes = mins)
            },
        )
    }
}
