package com.chantingscheduler.app.ui

/**
 * Builds the email body for the feedback form. Pure and string-only, so the
 * tests can pin the exact shape the owner receives.
 *
 * Privacy contract: the composer is never given schedule labels, sound names
 * or URIs, notes, location, or history — only the fields below. The topic
 * stays in English even in translated UIs so the owner can read it.
 */
object FeedbackComposer {

    const val RECIPIENT = "invoicebuddies@gmail.com"

    /** Constant for every topic, so the owner's inbox can filter reliably. */
    const val SUBJECT = "Daily Chanting App - Bug Report"

    enum class Topic(val englishLabel: String) {
        BUG("Report a bug"),
        FEATURE("Suggest a feature"),
        QUESTION("Question about using the app"),
        TRANSLATION("Translation issue"),
        OTHER("Something else"),
    }

    /**
     * Exactly what the "include app & device info" tick adds — nothing more,
     * ever. The preview in the UI renders these same fields.
     */
    data class Diagnostics(
        val appVersion: String,
        val androidVersion: String,
        val deviceManufacturerModel: String,
        val appLanguage: String,
        val exactAlarmsAllowed: Boolean,
        val batteryOptimisationExempt: Boolean,
        val fullScreenIntentAllowed: Boolean,
        val scheduleCount: Int,
    )

    fun body(
        topic: Topic,
        message: String,
        replyEmail: String?,
        includeDiagnostics: Boolean,
        diagnostics: Diagnostics?,
    ): String = buildString {
        appendLine("Topic: ${topic.englishLabel}")
        appendLine()
        appendLine(message.trim())
        replyEmail?.trim()?.takeIf { it.isNotEmpty() }?.let {
            appendLine()
            appendLine("Reply email: $it")
        }
        if (includeDiagnostics && diagnostics != null) {
            appendLine()
            appendLine("App version: ${diagnostics.appVersion}")
            appendLine("Android version: ${diagnostics.androidVersion}")
            appendLine("Device: ${diagnostics.deviceManufacturerModel}")
            appendLine("App language: ${diagnostics.appLanguage}")
            appendLine("Exact alarms: ${if (diagnostics.exactAlarmsAllowed) "Allowed" else "Not allowed"}")
            appendLine(
                "Battery optimisation: " +
                    if (diagnostics.batteryOptimisationExempt) "Unrestricted" else "Restricted",
            )
            appendLine(
                "Full-screen alarms: " +
                    if (diagnostics.fullScreenIntentAllowed) "Allowed" else "Not allowed",
            )
            appendLine("Schedules: ${diagnostics.scheduleCount}")
        }
    }
}
