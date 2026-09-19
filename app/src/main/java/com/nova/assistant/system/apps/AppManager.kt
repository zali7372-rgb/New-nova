package com.nova.assistant.system.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.nova.assistant.data.db.AppAliasEntity
import com.nova.assistant.data.repository.MemoryRepository
import java.text.Normalizer
import java.util.Locale
import kotlin.math.max

data class InstalledApp(
    val packageName: String,
    val label: String,
    val isSystemApp: Boolean
)

sealed class LaunchResult {
    data class Success(val app: InstalledApp) : LaunchResult()
    data class NotFound(val query: String, val suggestions: List<InstalledApp>) : LaunchResult()
    data class Error(val message: String) : LaunchResult()
}

/**
 * Discovers real installed, launchable apps on the device (no hardcoded list),
 * matches free-form Hungarian/English requests against them using aliases and
 * fuzzy matching, and launches the best match.
 */
class AppManager(
    private val context: Context,
    private val memoryRepository: MemoryRepository
) {
    private val packageManager: PackageManager = context.packageManager

    suspend fun ensureDefaultAliasesSeeded() {
        memoryRepository.seedDefaultAliasesIfEmpty(
            DEFAULT_ALIASES.flatMap { (pkgHint, aliases) ->
                aliases.map { AppAliasEntity(pkgHint, it) }
            }
        )
    }

    fun getInstalledLaunchableApps(): List<InstalledApp> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = packageManager.queryIntentActivities(launcherIntent, 0)
        return resolved.map { info ->
            val appInfo: ApplicationInfo = info.activityInfo.applicationInfo
            InstalledApp(
                packageName = appInfo.packageName,
                label = packageManager.getApplicationLabel(appInfo).toString(),
                isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            )
        }.distinctBy { it.packageName }
    }

    suspend fun findBestMatch(query: String): InstalledApp? {
        val apps = getInstalledLaunchableApps()
        if (apps.isEmpty()) return null

        val normalizedQuery = normalize(query)
        val aliases = memoryRepository.getAllAliases().groupBy { it.packageName }

        // 1) Exact label match.
        apps.firstOrNull { normalize(it.label) == normalizedQuery }?.let { return it }

        // 2) Alias match: does any known alias for this package equal the query,
        //    or does the query contain/get-contained-by the alias?
        for (app in apps) {
            val appAliases = aliases[app.packageName]?.map { it.alias } ?: emptyList()
            val builtIn = builtInAliasesFor(app.packageName, app.label)
            val allAliases = (appAliases + builtIn).map { normalize(it) }
            if (allAliases.any { it == normalizedQuery }) return app
        }

        // 3) Label contains query or query contains label (handles "discord"
        //    matching "Discord" label and partial names).
        apps.firstOrNull {
            val l = normalize(it.label)
            l.contains(normalizedQuery) || normalizedQuery.contains(l)
        }?.let { return it }

        // 4) Fuzzy match via edit distance as a last resort, for typos/ASR noise.
        val scored = apps.map { app -> app to editDistance(normalize(app.label), normalizedQuery) }
        val best = scored.minByOrNull { it.second }
        val threshold = max(2, normalizedQuery.length / 3)
        return if (best != null && best.second <= threshold) best.first else null
    }

    suspend fun launch(query: String): LaunchResult {
        val match = findBestMatch(query)
            ?: return LaunchResult.NotFound(query, suggestSimilar(query))

        val launchIntent = packageManager.getLaunchIntentForPackage(match.packageName)
            ?: return LaunchResult.Error("A(z) ${match.label} alkalmazás nem indítható el.")

        return try {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            LaunchResult.Success(match)
        } catch (e: Exception) {
            LaunchResult.Error("Nem sikerült elindítani a(z) ${match.label} alkalmazást: ${e.message}")
        }
    }

    private fun suggestSimilar(query: String): List<InstalledApp> {
        val normalizedQuery = normalize(query)
        return getInstalledLaunchableApps()
            .map { it to editDistance(normalize(it.label), normalizedQuery) }
            .sortedBy { it.second }
            .take(3)
            .map { it.first }
    }

    /** Common Hungarian/English aliases for popular apps, matched by package-name substring. */
    private fun builtInAliasesFor(packageName: String, label: String): List<String> {
        val hits = mutableListOf(label)
        for ((needle, aliases) in KNOWN_PACKAGE_ALIASES) {
            if (packageName.contains(needle, ignoreCase = true)) hits += aliases
        }
        return hits
    }

    private fun normalize(input: String): String {
        val lower = input.lowercase(Locale("hu"))
        val decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9]"), "")
    }

    private fun editDistance(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                }
            }
        }
        return dp[a.length][b.length]
    }

    companion object {
        /** package-name-substring -> known Hungarian/English aliases people actually say. */
        private val KNOWN_PACKAGE_ALIASES = mapOf(
            "discord" to listOf("discord", "disc", "dc"),
            "youtube" to listOf("youtube", "yt", "jutub"),
            "chrome" to listOf("chrome", "krom", "böngésző"),
            "whatsapp" to listOf("whatsapp", "wazap", "vocap"),
            "instagram" to listOf("instagram", "insta", "ig"),
            "facebook" to listOf("facebook", "fészbuk", "fb"),
            "spotify" to listOf("spotify", "szpotifáj"),
            "gmail" to listOf("gmail", "email", "levelezés"),
            "maps" to listOf("térkép", "google maps", "maps"),
            "camera" to listOf("kamera", "fényképező"),
            "settings" to listOf("beállítások", "settings")
        )

        /** Seeded on first run as user-editable aliases (package hint, list of aliases). */
        val DEFAULT_ALIASES: Map<String, List<String>> = mapOf(
            "com.discord" to listOf("discord", "disc", "dc"),
            "com.google.android.youtube" to listOf("youtube", "yt"),
            "com.android.chrome" to listOf("chrome", "böngésző")
        )
    }
}
