package com.flowwidgets.app.widgets

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.LruCache
import com.flowwidgets.app.data.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

/** Original vector artwork. The same renderer supplies the launcher and Compose previews. */
object VisualWidgetRenderer {
    data class State(
        val now: Long = System.currentTimeMillis(),
        val battery: BatteryInfo = BatteryInfo(-1, false),
        val weather: WeatherSnapshot? = null,
        val media: MediaInfo? = null,
        val photo: Bitmap? = null,
        val appIcon: Bitmap? = null,
        val shortcutLabel: String = "Elige un acceso",
        val events: List<CalendarEvent> = emptyList(),
    )

    data class Palette(val background: Int, val foreground: Int, val accent: Int)

    private val ink = Color.rgb(29, 31, 35)
    private val white = Color.rgb(255, 254, 250)
    private val imageCache =
        object : LruCache<String, Bitmap>(8 * 1024) {
            override fun sizeOf(key: String, value: Bitmap) = maxOf(1, value.byteCount / 1024)
        }

    fun palette(c: WidgetConfig): Palette {
        val automatic =
            c.style == WidgetStyle.CLASSIC &&
                c.background == 0xFF20272D &&
                c.textColor == 0xFFFFFFFF
        val bg =
            if (automatic)
                when (c.kind) {
                    WidgetKind.CLOCK -> Color.rgb(228, 157, 156)
                    WidgetKind.WEATHER -> Color.rgb(42, 124, 184)
                    WidgetKind.MEDIA -> Color.rgb(240, 63, 104)
                    WidgetKind.NOTE,
                    WidgetKind.CALENDAR -> white
                    WidgetKind.BATTERY -> Color.rgb(27, 30, 36)
                    WidgetKind.PHOTO -> Color.rgb(36, 64, 65)
                    WidgetKind.SHORTCUT -> Color.rgb(225, 232, 245)
                }
            else c.background.toInt()
        val fg =
            if (
                automatic &&
                    c.kind in listOf(WidgetKind.NOTE, WidgetKind.CALENDAR, WidgetKind.SHORTCUT)
            )
                ink
            else c.textColor.toInt()
        val accent =
            if (c.accent == 0xFFFF725E)
                when (c.kind) {
                    WidgetKind.NOTE -> Color.rgb(255, 207, 37)
                    WidgetKind.BATTERY -> Color.rgb(146, 234, 91)
                    WidgetKind.CALENDAR -> Color.rgb(231, 74, 78)
                    else -> c.accent.toInt()
                }
            else c.accent.toInt()
        return Palette(bg, fg, accent)
    }

