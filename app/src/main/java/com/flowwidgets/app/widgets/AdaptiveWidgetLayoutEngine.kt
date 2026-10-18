package com.flowwidgets.app.widgets

import android.appwidget.AppWidgetManager
import android.graphics.RectF
import android.os.Bundle
import android.util.SizeF
import com.flowwidgets.app.data.WidgetConfig
import com.flowwidgets.app.data.WidgetKind
import kotlin.math.*

/** Dimensions are launcher-provided dp, never inferred from a grid-cell count. */
object AdaptiveWidgetLayoutEngine {
    enum class Profile {
        TILE,
        ROW,
        COLUMN,
        CARD,
        EXPANDED,
        WIDE,
        PANEL,
    }

    enum class Strategy {
        EXACT_SIZE,
        RESPONSIVE_CONTENT,
    }

    data class Plan(
        val width: Float,
        val height: Float,
        val profile: Profile,
        val strategy: Strategy,
    ) {
        val clockOnly
            get() = width <= 300f && height <= 300f

        val tiny
            get() = profile == Profile.TILE

        val row
            get() = profile == Profile.ROW

        val column
            get() = profile == Profile.COLUMN

        val expanded
            get() = profile in setOf(Profile.EXPANDED, Profile.WIDE, Profile.PANEL)

        val panel
            get() = profile == Profile.PANEL

        val wide
            get() = profile == Profile.WIDE

        val shortcutCapacity
            get() =
                when (profile) {
                    Profile.TILE -> 1
                    Profile.ROW -> if (width >= 320) 4 else if (width >= 240) 3 else 2
                    Profile.COLUMN ->
                        if (height >= 320 && width >= 160) 8 else if (height >= 250) 4 else 2
                    Profile.CARD -> 4
                    Profile.EXPANDED -> 6
                    Profile.WIDE -> 8
                    Profile.PANEL ->
                        minOf(16, maxOf(9, (width / 76).toInt() * (height / 76).toInt()))
                }

        val photoCapacity
            get() =
                when {
                    panel -> 9
                    wide -> 4
                    expanded -> 2
                    column && height >= 250 -> 2
                    else -> 1
                }

        val eventCapacity
            get() =
                when {
                    panel -> 4
                    wide -> 3
                    expanded -> 2
                    row -> 1
                    else -> 0
                }

        val noteCapacity
            get() = if (panel) minOf(8, ((height - 60) / 34).toInt()) else 0

        val hourCapacity
            get() =
                when {
                    panel -> 6
                    wide -> 6
                    expanded -> 5
                    profile == Profile.CARD -> 3
                    else -> 0
                }

        val dayCapacity
            get() = if (panel) 4 else if (wide) 2 else 0

        val supportsProgress
            get() = expanded || (column && height >= 250)
    }

    data class ClockGeometry(
        val face: RectF,
        val date: RectF?,
        val calendar: RectF?,
        val zones: List<RectF>,
        val events: RectF?,
    )

    data class MediaGeometry(
        val art: RectF,
        val title: RectF?,
        val artist: RectF?,
        val controls: RectF?,
        val progress: RectF?,
        val album: RectF?,
        val queue: RectF?,
    )

    fun plan(kind: WidgetKind, width: Float, height: Float): Plan {
        val w = width.takeIf { it.isFinite() && it > 0 }?.coerceIn(40f, 1000f) ?: 180f
        val h = height.takeIf { it.isFinite() && it > 0 }?.coerceIn(40f, 1000f) ?: 180f
        val profile =
            when {
                w < 120 && h < 125 -> Profile.TILE
                h < 112 -> if (w < 130) Profile.TILE else Profile.ROW
                w < 132 || (h > w * 1.55f && w < 240) -> Profile.COLUMN
                w >= 280 && h >= 280 -> Profile.PANEL
                w >= 320 && h >= 125 -> Profile.WIDE
                w >= 235 && h >= 125 -> Profile.EXPANDED
                else -> Profile.CARD
            }
        return Plan(
            w,
            h,
            profile,
            if (kind in listOf(WidgetKind.PHOTO, WidgetKind.NOTE)) Strategy.EXACT_SIZE
            else Strategy.RESPONSIVE_CONTENT,
        )
    }

