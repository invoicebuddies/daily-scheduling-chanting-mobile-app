package com.chantingscheduler.app.ui.motion

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Language/accessibility-safety guard: at Off (and Minimal) no infinite
 * ambient transition may exist at all — static artwork only. At Calm the
 * ambient loops actually run. Asserts on the debug counter maintained by
 * [trackAmbient] / [rememberAmbientFloat].
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class MotionOffTest {

    @get:Rule
    val composeRule = createComposeRule()

    @org.junit.Before
    fun resetCounter() {
        // Isolate from other tests' compositions in the same JVM.
        MotionDebug.runningInfiniteTransitions.set(0)
    }

    @Test
    fun `Off level composes no running infinite transitions`() {
        composeRule.setContent {
            CompositionLocalProvider(LocalMotion provides MotionLevel.OFF) {
                SceneArt(scene = ArtScene.LOTUS, phase = DayPhase.DAY, animate = rememberAmbientActive())
            }
        }
        composeRule.waitForIdle()
        assertEquals(0, MotionDebug.runningInfiniteTransitions.get())
    }

    @Test
    fun `Minimal level composes no running infinite transitions`() {
        composeRule.setContent {
            CompositionLocalProvider(LocalMotion provides MotionLevel.MINIMAL) {
                SceneArt(scene = ArtScene.TEMPLE, phase = DayPhase.NIGHT, animate = rememberAmbientActive())
            }
        }
        composeRule.waitForIdle()
        assertEquals(0, MotionDebug.runningInfiniteTransitions.get())
    }

    @Test
    fun `Calm level runs the ambient loop`() {
        composeRule.setContent {
            CompositionLocalProvider(LocalMotion provides MotionLevel.CALM) {
                SceneArt(scene = ArtScene.LOTUS, phase = DayPhase.DAWN, animate = rememberAmbientActive())
            }
        }
        composeRule.waitForIdle()
        assertTrue(MotionDebug.runningInfiniteTransitions.get() >= 1)
    }

    @Test
    fun `Monk scene at Off runs nothing either`() {
        composeRule.setContent {
            CompositionLocalProvider(LocalMotion provides MotionLevel.OFF) {
                SceneArt(scene = ArtScene.MONK, phase = DayPhase.DAY, animate = rememberAmbientActive())
            }
        }
        composeRule.waitForIdle()
        assertEquals(0, MotionDebug.runningInfiniteTransitions.get())
    }
}
