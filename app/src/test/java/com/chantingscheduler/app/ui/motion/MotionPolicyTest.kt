package com.chantingscheduler.app.ui.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionPolicyTest {

    @Test
    fun `legacy reduce-motion true migrates to Minimal`() {
        assertEquals(MotionLevel.MINIMAL, MotionPolicy.migrateReduceMotion(true))
    }

    @Test
    fun `legacy reduce-motion false migrates to Calm`() {
        assertEquals(MotionLevel.CALM, MotionPolicy.migrateReduceMotion(false))
    }

    @Test
    fun `animator scale zero forces Off regardless of user choice`() {
        assertEquals(MotionLevel.OFF, MotionPolicy.effectiveLevel(MotionLevel.CALM, 0f))
        assertEquals(MotionLevel.OFF, MotionPolicy.effectiveLevel(MotionLevel.MINIMAL, 0f))
        assertEquals(MotionLevel.OFF, MotionPolicy.effectiveLevel(MotionLevel.OFF, 0f))
    }

    @Test
    fun `normal animator scale keeps the user choice`() {
        assertEquals(MotionLevel.CALM, MotionPolicy.effectiveLevel(MotionLevel.CALM, 1f))
        assertEquals(MotionLevel.MINIMAL, MotionPolicy.effectiveLevel(MotionLevel.MINIMAL, 0.5f))
        assertEquals(MotionLevel.OFF, MotionPolicy.effectiveLevel(MotionLevel.OFF, 1f))
    }

    @Test
    fun `ambient loops only run at Calm`() {
        assertTrue(MotionPolicy.ambientAllowed(MotionLevel.CALM))
        assertFalse(MotionPolicy.ambientAllowed(MotionLevel.MINIMAL))
        assertFalse(MotionPolicy.ambientAllowed(MotionLevel.OFF))
    }

    @Test
    fun `stored level strings round trip and unknown falls back to Calm`() {
        for (level in MotionLevel.entries) {
            assertEquals(level, MotionPolicy.levelFromStored(MotionPolicy.levelToStored(level)))
        }
        assertEquals(MotionLevel.CALM, MotionPolicy.levelFromStored(null))
        assertEquals(MotionLevel.CALM, MotionPolicy.levelFromStored("bogus"))
    }
}

class BreathCurveTest {

    @Test
    fun `one full loop returns to the start`() {
        assertEquals(0.0, BreathCurve.valueAt(0f), 0.0001)
        assertEquals(0.0, BreathCurve.valueAt(1f), 0.0001)
    }

    @Test
    fun `mid loop is full inhale`() {
        assertEquals(1.0, BreathCurve.valueAt(0.5f), 0.0001)
    }

    @Test
    fun `quarter breaths are half way`() {
        assertEquals(0.5, BreathCurve.valueAt(0.25f), 0.0001)
        assertEquals(0.5, BreathCurve.valueAt(0.75f), 0.0001)
    }

    @Test
    fun `phase is periodic`() {
        assertEquals(BreathCurve.valueAt(0.3f), BreathCurve.valueAt(1.3f), 0.0001)
    }
}
