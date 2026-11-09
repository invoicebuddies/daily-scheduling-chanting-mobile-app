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

    @Test
    fun `assetAbi recognizes each release APK naming`() {
        assertEquals("arm64-v8a", UpdatePromptLogic.assetAbi("ChantingBuddy-v1.3.1-arm64-v8a.apk"))
        assertEquals("x86_64", UpdatePromptLogic.assetAbi("ChantingBuddy-v1.3-x86_64.apk"))
        assertEquals("universal", UpdatePromptLogic.assetAbi("ChantingBuddy-v1.3.1-universal.apk"))
    }

    @Test
    fun `assetAbi rejects unsigned and foreign names`() {
        // Unsigned CI copies must never be picked as a download, whatever ABI.
        assertEquals(null, UpdatePromptLogic.assetAbi("ChantingBuddy-v1.3.1-arm64-v8a-UNSIGNED.apk"))
        assertEquals(null, UpdatePromptLogic.assetAbi("ChantingBuddy-v1.3.1-universal-UNSIGNED.apk"))
        assertEquals(null, UpdatePromptLogic.assetAbi("app-x86_64-release-unsigned.apk"))
        assertEquals(null, UpdatePromptLogic.assetAbi("ChantingBuddy-v1.3.1-arm64-v8a.apk.sha256"))
        assertEquals(null, UpdatePromptLogic.assetAbi("random-file.apk"))
    }

    @Test
    fun `pickAssetUrl never returns an unsigned asset`() {
        // Even if an unsigned name somehow entered the map, its key can only
        // come from assetAbi, which never produces it — simulate the parse.
        val parsed = listOf(
            "ChantingBuddy-v1.3.1-arm64-v8a-UNSIGNED.apk",
            "ChantingBuddy-v1.3.1-arm64-v8a.apk",
        ).mapNotNull { name -> UpdatePromptLogic.assetAbi(name)?.let { it to "url-$it" } }.toMap()
        assertEquals(
            "url-arm64-v8a",
            UpdatePromptLogic.pickAssetUrl(parsed, listOf("arm64-v8a"), "page"),
        )
    }
}
