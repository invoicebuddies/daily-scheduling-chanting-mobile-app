package com.chantingscheduler.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdatePromptLogicTest {

    @Test
    fun `stripMarkdown keeps first lines and removes formatting`() {
        val body = """
            ## What's new

            - **Alarm reliability** fixes
            - Silent-alarm *fixes*
            [a link](https://example.com)
            ![img](https://example.com/i.png)

            Fourth line that should not appear
        """.trimIndent()
        assertEquals(
            "What's new\nAlarm reliability fixes\nSilent-alarm fixes",
            UpdatePromptLogic.stripMarkdown(body, 3),
        )
    }

    @Test
    fun `stripMarkdown collapses blanks`() {
        val body = "\n\n\none\n\n\ntwo\n\n"
        assertEquals("one\ntwo", UpdatePromptLogic.stripMarkdown(body, 3))
    }

    @Test
    fun `pickAssetUrl prefers first supported ABI`() {
        val assets = mapOf(
            "armeabi-v7a" to "url-32",
            "arm64-v8a" to "url-64",
            "universal" to "url-u",
        )
        assertEquals("url-64", UpdatePromptLogic.pickAssetUrl(assets, listOf("arm64-v8a", "armeabi-v7a"), "page"))
    }

    @Test
    fun `pickAssetUrl falls back to universal then release page`() {
        assertEquals("url-u", UpdatePromptLogic.pickAssetUrl(mapOf("universal" to "url-u"), listOf("arm64-v8a"), "page"))
        assertEquals("page", UpdatePromptLogic.pickAssetUrl(emptyMap(), listOf("arm64-v8a"), "page"))
    }
}
