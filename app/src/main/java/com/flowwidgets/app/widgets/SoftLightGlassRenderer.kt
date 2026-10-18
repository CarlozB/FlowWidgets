package com.flowwidgets.app.widgets

import android.graphics.*
import android.util.LruCache
import com.flowwidgets.app.data.WidgetConfig
import com.flowwidgets.app.data.WidgetStyle
import kotlin.math.*

/** Generated light and frost; never samples or blurs the launcher's wallpaper. */
object SoftLightGlassRenderer {
    private data class Key(
        val width: Int,
        val height: Int,
        val style: WidgetStyle,
        val background: Int,
        val accent: Int,
        val opacity: Float,
        val glassOpacity: Float,
        val lighting: Float,
        val radius: Float,
    )

    private val cache =
        object : LruCache<Key, Bitmap>(6 * 1024) {
            override fun sizeOf(key: Key, value: Bitmap) = maxOf(1, value.byteCount / 1024)
        }

    fun background(
        c: WidgetConfig,
        width: Int,
        height: Int,
        radiusPx: Float = radius(c, width.toFloat(), height.toFloat()),
    ): Bitmap {
        val palette = VisualWidgetRenderer.palette(c)
        val key =
            Key(
                width,
                height,
                c.style,
                palette.background,
                palette.accent,
                c.opacity,
                c.glassOpacity,
                c.glassLighting,
                radiusPx,
            )
        cache.get(key)?.let {
            return it
        }
        val b =
            LiquidGlassWidgetBackground.render(
                c,
                width.coerceAtLeast(1),
                height.coerceAtLeast(1),
                radiusPx,
            )
        cache.put(key, b)
        return b
    }

    /** Small reflective surfaces, suitable for native-clock bitmaps and RemoteViews controls. */
    fun reflectionShader(bounds: RectF, tint: Int, opacity: Int = 235): Shader {
        val base = mix(tint, Color.BLACK, .22f)
        return LinearGradient(
            bounds.left,
            bounds.top,
            bounds.right,
            bounds.bottom,
            intArrayOf(
                withAlpha(mix(base, Color.WHITE, .2f), opacity),
                withAlpha(mix(base, Color.WHITE, .85f), opacity),
                withAlpha(mix(base, Color.WHITE, .55f), opacity),
                withAlpha(base, opacity),
                withAlpha(mix(base, Color.WHITE, .65f), opacity),
            ),
            floatArrayOf(0f, .28f, .34f, .65f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    fun progress(canvas: Canvas, bounds: RectF, fraction: Float, tint: Int) {
        if (bounds.width() <= 0 || bounds.height() <= 0) return
        val radius = bounds.height() / 2
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = reflectionShader(bounds, tint, 65) }
        canvas.drawRoundRect(bounds, radius, radius, paint)
        val amount = fraction.coerceIn(0f, 1f)
        if (amount > 0) {
            val filled =
                RectF(bounds.left, bounds.top, bounds.left + bounds.width() * amount, bounds.bottom)
            paint.shader = reflectionShader(bounds, tint)
            canvas.drawRoundRect(filled, radius, radius, paint)
            canvas.save()
            canvas.clipPath(
                Path().apply { addRoundRect(filled, radius, radius, Path.Direction.CW) }
            )
            paint.shader = null
            paint.color = Color.argb(160, 255, 255, 255)
            paint.strokeWidth = maxOf(.6f, bounds.height() * .16f)
            canvas.drawLine(
                filled.left,
                filled.top + paint.strokeWidth,
                filled.right,
                filled.top + paint.strokeWidth,
                paint,
            )
            canvas.restore()
        }
    }

    private fun withAlpha(color: Int, alpha: Int) =
        (color and 0xFFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    fun radius(c: WidgetConfig, width: Float, height: Float) =
        minOf(c.glassCorner, minOf(width, height) * .2f)

    private fun mix(a: Int, b: Int, t: Float) =
        Color.argb(
            Color.alpha(a),
            (Color.red(a) * (1 - t) + Color.red(b) * t).toInt(),
            (Color.green(a) * (1 - t) + Color.green(b) * t).toInt(),
            (Color.blue(a) * (1 - t) + Color.blue(b) * t).toInt(),
        )
}
