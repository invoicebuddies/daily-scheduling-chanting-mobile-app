package com.chantingscheduler.app.ui.motion

/**
 * Pure day-phase math for the scenery palettes. Tested in [MotionPolicyTest]'s
 * sibling suite.
 */
enum class DayPhase { DAWN, DAY, DUSK, NIGHT }

object DayPhasePolicy {

    /**
     * Phase at [nowMillis]: with sun times, dawn is the hour before sunrise,
     * dusk the hour after sunset; without them, a simple hour table. An
     * absent sun time (no location / polar day) falls back to the hour table.
     */
    fun phaseAt(
        nowMillis: Long,
        sunriseMillis: Long?,
        sunsetMillis: Long?,
    ): DayPhase {
        if (sunriseMillis != null && sunsetMillis != null &&
            sunriseMillis < sunsetMillis
        ) {
            val hour = 60 * 60_000L
            return when {
                nowMillis in (sunriseMillis - hour) until sunriseMillis -> DayPhase.DAWN
                nowMillis in sunriseMillis until sunsetMillis -> DayPhase.DAY
                nowMillis in sunsetMillis until (sunsetMillis + hour) -> DayPhase.DUSK
                else -> DayPhase.NIGHT
            }
        }
        val hour = ((nowMillis / 3_600_000L) % 24L + 24L) % 24L
        return when (hour) {
            in 5L until 8L -> DayPhase.DAWN
            in 8L until 17L -> DayPhase.DAY
            in 17L until 20L -> DayPhase.DUSK
            else -> DayPhase.NIGHT
        }
    }
}
