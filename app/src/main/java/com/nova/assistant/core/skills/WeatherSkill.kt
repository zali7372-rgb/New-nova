package com.nova.assistant.core.skills

import com.nova.assistant.core.intent.IntentResult
import com.nova.assistant.core.intent.NovaIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Real weather lookups via Open-Meteo (https://open-meteo.com), a free
 * weather API that requires no API key - so weather works out of the box
 * with zero configuration, unlike the general-purpose RemoteAIProvider.
 *
 * Location defaults to Budapest when the user didn't specify a city (e.g.
 * "Milyen idő lesz holnap?" with no city named); ContextManager fills in a
 * previously mentioned city automatically for follow-ups like "És Szegeden?".
 */
class WeatherSkill(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()
) : Skill {
    override val name = "WeatherSkill"
    override val description = "Aktuális időjárás lekérdezése egy adott városra."
    override val supportedIntents = setOf(NovaIntent.WEATHER)
    override val requiredPermissions: Set<String> = setOf(android.Manifest.permission.INTERNET)

    override suspend fun execute(intentResult: IntentResult): SkillResult {
        val city = intentResult.slots["location"] ?: DEFAULT_CITY
        return withContext(Dispatchers.IO) {
            try {
                val coords = geocode(city) ?: return@withContext SkillResult(
                    "Nem találtam ilyen nevű települést: $city"
                )
                val forecast = fetchForecast(coords.first, coords.second)
                    ?: return@withContext SkillResult(
                        "Sikerült megtalálni $city -t, de az időjárás lekérdezése most nem sikerült."
                    )
                SkillResult("$city -n jelenleg ${forecast.first}°C van, ${describeWeatherCode(forecast.second)}.")
            } catch (e: Exception) {
                SkillResult("Nem sikerült lekérdezni az időjárást: ${e.message ?: "ismeretlen hiba"}")
            }
        }
    }

    private fun geocode(city: String): Pair<Double, Double>? {
        val encoded = URLEncoder.encode(city, "UTF-8")
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=hu"
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val results = JSONObject(body).optJSONArray("results") ?: return null
            if (results.length() == 0) return null
            val first = results.getJSONObject(0)
            return first.getDouble("latitude") to first.getDouble("longitude")
        }
    }

    private fun fetchForecast(lat: Double, lon: Double): Pair<Double, Int>? {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code"
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val current = JSONObject(body).optJSONObject("current") ?: return null
            val temp = current.optDouble("temperature_2m", Double.NaN)
            val code = current.optInt("weather_code", -1)
            if (temp.isNaN()) return null
            return temp to code
        }
    }

    /** WMO weather codes, condensed to the phrases people actually care about. */
    private fun describeWeatherCode(code: Int): String = when (code) {
        0 -> "tiszta, napos idő van"
        1, 2, 3 -> "részben felhős"
        45, 48 -> "ködös"
        51, 53, 55, 56, 57 -> "szitáló eső várható"
        61, 63, 65, 66, 67 -> "esik az eső"
        71, 73, 75, 77 -> "havazik"
        80, 81, 82 -> "záporok várhatók"
        95, 96, 99 -> "zivatar várható"
        else -> "vegyes időjárás várható"
    }

    companion object {
        private const val DEFAULT_CITY = "Budapest"
    }
}
