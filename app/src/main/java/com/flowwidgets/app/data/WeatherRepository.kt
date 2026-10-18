package com.flowwidgets.app.data

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object WeatherRepository {
    private fun request(url: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        return try {
            check(connection.responseCode in 200..299) { "Weather HTTP ${connection.responseCode}" }
            connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun search(city: String): List<WeatherLocation> =
        withContext(Dispatchers.IO) {
            if (city.isBlank()) return@withContext emptyList()
            val result =
                request(
                        "https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(city,"UTF-8")}&count=8&language=es&format=json"
                    )
                    .optJSONArray("results") ?: JSONArray()
            (0 until result.length()).map {
                val j = result.getJSONObject(it)
                WeatherLocation(
                    listOf(j.optString("name"), j.optString("admin1"), j.optString("country"))
                        .filter { it.isNotBlank() }
                        .distinct()
                        .joinToString(", "),
                    j.getDouble("latitude"),
                    j.getDouble("longitude"),
                )
            }
        }

    private fun key(c: WidgetConfig) = "weather_${c.latitude}_${c.longitude}"

    fun cached(context: Context, config: WidgetConfig): WeatherSnapshot? =
        runCatching {
                val raw =
                    context
                        .getSharedPreferences("weather_cache", Context.MODE_PRIVATE)
                        .getString(key(config), null) ?: return null
                decode(JSONObject(raw))
            }
            .getOrNull()

    suspend fun refresh(context: Context, config: WidgetConfig): WeatherSnapshot? =
        refreshWith(context, config, ::request)

    internal suspend fun refreshWith(
        context: Context,
        config: WidgetConfig,
        fetch: (String) -> JSONObject,
    ): WeatherSnapshot? =
        withContext(Dispatchers.IO) {
            val lat = config.latitude ?: return@withContext null
            val lon = config.longitude ?: return@withContext null
            try {
                val j =
                    fetch(
                        "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code,relative_humidity_2m,apparent_temperature,wind_speed_10m&hourly=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min,weather_code,precipitation_probability_max&timezone=auto&forecast_days=5"
                    )
                val current = j.getJSONObject("current")
                val daily = j.getJSONObject("daily")
                val hourly = j.getJSONObject("hourly")
                val times = hourly.getJSONArray("time")
                val temps = hourly.getJSONArray("temperature_2m")
                val codes = hourly.getJSONArray("weather_code")
                val currentTime = current.getString("time").take(13)
                val start =
                    (0 until times.length()).firstOrNull {
                        times.getString(it).take(13) >= currentTime
                    } ?: 0
                val hours =
                    (start until minOf(start + 6, times.length())).map {
                        WeatherHour(
                            times.getString(it).substringAfter('T'),
                            temps.getDouble(it),
                            codes.getInt(it),
                        )
                    }
                val snapshot =
                    WeatherSnapshot(
                        current.getDouble("temperature_2m"),
                        description(current.getInt("weather_code")),
                        daily.getJSONArray("temperature_2m_max").getDouble(0),
                        daily.getJSONArray("temperature_2m_min").getDouble(0),
                        hours,
                        System.currentTimeMillis(),
                        current.getInt("weather_code"),
                        daily.optJSONArray("time")?.let { a ->
                            (0 until a.length()).map { i ->
                                WeatherDay(
                                    a.getString(i),
                                    daily.getJSONArray("temperature_2m_max").getDouble(i),
                                    daily.getJSONArray("temperature_2m_min").getDouble(i),
                                    daily.optJSONArray("weather_code")?.optInt(i, -1) ?: -1,
                                    daily.optJSONArray("precipitation_probability_max")?.let { v ->
                                        if (v.isNull(i)) null
                                        else v.optInt(i).takeIf { it in 0..100 }
                                    },
                                )
                            }
                        } ?: emptyList(),
                        if (
                            current.isNull("relative_humidity_2m") ||
                                !current.has("relative_humidity_2m")
                        )
                            null
                        else current.optInt("relative_humidity_2m").takeIf { it in 0..100 },
                        if (
                            current.isNull("apparent_temperature") ||
                                !current.has("apparent_temperature")
                        )
                            null
                        else current.optDouble("apparent_temperature").takeIf { it.isFinite() },
                        if (current.isNull("wind_speed_10m") || !current.has("wind_speed_10m")) null
                        else current.optDouble("wind_speed_10m").takeIf { it.isFinite() && it >= 0 },
                    )
                context
                    .getSharedPreferences("weather_cache", Context.MODE_PRIVATE)
                    .edit()
                    .putString(key(config), encode(snapshot).toString())
                    .apply()
                snapshot
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                cached(context, config)
            }
        }

    fun description(code: Int): String =
        when (code) {
            0 -> "Despejado"
            1,
            2 -> "Parcialmente nublado"
            3 -> "Nublado"
            45,
            48 -> "Niebla"
            51,
            53,
            55,
            56,
            57 -> "Llovizna"
            61,
            63,
            65,
            66,
            67,
            80,
            81,
            82 -> "Lluvia"
            71,
            73,
            75,
            77,
            85,
            86 -> "Nieve"
            95,
            96,
            99 -> "Tormenta"
            else -> "Tiempo variable"
        }

    private fun encode(s: WeatherSnapshot) =
        JSONObject()
            .put("temp", s.temp)
            .put("description", s.description)
            .put("high", s.high)
            .put("low", s.low)
            .put("updatedAt", s.updatedAt)
            .put("code", s.code)
            .put(
                "days",
                JSONArray().apply {
                    s.days.forEach { d ->
                        put(
                            JSONObject()
                                .put("date", d.date)
                                .put("high", d.high)
                                .put("low", d.low)
                                .put("code", d.code)
                                .put("rain", d.rainProbability)
                        )
                    }
                },
            )
            .put("humidity", s.humidity)
            .put("feelsLike", s.feelsLike)
            .put("windKmh", s.windKmh)
            .put(
                "hours",
                JSONArray().apply {
                    s.hours.forEach {
                        put(
                            JSONObject()
                                .put("time", it.time)
                                .put("temp", it.temp)
                                .put("code", it.code)
                        )
                    }
                },
            )

    private fun decode(j: JSONObject): WeatherSnapshot {
        val h = j.getJSONArray("hours")
        return WeatherSnapshot(
            j.getDouble("temp"),
            j.getString("description"),
            j.getDouble("high"),
            j.getDouble("low"),
            (0 until h.length()).map {
                val o = h.getJSONObject(it)
                WeatherHour(o.getString("time"), o.getDouble("temp"), o.getInt("code"))
            },
            j.getLong("updatedAt"),
            j.optInt("code", -1),
            j.optJSONArray("days")?.let { a ->
                (0 until a.length()).map { i ->
                    val d = a.getJSONObject(i)
                    WeatherDay(
                        d.getString("date"),
                        d.getDouble("high"),
                        d.getDouble("low"),
                        d.optInt("code", -1),
                        if (d.isNull("rain")) null else d.optInt("rain"),
                    )
                }
            } ?: emptyList(),
            if (j.isNull("humidity")) null else j.optInt("humidity"),
            if (j.isNull("feelsLike")) null else j.optDouble("feelsLike"),
            if (j.isNull("windKmh")) null else j.optDouble("windKmh"),
        )
    }
}
