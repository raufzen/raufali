package com.example.domain.parser

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlin.math.max
import kotlin.math.min

data class InstalledAppInfo(
    val packageName: String,
    val label: String
)

/**
 * Matches spoken Hindi (Devanagari), Hinglish, and English app names against installed apps
 * using canonical alias dictionaries + token overlap + Levenshtein fuzzy similarity.
 */
class AppLauncherMatcher(
    private val installedAppsProvider: () -> List<InstalledAppInfo>
) {

    constructor(context: Context) : this({ queryInstalledLauncherApps(context) })

    fun findBestMatch(rawQuery: String): InstalledAppInfo? {
        val apps = installedAppsProvider()
        if (apps.isEmpty()) return null
        return findBestMatchInList(rawQuery, apps)
    }

    fun findBestMatchInList(rawQuery: String, apps: List<InstalledAppInfo>): InstalledAppInfo? {
        val cleaned = cleanQuery(rawQuery)
        if (cleaned.isEmpty()) return null

        val canonicalQueries = expandWithAliases(cleaned)

        var bestApp: InstalledAppInfo? = null
        var bestScore = 0.0

        for (app in apps) {
            val normalizedLabel = cleanQuery(app.label)
            val packageToken = app.packageName.substringAfterLast('.').lowercase()

            for (candidateQuery in canonicalQueries) {
                val score = scoreMatch(candidateQuery, normalizedLabel, packageToken)
                if (score > bestScore) {
                    bestScore = score
                    bestApp = app
                }
            }
        }

        return if (bestScore >= MATCH_THRESHOLD) bestApp else null
    }

    private fun expandWithAliases(cleanedQuery: String): List<String> {
        val expanded = mutableSetOf(cleanedQuery)
        ALIAS_TO_CANONICAL[cleanedQuery]?.let { expanded.addAll(it) }

        // Check partial alias containment for phrases like "whatsapp app" or "गूगल मैप्स ऐप"
        for ((alias, canonicals) in ALIAS_TO_CANONICAL) {
            if (cleanedQuery == alias || cleanedQuery.contains(alias)) {
                expanded.addAll(canonicals)
            }
        }
        return expanded.toList()
    }

    private fun scoreMatch(query: String, label: String, packageToken: String): Double {
        if (query == label) return 1.0
        if (query == packageToken && query.length >= 3) return 0.96
        if (label.startsWith(query) && query.length >= 3) return 0.92
        if (label.contains(query) && query.length >= 3) return 0.86
        if (query.contains(label) && label.length >= 3) return 0.82

        val distance = levenshteinDistance(query, label)
        val maxLen = max(query.length, label.length).coerceAtLeast(1)
        val similarity = 1.0 - (distance.toDouble() / maxLen.toDouble())

        // Token-level similarity for multi-word names like "Google Chrome" vs "Chrome"
        val labelTokens = label.split(" ").filter { it.length >= 2 }
        val tokenBest = labelTokens.maxOfOrNull { token ->
            val d = levenshteinDistance(query, token)
            1.0 - (d.toDouble() / max(query.length, token.length).coerceAtLeast(1).toDouble())
        } ?: 0.0

        return max(similarity, tokenBest * 0.95)
    }

    private fun cleanQuery(raw: String): String {
        return raw
            .lowercase()
            .replace(Regex("""\b(app|application)\b"""), "")
            .replace(Regex("""\s+(?:ऐप|एप)(?:\s+|$)"""), " ")
            .replace(Regex("""[^\p{L}\p{M}\p{N}\s]"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    companion object {
        private const val MATCH_THRESHOLD = 0.68

        /**
         * Rich Hindi (Devanagari) + Hinglish + shorthand aliases mapped to canonical English labels.
         */
        val ALIAS_TO_CANONICAL: Map<String, List<String>> = mapOf(
            // WhatsApp
            "व्हाट्सएप" to listOf("whatsapp"),
            "वॉट्सऐप" to listOf("whatsapp"),
            "वाट्सएप" to listOf("whatsapp"),
            "व्हाट्सऐप" to listOf("whatsapp"),
            "watsapp" to listOf("whatsapp"),
            "whatsap" to listOf("whatsapp"),
            "wa" to listOf("whatsapp"),

            // YouTube
            "यूट्यूब" to listOf("youtube"),
            "यूटूब" to listOf("youtube"),
            "utube" to listOf("youtube"),
            "yt" to listOf("youtube"),

            // Instagram
            "इंस्टाग्राम" to listOf("instagram"),
            "इंस्टा" to listOf("instagram"),
            "insta" to listOf("instagram"),

            // Facebook
            "फेसबुक" to listOf("facebook"),
            "fb" to listOf("facebook"),

            // Chrome & Google
            "क्रोम" to listOf("chrome", "google chrome"),
            "गूगल क्रोम" to listOf("chrome", "google chrome"),
            "गूगल" to listOf("google"),

            // Maps
            "मैप्स" to listOf("maps", "google maps"),
            "गूगल मैप्स" to listOf("maps", "google maps"),
            "नक्शा" to listOf("maps", "google maps"),

            // Gmail / Email
            "जीमेल" to listOf("gmail"),
            "ईमेल" to listOf("gmail", "mail"),

            // Payments (India)
            "पेटीएम" to listOf("paytm"),
            "फोनपे" to listOf("phonepe"),
            "फ़ोनपे" to listOf("phonepe"),
            "गूगल पे" to listOf("gpay", "google pay"),
            "जीपे" to listOf("gpay", "google pay"),
            "gpay" to listOf("google pay", "gpay"),

            // System & Utility Apps
            "कैमरा" to listOf("camera"),
            "सेटिंग्स" to listOf("settings"),
            "सेटिंग" to listOf("settings"),
            "गैलरी" to listOf("gallery", "photos"),
            "फोटो" to listOf("photos", "gallery"),
            "फोटोज" to listOf("photos", "gallery"),
            "कैलकुलेटर" to listOf("calculator"),
            "घड़ी" to listOf("clock"),
            "क्लॉक" to listOf("clock"),
            "अलार्म" to listOf("clock"),
            "कैलेंडर" to listOf("calendar"),
            "मैसेज" to listOf("messages"),
            "मैसेजेस" to listOf("messages"),
            "कॉन्टैक्ट्स" to listOf("contacts"),
            "फोन" to listOf("phone", "dialer"),
            "डायलर" to listOf("phone", "dialer"),
            "प्ले स्टोर" to listOf("play store", "google play store"),
            "स्पॉटिफाई" to listOf("spotify"),
            "टेलीग्राम" to listOf("telegram"),
            "अमेज़न" to listOf("amazon"),
            "फ्लिपकार्ट" to listOf("flipkart"),
            "ज़ोमैटो" to listOf("zomato"),
            "स्विगी" to listOf("swiggy"),
            "उबर" to listOf("uber"),
            "ओला" to listOf("ola"),
            "नोट्स" to listOf("notes", "keep notes")
        )

        fun levenshteinDistance(a: String, b: String): Int {
            if (a == b) return 0
            if (a.isEmpty()) return b.length
            if (b.isEmpty()) return a.length

            var prev = IntArray(b.length + 1) { it }
            var curr = IntArray(b.length + 1)

            for (i in 1..a.length) {
                curr[0] = i
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    curr[j] = min(
                        min(curr[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost
                    )
                }
                val tmp = prev
                prev = curr
                curr = tmp
            }
            return prev[b.length]
        }

        fun queryInstalledLauncherApps(context: Context): List<InstalledAppInfo> {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
            return resolveInfos
                .mapNotNull { info ->
                    val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                    val label = info.loadLabel(pm).toString().trim()
                    if (label.isBlank()) null else InstalledAppInfo(pkg, label)
                }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
        }
    }
}
