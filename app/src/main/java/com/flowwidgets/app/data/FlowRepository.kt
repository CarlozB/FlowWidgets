package com.flowwidgets.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.flowStore by preferencesDataStore("flow_widgets")

object FlowRepository {
    private val widgets = stringPreferencesKey("widgets")
    private val themeKey = stringPreferencesKey("theme")

    fun configs(context: Context): Flow<List<WidgetConfig>> =
        context.applicationContext.flowStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { prefs -> decodeConfigs(prefs[widgets] ?: "[]") }

    internal fun decodeConfigs(raw: String): List<WidgetConfig> {
        val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        return (0 until array.length()).mapNotNull {
            runCatching { decode(array.getJSONObject(it)) }.getOrNull()
        }
    }

    suspend fun read(context: Context, id: Int): WidgetConfig? =
        configs(context).first().find { it.id == id }

    suspend fun save(context: Context, config: WidgetConfig) {
        context.applicationContext.flowStore.edit { prefs ->
            val array = runCatching { JSONArray(prefs[widgets] ?: "[]") }.getOrDefault(JSONArray())
            val result = JSONArray()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                if (item.optInt("id") != config.id) result.put(item)
            }
            result.put(encode(config))
            prefs[widgets] = result.toString()
        }
    }

    suspend fun delete(context: Context, id: Int) {
        context.applicationContext.flowStore.edit { prefs ->
            val source = runCatching { JSONArray(prefs[widgets] ?: "[]") }.getOrDefault(JSONArray())
            val result = JSONArray()
            for (i in 0 until source.length()) {
                val item = source.optJSONObject(i) ?: continue
                if (item.optInt("id") != id) result.put(item)
            }
            prefs[widgets] = result.toString()
        }
    }

    fun theme(context: Context): Flow<String> =
        context.applicationContext.flowStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { it[themeKey] ?: "system" }

    suspend fun setTheme(context: Context, value: String) {
        context.applicationContext.flowStore.edit { it[themeKey] = value }
    }

    fun encode(c: WidgetConfig) =
        JSONObject().apply {
            put("id", c.id)
            put("kind", c.kind.name)
            put("style", c.style.name)
            put("title", c.title)
            put("accent", c.accent)
            put("background", c.background)
            put("textColor", c.textColor)
            put("textScale", c.textScale)
            put("size", c.size)
            put("note", c.note)
            put("photoUri", c.photoUri)
            put("photoUris", JSONArray(c.photoUris))
            put("rotatePhotos", c.rotatePhotos)
            put("shortcutPackage", c.shortcutPackage)
            put("shortcutUri", c.shortcutUri)
            put("clock24", c.clock24)
            put("analog", c.analog)
            put("showDate", c.showDate)
            put("showSeconds", c.showSeconds)
            put("weekStartsMonday", c.weekStartsMonday)
            put("showEvents", c.showEvents)
            put("city", c.city)
            put("latitude", c.latitude)
            put("longitude", c.longitude)
            put("font", c.font)
            put("alignment", c.alignment)
            put("photoScale", c.photoScale)
            put("shortcutIcon", c.shortcutIcon)
        }

    fun decode(j: JSONObject): WidgetConfig {
        val d = WidgetConfig()
        fun optional(k: String) = if (j.isNull(k) || !j.has(k)) null else j.getString(k)
        val photos = j.optJSONArray("photoUris") ?: JSONArray()
        return WidgetConfig(
            id = j.optInt("id"),
            kind = runCatching { WidgetKind.valueOf(j.optString("kind")) }.getOrDefault(d.kind),
            style = runCatching { WidgetStyle.valueOf(j.optString("style")) }.getOrDefault(d.style),
            title = j.optString("title", d.title),
            accent = j.optLong("accent", d.accent),
            background = j.optLong("background", d.background),
            textColor = j.optLong("textColor", d.textColor),
            textScale = j.optDouble("textScale", 1.0).toFloat(),
            size = j.optString("size", d.size),
            note = j.optString("note"),
            photoUri = optional("photoUri"),
            photoUris = (0 until photos.length()).map { photos.getString(it) },
            rotatePhotos = j.optBoolean("rotatePhotos"),
            shortcutPackage = optional("shortcutPackage"),
            shortcutUri = optional("shortcutUri"),
            clock24 = j.optBoolean("clock24", true),
            analog = j.optBoolean("analog"),
            showDate = j.optBoolean("showDate", true),
            showSeconds = j.optBoolean("showSeconds"),
            weekStartsMonday = j.optBoolean("weekStartsMonday", true),
            showEvents = j.optBoolean("showEvents"),
            city = j.optString("city"),
            latitude =
                if (j.has("latitude") && !j.isNull("latitude")) j.getDouble("latitude") else null,
            longitude =
                if (j.has("longitude") && !j.isNull("longitude")) j.getDouble("longitude")
                else null,
            font = j.optString("font", d.font),
            alignment = j.optString("alignment", d.alignment),
            photoScale = j.optString("photoScale", d.photoScale),
            shortcutIcon = j.optString("shortcutIcon", d.shortcutIcon),
        )
    }
}
