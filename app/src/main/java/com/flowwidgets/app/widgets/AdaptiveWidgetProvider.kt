package com.flowwidgets.app.widgets

import android.Manifest
import android.app.*
import android.appwidget.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.*
import android.provider.CalendarContract
import android.provider.Settings
import android.view.View
import android.widget.RemoteViews
import com.flowwidgets.app.R
import com.flowwidgets.app.data.*
import java.text.SimpleDateFormat
import java.util.*
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
        private val photoCache =
            object : android.util.LruCache<String, Bitmap>(8 * 1024) {
                override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
            }
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
            val targetWidth =
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180).coerceAtLeast(40)
            val targetHeight =
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180).coerceAtLeast(40)
            val narrow = targetWidth < 120
            val short = targetHeight < 100
            val tiny = narrow && short
            var photoLoaded = false
            val cardLayout =
                when (config.font) {
                    "serif" -> R.layout.widget_card_serif
                    "monospace" -> R.layout.widget_card_mono
                    else -> R.layout.widget_card
                }
            val views = RemoteViews(context.packageName, cardLayout)
            var title = ""
            var detail = ""
            var label = kind.uppercase(Locale.getDefault())
            var action =
                context.packageManager.getLaunchIntentForPackage(context.packageName)
                    ?: Intent(Settings.ACTION_SETTINGS)
            when (kind) {
                "clock" -> {
                    label = "RELOJ"
                    detail =
                        if (config.showDate)
                            SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date())
                        else ""
                    views.setCharSequence(
                        R.id.widget_clock,
                        "setFormat12Hour",
                        if (config.clock24) (if (config.showSeconds) "HH:mm:ss" else "HH:mm")
                        else (if (config.showSeconds) "h:mm:ss a" else "h:mm a"),
                    )
                    views.setCharSequence(
                        R.id.widget_clock,
                        "setFormat24Hour",
                        if (config.clock24) (if (config.showSeconds) "HH:mm:ss" else "HH:mm")
                        else (if (config.showSeconds) "h:mm:ss a" else "h:mm a"),
                    )
                    if (config.analog) {
                        views.setViewVisibility(R.id.widget_analog, View.VISIBLE)
                    } else views.setViewVisibility(R.id.widget_clock, View.VISIBLE)
                    action = Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)
                }
                "battery" -> {
                    label = "BATERÍA"
                    val battery =
                        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                    val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
                    title = if (level >= 0 && scale > 0) "${level * 100 / scale}%" else "Sin datos"
                    val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
                    detail =
                        if (level < 0) "Lectura no disponible"
                        else if (plugged > 0) "Conectado al cargador" else "Usando batería"
                    views.setViewVisibility(
                        R.id.widget_image,
                        if (level >= 0 && scale > 0) View.VISIBLE else View.GONE,
                    )
                    views.setImageViewBitmap(
                        R.id.widget_image,
                        batteryGraphic(
                            (if (scale > 0) level * 100 / scale else 0).coerceIn(0, 100),
                            config.accent.toInt(),
                        ),
                    )
                    action = Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
                }
                "calendar" -> {
                    label = "CALENDARIO"
                    title = SimpleDateFormat("d MMM", Locale.getDefault()).format(Date())
                    detail =
                        if (config.showEvents) nextEvent(context)
                        else SimpleDateFormat("EEEE", Locale.getDefault()).format(Date())
                    if (
                        manager
                            .getAppWidgetOptions(id)
                            .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180) >= 160
                    ) {
                        views.setImageViewBitmap(R.id.widget_image, monthGraphic(config))
                        views.setViewVisibility(R.id.widget_image, View.VISIBLE)
                    }
                    action =
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(
                                "content://com.android.calendar/time/${System.currentTimeMillis()}"
                            ),
                        )
                }
                "weather" -> {
                    label = "CLIMA"
                    val weather = WeatherRepository.cached(context, config)
                    label = config.city.ifBlank { "CLIMA" }
                    title = weather?.let { "${it.temp.toInt()}°" } ?: "Configura una ciudad"
                    detail =
                        weather?.let {
                            "${it.description} · ${it.high.toInt()}° / ${it.low.toInt()}°\n" +
                                it.hours.take(4).joinToString("  ") { h ->
                                    "${h.time} ${h.temp.toInt()}°"
                                } +
                                "\nActualizado " +
                                SimpleDateFormat("d MMM HH:mm", Locale.getDefault())
                                    .format(Date(it.updatedAt))
                        } ?: "Abre Flow Widgets para actualizar el pronóstico"
                }
                "media" -> {
                    label = "MÚSICA"
                    val media = MediaRepository.current(context)
                    if (media != null) {
                        views.setViewVisibility(R.id.widget_media_controls, View.VISIBLE)
                        views.setTextViewText(R.id.widget_play, if (media.playing) "Ⅱ" else "▶")
                        views.setViewVisibility(
                            R.id.widget_previous,
                            if (media.canPrevious) View.VISIBLE else View.GONE,
                        )
                        views.setViewVisibility(
                            R.id.widget_next,
                            if (media.canNext) View.VISIBLE else View.GONE,
                        )
                        listOf(
                                R.id.widget_previous to "previous",
                                R.id.widget_play to "toggle",
                                R.id.widget_next to "next",
                            )
                            .forEach { (viewId, operation) ->
                                val command =
                                    Intent(context, WidgetMediaReceiver::class.java)
                                        .putExtra("media_action", operation)
                                views.setOnClickPendingIntent(
                                    viewId,
                                    PendingIntent.getBroadcast(
                                        context,
                                        id * 10 + viewId,
                                        command,
                                        PendingIntent.FLAG_UPDATE_CURRENT or
                                            PendingIntent.FLAG_IMMUTABLE,
                                    ),
                                )
                            }
                    }
                    title = media?.title ?: "Tu música"
                    detail = media?.artist ?: "Activa acceso a notificaciones en Flow Widgets"
                    media?.art?.let {
                        views.setImageViewBitmap(
                            R.id.widget_image,
                            Bitmap.createScaledBitmap(it, 320, 320, true),
                        )
                        views.setViewVisibility(R.id.widget_image, View.VISIBLE)
                    }
                    action =
                        Intent.makeMainSelectorActivity(
                            Intent.ACTION_MAIN,
                            Intent.CATEGORY_APP_MUSIC,
                        )
                }
                "note" -> {
                    label = "NOTA"
                    title =
                        if (short) config.note.ifBlank { "Toca para escribir una nota" }
                        else config.title.ifBlank { "Mi nota" }
                    detail = config.note.ifBlank { "Toca para escribir una nota" }
                    action =
                        Intent(context, WidgetConfigureActivity::class.java)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                }
                "photo" -> {
                    label = "FOTO"
                    val uri =
                        if (config.rotatePhotos && config.photoUris.isNotEmpty())
                            config.photoUris[
                                    ((System.currentTimeMillis() / 1800000) % config.photoUris.size)
                                        .toInt()]
                        else config.photoUri
                    if (uri != null) {
                        try {
                            val imageKey = "$uri|${config.photoScale}|$targetWidth|$targetHeight"
                            val rendered =
                                photoCache.get(imageKey)
                                    ?: run {
                                        val bitmap =
                                            ImageDecoder.decodeBitmap(
                                                ImageDecoder.createSource(
                                                    context.contentResolver,
                                                    Uri.parse(uri),
                                                )
                                            ) { decoder, info, _ ->
                                                val ratio =
                                                    minOf(
                                                        1f,
                                                        600f /
                                                            maxOf(info.size.width, info.size.height),
                                                    )
                                                decoder.setTargetSize(
                                                    (info.size.width * ratio)
                                                        .toInt()
                                                        .coerceAtLeast(1),
                                                    (info.size.height * ratio)
                                                        .toInt()
                                                        .coerceAtLeast(1),
                                                )
                                                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                                            }
                                        photoGraphic(
                                                bitmap,
                                                config.photoScale,
                                                targetWidth,
                                                targetHeight,
                                            )
                                            .also { photoCache.put(imageKey, it) }
                                    }
                            views.setImageViewBitmap(R.id.widget_image, rendered)
                            views.setViewVisibility(R.id.widget_image, View.VISIBLE)
                            photoLoaded = true
                        } catch (_: Exception) {
                            detail = "Vuelve a elegir tu foto"
                        }
                        if (!photoLoaded) {
                            title = "Foto no disponible"
                            detail = "Toca para volver a elegir una imagen"
                        }
                    } else {
                        title = "Tu foto favorita"
                        detail = "Toca para seleccionar una imagen"
                    }
                    action =
                        Intent(context, WidgetConfigureActivity::class.java)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                }
                "shortcut" -> {
                    label = "ACCESO DIRECTO"
                    val pkg = config.shortcutPackage ?: ""
                    val launch = context.packageManager.getLaunchIntentForPackage(pkg)
                    title =
                        if (launch != null)
                            try {
                                context.packageManager
                                    .getApplicationLabel(
                                        context.packageManager.getApplicationInfo(pkg, 0)
                                    )
                                    .toString()
                            } catch (_: Exception) {
                                pkg
                            }
                        else "Elige una aplicación"
                    if (config.shortcutUri != null && launch == null)
                        title =
                            config.title.ifBlank {
                                if (config.shortcutUri.startsWith("android.settings."))
                                    "Ajustes del sistema"
                                else "Contacto seleccionado"
                            }
                    detail = "Toca para abrir"
                    if (config.shortcutIcon == "app" && pkg.isNotBlank()) {
                        runCatching {
                            val icon = context.packageManager.getApplicationIcon(pkg)
                            val iconBitmap = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
                            icon.setBounds(0, 0, 192, 192)
                            icon.draw(Canvas(iconBitmap))
                            views.setImageViewBitmap(R.id.widget_image, iconBitmap)
                            views.setViewVisibility(R.id.widget_image, View.VISIBLE)
                        }
                    } else if (config.shortcutIcon != "app") {
                        title = "${config.shortcutIcon}  $title"
                    }
                    action =
                        config.shortcutUri
                            ?.takeIf { it.isNotBlank() }
                            ?.let {
                                runCatching {
                                        if (it.startsWith("android.settings.")) Intent(it)
                                        else Intent(Intent.ACTION_VIEW, Uri.parse(it))
                                    }
                                    .getOrNull()
                            }
                            ?: launch
                            ?: Intent(context, WidgetConfigureActivity::class.java)
                                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                }
            }
            if (config.title.isNotBlank() && kind != "note") label = config.title
            views.setInt(
                R.id.widget_root,
                "setBackgroundResource",
                when (config.style) {
                    WidgetStyle.ONE -> R.drawable.widget_background_one
                    WidgetStyle.GLASS -> R.drawable.widget_background_glass
                    WidgetStyle.MATERIAL -> R.drawable.widget_background_material
                    else -> R.drawable.widget_background
                },
            )
            views.setColorStateList(
                R.id.widget_root,
                "setBackgroundTintList",
                android.content.res.ColorStateList.valueOf(
                    if (config.style == WidgetStyle.GLASS)
                        (config.background.toInt() and 0x00FFFFFF) or 0xB8000000.toInt()
                    else config.background.toInt()
                ),
            )
            views.setInt(
                R.id.widget_root,
                "setGravity",
                when (config.alignment) {
                    "center" -> android.view.Gravity.CENTER
                    "end" -> android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
                    else -> android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
                },
            )
            views.setTextColor(R.id.widget_label, config.accent.toInt())
            views.setTextColor(R.id.widget_title, config.textColor.toInt())
            views.setTextColor(R.id.widget_detail, config.textColor.toInt())
            views.setTextColor(R.id.widget_clock, config.textColor.toInt())
            views.setTextViewText(R.id.widget_label, label)
            val styledTitle =
                android.text.SpannableString(title).apply {
                    setSpan(
                        android.text.style.TypefaceSpan(config.font),
                        0,
                        length,
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
            views.setTextViewText(R.id.widget_title, styledTitle)
            if (title.isBlank()) views.setViewVisibility(R.id.widget_title, View.GONE)
            val styledDetail =
                android.text.SpannableString(detail).apply {
                    setSpan(
                        android.text.style.TypefaceSpan(config.font),
                        0,
                        length,
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
            views.setTextViewText(R.id.widget_detail, styledDetail)
            val textGravity =
                when (config.alignment) {
                    "center" -> android.view.Gravity.CENTER
                    "end" -> android.view.Gravity.END
                    else -> android.view.Gravity.START
                }
            listOf(R.id.widget_label, R.id.widget_title, R.id.widget_detail, R.id.widget_clock)
                .forEach { views.setInt(it, "setGravity", textGravity) }
            views.setTextViewTextSize(
                R.id.widget_detail,
                android.util.TypedValue.COMPLEX_UNIT_SP,
                (if (kind == "note") 18f else 13f) * config.textScale,
            )
            views.setTextViewTextSize(
                R.id.widget_clock,
                android.util.TypedValue.COMPLEX_UNIT_SP,
                42f * config.textScale,
            )
            if (short) {
                views.setViewVisibility(R.id.widget_detail, View.GONE)
                if (kind != "photo" && !(kind == "clock" && config.analog))
                    views.setViewVisibility(R.id.widget_image, View.GONE)
                if (kind == "media") {
                    views.setViewVisibility(R.id.widget_label, View.GONE)
                    if (narrow) {
                        views.setViewVisibility(R.id.widget_previous, View.GONE)
                        views.setViewVisibility(R.id.widget_next, View.GONE)
                    }
                    views.setInt(R.id.widget_title, "setMaxLines", 1)
                    if (targetHeight < 76) views.setViewVisibility(R.id.widget_title, View.GONE)
                }
                views.setTextViewTextSize(
                    R.id.widget_clock,
                    android.util.TypedValue.COMPLEX_UNIT_SP,
                    27f * config.textScale,
                )
            }
            if (short || tiny) {
                val pad = (6 * context.resources.displayMetrics.density).toInt()
                views.setViewPadding(R.id.widget_root, pad, pad, pad, pad)
                views.setViewVisibility(R.id.widget_label, View.GONE)
            }
            if (photoLoaded) {
                views.setViewPadding(R.id.widget_root, 0, 0, 0, 0)
                listOf(R.id.widget_label, R.id.widget_title, R.id.widget_detail).forEach {
                    views.setViewVisibility(it, View.GONE)
                }
            }
            views.setTextViewTextSize(
                R.id.widget_title,
                android.util.TypedValue.COMPLEX_UNIT_SP,
                (if (narrow || (short && kind == "media")) 19f else 27f) * config.textScale,
            )
            if (action.resolveActivity(context.packageManager) == null)
                action =
                    Intent(context, WidgetConfigureActivity::class.java)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            val click =
                PendingIntent.getActivity(
                    context,
                    id,
                    action.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            views.setOnClickPendingIntent(R.id.widget_root, click)
            return views
        }

        private fun photoGraphic(
            source: Bitmap,
            scale: String,
            targetWidth: Int,
            targetHeight: Int,
        ): Bitmap {
            val resolution = 600f / maxOf(targetWidth, targetHeight)
            val outputWidth = (targetWidth * resolution).toInt().coerceIn(1, 600)
            val outputHeight = (targetHeight * resolution).toInt().coerceIn(1, 600)
            val result = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            val path =
                Path().apply {
                    addRoundRect(
                        RectF(0f, 0f, outputWidth.toFloat(), outputHeight.toFloat()),
                        24f * resolution,
                        24f * resolution,
                        Path.Direction.CW,
                    )
                }
            canvas.clipPath(path)
            val ratio =
                if (scale == "fit")
                    minOf(
                        outputWidth.toFloat() / source.width,
                        outputHeight.toFloat() / source.height,
                    )
                else
                    maxOf(
                        outputWidth.toFloat() / source.width,
                        outputHeight.toFloat() / source.height,
                    )
            val width = source.width * ratio
            val height = source.height * ratio
            canvas.drawBitmap(
                source,
                null,
                RectF(
                    (outputWidth - width) / 2,
                    (outputHeight - height) / 2,
                    (outputWidth + width) / 2,
                    (outputHeight + height) / 2,
                ),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )
            return result
        }

        private fun batteryGraphic(level: Int, accent: Int): Bitmap {
            val result = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            val p =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 14f
                    color = 0x446F8299
                }
            canvas.drawArc(92f, 12f, 228f, 148f, 0f, 360f, false, p)
            p.color = accent
            p.strokeCap = Paint.Cap.ROUND
            canvas.drawArc(92f, 12f, 228f, 148f, -90f, level * 3.6f, false, p)
            p.style = Paint.Style.FILL
            p.textSize = 30f
            p.textAlign = Paint.Align.CENTER
            canvas.drawText("$level%", 160f, 92f, p)
            p.color = 0x446F8299
            canvas.drawRoundRect(40f, 166f, 280f, 175f, 5f, 5f, p)
            p.color = accent
            canvas.drawRoundRect(40f, 166f, 40 + 240f * level / 100, 175f, 5f, 5f, p)
            return result
        }

        private fun monthGraphic(config: WidgetConfig): Bitmap {
            val bitmap = Bitmap.createBitmap(420, 285, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val p =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = config.textColor.toInt()
                    textSize = 22f
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.create(config.font, Typeface.NORMAL)
                }
            val today = Calendar.getInstance()
            val month = (today.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
            val labels =
                if (config.weekStartsMonday) listOf("L", "M", "X", "J", "V", "S", "D")
                else listOf("D", "L", "M", "X", "J", "V", "S")
            labels.forEachIndexed { i, d -> canvas.drawText(d, i * 60f + 30, 26f, p) }
            val offset =
                (month.get(Calendar.DAY_OF_WEEK) -
                    (if (config.weekStartsMonday) Calendar.MONDAY else Calendar.SUNDAY) + 7) % 7
            for (day in 1..month.getActualMaximum(Calendar.DAY_OF_MONTH)) {
                val cell = day - 1 + offset
                val x = (cell % 7) * 60f + 30
                val y = (cell / 7) * 39f + 67
                if (day == today.get(Calendar.DAY_OF_MONTH)) {
                    p.color = config.accent.toInt()
                    canvas.drawCircle(x, y - 8, 18f, p)
                    p.color = config.background.toInt()
                } else p.color = config.textColor.toInt()
                canvas.drawText(day.toString(), x, y, p)
            }
            return bitmap
        }

        private fun nextEvent(context: Context): String {
            if (
                context.checkSelfPermission(Manifest.permission.READ_CALENDAR) !=
                    PackageManager.PERMISSION_GRANTED
            )
                return "Permite acceso al calendario en Flow Widgets"
            return try {
                val now = System.currentTimeMillis()
                val uri =
                    CalendarContract.Instances.CONTENT_URI.buildUpon()
                        .apply {
                            ContentUris.appendId(this, now)
                            ContentUris.appendId(this, now + 7 * 86400000L)
                        }
                        .build()
                context.contentResolver
                    .query(
                        uri,
                        arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN),
                        null,
                        null,
                        "begin ASC",
                    )
                    ?.use {
                        if (it.moveToFirst())
                            "${SimpleDateFormat("EEE HH:mm", Locale.getDefault()).format(Date(it.getLong(1)))} · ${it.getString(0)}"
                        else "Sin eventos en los próximos 7 días"
                    } ?: "Calendario no disponible"
            } catch (_: Exception) {
                "No se pudo leer el calendario"
            }
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
