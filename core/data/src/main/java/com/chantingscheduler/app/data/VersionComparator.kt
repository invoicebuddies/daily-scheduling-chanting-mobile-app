package com.chantingscheduler.app.data

/**
 * Pure numeric version comparison for the GitHub update check: release tags
 * (`v1.10`) against the app's `versionName` (`1.9`). Compared part by part so
 * 1.10 > 1.9 and 1.3 > 1.2.1; lengths may differ. Anything malformed (empty,
 * non-numeric parts) counts as "not newer" — a weird tag must never prompt.
 */
object VersionComparator {

    fun isNewer(latestTag: String, currentVersion: String): Boolean {
        val latest = parse(latestTag) ?: return false
        val current = parse(currentVersion) ?: return false
        val max = maxOf(latest.size, current.size)
        for (i in 0 until max) {
            val a = latest.getOrElse(i) { 0 }
            val b = current.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun parse(version: String): List<Int>? {
        val cleaned = version.trim().removePrefix("v").removePrefix("V")
        if (cleaned.isEmpty()) return null
        val parts = cleaned.split('.')
        if (parts.any { it.isEmpty() || it.toIntOrNull() == null }) return null
        return parts.map { it.toInt() }
    }
}
