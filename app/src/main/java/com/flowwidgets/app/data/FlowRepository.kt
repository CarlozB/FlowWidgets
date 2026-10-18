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
    private val templates = stringPreferencesKey("widget_templates")
    private val pinOrigins = stringPreferencesKey("pin_origins")
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
            val origins = JSONObject(prefs[pinOrigins] ?: "{}")
            origins.remove(config.id.toString())
            prefs[pinOrigins] = origins.toString()
        }
    }

    /** Persist before asking the launcher, which may render before delivering its pin callback. */
    suspend fun preparePin(context: Context, config: WidgetConfig, draftId: Int) {
        require(draftId < 0)
        context.applicationContext.flowStore.edit { prefs ->
            val draft = config.copy(id = draftId)
            val saved = decodeConfigs(prefs[widgets] ?: "[]").filterNot { it.id == draftId }
            prefs[widgets] =
                JSONArray()
                    .apply {
                        (saved.filter { it.id >= 0 } +
                                saved.filter { it.id < 0 }.takeLast(15) +
                                draft)
                            .forEach { put(encode(it)) }
                    }
                    .toString()
            val byKind = JSONObject(prefs[templates] ?: "{}")
            byKind.put(config.kind.name, encode(draft))
            prefs[templates] = byKind.toString()
        }
    }

    /** Hosts without a callback still get the last explicitly chosen design for this type. */
    suspend fun ensureWidget(context: Context, id: Int, kind: WidgetKind): WidgetConfig {
        var result = WidgetConfig(id = id, kind = kind)
        context.applicationContext.flowStore.edit { prefs ->
            val saved = decodeConfigs(prefs[widgets] ?: "[]")
            val existing = saved.find { it.id == id }
            if (existing != null) {
                result = existing
                return@edit
            }
            val template =
                runCatching {
                        JSONObject(prefs[templates] ?: "{}").optJSONObject(kind.name)?.let(::decode)
                    }
                    .getOrNull()
            result = (template ?: result).copy(id = id, kind = kind)
            prefs[widgets] =
                JSONArray().apply { (saved + result).forEach { put(encode(it)) } }.toString()
            if (template != null) {
                val origins = JSONObject(prefs[pinOrigins] ?: "{}")
                origins.put(id.toString(), template.id)
                prefs[pinOrigins] = origins.toString()
            }
        }
        return result
    }

    /** Atomically bind the exact request; duplicate/late callbacks never undo an edited widget. */
    suspend fun completePin(context: Context, draftId: Int, id: Int): WidgetConfig? {
        var result: WidgetConfig? = null
        context.applicationContext.flowStore.edit { prefs ->
            val saved = decodeConfigs(prefs[widgets] ?: "[]")
            val draft = saved.find { it.id == draftId } ?: return@edit
            val existing = saved.find { it.id == id }
            val origins = JSONObject(prefs[pinOrigins] ?: "{}")
            result =
                if (
                    existing == null ||
                        (origins.has(id.toString()) && origins.optInt(id.toString()) != draftId)
                )
                    draft.copy(id = id)
                else existing
            prefs[widgets] =
                JSONArray()
                    .apply {
                        saved
                            .filterNot { it.id == draftId || it.id == id }
                            .forEach { put(encode(it)) }
                        put(encode(result!!))
                    }
                    .toString()
            origins.remove(id.toString())
            prefs[pinOrigins] = origins.toString()
        }
        return result
    }

    suspend fun update(context: Context, id: Int, transform: (WidgetConfig) -> WidgetConfig) {
        context.applicationContext.flowStore.edit { prefs ->
            val source = decodeConfigs(prefs[widgets] ?: "[]")
            prefs[widgets] =
                JSONArray()
                    .apply {
                        source.forEach {
                            put(encode(if (it.id == id) transform(it).copy(id = id) else it))
                        }
                    }
                    .toString()
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
            val origins = JSONObject(prefs[pinOrigins] ?: "{}")
            origins.remove(id.toString())
            prefs[pinOrigins] = origins.toString()
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
            put("fontWeight", c.fontWeight)
            put("automaticColors", c.automaticColors)
            put("alignment", c.alignment)
            put("photoScale", c.photoScale)
            put("shortcutIcon", c.shortcutIcon)
            put("timeZones", JSONArray(c.timeZones))
            put(
                "shortcuts",
                JSONArray().apply {
                    c.shortcuts.forEach { e ->
                        put(
                            JSONObject()
                                .put("id", e.id)
                                .put("label", e.label)
                                .put("packageName", e.packageName)
                                .put("uri", e.uri)
                                .put("icon", e.icon)
                        )
                    }
                },
            )
            put(
                "noteItems",
                JSONArray().apply {
                    c.noteItems.forEach { e ->
                        put(
                            JSONObject()
                                .put("id", e.id)
                                .put("text", e.text)
                                .put("checked", e.checked)
                        )
                    }
                },
            )
            put("opacity", c.opacity)
            put("glassOpacity", c.glassOpacity)
            put("glassLighting", c.glassLighting)
            put("glassCorner", c.glassCorner)
        }

    fun decode(j: JSONObject): WidgetConfig {
        val d = WidgetConfig()
        fun optional(k: String) = if (j.isNull(k) || !j.has(k)) null else j.getString(k)
        val photos = j.optJSONArray("photoUris") ?: JSONArray()
        fun bounded(key: String, default: Float, min: Float, max: Float): Float =
            j.optDouble(key, default.toDouble())
                .toFloat()
                .takeIf { it.isFinite() }
                ?.coerceIn(min, max) ?: default
        return WidgetConfig(
            id = j.optInt("id"),
            kind = runCatching { WidgetKind.valueOf(j.optString("kind")) }.getOrDefault(d.kind),
            style =
                runCatching {
                        WidgetStyle.valueOf(
                            j.optString("style").let { if (it == "ONE") "CLASSIC" else it }
                        )
                    }
                    .getOrDefault(d.style),
            title = j.optString("title", d.title),
            accent = j.optLong("accent", d.accent),
            background = j.optLong("background", d.background),
            textColor = j.optLong("textColor", d.textColor),
            textScale = bounded("textScale", 1f, .7f, 1.5f),
            size = j.optString("size", d.size),
            note = j.optString("note"),
            photoUri = optional("photoUri"),
            photoUris = (0 until photos.length()).map { photos.getString(it) },
            rotatePhotos = j.optBoolean("rotatePhotos"),
            shortcutPackage = optional("shortcutPackage"),
            shortcutUri = optional("shortcutUri"),
            clock24 = j.optBoolean("clock24", true),
            analog = j.optBoolean("analog", d.analog),
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
            automaticColors = j.optBoolean("automaticColors", true),
            font = j.optString("font", d.font).let { if (it == "sans") "inter" else it },
            fontWeight =
                j.optInt("fontWeight", 0).takeIf { it in listOf(0, 300, 400, 500, 600, 700) } ?: 0,
            alignment = j.optString("alignment", d.alignment),
            photoScale = j.optString("photoScale", d.photoScale),
            shortcutIcon = j.optString("shortcutIcon", d.shortcutIcon),
            timeZones =
                j.optJSONArray("timeZones")?.let { a ->
                    (0 until a.length())
                        .mapNotNull {
                            a.optString(it).takeIf { id ->
                                id in java.util.TimeZone.getAvailableIDs()
                            }
                        }
                        .distinct()
                        .take(3)
                } ?: d.timeZones,
            shortcuts =
                j.optJSONArray("shortcuts")?.let { a ->
                    (0 until a.length())
                        .mapNotNull { i ->
                            a.optJSONObject(i)?.let { e ->
                                ShortcutEntry(
                                    e.optString("id", "item-$i"),
                                    e.optString("label"),
                                    e.optString("packageName").takeIf { it.isNotBlank() },
                                    e.optString("uri").takeIf { it.isNotBlank() },
                                    e.optString("icon", "app"),
                                )
                            }
                        }
                        .take(15)
                } ?: emptyList(),
            noteItems =
                j.optJSONArray("noteItems")?.let { a ->
                    (0 until a.length())
                        .mapNotNull { i ->
                            a.optJSONObject(i)?.let { e ->
                                NoteEntry(
                                    e.optString("id", "note-$i"),
                                    e.optString("text"),
                                    e.optBoolean("checked"),
                                )
                            }
                        }
                        .take(20)
                } ?: emptyList(),
            opacity = bounded("opacity", 1f, .25f, 1f),
            glassOpacity = bounded("glassOpacity", .82f, .25f, 1f),
            glassLighting = bounded("glassLighting", .6f, 0f, 1f),
            glassCorner = bounded("glassCorner", 24f, 8f, 40f),
        )
    }
}
