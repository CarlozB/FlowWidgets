package com.flowwidgets.app.widgets

import android.app.*
import android.appwidget.*
import android.content.*
import android.graphics.*
import android.net.Uri
import android.os.*
import android.view.View
import android.widget.RemoteViews
import com.flowwidgets.app.R
import com.flowwidgets.app.data.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt
import kotlinx.coroutines.*

/** Launcher widgets use real device state. Missing access is shown explicitly. */
open class AdaptiveWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                super.onReceive(context, intent)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach {
            render(
                context,
                manager,
                it,
                javaClass.simpleName.removeSuffix("Widget").lowercase(Locale.ROOT),
            )
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        options: Bundle,
    ) {
        onUpdate(context, manager, intArrayOf(id))
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        ids.forEach { runBlocking(Dispatchers.IO) { FlowRepository.delete(context, it) } }
    }

    companion object {
        val providers =
            arrayOf(
                ClockWidget::class.java,
                BatteryWidget::class.java,
                CalendarWidget::class.java,
                WeatherWidget::class.java,
                MediaWidget::class.java,
                NoteWidget::class.java,
                PhotoWidget::class.java,
                ShortcutWidget::class.java,
            )

        fun updateAll(context: Context) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                CoroutineScope(Dispatchers.IO).launch { updateAll(context.applicationContext) }
                return
            }
            val manager = AppWidgetManager.getInstance(context)
            providers.forEach { type ->
                manager.getAppWidgetIds(ComponentName(context, type)).forEach {
                    render(
                        context,
                        manager,
                        it,
                        type.simpleName.removeSuffix("Widget").lowercase(Locale.ROOT),
                    )
                }
            }
        }

        fun render(context: Context, manager: AppWidgetManager, id: Int, kind: String) {
            manager.updateAppWidget(id, buildViews(context, manager, id, kind))
        }

        fun buildViews(
            context: Context,
            manager: AppWidgetManager,
            id: Int,
            kind: String,
        ): RemoteViews {
            val config =
                runBlocking(Dispatchers.IO) { FlowRepository.read(context, id) }
                    ?: WidgetConfig(id = id, kind = WidgetKind.valueOf(kind.uppercase(Locale.ROOT)))
            val options = manager.getAppWidgetOptions(id)
            val landscape =
                context.resources.configuration.orientation ==
                    android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val width =
                options
                    .getInt(
                        if (landscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
                        else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,
                        180,
                    )
                    .coerceIn(40, 600)
            val height =
                options
                    .getInt(
                        if (landscape) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
                        else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,
                        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180),
                    )
                    .coerceIn(40, 600)
            val state = VisualWidgetRenderer.snapshot(context, config)
            val palette = VisualWidgetRenderer.palette(config)
            val layout =
                when (config.font) {
                    "serif" -> R.layout.widget_visual_card_serif
                    "monospace" -> R.layout.widget_visual_card_mono
                    else -> R.layout.widget_visual_card
                }
            val views = RemoteViews(context.packageName, layout)
            views.setImageViewBitmap(
                R.id.widget_image,
                VisualWidgetRenderer.render(config, width, height, state, drawClock = false),
            )
            val detail =
                when (config.kind) {
                    WidgetKind.NOTE -> config.note.ifBlank { "Escribe tu próxima idea." }
                    WidgetKind.WEATHER ->
                        state.weather?.let {
                            "${config.city}, ${it.temp.roundToInt()} grados. ${it.description}. Actualizado ${SimpleDateFormat("d MMM HH:mm",Locale.getDefault()).format(Date(it.updatedAt))}"
                        } ?: "Configura una ciudad para consultar el clima"
                    WidgetKind.BATTERY ->
                        if (state.battery.level >= 0) "${state.battery.level}%"
                        else "Batería no disponible"
                    WidgetKind.MEDIA ->
                        state.media?.let { "${it.title}, ${it.artist}" }
                            ?: "Sin reproducción activa. Habilita el acceso multimedia."
                    WidgetKind.CALENDAR ->
                        SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(Date(state.now))
                    WidgetKind.PHOTO ->
                        if (state.photo != null) "Fotografía seleccionada"
                        else "Selecciona una fotografía"
                    WidgetKind.SHORTCUT -> config.title.ifBlank { state.shortcutLabel }
                    else -> "Reloj"
                }
            views.setContentDescription(R.id.widget_image, detail)
            if (config.kind == WidgetKind.CLOCK) {
                if (config.analog) {
                    views.setViewVisibility(R.id.widget_analog, View.VISIBLE)
                    views.setIcon(
                        R.id.widget_analog,
                        "setDial",
                        android.graphics.drawable.Icon.createWithBitmap(
                            VisualWidgetRenderer.dial(config)
                        ),
                    )
                    views.setIcon(
                        R.id.widget_analog,
                        "setHourHand",
                        android.graphics.drawable.Icon.createWithBitmap(
                            VisualWidgetRenderer.hand(
                                false,
                                false,
                                palette.accent,
                                config.style == WidgetStyle.GLASS,
                            )
                        ),
                    )
                    views.setIcon(
                        R.id.widget_analog,
                        "setMinuteHand",
                        android.graphics.drawable.Icon.createWithBitmap(
                            VisualWidgetRenderer.hand(
                                true,
                                false,
                                palette.accent,
                                config.style == WidgetStyle.GLASS,
                            )
                        ),
                    )
                    views.setIcon(
                        R.id.widget_analog,
                        "setSecondHand",
                        if (config.showSeconds)
                            android.graphics.drawable.Icon.createWithBitmap(
                                VisualWidgetRenderer.hand(true, true, palette.accent)
                            )
                        else null,
                    )
                    listOf(
                            RemoteViews.MARGIN_LEFT,
                            RemoteViews.MARGIN_RIGHT,
                            RemoteViews.MARGIN_TOP,
                        )
                        .forEach {
                            views.setViewLayoutMargin(
                                R.id.widget_analog,
                                it,
                                6f,
                                android.util.TypedValue.COMPLEX_UNIT_DIP,
                            )
                        }
                    views.setViewLayoutMargin(
                        R.id.widget_analog,
                        RemoteViews.MARGIN_BOTTOM,
                        if (config.showDate && height >= 140) 24f else 6f,
                        android.util.TypedValue.COMPLEX_UNIT_DIP,
                    )
                } else {
                    val format =
                        (if (config.clock24) "HH:mm" else "h:mm") +
                            (if (config.showSeconds) ":ss" else "") +
                            (if (config.clock24) "" else " a")
                    views.setCharSequence(R.id.widget_clock, "setFormat12Hour", format)
                    views.setCharSequence(R.id.widget_clock, "setFormat24Hour", format)
                    views.setTextColor(R.id.widget_clock, palette.foreground)
                    views.setTextViewTextSize(
                        R.id.widget_clock,
                        android.util.TypedValue.COMPLEX_UNIT_SP,
                        minOf(width / (if (config.showSeconds) 5.8f else 3.4f), height * .4f) *
                            config.textScale,
                    )
                    views.setViewVisibility(R.id.widget_clock, View.VISIBLE)
                }
                if (config.showDate && height >= 140) {
                    views.setViewVisibility(R.id.widget_date, View.VISIBLE)
                    views.setCharSequence(R.id.widget_date, "setFormat12Hour", "EEE, d MMM")
                    views.setCharSequence(R.id.widget_date, "setFormat24Hour", "EEE, d MMM")
                    views.setTextColor(
                        R.id.widget_date,
                        if (config.analog) palette.foreground else palette.foreground,
                    )
                }
            }
            if (config.kind == WidgetKind.MEDIA && state.media != null) {
                views.setViewVisibility(R.id.widget_media_controls, View.VISIBLE)
                val media = state.media
                views.setImageViewBitmap(
                    R.id.widget_play,
                    VisualWidgetRenderer.transportIcon(
                        if (media.playing) "pause" else "play",
                        palette.foreground,
                    ),
                )
                views.setImageViewBitmap(
                    R.id.widget_previous,
                    VisualWidgetRenderer.transportIcon("previous", palette.foreground),
                )
                views.setImageViewBitmap(
                    R.id.widget_next,
                    VisualWidgetRenderer.transportIcon("next", palette.foreground),
                )
                views.setViewVisibility(
                    R.id.widget_previous,
                    if (media.canPrevious && width >= 120) View.VISIBLE else View.GONE,
                )
                views.setViewVisibility(
                    R.id.widget_next,
                    if (media.canNext && width >= 120) View.VISIBLE else View.GONE,
                )
                views.setViewLayoutHeight(
                    R.id.widget_media_controls,
                    if (height < 100) 32f else 46f,
                    android.util.TypedValue.COMPLEX_UNIT_DIP,
                )
                listOf(
                        R.id.widget_previous to "previous",
                        R.id.widget_play to "toggle",
                        R.id.widget_next to "next",
                    )
                    .forEach { (view, action) ->
                        views.setOnClickPendingIntent(
                            view,
                            PendingIntent.getBroadcast(
                                context,
                                id * 10 + view,
                                Intent(context, WidgetMediaReceiver::class.java)
                                    .putExtra("media_action", action),
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                            ),
                        )
                    }
            }
            val fallback =
                Intent(context, WidgetConfigureActivity::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            var action =
                when (config.kind) {
                    WidgetKind.CLOCK -> Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)
                    WidgetKind.BATTERY -> Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
                    WidgetKind.CALENDAR ->
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("content://com.android.calendar/time/${state.now}"),
                        )
                    WidgetKind.SHORTCUT ->
                        config.shortcutUri?.let {
                            if (it.startsWith("android.settings.")) Intent(it)
                            else Intent(Intent.ACTION_VIEW, Uri.parse(it))
                        }
                            ?: config.shortcutPackage?.let {
                                context.packageManager.getLaunchIntentForPackage(it)
                            }
                            ?: fallback
                    WidgetKind.MEDIA ->
                        if (state.media != null)
                            Intent.makeMainSelectorActivity(
                                Intent.ACTION_MAIN,
                                Intent.CATEGORY_APP_MUSIC,
                            )
                        else fallback
                    else -> fallback
                }
            if (action.resolveActivity(context.packageManager) == null) action = fallback
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(
                    context,
                    id,
                    action.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            return views
        }
    }
}

class ClockWidget : AdaptiveWidgetProvider()

class BatteryWidget : AdaptiveWidgetProvider()

class CalendarWidget : AdaptiveWidgetProvider()

class WeatherWidget : AdaptiveWidgetProvider()

class MediaWidget : AdaptiveWidgetProvider()

class NoteWidget : AdaptiveWidgetProvider()

class PhotoWidget : AdaptiveWidgetProvider()

class ShortcutWidget : AdaptiveWidgetProvider()

class WidgetEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action !in
                setOf(
                    Intent.ACTION_BOOT_COMPLETED,
                    Intent.ACTION_TIME_CHANGED,
                    Intent.ACTION_TIMEZONE_CHANGED,
                    Intent.ACTION_DATE_CHANGED,
                    Intent.ACTION_POWER_CONNECTED,
                    Intent.ACTION_POWER_DISCONNECTED,
                    Intent.ACTION_BATTERY_LOW,
                    Intent.ACTION_BATTERY_OKAY,
                    Intent.ACTION_MY_PACKAGE_REPLACED,
                )
        )
            return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AdaptiveWidgetProvider.updateAll(context)
            } finally {
                pending.finish()
            }
        }
    }
}
