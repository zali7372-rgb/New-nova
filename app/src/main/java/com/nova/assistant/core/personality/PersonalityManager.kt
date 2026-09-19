package com.nova.assistant.core.personality

import kotlin.random.Random

/**
 * Centralizes NOVA's "voice" - greetings, help text, and the persona
 * description handed to AI providers - so tone stays consistent everywhere
 * and can be reskinned from one file.
 */
class PersonalityManager {

    fun greeting(): String = pick(GREETINGS)

    fun activationAck(): String = pick(ACTIVATION_ACKS)

    fun helpText(): String = HELP_TEXT

    fun errorMessage(context: String): String = "Hiba történt ($context). Próbáld meg újra."

    fun systemPersonaDescription(): String =
        "A hangneme magabiztos, tömör és segítőkész, de nem túl bőbeszédű. Kerüli a " +
            "felesleges udvariassági köröket, és a lényegre tér."

    private fun pick(options: List<String>) = options[Random.nextInt(options.size)]

    companion object {
        private val GREETINGS = listOf(
            "Szia! Miben segíthetek?",
            "Üdv! NOVA vagyok, hallgatlak.",
            "Szia! Készen állok."
        )
        private val ACTIVATION_ACKS = listOf(
            "Hallgatlak.",
            "Igen?",
            "Mondd."
        )
        private const val HELP_TEXT = "Én NOVA vagyok. Tudok alkalmazásokat megnyitni, " +
            "beállításokat kezelni, emlékezni dolgokra, időjárást mondani, internetes " +
            "keresést indítani, és beszélgetni is veled. Csak mondd el, mire van szükséged."
    }
}
