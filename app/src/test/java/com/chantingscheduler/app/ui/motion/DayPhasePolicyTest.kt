package com.chantingscheduler.app.ui.motion

import org.junit.Assert.assertEquals
import org.junit.Test

class DayPhasePolicyTest {

    // 2024-01-01T00:00:00Z — a known UTC midnight so hour math is exact.
    private val midnight = 1_704_067_200_000L

    @Test
    fun `hour table without sun times`() {
        assertEquals(DayPhase.DAWN, DayPhasePolicy.phaseAt(hour(6), null, null))
        assertEquals(DayPhase.DAY, DayPhasePolicy.phaseAt(hour(12), null, null))
        assertEquals(DayPhase.DUSK, DayPhasePolicy.phaseAt(hour(18), null, null))
        assertEquals(DayPhase.NIGHT, DayPhasePolicy.phaseAt(hour(23), null, null))
        assertEquals(DayPhase.NIGHT, DayPhasePolicy.phaseAt(hour(3), null, null))
    }

    @Test
    fun `sun times win over the hour table`() {
        val sunrise = hour(10)
        val sunset = hour(22)
        assertEquals(DayPhase.DAWN, DayPhasePolicy.phaseAt(hour(9), sunrise, sunset))
        assertEquals(DayPhase.DAY, DayPhasePolicy.phaseAt(hour(15), sunrise, sunset))
        assertEquals(DayPhase.DUSK, DayPhasePolicy.phaseAt(hour(22, 30), sunrise, sunset))
        assertEquals(DayPhase.NIGHT, DayPhasePolicy.phaseAt(hour(4), sunrise, sunset))
    }

    @Test
    fun `invalid sun order falls back to the hour table`() {
        assertEquals(DayPhase.DAY, DayPhasePolicy.phaseAt(hour(12), hour(20), hour(6)))
    }

    private fun hour(h: Int, min: Int = 0): Long = midnight + ((h * 60L + min) * 60_000L)
}
