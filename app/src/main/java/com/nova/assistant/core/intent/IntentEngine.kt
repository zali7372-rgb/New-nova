package com.nova.assistant.core.intent

import java.text.Normalizer
import java.util.Locale

/**
 * Classifies a raw Hungarian utterance into a [NovaIntent].
 *
 * This is deliberately NOT a giant if/else of exact strings. Every rule
 * matches against a *normalized* form of the utterance (lowercased, accents
 * stripped, punctuation removed) and looks for any of a set of trigger
 * phrases/verb stems, so "Nyisd meg a Discordot", "indítsd el a discordot"
 * and "discord inditasa" all resolve identically. Word order and Hungarian
 * verb conjugation/suffixes ("-ot", "-t", "-ítsd el") are handled by matching
 * stems rather than whole words wherever possible.
 */
class IntentEngine {

    fun classify(rawUtterance: String, hasPendingContext: Boolean = false): IntentResult {
        val normalized = normalize(rawUtterance)

        if (normalized.isBlank()) {
            return IntentResult(NovaIntent.UNKNOWN, 0f, originalUtterance = rawUtterance)
        }

        val rules = listOf(
            ::matchGreeting,
            ::matchHelp,
            ::matchMemorySave,
            ::matchMemoryForget,
            ::matchMemoryRead,
            ::matchDeviceAction,
            ::matchOpenWebpage,
            ::matchOpenApp,
            ::matchSearchWeb,
            ::matchSettings,
            ::matchWeather,
            ::matchTime
        )

        for (rule in rules) {
            val result = rule(normalized, rawUtterance)
            if (result != null) return result
        }

        // No specific rule matched. If there's an open conversational thread
        // (e.g. the previous turn asked a question NOVA can follow up on) and
        // this utterance is short, treat it as a continuation rather than a
        // fresh, unrelated question.
        if (hasPendingContext && wordCount(normalized) <= 6) {
            // Bare follow-ups like "És Szegeden?" never reach matchWeather
            // (they contain no weather trigger phrase), so without extracting
            // a location here too, a city mentioned in a follow-up would
            // never reach CommandProcessor's context-merge step - the old
            // city would silently stick around forever instead of updating.
            val location = extractLocation(normalized)
            val slots = if (location != null) mapOf("location" to location) else emptyMap()
            return IntentResult(NovaIntent.CONVERSATION, 0.55f, slots = slots, originalUtterance = rawUtterance)
        }

        return IntentResult(NovaIntent.GENERAL_QUESTION, 0.4f, originalUtterance = rawUtterance)
    }

    // ---------------------------------------------------------------------
    // Individual rule matchers. Each returns null if it doesn't match.
    // ---------------------------------------------------------------------

    private fun matchGreeting(n: String, raw: String): IntentResult? {
        val triggers = listOf("szia nova", "hello nova", "szia", "szevasz nova", "helo nova")
        return if (triggers.any { n == it || n.startsWith("$it ") }) {
            IntentResult(NovaIntent.GREETING, 0.95f, originalUtterance = raw)
        } else null
    }

    private fun matchHelp(n: String, raw: String): IntentResult? {
        val triggers = listOf("mit tudsz", "segits", "segitsg", "mire vagy kepes", "milyen parancsokat ismersz", "help")
        return if (triggers.any { n.contains(it) }) {
            IntentResult(NovaIntent.HELP, 0.9f, originalUtterance = raw)
        } else null
    }

    private fun matchMemorySave(n: String, raw: String): IntentResult? {
        val triggers = listOf("jegyezd meg", "jegyezd fel", "emlekezz erre", "ne felejtsd el", "vesd fel hogy")
        val trigger = longestMatchingTrigger(n, triggers) ?: return null
        val fact = rawTextAfter(raw, trigger)
            .removePrefix(",")
            .trim()
        return IntentResult(
            NovaIntent.MEMORY_SAVE,
            0.9f,
            slots = mapOf("fact" to fact),
            originalUtterance = raw
        )
    }

    /**
     * Prefers the longest trigger that's actually present, so e.g. for
     * "keress rá erre" both "keress ra" and "keress ra erre" are contained
     * substrings, but stripping the longer, more specific one leaves a
     * cleaner remainder (the query/fact text) with fewer leftover filler words.
     */
    private fun longestMatchingTrigger(n: String, triggers: List<String>): String? =
        triggers.filter { n.contains(it) }.maxByOrNull { it.length }

