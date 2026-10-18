package com.flowwidgets.app.widgets

import android.content.Context
import android.content.res.Resources
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import com.flowwidgets.app.R
import com.flowwidgets.app.data.WidgetConfig
import kotlin.math.min

/** Bundled official Inter; used by Canvas artwork and XML-backed native TextClocks. */
object WidgetTypography {
    @Volatile private var resources: Resources? = null

    fun initialize(context: Context) {
        resources = context.applicationContext.resources
    }

    val weights = listOf(300, 400, 500, 600, 700)

    fun weight(c: WidgetConfig, size: Float, bold: Boolean): Int =
        c.fontWeight.takeIf { it in weights }
            ?: when {
                bold -> if (size >= 24) 700 else 600
                size >= 30 -> 300
                size >= 18 -> 500
                else -> 400
            }

    fun typeface(c: WidgetConfig, size: Float, bold: Boolean): Typeface {
        if (c.font !in listOf("inter", "sans"))
            return Typeface.create(
                if (c.font == "system") "sans-serif" else c.font,
                if (bold) Typeface.BOLD else Typeface.NORMAL,
            )
        val resource = resources ?: error("Initialize WidgetTypography before rendering")
        return Typeface.create(
            resource.getFont(
                when (weight(c, size, bold)) {
                    300 -> R.font.inter_light
                    500 -> R.font.inter_medium
                    600 -> R.font.inter_semibold
                    700 -> R.font.inter_bold
                    else -> R.font.inter_regular
                }
            ),
            weight(c, size, bold),
            false,
        )
    }

    fun style(paint: Paint, c: WidgetConfig, size: Float, bold: Boolean, numeric: Boolean) {
        paint.typeface = typeface(c, size, bold)
        paint.letterSpacing = if (size >= 28) -.025f else if (size >= 16) -.012f else .005f
        paint.fontFeatureSettings = if (numeric) "'tnum' 1, 'lnum' 1" else "'kern' 1"
        if (c.font in listOf("inter", "sans"))
            paint.fontVariationSettings =
                "'wght' ${weight(c,size,bold)}, 'opsz' ${size.coerceIn(14f,32f)}"
    }

    fun fit(c: WidgetConfig, sample: String, requested: Float, width: Float, height: Float): Float {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        var fitted = requested
        repeat(3) {
            paint.textSize = fitted
            style(paint, c, fitted, false, true)
            val metrics = paint.fontMetrics
            fitted *=
                min(
                    1f,
                    min(
                        width.coerceAtLeast(1f) / paint.measureText(sample).coerceAtLeast(1f),
                        height.coerceAtLeast(1f) /
                            (metrics.descent - metrics.ascent).coerceAtLeast(1f),
                    ),
                )
        }
        return fitted
    }

    fun layout(c: WidgetConfig): Int =
        when (c.font) {
            "serif" -> R.layout.widget_adaptive_card_serif
            "monospace" -> R.layout.widget_adaptive_card_mono
            "system" -> R.layout.widget_adaptive_card
            else ->
                when (c.fontWeight) {
                    300 -> R.layout.widget_adaptive_inter_light
                    400 -> R.layout.widget_adaptive_inter_regular
                    500 -> R.layout.widget_adaptive_inter_medium
                    600 -> R.layout.widget_adaptive_inter_semibold
                    700 -> R.layout.widget_adaptive_inter_bold
                    else -> R.layout.widget_adaptive_inter_auto
                }
        }
}
