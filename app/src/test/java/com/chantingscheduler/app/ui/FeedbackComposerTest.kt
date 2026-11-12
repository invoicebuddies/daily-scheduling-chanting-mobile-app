package com.chantingscheduler.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackComposerTest {

    private val diagnostics = FeedbackComposer.Diagnostics(
        appVersion = "1.4",
        androidVersion = "16",
        deviceManufacturerModel = "Google Pixel 6",
        appLanguage = "English",
        exactAlarmsAllowed = true,
        batteryOptimisationExempt = false,
        fullScreenIntentAllowed = true,
        scheduleCount = 3,
    )

    @Test
    fun `body starts with the topic in English for every topic`() {
        for (topic in FeedbackComposer.Topic.entries) {
            val body = FeedbackComposer.body(topic, "Hello", null, false, null)
            assertTrue(
                "topic $topic must lead the body",
                body.startsWith("Topic: ${topic.englishLabel}\n"),
            )
        }
    }

    @Test
    fun `message follows the topic`() {
        val body = FeedbackComposer.body(
            FeedbackComposer.Topic.BUG,
            "The alarm did not ring.",
            null,
            false,
            null,
        )
        assertEquals(
            "Topic: Report a bug\n\nThe alarm did not ring.\n",
            body,
        )
    }

    @Test
    fun `diagnostics block appears only when included`() {
        val with = FeedbackComposer.body(
            FeedbackComposer.Topic.QUESTION, "Why?", null, true, diagnostics,
        )
        assertTrue(with.contains("App version: 1.4"))
        assertTrue(with.contains("Android version: 16"))
        assertTrue(with.contains("Device: Google Pixel 6"))
        assertTrue(with.contains("Exact alarms: Allowed"))
        assertTrue(with.contains("Battery optimisation: Restricted"))
        assertTrue(with.contains("Schedules: 3"))

        val without = FeedbackComposer.body(
            FeedbackComposer.Topic.QUESTION, "Why?", null, false, diagnostics,
        )
        assertFalse(without.contains("App version"))
        assertFalse(without.contains("Device:"))

        // Ticked but no data gathered yet: still nothing.
        val noData = FeedbackComposer.body(
            FeedbackComposer.Topic.QUESTION, "Why?", null, true, null,
        )
        assertFalse(noData.contains("App version"))
    }

    @Test
    fun `reply email appears only when filled`() {
        val filled = FeedbackComposer.body(
            FeedbackComposer.Topic.OTHER, "Hi", "me@example.org", false, null,
        )
        assertTrue(filled.contains("Reply email: me@example.org"))

        val blank = FeedbackComposer.body(
            FeedbackComposer.Topic.OTHER, "Hi", "   ", false, null,
        )
        assertFalse(blank.contains("Reply email"))

        val absent = FeedbackComposer.body(
            FeedbackComposer.Topic.OTHER, "Hi", null, false, null,
        )
        assertFalse(absent.contains("Reply email"))
    }

    @Test
    fun `subject is constant for every topic`() {
        for (topic in FeedbackComposer.Topic.entries) {
            assertEquals("Daily Chanting App - Bug Report", FeedbackComposer.SUBJECT)
        }
    }

    @Test
    fun `body never contains schedule labels or sound uris`() {
        // Labels/URIs the app knows about but must never leak into feedback.
        val scheduleLabels = listOf("Morning chant", "ทำวัตรเช้า")
        val soundUris = listOf(
            "content://com.android.providers.media/documents/ chanting.mp3",
            "android.resource://com.chantingscheduler.app/raw/fallback_chime",
        )
        val body = FeedbackComposer.body(
            FeedbackComposer.Topic.BUG,
            "Something went wrong.",
            "a@b.co",
            true,
            diagnostics,
        )
        scheduleLabels.forEach { assertFalse(body.contains(it)) }
        soundUris.forEach { assertFalse(body.contains(it)) }
    }
}
