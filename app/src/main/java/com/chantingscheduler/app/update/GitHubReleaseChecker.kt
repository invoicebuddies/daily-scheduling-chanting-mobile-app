package com.chantingscheduler.app.update

import com.chantingscheduler.app.data.UpdatePromptLogic
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Latest public release from GitHub, or null on any failure (never throws). */
data class ReleaseInfo(
    val tag: String,
    val name: String,
    /** Release notes body (markdown). */
    val body: String,
    /** Release page URL — last-resort download target. */
    val htmlUrl: String,
    /** Download URLs keyed by ABI tag ("arm64-v8a", "universal", …). */
    val assetUrls: Map<String, String>,
)

/**
 * Anonymous check against GitHub's latest-release endpoint. One request, no
 * user or device identifiers: a generic User-Agent with the app version and
 * the API Accept header. Any failure (offline, rate-limited, bad JSON) yields
 * null and stays silent.
 */
object GitHubReleaseChecker {

    private const val API_URL =
        "https://api.github.com/repos/invoicebuddies/daily-scheduling-chanting-mobile-app/releases/latest"
    private const val TIMEOUT_MS = 10_000

    suspend fun fetchLatest(userAgent: String): ReleaseInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(API_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", userAgent)
            if (conn.responseCode != 200) return@runCatching null
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            parse(text)
        }.getOrNull()
    }

    private fun parse(json: String): ReleaseInfo? = runCatching {
        val root = JSONObject(json)
        val tag = root.getString("tag_name")
        val assets = mutableMapOf<String, String>()
        val arr: JSONArray = root.optJSONArray("assets") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val asset = arr.getJSONObject(i)
            val abi = UpdatePromptLogic.assetAbi(asset.optString("name")) ?: continue
            assets[abi] = asset.getString("browser_download_url")
        }
        ReleaseInfo(
            tag = tag,
            name = root.optString("name").ifBlank { tag },
            body = root.optString("body"),
            htmlUrl = root.getString("html_url"),
            assetUrls = assets,
        )
    }.getOrNull()
}
