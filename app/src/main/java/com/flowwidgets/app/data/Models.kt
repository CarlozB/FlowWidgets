package com.flowwidgets.app.data

import android.graphics.Bitmap

enum class WidgetKind {
    CLOCK,
    BATTERY,
    CALENDAR,
    WEATHER,
    MEDIA,
    NOTE,
    PHOTO,
    SHORTCUT,
}

enum class WidgetStyle {
    CLASSIC,
    GLASS,
    MATERIAL,
}

data class WidgetConfig(
    val id: Int = 0,
    val kind: WidgetKind = WidgetKind.CLOCK,
    val style: WidgetStyle = WidgetStyle.CLASSIC,
    val title: String = "",
    val accent: Long = 0xFFFF725E,
    val background: Long = 0xFF20272D,
    val textColor: Long = 0xFFFFFFFF,
    val textScale: Float = 1f,
    val size: String = "2×2",
    val note: String = "",
    val photoUri: String? = null,
    val photoUris: List<String> = emptyList(),
    val rotatePhotos: Boolean = false,
    val shortcutPackage: String? = null,
    val shortcutUri: String? = null,
    val clock24: Boolean = true,
    val analog: Boolean = true,
    val showDate: Boolean = true,
    val showSeconds: Boolean = false,
    val weekStartsMonday: Boolean = true,
    val showEvents: Boolean = false,
    val city: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val font: String = "inter",
    val alignment: String = "start",
    val photoScale: String = "crop",
    val shortcutIcon: String = "app",
    val timeZones: List<String> = listOf("UTC"),
    val shortcuts: List<ShortcutEntry> = emptyList(),
    val noteItems: List<NoteEntry> = emptyList(),
    val opacity: Float = 1f,
    val glassOpacity: Float = .82f,
    val glassLighting: Float = .6f,
    val glassCorner: Float = 24f,
    val fontWeight: Int = 0,
    val automaticColors: Boolean = true,
)

data class ShortcutEntry(
    val id: String,
    val label: String = "",
    val packageName: String? = null,
    val uri: String? = null,
    val icon: String = "app",
)

data class NoteEntry(val id: String, val text: String, val checked: Boolean = false)

data class BatteryInfo(
    val level: Int,
    val charging: Boolean,
    val source: String? = null,
    val temperatureC: Double? = null,
    val voltageMv: Int? = null,
    val health: String? = null,
    val technology: String? = null,
)

data class CalendarEvent(val title: String, val time: Long)

data class AppShortcut(val label: String, val packageName: String)

data class WeatherLocation(val name: String, val latitude: Double, val longitude: Double)

data class WeatherHour(val time: String, val temp: Double, val code: Int)

data class WeatherDay(
    val date: String,
    val high: Double,
    val low: Double,
    val code: Int,
    val rainProbability: Int? = null,
)

data class WeatherSnapshot(
    val temp: Double,
    val description: String,
    val high: Double,
    val low: Double,
    val hours: List<WeatherHour>,
    val updatedAt: Long,
    val code: Int = -1,
    val days: List<WeatherDay> = emptyList(),
    val humidity: Int? = null,
    val feelsLike: Double? = null,
    val windKmh: Double? = null,
)

data class MediaInfo(
    val title: String,
    val artist: String,
    val art: Bitmap?,
    val playing: Boolean,
    val canPrevious: Boolean,
    val canNext: Boolean,
    val canToggle: Boolean = true,
    val positionMs: Long? = null,
    val durationMs: Long? = null,
    val album: String? = null,
    val queue: List<MediaQueueEntry> = emptyList(),
    val canSelectQueue: Boolean = false,
)

data class MediaQueueEntry(val id: Long, val title: String)
