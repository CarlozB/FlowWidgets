package com.flowwidgets.app.widgets

import android.app.Application
import android.graphics.*
import com.flowwidgets.app.data.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualWidgetRendererTest {
    @org.junit.Before
    fun initializeTypography() {
        WidgetTypography.initialize(
            androidx.test.core.app.ApplicationProvider.getApplicationContext<
                android.content.Context
            >()
        )
    }

    private val now = 1_760_522_940_000L
    private val weather =
        WeatherSnapshot(
            23.0,
            "Parcialmente nublado",
            26.0,
            18.0,
            listOf(
                WeatherHour("10:00", 22.0, 2),
                WeatherHour("11:00", 24.0, 0),
                WeatherHour("12:00", 25.0, 0),
                WeatherHour("13:00", 26.0, 2),
            ),
            now,
            2,
        )
    private val state =
        VisualWidgetRenderer.State(now = now, battery = BatteryInfo(78, true), weather = weather)

    @Test
    fun everyKindRendersEverySupportedAspectWithinBinderBudget() {
        for (kind in WidgetKind.entries) for ((w, h) in
            listOf(80 to 80, 160 to 80, 80 to 160, 180 to 180, 360 to 80, 360 to 180, 360 to 360)) {
            val bitmap =
                VisualWidgetRenderer.render(
                    WidgetConfig(kind = kind, note = "Una idea importante", city = "Lima"),
                    w,
                    h,
                    state,
                )
            assertTrue(bitmap.width <= 720 && bitmap.height <= 720)
            assertTrue(bitmap.byteCount <= 720 * 720 * 4)
            assertEquals("Rounded corner $kind $w×$h", 0, Color.alpha(bitmap.getPixel(0, 0)))
            assertTrue(Color.alpha(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)) > 0)
        }
    }

    @Test
    fun photoFitAndCropActuallyProduceDifferentVisibleGeometry() {
        val source =
            Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val c = WidgetConfig(kind = WidgetKind.PHOTO)
        val photoState = state.copy(photo = source)
        val fit = VisualWidgetRenderer.render(c.copy(photoScale = "fit"), 180, 180, photoState)
        val crop = VisualWidgetRenderer.render(c.copy(photoScale = "crop"), 180, 180, photoState)
        assertNotEquals(Color.RED, fit.getPixel(180, 25))
        assertEquals(Color.RED, crop.getPixel(180, 25))
        assertEquals(Color.RED, fit.getPixel(180, 180))
    }

    @Test
    fun vectorWeatherSymbolsAreDistinctForRealWeatherCategories() {
        val drawings =
            listOf(0, 2, 3, 45, 61, 71, 95).map { code ->
                val b = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
                VisualWidgetRenderer.weatherIcon(Canvas(b), 0f, 0f, 128f, code)
                val pixels = IntArray(128 * 128)
                b.getPixels(pixels, 0, 128, 0, 0, 128, 128)
                pixels.contentHashCode()
            }
        assertEquals(drawings.size, drawings.distinct().size)
    }

    @Test
    fun nativeClockBackgroundDoesNotFreezePaintedHandsAndDialHasRealMarks() {
        val config = WidgetConfig(kind = WidgetKind.CLOCK, analog = true)
        val native = VisualWidgetRenderer.render(config, 180, 180, state, drawClock = false)
        val preview = VisualWidgetRenderer.render(config, 180, 180, state, drawClock = true)
        assertNotEquals(native.getPixel(180, 180), preview.getPixel(180, 180))
        val dial = VisualWidgetRenderer.dial(config)
        assertEquals(0, Color.alpha(dial.getPixel(0, 0)))
        assertTrue(Color.alpha(dial.getPixel(160, 14)) > 0)
        assertEquals(32, VisualWidgetRenderer.hand(true, false, Color.RED).width)
    }

    @Test
    fun configuredColorsAndLegacyWeatherCacheAreSupported() {
        val c =
            WidgetConfig(
                kind = WidgetKind.NOTE,
                background = 0xFF102030,
                textColor = 0xFFEEDDCC,
                accent = 0xFF123456,
            )
        assertEquals(0xFF102030.toInt(), VisualWidgetRenderer.palette(c).background)
        assertEquals(0xFFEEDDCC.toInt(), VisualWidgetRenderer.palette(c).foreground)
        assertEquals(-1, weather.copy(code = -1).code)
        assertNotNull(VisualWidgetRenderer.render(c, 180, 180, state.copy(weather = null)))
    }

    @Test
    fun glassHandsAndTransportGlyphsHaveReflectionsAndKeepTransparentBounds() {
        val hand = VisualWidgetRenderer.hand(true, false, Color.RED, true)
        assertEquals(0, Color.alpha(hand.getPixel(0, 0)))
        assertNotEquals(hand.getPixel(16, 80), hand.getPixel(16, 140))
        for (action in listOf("play", "pause", "previous", "next")) {
            val bitmap = VisualWidgetRenderer.transportIcon(action, Color.WHITE)
            assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
            val shades = mutableSetOf<Int>()
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                val color = bitmap.getPixel(x, y)
                if (Color.alpha(color) > 220) shades.add(Color.red(color))
            }
            assertTrue("Visible reflected gradient for $action", shades.size > 8)
        }
    }

    @Test
    fun glassTimelineKeepsItsRealProgressAndDoesNotDrawOutsideTrack() {
        val bitmap = Bitmap.createBitmap(220, 30, Bitmap.Config.ARGB_8888)
        SoftLightGlassRenderer.progress(
            Canvas(bitmap),
            RectF(10f, 10f, 210f, 20f),
            .5f,
            Color.WHITE,
        )
        assertEquals(0, Color.alpha(bitmap.getPixel(0, 15)))
        assertTrue(Color.alpha(bitmap.getPixel(60, 15)) > Color.alpha(bitmap.getPixel(160, 15)))
        assertNotEquals(bitmap.getPixel(40, 15), bitmap.getPixel(90, 15))
        assertEquals(0, Color.alpha(bitmap.getPixel(215, 15)))
    }

    @Test
    fun exportDesignBoardFromTheProductionRenderer() {
        val board = Bitmap.createBitmap(1280, 870, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(board)
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader =
                    LinearGradient(
                        0f,
                        0f,
                        1280f,
                        870f,
                        0xFFAF5759.toInt(),
                        0xFFEDC793.toInt(),
                        Shader.TileMode.CLAMP,
                    )
            }
        canvas.drawPaint(paint)
        fun card(
            c: WidgetConfig,
            x: Float,
            y: Float,
            w: Int,
            h: Int,
            s: VisualWidgetRenderer.State = state,
        ) {
            val b = VisualWidgetRenderer.render(c, w, h, s)
            canvas.drawBitmap(
                b,
                null,
                RectF(x, y, x + w, y + h),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )
        }
        card(WidgetConfig(kind = WidgetKind.CLOCK, showDate = false), 28f, 28f, 240, 240)
        card(
            WidgetConfig(
                kind = WidgetKind.NOTE,
                title = "Notas",
                note = "Menos ruido.\nMás espacio para\nlo que importa.",
            ),
            290f,
            28f,
            240,
            240,
        )
        card(WidgetConfig(kind = WidgetKind.PHOTO), 552f, 28f, 300, 430)
        val cover = Bitmap.createBitmap(180, 180, Bitmap.Config.ARGB_8888)
        val art = Canvas(cover)
        paint.shader =
            LinearGradient(
                0f,
                0f,
                180f,
                180f,
                0xFF24354C.toInt(),
                0xFFEDA679.toInt(),
                Shader.TileMode.CLAMP,
            )
        art.drawPaint(paint)
        paint.shader = null
        paint.color = 0x66FFF7D6
        art.drawCircle(130f, 50f, 68f, paint)
        paint.color = 0xDD202F40.toInt()
        art.drawCircle(58f, 135f, 87f, paint)
        val media =
            MediaInfo("Tu banda sonora", "Portada de tu reproductor", cover, true, true, true)
        card(WidgetConfig(kind = WidgetKind.MEDIA), 874f, 28f, 378, 430, state.copy(media = media))
        // Transport glyphs are separate native click targets in the launcher; illustrate them here.
        for ((i, a) in listOf("previous", "pause", "next").withIndex()) canvas.drawBitmap(
            VisualWidgetRenderer.transportIcon(a, Color.WHITE),
            null,
            RectF(938f + i * 96, 390f, 986f + i * 96, 438f),
            null,
        )
        card(WidgetConfig(kind = WidgetKind.WEATHER, city = "Lima"), 28f, 290f, 502, 168)
        card(WidgetConfig(kind = WidgetKind.CALENDAR), 28f, 480f, 300, 300)
        card(WidgetConfig(kind = WidgetKind.BATTERY), 350f, 480f, 240, 240)
        card(
            WidgetConfig(kind = WidgetKind.SHORTCUT, title = "A tu ritmo", shortcutIcon = "✦"),
            612f,
            480f,
            240,
            240,
        )
        card(
            WidgetConfig(
                kind = WidgetKind.CLOCK,
                analog = false,
                showDate = true,
                background = 0xFF283A53,
            ),
            874f,
            480f,
            378,
            240,
        )
        paint.color = Color.WHITE
        paint.textSize = 22f
        paint.typeface = Typeface.create("sans", Typeface.BOLD)
        canvas.drawText("FLOW WIDGETS · DIBUJADO DESDE CÓDIGO", 30f, 815f, paint)
        paint.textSize = 14f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText(
            "Lámina de diseño del motor real. Datos ilustrativos de pruebas; la aplicación usa tus datos y permisos.",
            30f,
            844f,
            paint,
        )
        val file =
            File("build/reports/visual/FlowWidgets-design.png").apply { parentFile?.mkdirs() }
        file.outputStream().use { assertTrue(board.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        assertTrue(file.length() > 10_000)
    }

    @Test
    fun exportGalleryPreviewsWithoutInventedDeviceData() {
        val directory = File("build/reports/visual/gallery").apply { mkdirs() }
        for (kind in WidgetKind.entries) {
            val config = WidgetConfig(kind = kind)
            val drawing =
                VisualWidgetRenderer.render(config, 180, 180, VisualWidgetRenderer.State())
            File(directory, "preview_${kind.name.lowercase()}.png").outputStream().use {
                assertTrue(drawing.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        }
    }
}