    /** OPTION_APPWIDGET_SIZES is authoritative on Android 12+. Ignore corrupt/duplicate sizes. */
    fun sizes(options: Bundle): List<SizeF> {
        val reported =
            runCatching {
                    options.getParcelableArrayList(
                        AppWidgetManager.OPTION_APPWIDGET_SIZES,
                        SizeF::class.java,
                    )
                }
                .getOrNull()
                .orEmpty()
        val valid =
            reported
                .filter {
                    it.width.isFinite() && it.height.isFinite() && it.width > 0 && it.height > 0
                }
                .distinct()
        if (valid.isNotEmpty()) {
            val ordered =
                valid.sortedWith(compareBy<SizeF> { it.width * it.height }.thenBy { it.width })
            if (ordered.size <= 16) return ordered
            // RemoteViews accepts at most 16 size variants. Retain both extremes and sampled
            // aspects.
            return (0..15)
                .map { ordered[(it * (ordered.lastIndex) / 15.0).roundToInt()] }
                .distinct()
        }
        val minW =
            options
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180)
                .coerceAtLeast(40)
                .toFloat()
        val minH =
            options
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180)
                .coerceAtLeast(40)
                .toFloat()
        val maxW =
            options
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, minW.toInt())
                .coerceAtLeast(minW.toInt())
                .toFloat()
        val maxH =
            options
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minH.toInt())
                .coerceAtLeast(minH.toInt())
                .toFloat()
        return listOf(SizeF(minW, maxH), SizeF(maxW, minH)).distinct()
    }

    fun renderScale(plans: List<Plan>, screenWidth: Int, screenHeight: Int): Float {
        // Leave room for native-clock Icons and other RemoteViews images within Android's budget.
        val budget = maxOf(512_000.0, screenWidth.toDouble() * screenHeight * 6 * .5)
        val areas = plans.sumOf { it.width.toDouble() * it.height * 4 }
        return minOf(2f, sqrt(budget / maxOf(1.0, areas)).toFloat()).coerceAtLeast(.01f)
    }

    fun clock(c: WidgetConfig, p: Plan): ClockGeometry {
        val w = p.width
        val h = p.height
        if (p.clockOnly) {
            val side = minOf(w, h) * .98f
            return ClockGeometry(
                RectF((w - side) / 2, (h - side) / 2, (w + side) / 2, (h + side) / 2),
                null,
                null,
                emptyList(),
                null,
            )
        }
        val inset = if (p.tiny) 3f else 8f
        val face: RectF
        var calendar: RectF? = null
        var events: RectF? = null
        if (p.panel) {
            face = RectF(8f, 8f, w * .48f, h * .49f)
            calendar = RectF(w * .51f, 12f, w - 10, h * .65f)
            events = RectF(12f, h * .77f, w - 12, h - 10)
        } else if (p.expanded) {
            face = RectF(8f, 8f, w * .45f, h - 30)
            calendar = if (p.wide) null else RectF(w * .5f, 12f, w - 10, h - 14)
            if (p.wide) events = RectF(w * .51f, h * .56f, w - 12, h - 12)
        } else if (p.row)
            face =
                RectF(inset, inset, if (c.analog) minOf(h - 4, w * .42f) else w * .58f, h - inset)
        else face = RectF(inset, inset, w - inset, h - (if (c.showDate && !p.tiny) 30f else inset))
        val date =
            if (!c.showDate || p.tiny) null
            else
                when {
                    p.row -> RectF(face.right + 6, 8f, w - 6, h - 8)
                    p.panel -> RectF(10f, h * .5f, w * .48f, h * .5f + 24)
                    p.expanded -> RectF(face.left, h - 28, face.right, h - 6)
                    else -> RectF(6f, h - 28, w - 6, h - 5)
                }
        val zones =
            when {
                p.panel ->
                    c.timeZones.take(3).indices.map { i ->
                        RectF(
                            12 + i * (w - 24) / 3,
                            h * .65f,
                            12 + (i + 1) * (w - 24) / 3 - 6,
                            h * .77f,
                        )
                    }
                p.wide -> c.timeZones.take(1).map { RectF(w * .52f, 12f, w - 12, h * .5f) }
                else -> emptyList()
            }
        val fittedFace =
            if (c.analog) {
                val side = minOf(face.width(), face.height()).coerceAtLeast(1f)
                RectF(
                    face.centerX() - side / 2,
                    face.centerY() - side / 2,
                    face.centerX() + side / 2,
                    face.centerY() + side / 2,
                )
            } else face
        return ClockGeometry(fittedFace, date, calendar, zones, events)
    }

    fun media(p: Plan): MediaGeometry {
        val w = p.width
        val h = p.height
        if (p.tiny) return MediaGeometry(RectF(0f, 0f, w, h), null, null, null, null, null, null)
        val controls = RectF(8f, h - (if (p.row) 30f else 44f), w - 8, h - 3)
        if (p.row) {
            val s = minOf(h - 12, 60f)
            return MediaGeometry(
                RectF(6f, 6f, 6 + s, 6 + s),
                RectF(s + 14, 5f, w - 8, 27f),
                RectF(s + 14, 28f, w - 8, h - 32),
                RectF(s + 12, h - 31, w - 6, h - 2),
                null,
                null,
                null,
            )
        }
        if (p.panel)
            return MediaGeometry(
                RectF(14f, 14f, w * .5f, h * .46f),
                RectF(w * .54f, 20f, w - 14, 64f),
                RectF(w * .54f, 68f, w - 14, 96f),
                controls,
                RectF(16f, h * .52f, w - 16, h * .52f + 32),
                RectF(w * .54f, 104f, w - 14, h * .46f),
                RectF(16f, h * .64f, w - 16, h - 52),
            )
        if (p.expanded) {
            val s = minOf(h - 60, w * .35f)
            return MediaGeometry(
                RectF(12f, 12f, 12 + s, 12 + s),
                RectF(s + 24, 12f, w - 12, 48f),
                RectF(s + 24, 50f, w - 12, 70f),
                controls,
                RectF(s + 24, h - 72, w - 12, h - 46),
                if (p.wide) RectF(s + 24, 73f, w - 12, h - 74) else null,
                null,
            )
        }
        val s = minOf(w - 24, (h - 102).coerceAtLeast(38f))
        val top = if (p.column) 10f else 8f
        return MediaGeometry(
            RectF((w - s) / 2, top, (w + s) / 2, top + s),
            RectF(10f, top + s + 6, w - 10, top + s + 29),
            RectF(10f, top + s + 30, w - 10, h - 47),
            controls,
            if (p.supportsProgress) RectF(10f, h - 74, w - 10, h - 47) else null,
            null,
            null,
        )
    }

    fun grid(p: Plan, count: Int): List<RectF> {
        if (count <= 0) return emptyList()
        val columns =
            when {
                p.row -> count
                p.column -> if (p.width >= 160 && count > 4) 2 else 1
                p.panel -> minOf(4, ceil(sqrt(count.toDouble())).toInt())
                else -> minOf(if (p.wide) 4 else if (p.expanded) 3 else 2, count)
            }
        val rows = ceil(count.toDouble() / columns).toInt()
        val pad = if (p.tiny) 0f else 7f
        val gap = if (p.tiny) 0f else 5f
        val cw = (p.width - 2 * pad - gap * (columns - 1)) / columns
        val ch = (p.height - 2 * pad - gap * (rows - 1)) / rows
        return (0 until count).map { i ->
            val col = i % columns
            val row = i / columns
            RectF(
                pad + col * (cw + gap),
                pad + row * (ch + gap),
                pad + col * (cw + gap) + cw,
                pad + row * (ch + gap) + ch,
            )
        }
    }

    fun noteRows(p: Plan, count: Int): List<RectF> =
        (0 until minOf(count, p.noteCapacity)).map { i ->
            RectF(
                12f,
                48f + i * maxOf(30f, (p.height - 62) / maxOf(1, minOf(count, p.noteCapacity))),
                p.width - 12,
                48f + (i + 1) * maxOf(30f, (p.height - 62) / maxOf(1, minOf(count, p.noteCapacity))),
            )
        }

    val previewSizes =
        linkedMapOf(
            "1×1" to SizeF(80f, 80f),
            "1×2" to SizeF(80f, 170f),
            "2×1" to SizeF(170f, 80f),
            "2×2" to SizeF(180f, 180f),
            "3×1" to SizeF(255f, 80f),
            "3×2" to SizeF(255f, 180f),
            "2×3" to SizeF(180f, 270f),
            "4×1" to SizeF(350f, 80f),
            "4×2" to SizeF(350f, 180f),
            "2×4" to SizeF(180f, 360f),
            "4×4" to SizeF(360f, 360f),
        )
}
