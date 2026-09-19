package com.nova.assistant

import com.google.common.truth.Truth.assertThat
import com.nova.assistant.core.intent.IntentEngine
import com.nova.assistant.core.intent.NovaIntent
import org.junit.Test

class IntentEngineTest {

    private val engine = IntentEngine()

    @Test
    fun `open app variations all classify as OPEN_APP`() {
        val utterances = listOf(
            "Nyisd meg a YouTube-ot",
            "Indítsd el a Discordot",
            "nyisd meg a discordot",
            "indítsd el a discordot"
        )
        utterances.forEach { utterance ->
            val result = engine.classify(utterance)
            assertThat(result.intent).isEqualTo(NovaIntent.OPEN_APP)
        }
    }

    @Test
    fun `open app extracts app name slot`() {
        val result = engine.classify("Nyisd meg a YouTube-ot")
        assertThat(result.slots["appName"]).isNotEmpty()
    }

    @Test
    fun `memory save extracts fact and classifies correctly`() {
        val result = engine.classify("Nova, jegyezd meg, hogy szeretem az angol zenét")
        assertThat(result.intent).isEqualTo(NovaIntent.MEMORY_SAVE)
        assertThat(result.slots["fact"]).contains("angol zenét")
    }

    @Test
    fun `memory read phrases classify as MEMORY_READ`() {
        val result = engine.classify("Mit szeretek?")
        assertThat(result.intent).isEqualTo(NovaIntent.MEMORY_READ)
    }

    @Test
    fun `search web classifies and extracts query`() {
        val result = engine.classify("Keress rá erre az interneten: legjobb androidos telefonok")
        assertThat(result.intent).isEqualTo(NovaIntent.SEARCH_WEB)
    }

    @Test
    fun `greeting classifies as GREETING`() {
        val result = engine.classify("Szia Nova")
        assertThat(result.intent).isEqualTo(NovaIntent.GREETING)
    }

    @Test
    fun `help phrase classifies as HELP`() {
        val result = engine.classify("Nova, mit tudsz?")
        assertThat(result.intent).isEqualTo(NovaIntent.HELP)
    }

    @Test
    fun `weather phrase classifies as WEATHER`() {
        val result = engine.classify("Milyen idő lesz holnap?")
        assertThat(result.intent).isEqualTo(NovaIntent.WEATHER)
    }

    @Test
    fun `unrecognized short utterance with pending context becomes CONVERSATION`() {
        val result = engine.classify("És Szegeden?", hasPendingContext = true)
        assertThat(result.intent).isEqualTo(NovaIntent.CONVERSATION)
    }

    @Test
    fun `unrecognized utterance without context becomes GENERAL_QUESTION`() {
        val result = engine.classify("Mi az élet értelme?", hasPendingContext = false)
        assertThat(result.intent).isEqualTo(NovaIntent.GENERAL_QUESTION)
    }

    @Test
    fun `blank utterance is UNKNOWN`() {
        val result = engine.classify("   ")
        assertThat(result.intent).isEqualTo(NovaIntent.UNKNOWN)
    }

    @Test
    fun `device action wifi recognized`() {
        val result = engine.classify("Nyisd meg a Wifit")
        assertThat(result.intent).isEqualTo(NovaIntent.DEVICE_ACTION)
        assertThat(result.slots["action"]).isEqualTo("wifi")
    }

    @Test
    fun `device action does not shadow named app open`() {
        // Regression test: "nyisd meg a chrome-ot" must resolve to OPEN_APP
        // (launch the actual Chrome app), not to the generic "open a browser"
        // device action, even though both start with "nyisd meg a". This
        // previously wasn't caught because no test exercised this exact phrase.
        val result = engine.classify("Nyisd meg a Chrome-ot")
        assertThat(result.intent).isEqualTo(NovaIntent.OPEN_APP)
        assertThat(result.slots["appName"]).isEqualTo("Chrome")
    }

    @Test
    fun `weather extracts location with correct Hungarian locative suffix`() {
        // Regression test: city names take -on/-en/-ön ("Budapesten",
        // "Szegeden"), not -ban/-ben as the original extractLocation assumed -
        // the original version never actually matched a real city name.
        val result = engine.classify("Milyen idő van Budapesten?")
        assertThat(result.intent).isEqualTo(NovaIntent.WEATHER)
        assertThat(result.slots["location"]).isEqualTo("Budapest")
    }

    @Test
    fun `weather without a city has no location slot`() {
        val result = engine.classify("Milyen idő van?")
        assertThat(result.intent).isEqualTo(NovaIntent.WEATHER)
        assertThat(result.slots).doesNotContainKey("location")
    }

    @Test
    fun `follow-up extracts location for context merge`() {
        // Regression test: without extracting a location in the CONVERSATION
        // fallback path itself, CommandProcessor's context-merge step would
        // never see a new city - "És Szegeden?" would silently keep
        // whatever city was asked about previously, forever.
        val result = engine.classify("És Szegeden?", hasPendingContext = true)
        assertThat(result.intent).isEqualTo(NovaIntent.CONVERSATION)
        assertThat(result.slots["location"]).isEqualTo("Szeged")
    }

    @Test
    fun `search query extraction handles accented trigger words`() {
        // Regression test: raw text naturally contains "rá" (accented) where
        // the ASCII trigger constant has "ra" - a plain case-insensitive
        // substring search alone misses this and falls back to including the
        // whole utterance (verb and all) in the "query" slot.
        val result = engine.classify("Keress rá erre az interneten: legjobb androidos telefonok")
        assertThat(result.intent).isEqualTo(NovaIntent.SEARCH_WEB)
        assertThat(result.slots["query"]).contains("androidos telefonok")
        assertThat(result.slots["query"]).doesNotContain("keress")
    }
}