    /**
     * Finds [trigger] (an ASCII, accent-free phrase derived from the normalized
     * utterance) inside [raw] and returns everything after it, preserving the
     * user's original casing/accents in the result.
     *
     * Real Hungarian raw text often contains accented characters exactly where
     * an ASCII trigger constant has a plain one (e.g. raw "keress rá" vs.
     * trigger "keress ra") - a plain case-insensitive substring search alone
     * misses this (ignoreCase only folds case, it doesn't strip diacritics),
     * silently degrading to returning the whole utterance instead of just the
     * text after the trigger. This searches an accent-stripped,
     * LENGTH-PRESERVING view of raw (see [normalizePreserveLength]) so the
     * match position lines up with raw's own indices, then slices the real,
     * accented raw string at that position. Falls back to a plain
     * case-insensitive search, then to the full raw text, if that fails too.
     */
    private fun rawTextAfter(raw: String, trigger: String): String {
        val preserved = normalizePreserveLength(raw)
        val idx = preserved.indexOf(trigger)
        if (idx >= 0) {
            return raw.substring(minOf(idx + trigger.length, raw.length))
        }
        val fallbackIdx = raw.indexOf(trigger, ignoreCase = true)
        if (fallbackIdx < 0) return raw
        return raw.substring(minOf(fallbackIdx + trigger.length, raw.length))
    }

