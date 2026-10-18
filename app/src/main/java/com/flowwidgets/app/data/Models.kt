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
    ONE,
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
    val analog: Boolean = false,
    val showDate: Boolean = true,
    val showSeconds: Boolean = false,
    val weekStartsMonday: Boolean = true,
    val showEvents: Boolean = false,
    val city: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val font: String = "sans",
    val alignment: String = "start",
    val photoScale: String = "crop",
    val shortcutIcon: String = "app",
)

data class BatteryInfo(val level: Int, val charging: Boolean)

data class CalendarEvent(val title: String, val time: Long)

data class AppShortcut(val label: String, val packageName: String)

data class WeatherLocation(val name: String, val latitude: Double, val longitude: Double)

data class WeatherHour(val time: String, val temp: Double, val code: Int)

data class WeatherSnapshot(
    val temp: Double,
    val description: String,
    val high: Double,
    val low: Double,
    val hours: List<WeatherHour>,
    val updatedAt: Long,
)

data class MediaInfo(
    val title: String,
    val artist: String,
    val art: Bitmap?,
    val playing: Boolean,
    val canPrevious: Boolean,
    val canNext: Boolean,
)
