package com.chantingscheduler.app.data

/**
 * Pure presentation/decision helpers for the GitHub update prompt: markdown
 * stripping for the release notes blurb and best-asset selection.
 */
object UpdatePromptLogic {

    private val ASSET_NAME = Regex("""ChantingBuddy-v[0-9][0-9.]*-([A-Za-z0-9_-]+)\.apk""")

    /**
     * ABI tag ("arm64-v8a", "universal", …) for a release asset name, or null
     * when the name isn't a recognizable ChantingBuddy APK — including any
     * `-UNSIGNED.apk` build, which must never be offered as a download.
     */
    fun assetAbi(name: String): String? {
        val abi = ASSET_NAME.matchEntire(name)?.groupValues?.get(1) ?: return null
        return if (abi == "UNSIGNED" || abi.endsWith("-UNSIGNED")) null else abi
    }

    /**
     * First [maxLines] of the release body as plain text: markdown headers,
     * bold/italic, links, images, and list markers stripped; blank runs
     * collapsed. Cosmetic only — the goal is a calm, readable blurb.
     */
    fun stripMarkdown(body: String, maxLines: Int = 3): String {
        val cleaned = body.lines().map { line ->
            line
                .replace(Regex("!\\[[^]]*]\\([^)]*\\)"), "")
                .replace(Regex("\\[([^]]*)]\\([^)]*\\)"), "$1")
                .replace(Regex("[*_`#>]+"), "")
                .replace(Regex("^\\s*[-+]\\s+"), "")
                .trim()
        }
        return cleaned
            .filter { it.isNotEmpty() }
            .take(maxLines)
            .joinToString("\n")
    }

    /**
     * Best download URL for this device: the first supported ABI (in
     * priority order) with a matching asset, then the universal APK, then the
     * release page as a last resort.
     */
    fun pickAssetUrl(
        assets: Map<String, String>,
        supportedAbis: List<String>,
        htmlUrl: String,
    ): String {
        for (abi in supportedAbis) {
            assets[abi]?.let { return it }
        }
        assets["universal"]?.let { return it }
        return htmlUrl
    }
}
