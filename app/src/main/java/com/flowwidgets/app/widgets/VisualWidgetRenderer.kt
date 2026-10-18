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
        val photos: List<Bitmap> = emptyList(),
        val shortcuts: List<ShortcutVisual> = emptyList(),
        val calendarAllowed: Boolean = false,
    )

    data class ShortcutVisual(
        val entry: ShortcutEntry,
        val label: String,
        val icon: Bitmap?,
        val available: Boolean,
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
            c.automaticColors &&
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
            if (c.automaticColors && c.accent == 0xFFFF725E)
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
    fun shortcutEntries(c: WidgetConfig): List<ShortcutEntry> {
        val main =
            if (c.shortcutPackage != null || c.shortcutUri != null)
                listOf(
                    ShortcutEntry(
                        "primary",
                        c.title,
                        c.shortcutPackage,
                        c.shortcutUri,
                        c.shortcutIcon,
                    )
                )
            else emptyList()
        return (main + c.shortcuts).distinctBy { it.id }.take(16)
    }

    fun shortcutIntent(context: Context, entry: ShortcutEntry): android.content.Intent? =
        runCatching {
                val intent =
                    entry.uri?.let {
                        if (it.startsWith("android.settings.")) android.content.Intent(it)
                        else
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                Uri.parse(it),
                            )
                    }
                        ?: entry.packageName?.let {
                            context.packageManager.getLaunchIntentForPackage(it)
                        }
                intent?.takeIf { it.resolveActivity(context.packageManager) != null }
            }
            .getOrNull()

    fun snapshot(
        context: Context,
        c: WidgetConfig,
        now: Long = System.currentTimeMillis(),
        photoLimit: Int = 1,
        shortcutLimit: Int = 16,
    ): State {
        WidgetTypography.initialize(context)
        val photos =
            if (c.kind == WidgetKind.PHOTO) {
                val uris = (c.photoUris.ifEmpty { listOfNotNull(c.photoUri) }).distinct()
                val start =
                    if (c.rotatePhotos && uris.isNotEmpty()) ((now / 1_800_000) % uris.size).toInt()
                    else 0
                (uris.drop(start) + uris.take(start)).take(photoLimit.coerceIn(1, 9)).mapNotNull {
                    uri ->
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
            } else emptyList()
        val shortcuts =
            if (c.kind == WidgetKind.SHORTCUT)
                shortcutEntries(c).take(shortcutLimit).map { entry ->
                    var icon: Bitmap? = null
                    var label =
                        entry.label.ifBlank {
                            if (entry.uri?.startsWith("android.settings.") == true) "Ajustes"
                            else if (entry.uri != null) "Contacto" else "Aplicación"
                        }
                    entry.packageName?.let { pkg ->
                        runCatching {
                            if (entry.label.isBlank())
                                label =
                                    context.packageManager
                                        .getApplicationLabel(
                                            context.packageManager.getApplicationInfo(pkg, 0)
                                        )
                                        .toString()
                            val d = context.packageManager.getApplicationIcon(pkg)
                            icon =
                                Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888).also {
                                    d.setBounds(0, 0, 128, 128)
                                    d.draw(Canvas(it))
                                }
                        }
                    }
                    ShortcutVisual(entry, label, icon, shortcutIntent(context, entry) != null)
                }
            else emptyList()
        return State(
            now = now,
            battery =
                if (c.kind == WidgetKind.BATTERY) DeviceData.battery(context)
                else BatteryInfo(-1, false),
            weather =
                if (c.kind == WidgetKind.WEATHER) WeatherRepository.cached(context, c) else null,
            media = if (c.kind == WidgetKind.MEDIA) MediaRepository.current(context) else null,
            photo = photos.firstOrNull(),
            appIcon = shortcuts.firstOrNull()?.icon,
            shortcutLabel = shortcuts.firstOrNull()?.label ?: "Elige un acceso",
            events =
                if (c.kind in listOf(WidgetKind.CALENDAR, WidgetKind.CLOCK) && c.showEvents)
                    DeviceData.events(context)
                else emptyList(),
            photos = photos,
            shortcuts = shortcuts,
            calendarAllowed =
                context.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }

    fun render(
        c: WidgetConfig,
        width: Int,
        height: Int,
        state: State,
        drawClock: Boolean = true,
        maxScale: Float = 2f,
    ): Bitmap {
        val w = width.coerceIn(40, 1000).toFloat()
        val h = height.coerceIn(40, 1000).toFloat()
        val factor = minOf(maxScale, 720f / maxOf(w, h))
        val bitmap =
            Bitmap.createBitmap(
                (w * factor).roundToInt(),
                (h * factor).roundToInt(),
                Bitmap.Config.ARGB_8888,
            )
        val canvas = Canvas(bitmap)
        canvas.scale(factor, factor)
        val radius = SoftLightGlassRenderer.radius(c, w, h)
        canvas.clipPath(
            Path().apply { addRoundRect(RectF(0f, 0f, w, h), radius, radius, Path.Direction.CW) }
        )
        val p = palette(c)
        canvas.drawBitmap(
            SoftLightGlassRenderer.background(c, bitmap.width, bitmap.height),
            null,
            RectF(0f, 0f, w, h),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )
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
        val plan = AdaptiveWidgetLayoutEngine.plan(c.kind, w, h)
        val geometry = AdaptiveWidgetLayoutEngine.clock(c, plan)
        if (draw) {
            val rect = geometry.face
            if (c.analog) {
                val size = minOf(rect.width(), rect.height())
                cv.save()
                cv.translate(rect.centerX() - size / 2, rect.centerY() - size / 2)
                cv.scale(size / 320, size / 320)
                cv.drawBitmap(dial(c), 0f, 0f, null)
                val cal = Calendar.getInstance().apply { timeInMillis = s.now }
                val minute = cal.get(Calendar.MINUTE) + cal.get(Calendar.SECOND) / 60f
                val hour = cal.get(Calendar.HOUR) + minute / 60f
                for ((angle, long) in listOf(hour * 30 to false, minute * 6 to true)) {
                    cv.save()
                    cv.rotate(angle, 160f, 160f)
                    cv.drawBitmap(
                        hand(
                            long,
                            false,
                            p.accent,
                            c.style == WidgetStyle.GLASS,
                            if (c.automaticColors) null else p.foreground,
                        ),
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
            } else {
                val format =
                    (if (c.clock24) "HH:mm" else "h:mm") +
                        (if (c.showSeconds && !plan.tiny) ":ss" else "") +
                        (if (c.clock24 || plan.tiny) "" else " a")
                val font =
                    minOf(
                        rect.width() / (if (c.showSeconds && !plan.tiny) 5.8f else 3.4f),
                        rect.height() * .64f,
                    )
                val sample =
                    "88:88" +
                        (if (c.showSeconds && !plan.tiny) ":88" else "") +
                        (if (!c.clock24 && !plan.tiny) " PM" else "")
                val fitted =
                    WidgetTypography.fit(
                        c,
                        sample,
                        font * c.textScale,
                        rect.width() - 8,
                        rect.height() - 4,
                    ) / c.textScale
                text(
                    cv,
                    date(s.now, format),
                    rect.centerX(),
                    rect.centerY() + fitted * c.textScale * .34f,
                    fitted,
                    p.foreground,
                    c,
                    align = Paint.Align.CENTER,
                )
            }
            geometry.date?.let { r ->
                text(
                    cv,
                    date(s.now, "EEE, d MMM"),
                    r.centerX(),
                    r.centerY() + 4,
                    minOf(12f, r.width() / 7),
                    p.foreground,
                    c,
                    align = Paint.Align.CENTER,
                    maxWidth = r.width(),
                )
            }
        }
        geometry.calendar?.let { r -> region(cv, r) { rw, rh -> month(cv, rw, rh, c, p, s) } }
        geometry.zones.forEachIndexed { i, r ->
            val id = c.timeZones[i]
            text(
                cv,
                id.substringAfterLast('/').replace('_', ' '),
                r.left,
                r.top + 10,
                9f,
                alpha(p.foreground, 170),
                c,
                maxWidth = r.width(),
            )
            if (draw) {
                val format =
                    SimpleDateFormat(if (c.clock24) "HH:mm" else "h:mm a", Locale.getDefault())
                        .apply { timeZone = TimeZone.getTimeZone(id) }
                text(
                    cv,
                    format.format(Date(s.now)),
                    r.left,
                    r.bottom - 4,
                    17f,
                    p.foreground,
                    c,
                    maxWidth = r.width(),
                )
            }
        }
        if (c.showEvents)
            geometry.events?.let { agenda(cv, it, c, p, s, plan.eventCapacity.coerceAtLeast(1)) }
    }

    /** Native AnalogClock uses these original bitmap Icons and keeps the hands on time itself. */
    fun dial(c: WidgetConfig): Bitmap {
        val b = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val automaticFace =
            c.automaticColors &&
                c.style != WidgetStyle.GLASS &&
                c.background == 0xFF20272D &&
                c.textColor == 0xFFFFFFFF
        val face = if (automaticFace) white else palette(c).background
        val fg = if (automaticFace) ink else palette(c).foreground
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

    fun hand(
        minute: Boolean,
        second: Boolean,
        accent: Int,
        light: Boolean = false,
        foreground: Int? = null,
    ): Bitmap {
        val b = Bitmap.createBitmap(32, 320, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (second) accent else foreground ?: if (light) white else ink
                strokeWidth = if (second) 2f else if (minute) 5f else 7f
                strokeCap = Paint.Cap.ROUND
            }
        cv.drawLine(16f, if (minute) 35f else 70f, 16f, if (second) 186f else 174f, paint)
        cv.drawCircle(16f, 160f, if (second) 4f else 5f, paint)
        return b
    }

    private fun battery(cv: Canvas, w: Float, h: Float, c: WidgetConfig, p: Palette, s: State) {
        val plan = AdaptiveWidgetLayoutEngine.plan(c.kind, w, h)
        val b = s.battery
        val level = if (b.level >= 0) "${b.level}%" else "—"
        val status = if (b.level < 0) "Sin lectura" else if (b.charging) "Cargando" else "En uso"
        val details =
            listOfNotNull(
                "Estado" to status,
                b.source?.let { "Conexión" to it },
                b.temperatureC?.let {
                    "Temperatura" to String.format(Locale.getDefault(), "%.1f °C", it)
                },
                b.voltageMv?.let {
                    "Voltaje" to String.format(Locale.getDefault(), "%.2f V", it / 1000.0)
                },
                b.health?.let { "Estado informado" to it },
                b.technology?.let { "Tecnología" to it },
            )
        if (plan.row) {
            text(
                cv,
                level,
                12f,
                h * .55f,
                minOf(32f, h * .43f),
                p.foreground,
                c,
                bold = true,
                maxWidth = w * .4f,
            )
            text(cv, status, w * .43f, 22f, 11f, p.foreground, c, maxWidth = w * .55f - 12)
            val left = w * .43f
            val right = w - 14
            val y = h * .57f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = alpha(p.foreground, 35) }
            cv.drawRoundRect(RectF(left, y, right, y + 7), 4f, 4f, paint)
            if (b.level >= 0) {
                paint.color = p.accent
                cv.drawRoundRect(
                    RectF(left, y, left + (right - left) * b.level.coerceIn(0, 100) / 100, y + 7),
                    4f,
                    4f,
                    paint,
                )
            }
            b.source?.let {
                text(cv, it, left, h - 8, 9f, alpha(p.foreground, 180), c, maxWidth = right - left)
            }
            return
        }
        val cx = if (plan.expanded && !plan.panel) w * .24f else w / 2
        val cy =
            if (plan.panel) h * .27f
            else if (plan.expanded) h * .47f else h * (if (plan.tiny) .5f else .43f)
        val r =
            minOf(
                if (plan.panel) h * .2f else h * .29f,
                if (plan.expanded && !plan.panel) w * .17f else w * .34f,
            )
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = maxOf(5f, r * .16f)
                strokeCap = Paint.Cap.ROUND
                color = alpha(p.foreground, 30)
            }
        cv.drawCircle(cx, cy, r, paint)
        if (b.level >= 0) {
            paint.color = p.accent
            cv.drawArc(
                cx - r,
                cy - r,
                cx + r,
                cy + r,
                -90f,
                b.level.coerceIn(0, 100) * 3.6f,
                false,
                paint,
            )
        }
        text(
            cv,
            level,
            cx,
            cy + r * .19f,
            r * .54f,
            p.foreground,
            c,
            align = Paint.Align.CENTER,
            bold = true,
        )
        if (plan.tiny) return
        if (plan.panel || plan.expanded) {
            val rect =
                if (plan.panel) RectF(18f, h * .55f, w - 18, h - 12)
                else RectF(w * .5f, 18f, w - 14, h - 12)
            val rows = details.take(if (plan.panel) minOf(6, (rect.height() / 30).toInt()) else 3)
            val line = minOf(32f, rect.height() / maxOf(1, rows.size))
            rows.forEachIndexed { i, (label, value) ->
                text(
                    cv,
                    label,
                    rect.left,
                    rect.top + i * line + 11,
                    9f,
                    alpha(p.foreground, 165),
                    c,
                    maxWidth = rect.width(),
                )
                text(
                    cv,
                    value,
                    rect.left,
                    rect.top + i * line + 26,
                    12f,
                    p.foreground,
                    c,
                    maxWidth = rect.width(),
                )
            }
        } else {
            batteryIcon(cv, w / 2 - 12, h * .78f, 24f, p.accent, b.charging)
            text(cv, status, w / 2, h * .94f, 10f, p.foreground, c, align = Paint.Align.CENTER)
        }
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
        val plan = AdaptiveWidgetLayoutEngine.plan(c.kind, w, h)
        val cal = Calendar.getInstance().apply { timeInMillis = s.now }
        if (plan.tiny) {
            text(
                cv,
                cal.get(Calendar.DAY_OF_MONTH).toString(),
                w / 2,
                h * .74f,
                minOf(w * .52f, h * .58f),
                p.foreground,
                c,
                align = Paint.Align.CENTER,
            )
            return
        }
        if (plan.row || plan.column) {
            text(
                cv,
                date(s.now, "EEE, d MMM"),
                12f,
                if (plan.column) 25f else 27f,
                if (plan.column) 12f else 17f,
                p.accent,
                c,
                maxWidth = w - 24,
                bold = true,
            )
            if (plan.column)
                text(
                    cv,
                    cal.get(Calendar.DAY_OF_MONTH).toString(),
                    w / 2,
                    h * .57f,
                    minOf(48f, w * .5f),
                    p.foreground,
                    c,
                    align = Paint.Align.CENTER,
                )
            if (c.showEvents)
                agenda(
                    cv,
                    RectF(12f, if (plan.column) h * .68f else 36f, w - 12, h - 8),
                    c,
                    p,
                    s,
                    1,
                )
            return
        }
        if (plan.panel) {
            region(cv, RectF(0f, 0f, w, h * .62f)) { rw, rh ->
                month(cv, rw, rh, c.copy(showEvents = false), p, s)
            }
            agenda(cv, RectF(14f, h * .65f, w - 14, h - 12), c, p, s, 4)
        } else if (plan.wide) {
            region(cv, RectF(0f, 0f, w * .48f, h)) { rw, rh ->
                month(cv, rw, rh, c.copy(showEvents = false), p, s)
            }
            agenda(cv, RectF(w * .51f, 14f, w - 12, h - 12), c, p, s, 3)
        } else {
            text(
                cv,
                date(s.now, "MMMM yyyy"),
                14f,
                24f,
                14f,
                p.accent,
                c,
                maxWidth = w - 28,
                bold = true,
            )
            val week =
                (cal.clone() as Calendar).apply {
                    add(
                        Calendar.DAY_OF_MONTH,
                        -((get(Calendar.DAY_OF_WEEK) -
                            (if (c.weekStartsMonday) Calendar.MONDAY else Calendar.SUNDAY) + 7) % 7),
                    )
                }
            val top = if (plan.expanded) 37f else h * .35f
            val cell = (w - 24) / 7
            for (i in 0..6) {
                val x = 12 + cell * (i + .5f)
                text(
                    cv,
                    date(week.timeInMillis, "EEE").take(1),
                    x,
                    top + 10,
                    10f,
                    alpha(p.foreground, 160),
                    c,
                    align = Paint.Align.CENTER,
                )
                val today =
                    week.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR) &&
                        week.get(Calendar.YEAR) == cal.get(Calendar.YEAR)
                if (today)
                    cv.drawCircle(
                        x,
                        top + 32,
                        minOf(15f, cell * .43f),
                        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.accent },
                    )
                text(
                    cv,
                    week.get(Calendar.DAY_OF_MONTH).toString(),
                    x,
                    top + 37,
                    14f,
                    if (today) (if (luminance(p.accent) > .55) ink else Color.WHITE)
                    else p.foreground,
                    c,
                    align = Paint.Align.CENTER,
                )
                week.add(Calendar.DAY_OF_MONTH, 1)
            }
            if (plan.expanded) agenda(cv, RectF(14f, top + 58, w - 14, h - 12), c, p, s, 2)
            else
                text(
                    cv,
                    date(s.now, "EEEE"),
                    w / 2,
                    h * .86f,
                    12f,
                    alpha(p.foreground, 175),
                    c,
                    align = Paint.Align.CENTER,
                    maxWidth = w - 20,
                )
        }
    }

    private fun agenda(cv: Canvas, rect: RectF, c: WidgetConfig, p: Palette, s: State, count: Int) {
        if (rect.height() < 16) return
        if (!c.showEvents || !s.calendarAllowed) {
            paragraph(
                cv,
                if (c.showEvents) "Permiso de calendario pendiente"
                else "Activa eventos en el editor",
                rect.left,
                rect.top,
                rect.width(),
                rect.height(),
                10f,
                alpha(p.foreground, 160),
                c,
            )
            return
        }
        val events = s.events.take(count.coerceAtLeast(1))
        if (events.isEmpty()) {
            paragraph(
                cv,
                "Sin próximos eventos",
                rect.left,
                rect.top,
                rect.width(),
                rect.height(),
                11f,
                alpha(p.foreground, 175),
                c,
            )
            return
        }
        val line = minOf(34f, rect.height() / events.size)
        events.forEachIndexed { i, e ->
            val y = rect.top + i * line
            cv.drawRoundRect(
                RectF(rect.left, y + 3, rect.left + 3, y + line - 3),
                1.5f,
                1.5f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.accent },
            )
            text(
                cv,
                date(e.time, "EEE HH:mm"),
                rect.left + 9,
                y + 10,
                8f,
                alpha(p.foreground, 160),
                c,
                maxWidth = rect.width() - 12,
            )
            text(
                cv,
                e.title,
                rect.left + 9,
                y + minOf(25f, line - 2),
                11f,
                p.foreground,
                c,
                maxWidth = rect.width() - 12,
            )
        }
    }

    private fun month(cv: Canvas, w: Float, h: Float, c: WidgetConfig, p: Palette, s: State) {
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
        val plan = AdaptiveWidgetLayoutEngine.plan(c.kind, w, h)
        val weather = s.weather
        if (plan.tiny) {
            weatherIcon(cv, w * .31f, 3f, minOf(w * .4f, h * .48f), weather?.code ?: -1)
            text(
                cv,
                weather?.let { "${it.temp.roundToInt()}°" } ?: "—°",
                w / 2,
                h * .84f,
                minOf(31f, h * .4f),
                p.foreground,
                c,
                align = Paint.Align.CENTER,
            )
            if (weather != null)
                text(
                    cv,
                    date(weather.updatedAt, "HH:mm"),
                    w / 2,
                    h - 2,
                    7f,
                    alpha(p.foreground, 180),
                    c,
                    align = Paint.Align.CENTER,
                )
            return
        }
        if (plan.row) {
            weatherIcon(cv, w - 54, 8f, 43f, weather?.code ?: -1)
            text(
                cv,
                c.city.ifBlank { "El tiempo" },
                10f,
                17f,
                10f,
                p.foreground,
                c,
                maxWidth = w - 68,
            )
            text(
                cv,
                weather?.let { "${it.temp.roundToInt()}°" } ?: "—°",
                10f,
                h - 16,
                34f,
                p.foreground,
                c,
            )
            if (w >= 165)
                text(
                    cv,
                    weather?.description ?: "Elige una ciudad",
                    72f,
                    h * .63f,
                    10f,
                    p.foreground,
                    c,
                    maxWidth = w - 130,
                )
        } else {
            val current =
                when {
                    plan.panel -> RectF(0f, 0f, w, h * .33f)
                    plan.wide -> RectF(0f, 0f, w * .37f, h - 12)
                    else -> RectF(0f, 0f, w, h * (if (plan.column) .72f else .57f))
                }
            weatherCurrent(cv, current, c, p, s)
            if (weather != null) {
                val hourly =
                    when {
                        plan.panel -> RectF(12f, h * .36f, w - 12, h * .56f)
                        plan.wide -> RectF(w * .4f, 14f, w - 12, h * .52f)
                        else -> RectF(12f, h * .65f, w - 12, h - 16)
                    }
                if (plan.hourCapacity > 0)
                    forecastHours(cv, hourly, c, p, weather.hours.take(plan.hourCapacity))
                val daily =
                    if (plan.panel) RectF(14f, h * .61f, w - 14, h - 22)
                    else if (plan.wide) RectF(w * .4f, h * .6f, w - 12, h - 18) else null
                daily?.let { r ->
                    val days = weather.days.take(plan.dayCapacity)
                    val line = minOf(30f, r.height() / maxOf(1, days.size))
                    days.forEachIndexed { i, d ->
                        val y = r.top + i * line
                        text(cv, d.date.takeLast(5), r.left, y + 15, 10f, p.foreground, c)
                        weatherIcon(cv, r.left + 45, y, 22f, d.code)
                        text(
                            cv,
                            "${d.low.roundToInt()}° / ${d.high.roundToInt()}°" +
                                (d.rainProbability?.let { "   $it%" } ?: ""),
                            r.left + 75,
                            y + 15,
                            10f,
                            p.foreground,
                            c,
                            maxWidth = r.width() - 78,
                        )
                    }
                }
            }
        }
        if (weather != null)
            text(
                cv,
                "${date(weather.updatedAt,"d MMM HH:mm")} · Open-Meteo",
                10f,
                h - 4,
                7f,
                alpha(p.foreground, 180),
                c,
                maxWidth = w - 20,
            )
    }

    private fun weatherCurrent(cv: Canvas, r: RectF, c: WidgetConfig, p: Palette, s: State) {
        region(cv, r) { w, h ->
            val weather = s.weather
            val code =
                weather?.let { if (it.code >= 0) it.code else it.hours.firstOrNull()?.code ?: -1 }
                    ?: -1
            weatherIcon(cv, w - minOf(54f, w * .36f) - 10, 16f, minOf(48f, w * .36f, h * .5f), code)
            text(
                cv,
                c.city.ifBlank { "El tiempo" },
                12f,
                15f,
                11f,
                p.foreground,
                c,
                maxWidth = w - 24,
            )
            text(
                cv,
                weather?.let { "${it.temp.roundToInt()}°" } ?: "—°",
                12f,
                h * .58f,
                minOf(52f, h * .43f),
                p.foreground,
                c,
            )
            text(
                cv,
                weather?.description ?: "Elige una ciudad",
                12f,
                h * .8f,
                10f,
                p.foreground,
                c,
                maxWidth = w - 24,
            )
            text(
                cv,
                weather?.let { "Máx. ${it.high.roundToInt()}°  Mín. ${it.low.roundToInt()}°" }
                    ?: "Actualiza con conexión",
                12f,
                h * .96f,
                9f,
                alpha(p.foreground, 185),
                c,
                maxWidth = w - 24,
            )
            if (w >= 300 && weather != null) {
                val extra =
                    listOfNotNull(
                            weather.feelsLike?.let { "Sensación ${it.roundToInt()}°" },
                            weather.humidity?.let { "Humedad $it%" },
                            weather.windKmh?.let { "Viento ${it.roundToInt()} km/h" },
                        )
                        .joinToString(" · ")
                text(cv, extra, w * .4f, h * .66f, 10f, p.foreground, c, maxWidth = w * .56f)
            }
        }
    }

    private fun forecastHours(
        cv: Canvas,
        r: RectF,
        c: WidgetConfig,
        p: Palette,
        hours: List<WeatherHour>,
    ) {
        if (r.width() < 25 || r.height() < 35) return
        val cell = r.width() / maxOf(1, hours.size)
        hours.forEachIndexed { i, h ->
            val x = r.left + cell * (i + .5f)
            text(
                cv,
                h.time.take(5),
                x,
                r.top + 9,
                8f,
                alpha(p.foreground, 180),
                c,
                align = Paint.Align.CENTER,
            )
            weatherIcon(cv, x - 11, r.top + 13, 22f, h.code)
            text(
                cv,
                "${h.temp.roundToInt()}°",
                x,
                r.bottom - 1,
                11f,
                p.foreground,
                c,
                align = Paint.Align.CENTER,
            )
        }
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
        val plan = AdaptiveWidgetLayoutEngine.plan(c.kind, w, h)
        val header = if (plan.tiny) 0f else if (plan.row) 6f else 34f
        if (header > 0)
            cv.drawRect(
                0f,
                0f,
                w,
                header,
                Paint().apply { color = alpha(p.accent, (255 * c.opacity).roundToInt()) },
            )
        if (header > 10)
            text(
                cv,
                c.title.ifBlank { "Notas" },
                14f,
                22f,
                12f,
                if (luminance(p.accent) > .5) ink else Color.WHITE,
                c,
                bold = true,
                maxWidth = w - 28,
            )
        if (plan.panel && c.noteItems.isNotEmpty()) {
            AdaptiveWidgetLayoutEngine.noteRows(plan, c.noteItems.size).forEachIndexed { i, r ->
                val item = c.noteItems[i]
                val paint =
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = if (item.checked) p.accent else alpha(p.foreground, 110)
                        style = if (item.checked) Paint.Style.FILL else Paint.Style.STROKE
                        strokeWidth = 1.5f
                    }
                cv.drawCircle(r.left + 10, r.centerY(), 8f, paint)
                if (item.checked) {
                    paint.color = if (luminance(p.accent) > .5) ink else Color.WHITE
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 1.8f
                    cv.drawPath(
                        Path().apply {
                            moveTo(r.left + 6, r.centerY())
                            lineTo(r.left + 9, r.centerY() + 3)
                            lineTo(r.left + 14, r.centerY() - 4)
                        },
                        paint,
                    )
                }
                text(
                    cv,
                    item.text,
                    r.left + 27,
                    r.centerY() + 5,
                    14f,
                    if (item.checked) alpha(p.foreground, 135) else p.foreground,
                    c,
                    maxWidth = r.width() - 30,
                )
            }
            return
        }
        val size = (if (plan.tiny) 12f else if (plan.row) 14f else 17f) * c.textScale
        val pad = if (plan.tiny) 7f else 13f
        val available = h - header - pad * 2
        paragraph(
            cv,
            c.note.ifBlank { "Escribe tu próxima idea." },
            pad,
            header + pad,
            w - 2 * pad,
            if (plan.tiny || plan.row) minOf(available, 2 * (size + 3)) else available,
            size,
            p.foreground,
            c,
        )
    }

    private fun media(cv: Canvas, w: Float, h: Float, c: WidgetConfig, p: Palette, s: State) {
        val plan = AdaptiveWidgetLayoutEngine.plan(c.kind, w, h)
        val g = AdaptiveWidgetLayoutEngine.media(plan)
        val media = s.media
        if (media?.art != null) image(cv, media.art, g.art, if (plan.tiny) 0f else 10f, true)
        else {
            cv.drawRoundRect(
                g.art,
                10f,
                10f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = alpha(Color.BLACK, 22) },
            )
            vinyl(
                cv,
                g.art.centerX(),
                g.art.centerY(),
                minOf(g.art.width(), g.art.height()) * .36f,
                p.accent,
            )
        }
        if (plan.tiny) {
            if (media != null) {
                val x = w - 14
                val y = h - 14
                cv.drawCircle(
                    x,
                    y,
                    10f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = alpha(Color.BLACK, 120) },
                )
                image(
                    cv,
                    transportIcon(if (media.playing) "pause" else "play", Color.WHITE),
                    RectF(x - 10, y - 10, x + 10, y + 10),
                    0f,
                    true,
                )
            }
            return
        }
        g.title?.let { r ->
            paragraph(
                cv,
                media?.title ?: "Sin sesión activa",
                r.left,
                r.top,
                r.width(),
                r.height(),
                if (plan.row) 11f else 15f,
                p.foreground,
                c,
                bold = true,
            )
        }
        g.artist?.let { r ->
            if (r.height() >= 10)
                paragraph(
                    cv,
                    media?.artist ?: "Abre tu reproductor",
                    r.left,
                    r.top,
                    r.width(),
                    r.height(),
                    10f,
                    alpha(p.foreground, 190),
                    c,
                )
        }
        g.album?.let { r ->
            if (r.height() > 10)
                paragraph(
                    cv,
                    media?.album ?: "",
                    r.left,
                    r.top,
                    r.width(),
                    r.height(),
                    10f,
                    alpha(p.foreground, 175),
                    c,
                )
        }
        g.progress?.let { r ->
            if (
                media?.positionMs != null &&
                    media.durationMs != null &&
                    media.durationMs > 0 &&
                    r.width() > 10
            ) {
                val y = r.top + 4
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = alpha(p.foreground, 35) }
                cv.drawRoundRect(RectF(r.left, y, r.right, y + 3), 2f, 2f, paint)
                paint.color = p.foreground
                cv.drawRoundRect(
                    RectF(
                        r.left,
                        y,
                        r.left +
                            r.width() *
                                (media.positionMs.toFloat() / media.durationMs).coerceIn(0f, 1f),
                        y + 3,
                    ),
                    2f,
                    2f,
                    paint,
                )
                text(cv, elapsed(media.positionMs), r.left, r.bottom - 2, 8f, p.foreground, c)
                text(
                    cv,
                    elapsed(media.durationMs),
                    r.right,
                    r.bottom - 2,
                    8f,
                    p.foreground,
                    c,
                    align = Paint.Align.RIGHT,
                )
            }
        }
        g.queue?.let { r ->
            val rows = media?.queue.orEmpty().take(3)
            if (rows.isEmpty())
                text(
                    cv,
                    if (media == null) "Activa acceso multimedia en Ajustes"
                    else "La sesión no comparte una cola",
                    r.left,
                    r.top + 12,
                    9f,
                    alpha(p.foreground, 160),
                    c,
                    maxWidth = r.width(),
                )
            val line = minOf(28f, r.height() / maxOf(1, rows.size))
            rows.forEachIndexed { i, e ->
                text(
                    cv,
                    "${i+1}. ${e.title}",
                    r.left,
                    r.top + i * line + 16,
                    11f,
                    p.foreground,
                    c,
                    maxWidth = r.width(),
                )
            }
        }
    }

    private fun elapsed(ms: Long): String {
        val seconds = (ms / 1000).coerceAtLeast(0)
        return "${seconds/60}:${(seconds%60).toString().padStart(2,'0')}"
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
        val plan = AdaptiveWidgetLayoutEngine.plan(c.kind, w, h)
        val photos = s.photos.ifEmpty { listOfNotNull(s.photo) }.take(plan.photoCapacity)
        if (photos.isNotEmpty()) {
            if (photos.size == 1)
                image(cv, photos[0], RectF(0f, 0f, w, h), 0f, c.photoScale != "fit")
            else
                AdaptiveWidgetLayoutEngine.grid(plan, photos.size).forEachIndexed { i, r ->
                    image(cv, photos[i], r, 10f, c.photoScale != "fit")
                }
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
                "Elige tus fotografías",
                x,
                h * .79f,
                11f,
                p.foreground,
                c,
                align = Paint.Align.CENTER,
                maxWidth = w - 20,
            )
    }

    private fun shortcut(cv: Canvas, w: Float, h: Float, c: WidgetConfig, p: Palette, s: State) {
        val plan = AdaptiveWidgetLayoutEngine.plan(c.kind, w, h)
        val entries = s.shortcuts.take(plan.shortcutCapacity)
        val add = entries.size < plan.shortcutCapacity
        val rects =
            AdaptiveWidgetLayoutEngine.grid(
                plan,
                (entries.size + if (add) 1 else 0).coerceAtLeast(1),
            )
        rects.forEachIndexed { i, r ->
            val entry = entries.getOrNull(i)
            val size = minOf(r.width() * .57f, r.height() * (if (plan.tiny) .72f else .55f), 76f)
            val x = r.centerX() - size / 2
            val y =
                if (plan.tiny) r.centerY() - size / 2
                else r.top + maxOf(4f, (r.height() - size - 20) / 2)
            if (entry?.entry?.icon == "app" && entry.icon != null)
                image(cv, entry.icon, RectF(x, y, x + size, y + size), size * .22f, true)
            else {
                val paint =
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color =
                            if (entry?.available == false) alpha(p.foreground, 50)
                            else if (entry == null) alpha(p.foreground, 20) else p.accent
                    }
                cv.drawRoundRect(RectF(x, y, x + size, y + size), size * .22f, size * .22f, paint)
                text(
                    cv,
                    if (entry == null) "+"
                    else if (entry.entry.icon == "app") "↗" else entry.entry.icon,
                    x + size / 2,
                    y + size * .72f,
                    size * .58f,
                    if (entry == null) p.foreground else Color.WHITE,
                    c,
                    align = Paint.Align.CENTER,
                )
            }
            if (!plan.tiny && r.height() >= 60)
                text(
                    cv,
                    if (entry == null) "Añadir"
                    else if (!entry.available) "No disponible" else entry.label,
                    r.centerX(),
                    minOf(r.bottom - 3, y + size + 16),
                    10f,
                    p.foreground,
                    c,
                    align = Paint.Align.CENTER,
                    maxWidth = r.width() - 5,
                )
        }
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

    private inline fun region(cv: Canvas, r: RectF, draw: (Float, Float) -> Unit) {
        if (r.width() <= 0 || r.height() <= 0) return
        cv.save()
        cv.clipRect(r)
        cv.translate(r.left, r.top)
        draw(r.width(), r.height())
        cv.restore()
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
                WidgetTypography.style(this, c, textSize, bold, value.any(Char::isDigit))
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
                WidgetTypography.style(this, c, textSize, bold, value.any(Char::isDigit))
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
