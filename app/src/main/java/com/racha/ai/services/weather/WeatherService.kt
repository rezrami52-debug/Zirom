package com.racha.ai.services.weather

import android.util.Log
import com.racha.ai.domain.model.WeatherInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * خدمة الطقس باستخدام Open-Meteo (مجاني، بلا مفتاح).
 */
object WeatherService {

    private const val TAG = "RachaWeather"

    /**
     * جلب الطقس الحالي.
     */
    suspend fun fetchWeather(
        latitude: Double,
        longitude: Double,
        cityName: String = ""
    ): WeatherInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL(
                "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$latitude" +
                "&longitude=$longitude" +
                "&current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code" +
                "&timezone=auto"
            )

            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val response = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(response)
            val current = json.getJSONObject("current")

            WeatherInfo(
                temperature = current.getDouble("temperature_2m"),
                feelsLike = current.getDouble("apparent_temperature"),
                humidity = current.getInt("relative_humidity_2m"),
                windSpeed = current.getDouble("wind_speed_10m"),
                weatherCode = current.getInt("weather_code"),
                cityName = cityName
            )
        } catch (e: Exception) {
            Log.e(TAG, "فشل جلب الطقس", e)
            null
        }
    }
}