    /**
     * Read only legitimate sources. Missing photos, weather and sessions remain explicit empty
     * states.
     */
    fun snapshot(context: Context, c: WidgetConfig, now: Long = System.currentTimeMillis()): State {
        var photo: Bitmap? = null
        var icon: Bitmap? = null
        var label =
            if (c.shortcutUri?.startsWith("android.settings.") == true) "Ajustes del sistema"
            else if (c.shortcutUri != null) "Contacto seleccionado" else "Elige un acceso"
        if (c.kind == WidgetKind.PHOTO) {
            val uri =
                if (c.rotatePhotos && c.photoUris.isNotEmpty())
                    c.photoUris[((now / 1_800_000) % c.photoUris.size).toInt()]
                else c.photoUri
            if (uri != null)
                photo =
                    runCatching {
                            imageCache.get(uri)
                                ?: ImageDecoder.decodeBitmap(
                                        ImageDecoder.createSource(
                                            context.contentResolver,
                                            Uri.parse(uri),
                                        )
                                    ) { decoder, info, _ ->
                                        val ratio =
                                            minOf(
                                                1f,
                                                720f / maxOf(info.size.width, info.size.height),
                                            )
                                        decoder.setTargetSize(
                                            (info.size.width * ratio).toInt().coerceAtLeast(1),
                                            (info.size.height * ratio).toInt().coerceAtLeast(1),
                                        )
                                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                                    }
                                    .also { imageCache.put(uri, it) }
                        }
                        .getOrNull()
        }
        if (c.kind == WidgetKind.SHORTCUT && c.shortcutPackage != null)
            runCatching {
                label =
                    context.packageManager
                        .getApplicationLabel(
                            context.packageManager.getApplicationInfo(c.shortcutPackage, 0)
                        )
                        .toString()
                val drawable = context.packageManager.getApplicationIcon(c.shortcutPackage)
                icon =
                    Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888).also {
                        drawable.setBounds(0, 0, 160, 160)
                        drawable.draw(Canvas(it))
                    }
            }
        return State(
            now,
            if (c.kind == WidgetKind.BATTERY) DeviceData.battery(context)
            else BatteryInfo(-1, false),
            if (c.kind == WidgetKind.WEATHER) WeatherRepository.cached(context, c) else null,
            if (c.kind == WidgetKind.MEDIA) MediaRepository.current(context) else null,
            photo,
            icon,
            label,
            if (c.kind == WidgetKind.CALENDAR && c.showEvents) DeviceData.events(context)
            else emptyList(),
        )
    }

    fun render(
        c: WidgetConfig,
        width: Int,
        height: Int,
        state: State,
        drawClock: Boolean = true,
    ): Bitmap {
        val w = width.coerceIn(40, 600).toFloat()
        val h = height.coerceIn(40, 600).toFloat()
        val factor = minOf(2f, 720f / maxOf(w, h))
        val bitmap =
            Bitmap.createBitmap(
                (w * factor).roundToInt(),
                (h * factor).roundToInt(),
                Bitmap.Config.ARGB_8888,
            )
        val canvas = Canvas(bitmap)
        canvas.scale(factor, factor)
        val radius = minOf(if (c.style == WidgetStyle.ONE) 26f else 22f, minOf(w, h) * .2f)
        canvas.clipPath(
            Path().apply { addRoundRect(RectF(0f, 0f, w, h), radius, radius, Path.Direction.CW) }
        )
        val p = palette(c)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val base = if (c.style == WidgetStyle.GLASS) alpha(p.background, 195) else p.background
        bgPaint.shader =
            LinearGradient(
                0f,
                0f,
                w,
                h,
                intArrayOf(blend(base, Color.WHITE, .12f), base, blend(base, Color.BLACK, .12f)),
                null,
                Shader.TileMode.CLAMP,
            )
        canvas.drawRect(0f, 0f, w, h, bgPaint)
        if (c.kind != WidgetKind.NOTE && c.kind != WidgetKind.CALENDAR) {
            val glow =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader =
                        RadialGradient(
                            w * .8f,
                            h * .05f,
                            maxOf(w, h) * .8f,
                            alpha(Color.WHITE, 32),
                            Color.TRANSPARENT,
                            Shader.TileMode.CLAMP,
                        )
                }
            canvas.drawCircle(w * .8f, h * .05f, maxOf(w, h) * .8f, glow)
        }
        when (c.kind) {
            WidgetKind.CLOCK -> clock(canvas, w, h, c, p, state, drawClock)
            WidgetKind.BATTERY -> battery(canvas, w, h, c, p, state)
            WidgetKind.CALENDAR -> calendar(canvas, w, h, c, p, state)
            WidgetKind.WEATHER -> weather(canvas, w, h, c, p, state)
            WidgetKind.MEDIA -> media(canvas, w, h, c, p, state)
            WidgetKind.NOTE -> note(canvas, w, h, c, p, state)
            WidgetKind.PHOTO -> photo(canvas, w, h, c, p, state)
            WidgetKind.SHORTCUT -> shortcut(canvas, w, h, c, p, state)
        }
        if (c.style == WidgetStyle.GLASS)
            canvas.drawRoundRect(
                RectF(.7f, .7f, w - .7f, h - .7f),
                radius,
                radius,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = alpha(Color.WHITE, 100)
                    style = Paint.Style.STROKE
                    strokeWidth = 1.4f
                },
            )
        return bitmap
    }

    private fun clock(
        cv: Canvas,
        w: Float,
        h: Float,
        c: WidgetConfig,
        p: Palette,
        s: State,
        draw: Boolean,
    ) {
        if (!draw) return
        if (c.analog) {
            val size = minOf(w - 12, h - (if (c.showDate && h >= 140) 30f else 12f))
            val top = if (c.showDate && h >= 140) 6f else (h - size) / 2
            cv.save()
            cv.translate((w - size) / 2, top)
            cv.scale(size / 320, size / 320)
            cv.drawBitmap(dial(c), 0f, 0f, null)
            val cal = Calendar.getInstance().apply { timeInMillis = s.now }
            val minute = cal.get(Calendar.MINUTE) + cal.get(Calendar.SECOND) / 60f
            val hour = cal.get(Calendar.HOUR) + minute / 60f
            for ((angle, long) in listOf(hour * 30 to false, minute * 6 to true)) {
                cv.save()
                cv.rotate(angle, 160f, 160f)
                cv.drawBitmap(
                    hand(long, false, p.accent, c.style == WidgetStyle.GLASS),
                    144f,
                    0f,
                    null,
                )
                cv.restore()
            }
            if (c.showSeconds) {
                cv.save()
                cv.rotate(cal.get(Calendar.SECOND) * 6f, 160f, 160f)
                cv.drawBitmap(hand(true, true, p.accent), 144f, 0f, null)
                cv.restore()
            }
            cv.restore()
            if (c.showDate && h >= 140)
                text(
                    cv,
                    date(s.now, "EEE, d MMM"),
                    w / 2,
                    h - 12,
                    11f,
                    p.foreground,
                    c,
                    align = Paint.Align.CENTER,
                )
        } else {
            val font = minOf(w / (if (c.showSeconds) 5.8f else 3.4f), h * .4f)
            text(
                cv,
                SimpleDateFormat(
                        (if (c.clock24) "HH:mm" else "h:mm") +
                            (if (c.showSeconds) ":ss" else "") +
                            (if (c.clock24) "" else " a"),
                        Locale.getDefault(),
                    )
                    .format(Date(s.now)),
                w / 2,
                h / 2 + font * .34f,
                font,
                p.foreground,
                c,
                align = Paint.Align.CENTER,
            )
            if (c.showDate && h >= 100)
                text(
                    cv,
                    date(s.now, "EEE, d MMM"),
                    w / 2,
                    h - 12,
                    11f,
                    p.foreground,
                    c,
                    align = Paint.Align.CENTER,
                )
        }
    }

    /** Native AnalogClock uses these original bitmap Icons and keeps the hands on time itself. */
    fun dial(c: WidgetConfig): Bitmap {
        val b = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val face = if (c.style == WidgetStyle.GLASS) Color.rgb(35, 40, 48) else white
        val fg = if (c.style == WidgetStyle.GLASS) white else ink
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = face }
        cv.drawCircle(160f, 160f, 155f, paint)
        paint.color = alpha(fg, 30)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        cv.drawCircle(160f, 160f, 150f, paint)
        paint.style = Paint.Style.FILL
        for (i in 0..59) {
            val a = i * Math.PI / 30
            val outer = 146f
            val inner = if (i % 5 == 0) 134f else 140f
            paint.color = alpha(fg, if (i % 5 == 0) 225 else 100)
            paint.strokeWidth = if (i % 5 == 0) 2.5f else 1.1f
            cv.drawLine(
                160 + sin(a).toFloat() * inner,
                160 - cos(a).toFloat() * inner,
                160 + sin(a).toFloat() * outer,
                160 - cos(a).toFloat() * outer,
                paint,
            )
        }
        for (i in 1..12) {
            val a = i * Math.PI / 6
            text(
                cv,
                i.toString(),
                160 + sin(a).toFloat() * 112,
                160 - cos(a).toFloat() * 112 + 8,
                25f,
                fg,
                c,
                align = Paint.Align.CENTER,
            )
        }
        paint.color = palette(c).accent
        cv.drawCircle(160f, 160f, 5f, paint)
        return b
    }

    fun hand(minute: Boolean, second: Boolean, accent: Int, light: Boolean = false): Bitmap {
        val b = Bitmap.createBitmap(32, 320, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (second) accent else if (light) white else ink
                strokeWidth = if (second) 2f else if (minute) 5f else 7f
                strokeCap = Paint.Cap.ROUND
            }
        cv.drawLine(16f, if (minute) 35f else 70f, 16f, if (second) 186f else 174f, paint)
        cv.drawCircle(16f, 160f, if (second) 4f else 5f, paint)
        return b
    }

    private fun battery(cv: Canvas, w: Float, h: Float, c: WidgetConfig, p: Palette, s: State) {
        val compact = h < 100 || w < 115
        val cx = if (w > h * 1.6f) w * .73f else w / 2
        val cy = if (compact) h / 2 else h * .49f
        val r = minOf(h * (if (compact) .35f else .29f), w * .34f)
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = maxOf(5f, r * .16f)
                strokeCap = Paint.Cap.ROUND
                color = alpha(p.foreground, 30)
            }
        cv.drawCircle(cx, cy, r, paint)
        if (s.battery.level >= 0) {
            paint.color = p.accent
            cv.drawArc(
                cx - r,
                cy - r,
                cx + r,
                cy + r,
                -90f,
                s.battery.level.coerceIn(0, 100) * 3.6f,
                false,
                paint,
            )
        }
        text(
            cv,
            if (s.battery.level >= 0) "${s.battery.level}%" else "—",
            cx,
            cy + r * .19f,
            r * .54f,
            p.foreground,
            c,
            align = Paint.Align.CENTER,
            bold = true,
        )
        val gx = if (w > h * 1.6f) w * .19f else w / 2
        val gy = if (w > h * 1.6f) h * .47f else h * .83f
        batteryIcon(cv, gx - 12, gy - 10, 24f, p.accent, s.battery.charging)
        if (!compact && w <= h * 1.6f)
            text(
                cv,
                if (s.battery.charging) "Cargando" else "Batería",
                w / 2,
                h * .94f,
                10f,
                alpha(p.foreground, 180),
                c,
                align = Paint.Align.CENTER,
            )
    }

    private fun batteryIcon(
        cv: Canvas,
        x: Float,
        y: Float,
        size: Float,
        color: Int,
        charging: Boolean,
    ) {
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.STROKE
                strokeWidth = 1.8f
            }
        cv.drawRoundRect(RectF(x, y, x + size, y + size * .55f), 3f, 3f, paint)
        cv.drawLine(x + size + 2, y + size * .18f, x + size + 2, y + size * .37f, paint)
        if (charging) {
            paint.style = Paint.Style.FILL
            cv.drawPath(
                Path().apply {
                    moveTo(x + size * .58f, y + size * .05f)
                    lineTo(x + size * .34f, y + size * .3f)
                    lineTo(x + size * .54f, y + size * .3f)
                    lineTo(x + size * .44f, y + size * .51f)
                    lineTo(x + size * .7f, y + size * .24f)
                    lineTo(x + size * .5f, y + size * .24f)
                    close()
                },
                paint,
            )
        }
    }

    private fun calendar(cv: Canvas, w: Float, h: Float, c: WidgetConfig, p: Palette, s: State) {
        val cal = Calendar.getInstance().apply { timeInMillis = s.now }
        val compact = w < 140 || h < 125
        if (compact) {
            text(
                cv,
                date(s.now, "EEE").uppercase(),
                w / 2,
                h * .26f,
                minOf(12f, h * .15f),
                p.accent,
                c,
                align = Paint.Align.CENTER,
                bold = true,
            )
            text(
                cv,
                cal.get(Calendar.DAY_OF_MONTH).toString(),
                w / 2,
                h * .8f,
                minOf(w * .5f, h * .53f),
                p.foreground,
                c,
                align = Paint.Align.CENTER,
            )
            return
        }
        text(
            cv,
            date(s.now, "MMMM").uppercase(),
            16f,
            25f,
            12f,
            p.accent,
            c,
            bold = true,
            maxWidth = w - 32,
        )
        val bottom = if (c.showEvents && h > 210) 48f else 13f
        val top = 44f
        val row = (h - top - bottom) / 7
        val col = (w - 24) / 7
        val weekdays =
            if (c.weekStartsMonday) listOf("L", "M", "X", "J", "V", "S", "D")
            else listOf("D", "L", "M", "X", "J", "V", "S")
        weekdays.forEachIndexed { i, d ->
            text(
                cv,
                d,
                12 + col * (i + .5f),
                top + row * .55f,
                9f,
                alpha(p.foreground, 125),
                c,
                align = Paint.Align.CENTER,
            )
        }
        val first = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
        val offset =
            (first.get(Calendar.DAY_OF_WEEK) -
                (if (c.weekStartsMonday) Calendar.MONDAY else Calendar.SUNDAY) + 7) % 7
        for (day in 1..cal.getActualMaximum(Calendar.DAY_OF_MONTH)) {
            val index = day - 1 + offset
            val x = 12 + col * (index % 7 + .5f)
            val y = top + row * (index / 7 + 1.62f)
            val today = day == cal.get(Calendar.DAY_OF_MONTH)
            if (today)
                cv.drawCircle(
                    x,
                    y - 3,
                    minOf(row * .42f, col * .35f),
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.accent },
                )
            text(
                cv,
                day.toString(),
                x,
                y + 1,
                minOf(20f, row * .55f),
                if (today) (if (luminance(p.accent) > .55f) ink else Color.WHITE) else p.foreground,
                c,
                align = Paint.Align.CENTER,
                bold = today,
            )
        }
        if (bottom > 20) {
            cv.drawRoundRect(
                RectF(12f, h - 42, w - 12, h - 10),
                8f,
                8f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = alpha(p.accent, 22) },
            )
            text(
                cv,
                s.events.firstOrNull()?.let { "${date(it.time,"HH:mm")}  ${it.title}" }
                    ?: "Sin próximos eventos · revisa el permiso",
                20f,
                h - 22,
                10f,
                p.foreground,
                c,
                maxWidth = w - 40,
            )
        }
    }

    private fun weather(cv: Canvas, w: Float, h: Float, c: WidgetConfig, p: Palette, s: State) {
        val weather = s.weather
        val compact = h < 105 || w < 120
        val pad = if (compact) 10f else 16f
        val iconSize = minOf(h * (if (compact) .6f else .33f), w * .35f)
        weatherIcon(
            cv,
            w - pad - iconSize,
            pad,
            iconSize,
            weather?.let { if (it.code >= 0) it.code else it.hours.firstOrNull()?.code ?: -1 } ?: -1,
        )
        text(
            cv,
            c.city.ifBlank { "El tiempo" },
            pad,
            pad + 10,
            if (compact) 9f else 12f,
            p.foreground,
            c,
            maxWidth = w - iconSize - 2 * pad,
        )
        text(
            cv,
            weather?.let { "${it.temp.roundToInt()}°" } ?: "—°",
            pad,
            if (compact) h * .78f else h * .42f,
            minOf(if (compact) 32f else 60f, h * .37f),
            p.foreground,
            c,
        )
        if (compact) {
            if (weather != null && w >= 115)
                text(
                    cv,
                    date(weather.updatedAt, "d MMM HH:mm"),
                    pad,
                    h - 4,
                    6.5f,
                    alpha(p.foreground, 170),
                    c,
                )
            return
        }
        text(
            cv,
            weather?.description ?: "Elige una ciudad",
            pad,
            h * .54f,
            11f,
            alpha(p.foreground, 220),
            c,
            maxWidth = w - 2 * pad,
        )
        text(
            cv,
            weather?.let { "Máx. ${it.high.roundToInt()}°   Mín. ${it.low.roundToInt()}°" }
                ?: "Actualiza con conexión",
            pad,
            h * .65f,
            10f,
            alpha(p.foreground, 185),
            c,
            maxWidth = w - 2 * pad,
        )
        if (weather != null && h >= 150) {
            val hours = weather.hours.take(if (w > 270) 6 else 4)
            val cell = (w - 2 * pad) / maxOf(1, hours.size)
            hours.forEachIndexed { i, hour ->
                val x = pad + cell * (i + .5f)
                text(
                    cv,
                    hour.time.take(5),
                    x,
                    h * .76f,
                    10f,
                    alpha(p.foreground, 190),
                    c,
                    align = Paint.Align.CENTER,
                )
                weatherIcon(cv, x - 10, h * .79f, 20f, hour.code)
                text(
                    cv,
                    "${hour.temp.roundToInt()}°",
                    x,
                    h * .93f,
                    13f,
                    p.foreground,
                    c,
                    align = Paint.Align.CENTER,
                )
            }
        }
        if (weather != null)
            text(
                cv,
                "${date(weather.updatedAt,"d MMM HH:mm")} · Open-Meteo",
                pad,
                h - 4,
                6.5f,
                alpha(p.foreground, 175),
                c,
                maxWidth = w - 2 * pad,
            )
    }

    /** Clouds, sun, mist, snow, lightning and rain are vector paths, never font emoji. */
    fun weatherIcon(cv: Canvas, x: Float, y: Float, size: Float, code: Int) {
        cv.save()
        cv.translate(x, y)
        cv.scale(size / 64, size / 64)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (code < 0) {
            paint.color = 0x55FFFFFF
            cv.drawCircle(32f, 27f, 14f, paint)
            paint.color = Color.WHITE
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            cv.drawCircle(32f, 27f, 14f, paint)
            cv.drawLine(32f, 44f, 32f, 49f, paint)
            cv.restore()
            return
        }
        val clear = code == 0
        if (code <= 2) {
            val cx = if (clear) 32f else 24f
            val cy = if (clear) 31f else 23f
            paint.color = Color.rgb(255, 213, 68)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.8f
            paint.strokeCap = Paint.Cap.ROUND
            for (i in 0..7) {
                val a = i * Math.PI / 4
                cv.drawLine(
                    cx + cos(a).toFloat() * 18,
                    cy + sin(a).toFloat() * 18,
                    cx + cos(a).toFloat() * 23,
                    cy + sin(a).toFloat() * 23,
                    paint,
                )
            }
            paint.style = Paint.Style.FILL
            paint.shader =
                RadialGradient(
                    cx - 3,
                    cy - 3,
                    18f,
                    0xFFFFED8C.toInt(),
                    0xFFFFB52E.toInt(),
                    Shader.TileMode.CLAMP,
                )
            cv.drawCircle(cx, cy, 13f, paint)
            paint.shader = null
        }
        if (!clear) {
            paint.color = Color.rgb(235, 244, 253)
            cv.drawRoundRect(RectF(10f, 29f, 58f, 48f), 9f, 9f, paint)
            cv.drawCircle(26f, 29f, 12f, paint)
            cv.drawCircle(41f, 32f, 11f, paint)
            if (code in 51..67 || code in 80..82) {
                paint.color = Color.rgb(130, 211, 255)
                paint.strokeWidth = 3f
                paint.strokeCap = Paint.Cap.ROUND
                for (i in 0..2) cv.drawLine(20f + i * 13, 52f, 17f + i * 13, 58f, paint)
            }
            if (code in 71..77 || code in 85..86) {
                paint.color = Color.WHITE
                for (i in 0..2) cv.drawCircle(19f + i * 14, 55f, 2f, paint)
            }
            if (code in 95..99) {
                paint.color = Color.rgb(255, 216, 52)
                cv.drawPath(
                    Path().apply {
                        moveTo(35f, 44f)
                        lineTo(27f, 55f)
                        lineTo(34f, 54f)
                        lineTo(29f, 64f)
                        lineTo(44f, 49f)
                        lineTo(36f, 50f)
                        close()
                    },
                    paint,
                )
            }
            if (code in 45..48) {
                paint.color = 0xBBDDE8EE.toInt()
                paint.strokeWidth = 2.5f
                cv.drawLine(11f, 52f, 54f, 52f, paint)
                cv.drawLine(17f, 58f, 48f, 58f, paint)
            }
        }
        cv.restore()
    }

    private fun note(cv: Canvas, w: Float, h: Float, c: WidgetConfig, p: Palette, s: State) {
        val compact = h < 100
        val header = if (compact) 19f else minOf(38f, h * .21f)
        cv.drawRect(0f, 0f, w, header, Paint().apply { color = p.accent })
        val headerInk = if (luminance(p.accent) > .5f) ink else Color.WHITE
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = headerInk
                strokeWidth = 1.2f
                style = Paint.Style.STROKE
            }
        cv.drawRoundRect(RectF(13f, header / 2 - 5, 25f, header / 2 + 5), 1.5f, 1.5f, paint)
        cv.drawLine(13f, header / 2 - 2, 25f, header / 2 - 2, paint)
        text(
            cv,
            c.title.ifBlank { "Notas" },
            32f,
            header / 2 + 4,
            10f,
            headerInk,
            c,
            bold = true,
            maxWidth = w - 46,
        )
        val padding = if (compact) 10f else 15f
        val body = c.note.ifBlank { "Escribe tu próxima idea." }
        paragraph(
            cv,
            body,
            padding,
            header + padding,
            w - 2 * padding,
            h - header - 2 * padding,
            (if (compact) 12f else 17f) * c.textScale,
            p.foreground,
            c,
        )
        if (!compact && h > 170) {
            text(cv, date(s.now, "EEE, d MMM"), padding, h - 12, 9f, alpha(p.foreground, 105), c)
        }
    }

    private fun media(cv: Canvas, w: Float, h: Float, c: WidgetConfig, p: Palette, s: State) {
        val compact = h < 105
        val portrait = !compact && w <= h * 1.2f
        val cover =
            if (compact) minOf(h - 48, 48f).coerceAtLeast(16f)
            else if (portrait) minOf(w - 30, h * .5f) else minOf(w * .37f, h * .57f)
        val x = if (portrait) (w - cover) / 2 else if (compact) 10f else 15f
        val y = if (compact) 8f else 16f
        if (s.media?.art != null)
            image(cv, s.media.art, RectF(x, y, x + cover, y + cover), 10f, true)
        else {
            cv.drawRoundRect(
                RectF(x, y, x + cover, y + cover),
                10f,
                10f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = alpha(Color.BLACK, 22) },
            )
            vinyl(cv, x + cover / 2, y + cover / 2, cover * .36f, p.accent)
        }
        if (h < 76) return
        if (portrait) {
            val title = s.media?.title ?: "Dale al play"
            text(
                cv,
                title,
                w / 2,
                y + cover + 22,
                15f,
                p.foreground,
                c,
                align = Paint.Align.CENTER,
                bold = true,
                maxWidth = w - 26,
            )
            text(
                cv,
                s.media?.artist ?: "Conecta tu reproductor",
                w / 2,
                y + cover + 38,
                10f,
                alpha(p.foreground, 190),
                c,
                align = Paint.Align.CENTER,
                maxWidth = w - 28,
            )
        } else {
            val tx = x + cover + 11
            text(
                cv,
                if (s.media?.playing == true) "SONANDO" else "TU MÚSICA",
                tx,
                y + 10,
                8f,
                alpha(p.foreground, 190),
                c,
                maxWidth = w - tx - 12,
            )
            paragraph(
                cv,
                s.media?.title ?: "Dale al play",
                tx,
                y + 18,
                w - tx - 12,
                if (compact) 18f else cover * .6f,
                if (compact) 10f else 16f,
                p.foreground,
                c,
                bold = true,
            )
            if (!compact)
                text(
                    cv,
                    s.media?.artist ?: "Conecta tu reproductor",
                    tx,
                    y + cover - 2,
                    10f,
                    alpha(p.foreground, 190),
                    c,
                    maxWidth = w - tx - 12,
                )
        }
        if (!compact) musicNote(cv, w - 28, 12f, 14f, alpha(p.foreground, 220))
        if (s.media == null && h > 240)
            text(
                cv,
                "Activa el acceso multimedia en Ajustes",
                w / 2,
                h * .82f,
                9f,
                alpha(p.foreground, 190),
                c,
                align = Paint.Align.CENTER,
                maxWidth = w - 24,
            )
        // Transport buttons are separate native click targets, not part of this bitmap.
    }

    private fun vinyl(cv: Canvas, x: Float, y: Float, r: Float, accent: Int) {
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader =
                    RadialGradient(
                        x - r * .4f,
                        y - r * .5f,
                        r * 2,
                        0xFF51515A.toInt(),
                        0xFF17171B.toInt(),
                        Shader.TileMode.CLAMP,
                    )
            }
        cv.drawCircle(x, y, r, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = 0x555F5F68
        for (f in listOf(.48f, .61f, .75f, .87f)) cv.drawCircle(x, y, r * f, paint)
        paint.style = Paint.Style.FILL
        paint.color = blend(accent, Color.WHITE, .4f)
        cv.drawCircle(x, y, r * .28f, paint)
        paint.color = 0xFF27272C.toInt()
        cv.drawCircle(x, y, 2f, paint)
    }

    private fun photo(cv: Canvas, w: Float, h: Float, c: WidgetConfig, p: Palette, s: State) {
        if (s.photo != null) {
            image(cv, s.photo, RectF(0f, 0f, w, h), 0f, c.photoScale != "fit")
            return
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = alpha(p.foreground, 45) }
        val x = w / 2
        val y = h * .43f
        val size = minOf(w, h) * .45f
        cv.drawCircle(x + size * .24f, y - size * .15f, size * .14f, paint)
        cv.drawPath(
            Path().apply {
                moveTo(x - size * .48f, y + size * .35f)
                lineTo(x - size * .08f, y - size * .18f)
                lineTo(x + size * .12f, y + size * .12f)
                lineTo(x + size * .29f, y - size * .01f)
                lineTo(x + size * .5f, y + size * .35f)
                close()
            },
            paint,
        )
        if (h >= 100)
            text(
                cv,
                "Elige una foto",
                x,
                h * .79f,
                12f,
                p.foreground,
                c,
                align = Paint.Align.CENTER,
            )
    }

    private fun shortcut(cv: Canvas, w: Float, h: Float, c: WidgetConfig, p: Palette, s: State) {
        val compact = h < 95
        val size = minOf(minOf(w, h) * .49f, 72f)
        val x = if (w > h * 1.6f) 15f else (w - size) / 2
        val y = if (compact) (h - size) / 2 else h * .19f
        if (c.shortcutIcon == "app" && s.appIcon != null)
            image(cv, s.appIcon, RectF(x, y, x + size, y + size), size * .23f, true)
        else {
            cv.drawRoundRect(
                RectF(x, y, x + size, y + size),
                size * .23f,
                size * .23f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader =
                        LinearGradient(
                            x,
                            y,
                            x + size,
                            y + size,
                            p.accent,
                            blend(p.accent, Color.WHITE, .4f),
                            Shader.TileMode.CLAMP,
                        )
                },
            )
            text(
                cv,
                if (c.shortcutIcon == "app") "↗" else c.shortcutIcon,
                x + size / 2,
                y + size * .7f,
                size * .57f,
                Color.WHITE,
                c,
                align = Paint.Align.CENTER,
            )
        }
        if (!compact || w > h * 1.6f)
            text(
                cv,
                c.title.ifBlank { s.shortcutLabel },
                if (compact) x + size + 13 else w / 2,
                if (compact) h * .55f else h * .8f,
                if (compact) 12f else 13f,
                p.foreground,
                c,
                align = if (compact) Paint.Align.LEFT else Paint.Align.CENTER,
                maxWidth = if (compact) w - size - 42 else w - 22,
                bold = true,
            )
    }

    fun transportIcon(action: String, color: Int): Bitmap {
        val b = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        when (action) {
            "pause" -> {
                cv.drawRoundRect(RectF(33f, 29f, 43f, 67f), 2f, 2f, p)
                cv.drawRoundRect(RectF(53f, 29f, 63f, 67f), 2f, 2f, p)
            }
            "previous",
            "next" -> {
                cv.save()
                if (action == "previous") cv.scale(-1f, 1f, 48f, 48f)
                cv.drawPath(
                    Path().apply {
                        moveTo(28f, 29f)
                        lineTo(28f, 67f)
                        lineTo(58f, 48f)
                        close()
                    },
                    p,
                )
                cv.drawRoundRect(RectF(61f, 29f, 66f, 67f), 1f, 1f, p)
                cv.restore()
            }
            else ->
                cv.drawPath(
                    Path().apply {
                        moveTo(36f, 27f)
                        lineTo(36f, 69f)
                        lineTo(69f, 48f)
                        close()
                    },
                    p,
                )
        }
        return b
    }

    private fun musicNote(cv: Canvas, x: Float, y: Float, size: Float, color: Int) {
        val p =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.STROKE
                strokeWidth = size * .13f
                strokeCap = Paint.Cap.ROUND
            }
        cv.drawPath(
            Path().apply {
                moveTo(x, y + size)
                lineTo(x, y + size * .2f)
                lineTo(x + size * .6f, y)
                lineTo(x + size * .6f, y + size * .8f)
            },
            p,
        )
        p.style = Paint.Style.FILL
        cv.drawOval(RectF(x - size * .23f, y + size * .78f, x + size * .04f, y + size * 1.04f), p)
        cv.drawOval(RectF(x + size * .38f, y + size * .58f, x + size * .65f, y + size * .84f), p)
    }

    private fun image(cv: Canvas, b: Bitmap, rect: RectF, radius: Float, crop: Boolean) {
        cv.save()
        if (radius > 0)
            cv.clipPath(Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) })
        val ratio =
            if (crop) maxOf(rect.width() / b.width, rect.height() / b.height)
            else minOf(rect.width() / b.width, rect.height() / b.height)
        val dw = b.width * ratio
        val dh = b.height * ratio
        cv.clipRect(rect)
        cv.drawBitmap(
            b,
            null,
            RectF(
                rect.centerX() - dw / 2,
                rect.centerY() - dh / 2,
                rect.centerX() + dw / 2,
                rect.centerY() + dh / 2,
            ),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )
        cv.restore()
    }

    private fun text(
        cv: Canvas,
        value: String,
        x: Float,
        y: Float,
        size: Float,
        color: Int,
        c: WidgetConfig,
        align: Paint.Align = Paint.Align.LEFT,
        bold: Boolean = false,
        maxWidth: Float = Float.MAX_VALUE,
    ) {
        val p =
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                textSize = (size * c.textScale).coerceAtLeast(5f)
                typeface = Typeface.create(c.font, if (bold) Typeface.BOLD else Typeface.NORMAL)
                textAlign = align
            }
        val shown =
            if (maxWidth.isFinite() && maxWidth > 0)
                TextUtils.ellipsize(value, p, maxWidth, TextUtils.TruncateAt.END).toString()
            else value
        cv.drawText(shown, x, y, p)
    }

    private fun paragraph(
        cv: Canvas,
        value: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        size: Float,
        color: Int,
        c: WidgetConfig,
        bold: Boolean = false,
    ) {
        if (width < 8 || height < 8) return
        val effectiveSize = size * if (c.kind == WidgetKind.NOTE) 1f else c.textScale
        val paint =
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                textSize = effectiveSize.coerceAtLeast(7f)
                typeface = Typeface.create(c.font, if (bold) Typeface.BOLD else Typeface.NORMAL)
            }
        val layout =
            StaticLayout.Builder.obtain(
                    value,
                    0,
                    value.length,
                    paint,
                    width.toInt().coerceAtLeast(1),
                )
                .setAlignment(
                    when (c.alignment) {
                        "center" -> Layout.Alignment.ALIGN_CENTER
                        "end" -> Layout.Alignment.ALIGN_OPPOSITE
                        else -> Layout.Alignment.ALIGN_NORMAL
                    }
                )
                .setIncludePad(false)
                .setLineSpacing(2f, 1f)
                .setMaxLines(maxOf(1, (height / (effectiveSize + 3)).toInt()))
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()
        cv.save()
        cv.translate(x, y)
        cv.clipRect(0f, 0f, width, height)
        layout.draw(cv)
        cv.restore()
    }

    private fun date(time: Long, format: String) =
        SimpleDateFormat(format, Locale.getDefault()).format(Date(time))

    private fun alpha(color: Int, alpha: Int) =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private fun blend(a: Int, b: Int, t: Float) =
        Color.argb(
            Color.alpha(a),
            (Color.red(a) * (1 - t) + Color.red(b) * t).toInt(),
            (Color.green(a) * (1 - t) + Color.green(b) * t).toInt(),
            (Color.blue(a) * (1 - t) + Color.blue(b) * t).toInt(),
        )

    private fun luminance(c: Int) =
        (Color.red(c) * .299f + Color.green(c) * .587f + Color.blue(c) * .114f) / 255
}
