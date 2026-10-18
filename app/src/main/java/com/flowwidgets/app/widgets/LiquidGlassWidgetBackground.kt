package com.flowwidgets.app.widgets

import android.graphics.Bitmap
import android.graphics.Color
import com.flowwidgets.app.data.WidgetConfig
import dev.liquidglass.core.GlassMath
import kotlin.math.*

/**
 * CPU adapter of LiquidGlass optics for launcher-safe bitmap snapshots. The backdrop is a
 * generated, smoothly diffused light field, never the launcher's wallpaper. Only the cached
 * background is rendered here; text, artwork and live clocks remain separate.
 */
object LiquidGlassWidgetBackground {
    fun render(config: WidgetConfig, width: Int, height: Int, radiusPx: Float): Bitmap {
        val scale = minOf(1f, 256f / maxOf(width, height))
        val w = (width * scale).roundToInt().coerceAtLeast(1)
        val h = (height * scale).roundToInt().coerceAtLeast(1)
        val radius = minOf(radiusPx * scale, minOf(w, h) / 2f)
        val palette = VisualWidgetRenderer.palette(config)
        val pixels = IntArray(w * h)
        val lighting = config.glassLighting
        val baseAlpha =
            Color.alpha(palette.background) / 255f * config.opacity * config.glassOpacity
        val band = minOf(w, h) * .16f
        fun distance(x: Float, y: Float) =
            GlassMath.sdRoundedBox(x - w / 2f, y - h / 2f, w / 2f, h / 2f, radius)
        for (y in 0 until h) for (x in 0 until w) {
            val px = x + .5f
            val py = y + .5f
            val d = distance(px, py)
            val coverage = (.5f - d).coerceIn(0f, 1f)
            if (coverage <= 0f) continue
            val lens = GlassMath.lensProfile(maxOf(0f, -d), band)
            var nx = 0f
            var ny = 0f
            if (lens > 0f || d > -2f) {
                val dx = distance(px + 1, py) - distance(px - 1, py)
                val dy = distance(px, py + 1) - distance(px, py - 1)
                val length = sqrt(dx * dx + dy * dy).coerceAtLeast(GlassMath.EPSILON)
                nx = dx / length
                ny = dy / length
            }
            val u = (px - nx * lens * band * .8f) / w
            val v = (py - ny * lens * band * .8f) / h
            // Diffused light field: broad Gaussian lights have no hard detail to alias or blur.
            val glow = exp(-(((u - .12f) / .55f).pow(2) + ((v - .03f) / .65f).pow(2)) * 2f)
            val accent = exp(-(((u - .94f) / .65f).pow(2) + ((v - .9f) / .8f).pow(2)) * 2f)
            val rim = exp(-abs(d) / maxOf(.7f, minOf(w, h) * .009f))
            val incidence = abs(nx * -.57f + ny * -.82f)
            val highlight = (rim * (.25f + .65f * incidence) + lens * .15f) * lighting
            fun channel(base: Int, tint: Int): Int {
                var c = base.toFloat()
                c += (255 - c) * glow * (.16f + .3f * lighting)
                c += (tint - c) * accent * .22f
                c += (255 - c) * highlight.coerceIn(0f, .9f)
                return c.roundToInt().coerceIn(0, 255)
            }
            pixels[y * w + x] =
                Color.argb(
                    ((baseAlpha + highlight * .13f).coerceIn(0f, 1f) * coverage * 255).roundToInt(),
                    channel(Color.red(palette.background), Color.red(palette.accent)),
                    channel(Color.green(palette.background), Color.green(palette.accent)),
                    channel(Color.blue(palette.background), Color.blue(palette.accent)),
                )
        }
        val small = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        return if (w == width && h == height) small
        else Bitmap.createScaledBitmap(small, width, height, true).also { small.recycle() }
    }
}
