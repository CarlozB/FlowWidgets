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
        runBlocking(Dispatchers.IO) { WidgetWorkScheduler.reconcile(context) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        options: Bundle,
    ) {
        render(
            context,
            manager,
            id,
            javaClass.simpleName.removeSuffix("Widget").lowercase(Locale.ROOT),
            options,
        )
        runBlocking(Dispatchers.IO) { WidgetWorkScheduler.reconcile(context) }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        runBlocking(Dispatchers.IO) {
            ids.forEach { FlowRepository.delete(context, it) }
            WidgetWorkScheduler.reconcile(context)
        }
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

        fun updateAll(context: Context) =
            WidgetUpdateDispatcher.request(context, WidgetKind.entries.toSet())

        fun render(
            context: Context,
            manager: AppWidgetManager,
            id: Int,
            kind: String,
            options: Bundle = manager.getAppWidgetOptions(id),
        ) {
            manager.updateAppWidget(id, buildViews(context, manager, id, kind, options))
        }

        fun buildViews(
            context: Context,
            manager: AppWidgetManager,
            id: Int,
            kind: String,
            options: Bundle = manager.getAppWidgetOptions(id),
        ): RemoteViews {
            val config =
                runBlocking(Dispatchers.IO) {
                    FlowRepository.ensureWidget(
                        context,
                        id,
                        WidgetKind.valueOf(kind.uppercase(Locale.ROOT)),
                    )
                }
            val sizes = AdaptiveWidgetLayoutEngine.sizes(options)
            val plans =
                sizes.map { AdaptiveWidgetLayoutEngine.plan(config.kind, it.width, it.height) }
            val state =
                VisualWidgetRenderer.snapshot(
                    context,
                    if (
                        config.kind == WidgetKind.CLOCK &&
                            plans.none {
                                AdaptiveWidgetLayoutEngine.clock(config, it).events != null
                            }
                    )
                        config.copy(showEvents = false)
                    else config,
                    photoLimit = plans.maxOf { it.photoCapacity },
                    shortcutLimit = plans.maxOf { it.shortcutCapacity },
                )
            val metrics = context.resources.displayMetrics
            val scale =
                AdaptiveWidgetLayoutEngine.renderScale(
                    plans,
                    metrics.widthPixels,
                    metrics.heightPixels,
                )
            val variants = plans.map { buildVariant(context, config, it, state, scale) }
            if (variants.size == 1) return variants.first()
            val reported =
                runCatching {
                        options.getParcelableArrayList(
                            AppWidgetManager.OPTION_APPWIDGET_SIZES,
                            android.util.SizeF::class.java,
                        )
                    }
                    .getOrNull()
                    .orEmpty()
            return if (
                reported.any {
                    it.width > 0 && it.height > 0 && it.width.isFinite() && it.height.isFinite()
                }
            )
                RemoteViews(sizes.zip(variants).toMap())
            else RemoteViews(variants[1], variants[0])
        }

        fun buildVariant(
            context: Context,
            c: WidgetConfig,
            p: AdaptiveWidgetLayoutEngine.Plan,
            state: VisualWidgetRenderer.State,
            scale: Float = 2f,
        ): RemoteViews {
            WidgetTypography.initialize(context)
            val layout = WidgetTypography.layout(c)
            val v = RemoteViews(context.packageName, layout)
            val palette = VisualWidgetRenderer.palette(c)
            v.setImageViewBitmap(
                R.id.widget_image,
                VisualWidgetRenderer.render(
                    c,
                    p.width.toInt(),
                    p.height.toInt(),
                    state,
                    drawClock = false,
                    maxScale = scale,
                ),
            )
            v.removeAllViews(R.id.widget_hits)
            listOf(
                    R.id.widget_clock,
                    R.id.widget_analog,
                    R.id.widget_date,
                    R.id.widget_zone_1,
                    R.id.widget_zone_2,
                    R.id.widget_zone_3,
                    R.id.widget_media_controls,
                )
                .forEach { v.setViewVisibility(it, View.GONE) }
            val detail =
                when (c.kind) {
                    WidgetKind.NOTE ->
                        if (p.panel && c.noteItems.isNotEmpty())
                            c.noteItems.joinToString(". ") {
                                "${if(it.checked) "Completado" else "Pendiente"}: ${it.text}"
                            }
                        else c.note.ifBlank { "Escribe tu próxima idea." }
                    WidgetKind.WEATHER ->
                        state.weather?.let {
                            "${c.city}, ${it.temp.roundToInt()} grados. ${it.description}. Actualizado ${SimpleDateFormat("d MMM HH:mm",Locale.getDefault()).format(Date(it.updatedAt))}"
                        } ?: "Configura una ciudad para consultar el clima"
                    WidgetKind.BATTERY ->
                        if (state.battery.level >= 0)
                            "${state.battery.level}%${if(state.battery.charging) ", cargando" else ""}"
                        else "Batería no disponible"
                    WidgetKind.MEDIA ->
                        state.media?.let { "${it.title}, ${it.artist}" }
                            ?: "Sin sesión activa. Habilita el acceso multimedia en el editor."
                    WidgetKind.CALENDAR ->
                        SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(Date(state.now))
                    WidgetKind.PHOTO ->
                        if (state.photo != null || state.photos.isNotEmpty())
                            "Fotografías seleccionadas"
                        else "Selecciona fotografías"
                    WidgetKind.SHORTCUT ->
                        "Accesos: " +
                            state.shortcuts.take(p.shortcutCapacity).joinToString { it.label }
                    else -> "Reloj"
                }
            v.setContentDescription(R.id.widget_image, detail)
            if (c.kind == WidgetKind.CLOCK) bindClock(v, c, p, palette)
            if (c.kind == WidgetKind.MEDIA && !p.tiny && state.media != null) {
                val media = state.media
                val g = AdaptiveWidgetLayoutEngine.media(p)
                g.controls?.let { rect ->
                    v.setViewVisibility(R.id.widget_media_controls, View.VISIBLE)
                    v.setViewLayoutWidth(
                        R.id.widget_media_controls,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT.toFloat(),
                        android.util.TypedValue.COMPLEX_UNIT_PX,
                    )
                    v.setViewLayoutHeight(
                        R.id.widget_media_controls,
                        rect.height(),
                        android.util.TypedValue.COMPLEX_UNIT_DIP,
                    )
                    listOf(
                            RemoteViews.MARGIN_LEFT to rect.left,
                            RemoteViews.MARGIN_RIGHT to (p.width - rect.right),
                            RemoteViews.MARGIN_TOP to 0f,
                            RemoteViews.MARGIN_BOTTOM to (p.height - rect.bottom),
                        )
                        .forEach { (edge, margin) ->
                            v.setViewLayoutMargin(
                                R.id.widget_media_controls,
                                edge,
                                margin,
                                android.util.TypedValue.COMPLEX_UNIT_DIP,
                            )
                        }
                    listOf(
                            Triple(R.id.widget_previous, "previous", media.canPrevious),
                            Triple(
                                R.id.widget_play,
                                if (media.playing) "pause" else "play",
                                media.canToggle,
                            ),
                            Triple(R.id.widget_next, "next", media.canNext),
                        )
                        .forEach { (view, action, allowed) ->
                            v.setViewVisibility(view, if (allowed) View.VISIBLE else View.INVISIBLE)
                            v.setImageViewBitmap(
                                view,
                                VisualWidgetRenderer.transportIcon(action, palette.foreground),
                            )
                            v.setContentDescription(
                                view,
                                when (action) {
                                    "previous" -> "Anterior"
                                    "next" -> "Siguiente"
                                    "pause" -> "Pausar"
                                    else -> "Reproducir"
                                },
                            )
                            v.setOnClickPendingIntent(
                                view,
                                PendingIntent.getBroadcast(
                                    context,
                                    c.id * 32 + view,
                                    Intent(context, WidgetMediaReceiver::class.java)
                                        .putExtra("media_action", action),
                                    PendingIntent.FLAG_UPDATE_CURRENT or
                                        PendingIntent.FLAG_IMMUTABLE,
                                ),
                            )
                        }
                }
                if (media.canSelectQueue)
                    g.queue?.let { r ->
                        val rows = media.queue.take(3)
                        val line = minOf(28f, r.height() / maxOf(1, rows.size))
                        rows.forEachIndexed { i, e ->
                            addHit(
                                v,
                                context,
                                android.graphics.RectF(
                                    r.left,
                                    r.top + i * line,
                                    r.right,
                                    r.top + (i + 1) * line,
                                ),
                                "Reproducir ${e.title}",
                                PendingIntent.getBroadcast(
                                    context,
                                    c.id * 64 + i,
                                    Intent(context, WidgetMediaReceiver::class.java)
                                        .putExtra("media_action", "queue:${e.id}"),
                                    PendingIntent.FLAG_UPDATE_CURRENT or
                                        PendingIntent.FLAG_IMMUTABLE,
                                ),
                            )
                        }
                    }
            }
            val editor =
                Intent(context, WidgetConfigureActivity::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, c.id)
            if (c.kind == WidgetKind.SHORTCUT) {
                val entries = state.shortcuts.take(p.shortcutCapacity)
                val add = entries.size < p.shortcutCapacity
                val rects =
                    AdaptiveWidgetLayoutEngine.grid(
                        p,
                        (entries.size + if (add) 1 else 0).coerceAtLeast(1),
                    )
                rects.forEachIndexed { i, r ->
                    val item = entries.getOrNull(i)
                    val intent =
                        item?.let { VisualWidgetRenderer.shortcutIntent(context, it.entry) }
                            ?: editor
                    addHit(
                        v,
                        context,
                        r,
                        item?.label ?: "Añadir acceso",
                        PendingIntent.getActivity(
                            context,
                            c.id * 32 + i,
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        ),
                    )
                }
            }
            if (c.kind == WidgetKind.NOTE && p.panel)
                AdaptiveWidgetLayoutEngine.noteRows(p, c.noteItems.size).forEachIndexed { i, r ->
                    val entry = c.noteItems[i]
                    val intent =
                        Intent(context, WidgetNoteReceiver::class.java)
                            .setData(
                                Uri.parse("flowwidgets://notes/${c.id}/${Uri.encode(entry.id)}")
                            )
                            .putExtra("widget_id", c.id)
                            .putExtra("note_id", entry.id)
                    addHit(
                        v,
                        context,
                        r,
                        "${if(entry.checked) "Desmarcar" else "Completar"} ${entry.text}",
                        PendingIntent.getBroadcast(
                            context,
                            c.id * 32 + i,
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        ),
                    )
                }
            var action =
                when (c.kind) {
                    WidgetKind.CLOCK -> Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)
                    WidgetKind.BATTERY -> Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
                    WidgetKind.CALENDAR ->
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("content://com.android.calendar/time/${state.now}"),
                        )
                    WidgetKind.MEDIA ->
                        if (state.media != null)
                            Intent.makeMainSelectorActivity(
                                Intent.ACTION_MAIN,
                                Intent.CATEGORY_APP_MUSIC,
                            )
                        else editor
                    else -> editor
                }
            if (action.resolveActivity(context.packageManager) == null) action = editor
            v.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(
                    context,
                    c.id,
                    action.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            return v
        }

        private val clockArt = android.util.LruCache<String, android.graphics.Bitmap>(24)

        @Synchronized
        private fun artwork(
            key: String,
            create: () -> android.graphics.Bitmap,
        ): android.graphics.Bitmap = clockArt.get(key) ?: create().also { clockArt.put(key, it) }

        private fun bindClock(
            v: RemoteViews,
            c: WidgetConfig,
            p: AdaptiveWidgetLayoutEngine.Plan,
            palette: VisualWidgetRenderer.Palette,
        ) {
            val g = AdaptiveWidgetLayoutEngine.clock(c, p)
            if (c.analog || p.clockOnly) {
                v.setViewVisibility(R.id.widget_analog, View.VISIBLE)
                if (p.clockOnly) {
                    // Some launchers report dp bounds different from the final padded host view.
                    // Fill that actual view and let AnalogClock center its circular dial itself.
                    v.setViewLayoutWidth(
                        R.id.widget_analog,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT.toFloat(),
                        android.util.TypedValue.COMPLEX_UNIT_PX,
                    )
                    v.setViewLayoutHeight(
                        R.id.widget_analog,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT.toFloat(),
                        android.util.TypedValue.COMPLEX_UNIT_PX,
                    )
                    val inset = minOf(p.width, p.height) * .01f
                    listOf(
                            RemoteViews.MARGIN_LEFT,
                            RemoteViews.MARGIN_TOP,
                            RemoteViews.MARGIN_RIGHT,
                            RemoteViews.MARGIN_BOTTOM,
                        )
                        .forEach {
                            v.setViewLayoutMargin(
                                R.id.widget_analog,
                                it,
                                inset,
                                android.util.TypedValue.COMPLEX_UNIT_DIP,
                            )
                        }
                } else place(v, R.id.widget_analog, g.face)
                v.setIcon(
                    R.id.widget_analog,
                    "setDial",
                    android.graphics.drawable.Icon.createWithBitmap(
                        artwork(
                            "dial:${c.style}:${c.accent}:${c.textColor}:${c.font}:${c.fontWeight}:${c.textScale}:${c.background}:${c.automaticColors}"
                        ) {
                            VisualWidgetRenderer.dial(c)
                        }
                    ),
                )
                v.setIcon(
                    R.id.widget_analog,
                    "setHourHand",
                    android.graphics.drawable.Icon.createWithBitmap(
                        artwork(
                            "hour:${palette.accent}:${c.style}:${palette.foreground}:${c.automaticColors}"
                        ) {
                            VisualWidgetRenderer.hand(
                                false,
                                false,
                                palette.accent,
                                c.style == WidgetStyle.GLASS,
                                if (c.automaticColors) null else palette.foreground,
                            )
                        }
                    ),
                )
                v.setIcon(
                    R.id.widget_analog,
                    "setMinuteHand",
                    android.graphics.drawable.Icon.createWithBitmap(
                        artwork(
                            "minute:${palette.accent}:${c.style}:${palette.foreground}:${c.automaticColors}"
                        ) {
                            VisualWidgetRenderer.hand(
                                true,
                                false,
                                palette.accent,
                                c.style == WidgetStyle.GLASS,
                                if (c.automaticColors) null else palette.foreground,
                            )
                        }
                    ),
                )
                v.setIcon(
                    R.id.widget_analog,
                    "setSecondHand",
                    if (c.showSeconds)
                        android.graphics.drawable.Icon.createWithBitmap(
                            artwork("second:${palette.accent}") {
                                VisualWidgetRenderer.hand(true, true, palette.accent)
                            }
                        )
                    else null,
                )
            } else {
                val format =
                    (if (c.clock24) "HH:mm" else "h:mm") +
                        (if (c.showSeconds && !p.tiny) ":ss" else "") +
                        (if (c.clock24 || p.tiny) "" else " a")
                val font =
                    minOf(
                        g.face.width() / (if (c.showSeconds && !p.tiny) 5.8f else 3.4f),
                        g.face.height() * .64f,
                    ) * c.textScale
                val sample =
                    (if (c.clock24) "88:88" else "88:88") +
                        (if (c.showSeconds && !p.tiny) ":88" else "") +
                        (if (!c.clock24 && !p.tiny) " PM" else "")
                val fitted =
                    WidgetTypography.fit(c, sample, font, g.face.width() - 8, g.face.height() - 4)
                clockText(v, R.id.widget_clock, g.face, format, null, fitted, palette.foreground, c)
            }
            g.date?.let {
                clockText(
                    v,
                    R.id.widget_date,
                    it,
                    "EEE, d MMM",
                    null,
                    minOf(12f, it.width() / 7),
                    palette.foreground,
                    c,
                )
            }
            g.zones.forEachIndexed { i, r ->
                val id = listOf(R.id.widget_zone_1, R.id.widget_zone_2, R.id.widget_zone_3)[i]
                clockText(
                    v,
                    id,
                    android.graphics.RectF(r.left, r.top + 13, r.right, r.bottom),
                    if (c.clock24) "HH:mm" else "h:mm a",
                    c.timeZones[i],
                    minOf(17f, r.width() / 4),
                    palette.foreground,
                    c,
                )
            }
        }

        private fun clockText(
            v: RemoteViews,
            id: Int,
            r: android.graphics.RectF,
            format: String,
            zone: String?,
            size: Float,
            color: Int,
            c: WidgetConfig,
        ) {
            v.setViewVisibility(id, View.VISIBLE)
            place(v, id, r)
            v.setCharSequence(id, "setFormat12Hour", format)
            v.setCharSequence(id, "setFormat24Hour", format)
            if (zone != null) v.setString(id, "setTimeZone", zone)
            v.setTextViewTextSize(
                id,
                android.util.TypedValue.COMPLEX_UNIT_DIP,
                size.coerceAtLeast(7f),
            )
            v.setTextColor(id, color)
            v.setFloat(
                id,
                "setLetterSpacing",
                if (size >= 28) -.025f else if (size >= 16) -.012f else .005f,
            )
            v.setString(id, "setFontFeatureSettings", "'tnum' 1, 'lnum' 1")
            if (c.font in listOf("inter", "sans"))
                v.setString(
                    id,
                    "setFontVariationSettings",
                    "'wght' ${WidgetTypography.weight(c,size,false)}, 'opsz' ${size.coerceIn(14f,32f)}",
                )
        }

        private fun place(v: RemoteViews, id: Int, r: android.graphics.RectF) {
            v.setViewLayoutWidth(
                id,
                r.width().coerceAtLeast(1f),
                android.util.TypedValue.COMPLEX_UNIT_DIP,
            )
            v.setViewLayoutHeight(
                id,
                r.height().coerceAtLeast(1f),
                android.util.TypedValue.COMPLEX_UNIT_DIP,
            )
            v.setViewLayoutMargin(
                id,
                RemoteViews.MARGIN_LEFT,
                r.left,
                android.util.TypedValue.COMPLEX_UNIT_DIP,
            )
            v.setViewLayoutMargin(
                id,
                RemoteViews.MARGIN_TOP,
                r.top,
                android.util.TypedValue.COMPLEX_UNIT_DIP,
            )
            v.setViewLayoutMargin(
                id,
                RemoteViews.MARGIN_BOTTOM,
                0f,
                android.util.TypedValue.COMPLEX_UNIT_DIP,
            )
            v.setViewLayoutMargin(
                id,
                RemoteViews.MARGIN_RIGHT,
                0f,
                android.util.TypedValue.COMPLEX_UNIT_DIP,
            )
        }

        private fun addHit(
            parent: RemoteViews,
            context: Context,
            r: android.graphics.RectF,
            label: String,
            pending: PendingIntent,
        ) {
            if (r.width() < 1 || r.height() < 1) return
            val child = RemoteViews(context.packageName, R.layout.widget_hit_target)
            place(child, R.id.widget_hit, r)
            child.setContentDescription(R.id.widget_hit, label)
            child.setOnClickPendingIntent(R.id.widget_hit, pending)
            parent.addView(R.id.widget_hits, child)
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
                val kinds =
                    when (intent.action) {
                        Intent.ACTION_POWER_CONNECTED,
                        Intent.ACTION_POWER_DISCONNECTED,
                        Intent.ACTION_BATTERY_LOW,
                        Intent.ACTION_BATTERY_OKAY -> setOf(WidgetKind.BATTERY)
                        Intent.ACTION_TIME_CHANGED,
                        Intent.ACTION_TIMEZONE_CHANGED,
                        Intent.ACTION_DATE_CHANGED ->
                            setOf(WidgetKind.CLOCK, WidgetKind.CALENDAR, WidgetKind.WEATHER)
                        else -> WidgetKind.entries.toSet()
                    }
                WidgetUpdateDispatcher.request(context, kinds).await()
                WidgetWorkScheduler.reconcile(context)
            } finally {
                pending.finish()
            }
        }
    }
}
