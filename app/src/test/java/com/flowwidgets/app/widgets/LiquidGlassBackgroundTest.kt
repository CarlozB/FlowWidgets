package com.flowwidgets.app.widgets

import android.app.Application
import android.content.Context
import android.graphics.*
import androidx.test.core.app.ApplicationProvider
import com.flowwidgets.app.data.*
import dev.liquidglass.core.GlassMath
import java.io.File
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiquidGlassBackgroundTest {
    @Before
    fun fonts() {
        WidgetTypography.initialize(ApplicationProvider.getApplicationContext<Context>())
    }

    @Test
    fun opticsMaskAndSettingsRemainCorrectAtEveryWidgetAspect() {
        assertEquals(-60f, GlassMath.sdRoundedBox(0f, 0f, 90f, 60f, 20f), .001f)
        assertEquals(1f, GlassMath.lensProfile(0f, 12f), .001f)
        assertEquals(0f, GlassMath.lensProfile(12f, 12f), .001f)
        val config =
            WidgetConfig(style = WidgetStyle.GLASS, background = 0xFF183749, accent = 0xFF83BCC8)
        AdaptiveWidgetLayoutEngine.previewSizes.values.forEach { size ->
            val b =
                SoftLightGlassRenderer.background(config, size.width.toInt(), size.height.toInt())
            assertEquals(size.width.toInt(), b.width)
            assertEquals(size.height.toInt(), b.height)
            assertEquals(0, Color.alpha(b.getPixel(0, 0)))
            assertTrue(Color.alpha(b.getPixel(b.width / 2, b.height / 2)) > 0)
        }
        val a = SoftLightGlassRenderer.background(config, 360, 360)
        assertSame(a, SoftLightGlassRenderer.background(config, 360, 360))
        val transparent =
            SoftLightGlassRenderer.background(config.copy(glassOpacity = .3f), 360, 360)
        assertTrue(Color.alpha(a.getPixel(180, 180)) > Color.alpha(transparent.getPixel(180, 180)))
        val dim = SoftLightGlassRenderer.background(config.copy(glassLighting = 0f), 360, 360)
        assertNotEquals(a.getPixel(120, 30), dim.getPixel(120, 30))
    }

    @Test
    fun exportGlassComponentsFromProductionRenderer() {
        val b = Bitmap.createBitmap(1120, 780, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(b)
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader =
                    LinearGradient(
                        0f,
                        0f,
                        1120f,
                        780f,
                        0xFF153B4A.toInt(),
                        0xFF6F4D68.toInt(),
                        Shader.TileMode.CLAMP,
                    )
            }
        canvas.drawPaint(paint)
        val config =
            WidgetConfig(
                style = WidgetStyle.GLASS,
                background = 0xFF253747,
                accent = 0xFFAAC8D5,
                textColor = 0xFFFFFFFF,
                automaticColors = false,
            )
        val cover = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
        Canvas(cover).apply {
            drawColor(0xFFCA957B.toInt())
            drawCircle(
                80f,
                80f,
                55f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF374F62.toInt() },
            )
        }
        val state =
            VisualWidgetRenderer.State(
                now = 1760522940000,
                media =
                    MediaInfo(
                        "Tu banda sonora",
                        "Sesión ilustrativa de prueba",
                        cover,
                        true,
                        true,
                        true,
                        positionMs = 65000,
                        durationMs = 210000,
                    ),
            )
        fun card(kind: WidgetKind, x: Float, y: Float, w: Int, h: Int) {
            val drawing = VisualWidgetRenderer.render(config.copy(kind = kind), w, h, state)
            canvas.drawBitmap(drawing, null, RectF(x, y, x + w, y + h), null)
            if (kind == WidgetKind.MEDIA) {
                val g =
                    AdaptiveWidgetLayoutEngine.media(
                        AdaptiveWidgetLayoutEngine.plan(kind, w.toFloat(), h.toFloat())
                    )
                g.controls?.let { r ->
                    listOf("previous", "pause", "next").forEachIndexed { i, action ->
                        val cx = x + r.left + r.width() * (i + .5f) / 3
                        canvas.drawBitmap(
                            VisualWidgetRenderer.transportIcon(action, Color.WHITE),
                            null,
                            RectF(cx - 18, y + r.centerY() - 18, cx + 18, y + r.centerY() + 18),
                            null,
                        )
                    }
                }
            }
        }
        card(WidgetKind.CLOCK, 28f, 60f, 250, 250)
        card(WidgetKind.MEDIA, 310f, 60f, 360, 250)
        card(WidgetKind.MEDIA, 700f, 60f, 390, 360)
        paint.shader = null
        paint.color = Color.WHITE
        paint.typeface = Typeface.DEFAULT
        paint.textSize = 20f
        canvas.drawText("LiquidGlass · núcleo óptico adaptado a RemoteViews", 28f, 32f, paint)
        listOf("play", "pause", "previous", "next").forEachIndexed { i, action ->
            canvas.drawBitmap(
                VisualWidgetRenderer.transportIcon(action, Color.WHITE),
                null,
                RectF(28f + i * 140, 460f, 124f + i * 140, 556f),
                null,
            )
        }
        SoftLightGlassRenderer.progress(canvas, RectF(28f, 600f, 650f, 610f), .6f, Color.WHITE)
        paint.textSize = 16f
        canvas.drawText(
            "Reflejos estáticos · esfera y números conservados · controles nativos en el escritorio",
            28f,
            675f,
            paint,
        )
        canvas.drawText(
            "Datos ilustrativos de pruebas. No es desenfoque en vivo del fondo del launcher.",
            28f,
            706f,
            paint,
        )
        File("build/reports/visual/FlowWidgets-LiquidGlass.png")
            .apply { parentFile?.mkdirs() }
            .outputStream()
            .use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
