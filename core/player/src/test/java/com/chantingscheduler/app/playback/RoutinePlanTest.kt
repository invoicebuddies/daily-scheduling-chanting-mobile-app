package com.chantingscheduler.app.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutinePlanTest {

    @Test
    fun `empty plan means playlist mode`() {
        assertFalse(RoutinePlan(emptyList()).isRoutine)
        assertFalse(RoutinePlan.fromExtras(emptyList(), emptyList()).isRoutine)
    }

    @Test
    fun `steps with non-positive durations are dropped`() {
        val plan = RoutinePlan(
            listOf(
                RoutineStepPlan("uri1", 5),
                RoutineStepPlan("uri2", 0),
                RoutineStepPlan(null, -3),
                RoutineStepPlan("uri3", 10),
            ),
        )
        assertEquals(2, plan.stepCount)
        assertEquals(15, plan.totalDurationMinutes)
        assertEquals("uri1", plan.soundUri(0))
        assertEquals("uri3", plan.soundUri(1))
    }

    @Test
    fun `blank uri becomes a silence step`() {
        val plan = RoutinePlan.fromExtras(listOf("content://a", "", "content://b"), listOf(5, 3, 2))
        assertEquals(3, plan.stepCount)
        assertFalse(plan.isSilence(0))
        assertTrue(plan.isSilence(1))
        assertNull(plan.soundUri(1))
        assertEquals(3, plan.durationMinutes(1))
    }

    @Test
    fun `indexAfter walks the steps and signals the end`() {
        val plan = RoutinePlan(
            listOf(RoutineStepPlan("a", 1), RoutineStepPlan(null, 2)),
        )
        assertEquals(1, plan.indexAfter(0))
        assertEquals(-1, plan.indexAfter(1))
        assertEquals(-1, plan.indexAfter(2))
    }

    @Test
    fun `mismatched parallel arrays zip to the shorter length`() {
        val plan = RoutinePlan.fromExtras(
            uris = listOf("a", "b", "c"),
            minutes = listOf(5, 10),
        )
        assertEquals(2, plan.stepCount)
    }
}
