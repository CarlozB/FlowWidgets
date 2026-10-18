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

    fun background(c: WidgetConfig, width: Int, height: Int): Bitmap {
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
                c.glassCorner,
            )
        cache.get(key)?.let {
            return it
        }
        val b =
            Bitmap.createBitmap(
                width.coerceAtLeast(1),
                height.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
        val cv = Canvas(b)
        val w = width.toFloat()
        val h = height.toFloat()
        val glass = c.style == WidgetStyle.GLASS
        val a =
            (Color.alpha(palette.background) * c.opacity * (if (glass) c.glassOpacity else 1f))
                .roundToInt()
                .coerceIn(0, 255)
        val base = (palette.background and 0xFFFFFF) or (a shl 24)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader =
            LinearGradient(
                0f,
                0f,
                w,
                h,
                intArrayOf(
                    mix(base, Color.WHITE, if (glass) .28f else .10f),
                    base,
                    mix(base, palette.accent, if (glass) .13f else .03f),
                ),
                null,
                Shader.TileMode.CLAMP,
            )
        cv.drawRect(0f, 0f, w, h, p)
        val strength = if (glass) c.glassLighting else .12f
        p.shader =
            RadialGradient(
                w * .15f,
                h * .03f,
                maxOf(w, h) * .8f,
                Color.argb((120 * strength).roundToInt(), 255, 255, 255),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
        cv.drawRect(0f, 0f, w, h, p)
        if (glass) {
            p.shader =
                RadialGradient(
                    w * .92f,
                    h * .8f,
                    maxOf(w, h) * .7f,
                    (palette.accent and 0xFFFFFF) or ((45 * strength).roundToInt() shl 24),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP,
                )
            cv.drawRect(0f, 0f, w, h, p)
            p.shader = null
            p.color = Color.argb((70 * strength).roundToInt(), 255, 255, 255)
            p.strokeWidth = maxOf(1f, minOf(w, h) * .004f)
            p.style = Paint.Style.STROKE
            cv.drawPath(
                Path().apply {
                    moveTo(-w * .1f, h * .38f)
                    cubicTo(w * .2f, -h * .1f, w * .7f, h * .36f, w * 1.1f, -h * .02f)
                },
                p,
            )
        }
        cache.put(key, b)
        return b
    }

    fun radius(c: WidgetConfig, width: Float, height: Float) =
        minOf(if (c.style == WidgetStyle.GLASS) c.glassCorner else 22f, minOf(width, height) * .2f)

    private fun mix(a: Int, b: Int, t: Float) =
        Color.argb(
            Color.alpha(a),
            (Color.red(a) * (1 - t) + Color.red(b) * t).toInt(),
            (Color.green(a) * (1 - t) + Color.green(b) * t).toInt(),
            (Color.blue(a) * (1 - t) + Color.blue(b) * t).toInt(),
        )
}