    /**
     * Like [normalize], but never collapses/trims whitespace or removes
     * characters - only lowercases and strips accents, one output character
     * per input character - so index i in the result stays aligned with
     * index i in the original [text]. [rawTextAfter] relies on this to find a
     * trigger phrase inside raw text that may use accented Hungarian
     * characters an (deliberately ASCII) trigger constant doesn't contain.
     */
    private fun normalizePreserveLength(text: String): String {
        val lower = text.lowercase(Locale("hu"))
        val decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD)
        val noAccents = decomposed.replace(Regex("\\p{Mn}+"), "")
        return noAccents.map { ch -> if (ch.isLetterOrDigit() || ch == ' ' || ch == '-' || ch == '?') ch else ' ' }
            .joinToString("")
    }

    private fun matchMemoryForget(n: String, raw: String): IntentResult? {
        val triggers = listOf("felejtsd el", "torold a", "torold ki", "felejtsd el hogy")
        return if (triggers.any { n.contains(it) }) {
            IntentResult(NovaIntent.MEMORY_FORGET, 0.85f, originalUtterance = raw)
        } else null
    }

    private fun matchMemoryRead(n: String, raw: String): IntentResult? {
        val triggers = listOf(
            "mit tudsz rolam", "mit szeretek", "mire emlekszel",
            "mi volt az elozo kerdesem", "mit mondtam korabban", "emlekszel meg"
        )
        return if (triggers.any { n.contains(it) }) {
            IntentResult(NovaIntent.MEMORY_READ, 0.9f, originalUtterance = raw)
        } else null
    }

    private fun matchOpenApp(n: String, raw: String): IntentResult? {
        // Two Hungarian word orders both mean "open/launch X":
        //   verb-first:  "nyisd meg a X-ot" / "indítsd el a X-ot"  -> name AFTER trigger
        //   noun-first:  "X indítása" / "X indítás"                -> name BEFORE trigger
        val verbFirstTriggers = listOf(
            "nyisd meg a", "nyisd meg", "inditsd el a", "inditsd el", "nyisd ki a", "indits"
        )
        val nounFirstTriggers = listOf("inditasa", "megnyitasa")

        for (t in verbFirstTriggers.sortedByDescending { it.length }) {
            val idx = n.indexOf(t)
            if (idx >= 0) {
                val appName = stripTrailingHungarianSuffix(
                    raw.substring(minOf(idx + t.length, raw.length)).trim().removeSuffix(".").trim()
                )
                if (appName.isNotBlank()) {
                    return IntentResult(
                        NovaIntent.OPEN_APP, 0.8f,
                        slots = mapOf("appName" to appName),
                        originalUtterance = raw
                    )
                }
            }
        }

        for (t in nounFirstTriggers.sortedByDescending { it.length }) {
            val idx = n.indexOf(t)
            if (idx >= 0) {
                val appName = stripTrailingHungarianSuffix(
                    raw.substring(0, minOf(idx, raw.length)).trim().removeSuffix(".").trim()
                )
                if (appName.isNotBlank()) {
                    return IntentResult(
                        NovaIntent.OPEN_APP, 0.8f,
                        slots = mapOf("appName" to appName),
                        originalUtterance = raw
                    )
                }
            }
        }

        return null
    }

    private fun matchOpenWebpage(n: String, raw: String): IntentResult? {
        val triggers = listOf("nyisd meg ezt az oldalt", "nyisd meg a weboldalt", "nyisd meg az oldalt")
        return if (triggers.any { n.contains(it) }) {
            IntentResult(NovaIntent.OPEN_WEBPAGE, 0.75f, originalUtterance = raw)
        } else null
    }

    private fun matchSearchWeb(n: String, raw: String): IntentResult? {
        val triggers = listOf(
            "keress ra", "keress ra erre", "keress az interneten", "kutass ra",
            "mi a legujabb hir", "keress egy videot", "google-ozd meg", "keresd meg az interneten"
        )
        val trigger = longestMatchingTrigger(n, triggers) ?: return null
        val query = rawTextAfter(raw, trigger).trim().ifBlank { raw }
        return IntentResult(
            NovaIntent.SEARCH_WEB,
            0.85f,
            slots = mapOf("query" to query),
            originalUtterance = raw
        )
    }

    private fun matchDeviceAction(n: String, raw: String): IntentResult? {
        val map = linkedMapOf(
            "wifi" to listOf("nyisd meg a wifit", "wifi beallitasok", "wifi beallitasai"),
            "bluetooth" to listOf("nyisd meg a bluetoothot", "bluetooth beallitasok"),
            "camera" to listOf("nyisd meg a kamerat", "inditsd el a kamerat"),
            "calendar" to listOf("nyisd meg a naptart", "naptar megnyitasa"),
            "clock" to listOf("nyisd meg az orat", "ora megnyitasa", "allits be egy ebresztot", "allits be egy emlekeztetot"),
            "contacts" to listOf("nyisd meg a kontaktokat", "nyisd meg a telefonkonyvet"),
            "phone_settings" to listOf("nyisd meg a beallitasokat", "telefon beallitasok"),
            "notification_settings" to listOf("ertesitesi beallitasok", "nyisd meg az ertesiteseket"),
            "app_settings" to listOf("alkalmazas beallitasai"),
            "browser" to listOf("nyisd meg a bongeszot")
        )
        for ((action, triggers) in map) {
            if (triggers.any { n.contains(it) }) {
                return IntentResult(
                    NovaIntent.DEVICE_ACTION,
                    0.85f,
                    slots = mapOf("action" to action),
                    originalUtterance = raw
                )
            }
        }
        return null
    }

    private fun matchSettings(n: String, raw: String): IntentResult? {
        val triggers = listOf("nyisd meg a nova beallitasait", "nova beallitasok", "allitsd at a beallitasokat")
        return if (triggers.any { n.contains(it) }) {
            IntentResult(NovaIntent.SETTINGS, 0.8f, originalUtterance = raw)
        } else null
    }

    private fun matchWeather(n: String, raw: String): IntentResult? {
        val triggers = listOf("milyen ido", "milyen az ido", "fog esni", "hideg lesz", "meleg lesz", "idojaras")
        val trigger = triggers.firstOrNull { n.contains(it) } ?: return null
        val location = extractLocation(n)
        return IntentResult(
            NovaIntent.WEATHER,
            0.85f,
            slots = if (location != null) mapOf("location" to location) else emptyMap(),
            originalUtterance = raw
        )
    }

    private fun matchTime(n: String, raw: String): IntentResult? {
        val triggers = listOf("hany ora van", "mennyi az ido", "mi az idopont")
        return if (triggers.any { n.contains(it) }) {
            IntentResult(NovaIntent.TIME, 0.9f, originalUtterance = raw)
        } else null
    }

    // ---------------------------------------------------------------------

    /**
     * Extracts a city name from a Hungarian locative-case word, e.g.
     * "Budapesten" -> "Budapest", "Szegeden" -> "Szeged". Hungarian's locative
     * suffix depends on vowel harmony and which case is used (-ban/-ben
     * "inside", -on/-en/-ön "at/on", -nál/-nél "by") - this covers the common
     * ones for city names as a pragmatic heuristic, not a full morphological
     * analyzer. Note "-on/-en/-ön" is the common case for city names
     * ("Budapesten", "Szegeden") - the original -ban/-ben-only version of
     * this function never actually matched real city names.
     */
    private fun extractLocation(n: String): String? {
        val match = Regex("([a-z]+)\\??$").find(n) ?: return null
        val word = match.groupValues[1]
        val suffixes = listOf("ban", "ben", "nal", "nel", "on", "en")
        for (suffix in suffixes.sortedByDescending { it.length }) {
            if (word.endsWith(suffix) && word.length > suffix.length + 1) {
                return word.dropLast(suffix.length).replaceFirstChar { it.uppercase(Locale("hu")) }
            }
        }
        return null
    }

    private fun stripTrailingHungarianSuffix(name: String): String {
        // Strip common Hungarian accusative/object suffixes so "Discordot" -> "Discord",
        // "Youtube-ot" -> "Youtube", "Beallitasokat" -> "Beallitasok" stays app-searchable.
        var result = name.trim()
        for (suffix in listOf("-ot", "-et", "-t", "-át")) {
            if (result.endsWith(suffix, ignoreCase = true)) {
                result = result.dropLast(suffix.length)
                return result
            }
        }
        return result
    }

    private fun wordCount(s: String) = s.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size

    /** Lowercase, strip accents, collapse punctuation/whitespace. */
    private fun normalize(input: String): String {
        val lower = input.lowercase(Locale("hu"))
        val decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD)
        val noAccents = decomposed.replace(Regex("\\p{Mn}+"), "")
        return noAccents
            .replace(Regex("[^a-z0-9?\\s-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
