package com.caicai.garden.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import kotlin.math.roundToInt

class WeatherService {
    suspend fun fetch(garden: Garden): WeatherForecast = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(
                "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=${garden.latitude}" +
                    "&longitude=${garden.longitude}" +
                    "&current=temperature_2m,relative_humidity_2m,wind_speed_10m" +
                    "&daily=temperature_2m_max,temperature_2m_min,precipitation_sum" +
                    "&forecast_days=7" +
                    "&timezone=auto"
            )
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            decode(JSONObject(body), garden.locationName)
        }.getOrElse {
            fallback(garden.locationName)
        }
    }

    private fun decode(json: JSONObject, locationName: String): WeatherForecast {
        val current = json.getJSONObject("current")
        val dailyJson = json.getJSONObject("daily")
        val dates = dailyJson.getJSONArray("time")
        val maxTemps = dailyJson.getJSONArray("temperature_2m_max")
        val minTemps = dailyJson.getJSONArray("temperature_2m_min")
        val precipitations = dailyJson.getJSONArray("precipitation_sum")
        val days = (0 until dates.length()).map { index ->
            WeatherDay(
                date = LocalDate.parse(dates.getString(index)),
                maxTempC = maxTemps.getDouble(index),
                minTempC = minTemps.getDouble(index),
                precipitationMm = precipitations.getDouble(index)
            )
        }

        return WeatherForecast(
            locationName = locationName,
            currentTempC = current.getDouble("temperature_2m"),
            humidityPercent = current.optDouble("relative_humidity_2m", 0.0).roundToInt(),
            windKmh = current.optDouble("wind_speed_10m", 0.0),
            daily = days,
            source = "Open-Meteo"
        )
    }

    private fun fallback(locationName: String): WeatherForecast {
        val today = LocalDate.now()
        val days = (0..6).map { offset ->
            WeatherDay(
                date = today.plusDays(offset.toLong()),
                maxTempC = 30.0 + (offset % 2),
                minTempC = 21.0,
                precipitationMm = if (offset == 2) 4.0 else 0.0
            )
        }
        return WeatherForecast(
            locationName = locationName,
            currentTempC = 28.0,
            humidityPercent = 58,
            windKmh = 7.0,
            daily = days,
            source = "离线估算",
            isFallback = true
        )
    }
}
