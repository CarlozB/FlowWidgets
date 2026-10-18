package com.flowwidgets.app.widgets

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.util.SizeF
import android.widget.FrameLayout
import android.widget.TextClock
import androidx.test.core.app.ApplicationProvider
import com.flowwidgets.app.R
import com.flowwidgets.app.data.*
import java.io.File
import kotlinx.coroutines.runBlocking
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
class PlacementTypographyTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun prepare() {
        WidgetTypography.initialize(context)
    }

    @Test
    fun firstHostUpdateKeepsChosenDesignWithoutPinCallback() = runBlocking {
        for ((index, style) in WidgetStyle.entries.withIndex()) {
            val id = 2200 + index
            val selected =
                WidgetConfig(
                    kind = WidgetKind.NOTE,
                    style = style,
                    background = 0xFF102030,
                    textColor = 0xFFABCDEF,
                    accent = 0xFFFF725E,
                    automaticColors = false,
                    note = "Mi diseño",
                    fontWeight = 600,
                )
            FlowRepository.preparePin(context, selected, -id)
            val options =
                Bundle().apply {
                    putParcelableArrayList(
                        AppWidgetManager.OPTION_APPWIDGET_SIZES,
                        arrayListOf(SizeF(180f, 180f)),
                    )
                }
            val views =
                AdaptiveWidgetProvider.buildViews(
                    context,
                    AppWidgetManager.getInstance(context),
                    id,
                    "note",
                    options,
                )
            assertNotNull(views.apply(context, FrameLayout(context)))
            assertEquals(selected.copy(id = id), FlowRepository.read(context, id))
            assertEquals(
                selected.copy(id = id),
                FlowRepository.ensureWidget(context, id, WidgetKind.NOTE),
            )
            val palette = VisualWidgetRenderer.palette(FlowRepository.read(context, id)!!)
            assertEquals(selected.background.toInt(), palette.background)
            assertEquals(selected.accent.toInt(), palette.accent)
            assertEquals(selected.textColor.toInt(), palette.foreground)
            assertEquals(selected.copy(id = id), FlowRepository.completePin(context, -id, id))
            assertNull(FlowRepository.completePin(context, -id, id))
            FlowRepository.delete(context, id)
        }
    }

    @Test
    fun callbackBeforeUpdateAndLateCallbackAfterEditAreSafe() = runBlocking {
        val selected =
            WidgetConfig(kind = WidgetKind.BATTERY, style = WidgetStyle.GLASS, glassOpacity = .5f)
        FlowRepository.preparePin(context, selected, -2300)
        assertEquals(selected.copy(id = 2300), FlowRepository.completePin(context, -2300, 2300))
        assertEquals(
            selected.copy(id = 2300),
            FlowRepository.ensureWidget(context, 2300, selected.kind),
        )
        FlowRepository.preparePin(context, selected, -2301)
        FlowRepository.ensureWidget(context, 2301, selected.kind)
        val edited = selected.copy(id = 2301, accent = 0xFF123456, glassCorner = 35f)
        FlowRepository.save(context, edited)
        FlowRepository.completePin(context, -2301, 2301)
        assertEquals(edited, FlowRepository.read(context, 2301))
        FlowRepository.preparePin(context, selected.copy(kind = WidgetKind.NOTE), -2302)
        assertEquals(edited, FlowRepository.ensureWidget(context, 2301, selected.kind))
        FlowRepository.delete(context, 2300)
        FlowRepository.delete(context, 2301)
        FlowRepository.delete(context, -2302)
    }

    @Test
    fun exactCallbacksDisambiguateConcurrentRequestsOfSameType() = runBlocking {
        val a = WidgetConfig(kind = WidgetKind.PHOTO, style = WidgetStyle.MATERIAL, title = "A")
        val b = a.copy(style = WidgetStyle.GLASS, title = "B")
        FlowRepository.preparePin(context, a, -2400)
        FlowRepository.preparePin(context, b, -2401)
        FlowRepository.ensureWidget(context, 2400, a.kind)
        assertEquals(a.copy(id = 2400), FlowRepository.completePin(context, -2400, 2400))
        assertEquals(b.copy(id = 2401), FlowRepository.completePin(context, -2401, 2401))
        assertEquals(a.copy(id = 2400), FlowRepository.read(context, 2400))
        FlowRepository.delete(context, 2400)
        FlowRepository.delete(context, 2401)
    }

    @Test
    fun interWeightsApplyToCanvasAndNativeRemoteViewsWithTabularDigits() {
        val family = context.resources.getFont(R.font.inter_variable)
        for (weight in WidgetTypography.weights) {
            val c = WidgetConfig(kind = WidgetKind.CLOCK, analog = false, fontWeight = weight)
            val tf = WidgetTypography.typeface(c, 36f, false)
            assertEquals(weight, tf.weight)
            val paint =
                Paint().apply {
                    textSize = 48f
                    WidgetTypography.style(this, c, 48f, false, true)
                }
            assertEquals(paint.measureText("1111"), paint.measureText("8888"), .05f)
            val p = AdaptiveWidgetLayoutEngine.plan(c.kind, 350f, 180f)
            val root =
                AdaptiveWidgetProvider.buildVariant(context, c, p, VisualWidgetRenderer.State())
                    .apply(context, FrameLayout(context))
            val clock = root.findViewById<TextClock>(R.id.widget_clock)
            assertEquals(weight, clock.typeface.weight)
            assertTrue(clock.fontFeatureSettings?.contains("tnum") == true)
            val expected = Typeface.create(family, weight, false)
            val expectedPaint =
                Paint().apply {
                    typeface = expected
                    textSize = clock.textSize
                    fontVariationSettings = clock.fontVariationSettings
                    letterSpacing = clock.letterSpacing
                    fontFeatureSettings = clock.fontFeatureSettings
                }
            assertEquals(
                expectedPaint.measureText("Flow Widgets"),
                clock.paint.measureText("Flow Widgets"),
                .1f,
            )
        }
        assertEquals(
            "inter",
            FlowRepository.decode(org.json.JSONObject("""{"font":"sans"}""")).font,
        )
        assertEquals(
            600,
            FlowRepository.decode(FlowRepository.encode(WidgetConfig(fontWeight = 600))).fontWeight,
        )
    }

    @Test
    fun analogFacesAreSquareCenteredAndClearOfDates() {
        for (size in
            listOf(
                SizeF(40f, 40f),
                SizeF(80f, 80f),
                SizeF(132f, 145f),
                SizeF(160f, 180f),
                SizeF(184f, 184f),
                SizeF(180f, 360f),
                SizeF(350f, 180f),
                SizeF(360f, 360f),
            )) {
            val p = AdaptiveWidgetLayoutEngine.plan(WidgetKind.CLOCK, size.width, size.height)
            val g = AdaptiveWidgetLayoutEngine.clock(WidgetConfig(), p)
            assertEquals(g.face.width(), g.face.height(), .01f)
            assertTrue(
                g.face.left >= 0 &&
                    g.face.top >= 0 &&
                    g.face.right <= size.width &&
                    g.face.bottom <= size.height
            )
            if (!p.expanded && !p.row) assertEquals(size.width / 2, g.face.centerX(), .01f)
            if (!p.row && g.date != null) assertTrue(g.face.bottom <= g.date.top)
        }
        val c =
            WidgetConfig(
                analog = false,
                showSeconds = true,
                clock24 = false,
                fontWeight = 700,
                textScale = 1.5f,
            )
        val fitted = WidgetTypography.fit(c, "88:88:88 PM", 70f, 120f, 60f)
        val paint =
            Paint().apply {
                textSize = fitted
                WidgetTypography.style(this, c, fitted, false, true)
            }
        assertTrue(paint.measureText("88:88:88 PM") <= 120.1f)
    }

    @Test
    fun nativeCompactClockBoundsAreCenteredAfterHostMeasurement() {
        for (size in listOf(80, 132, 160, 184)) {
            val c = WidgetConfig(kind = WidgetKind.CLOCK, analog = true)
            val plan = AdaptiveWidgetLayoutEngine.plan(c.kind, size.toFloat(), size.toFloat())
            val root =
                AdaptiveWidgetProvider.buildVariant(context, c, plan, VisualWidgetRenderer.State())
                    .apply(context, FrameLayout(context))
            val pixels = (size * context.resources.displayMetrics.density).toInt()
            val spec =
                android.view.View.MeasureSpec.makeMeasureSpec(
                    pixels,
                    android.view.View.MeasureSpec.EXACTLY,
                )
            root.measure(spec, spec)
            root.layout(0, 0, pixels, pixels)
            val clock = root.findViewById<android.view.View>(R.id.widget_analog)
            assertEquals(clock.width, clock.height)
            assertEquals(pixels / 2f, clock.left + clock.width / 2f, 1f)
            assertTrue(clock.top >= 0 && clock.bottom <= pixels)
        }
    }

    @Test
    fun compactClockAlwaysShowsOnlyLargeAnalogFaceAndLegacyStyleMigrates() {
        val config =
            WidgetConfig(
                analog = false,
                showDate = true,
                showEvents = true,
                timeZones = listOf("UTC", "Asia/Tokyo"),
            )
        for (size in listOf(80f, 132f, 180f, 240f, 280f)) {
            val plan = AdaptiveWidgetLayoutEngine.plan(config.kind, size, size)
            val geometry = AdaptiveWidgetLayoutEngine.clock(config, plan)
            assertEquals(size / 2, geometry.face.centerY(), .01f)
            assertTrue(geometry.face.width() >= size * .9f)
            assertNull(geometry.date)
            assertNull(geometry.calendar)
            assertNull(geometry.events)
            assertTrue(geometry.zones.isEmpty())
            val root =
                AdaptiveWidgetProvider.buildVariant(
                        context,
                        config,
                        plan,
                        VisualWidgetRenderer.State(),
                    )
                    .apply(context, FrameLayout(context))
            assertEquals(
                android.view.View.VISIBLE,
                root.findViewById<android.view.View>(R.id.widget_analog).visibility,
            )
            listOf(
                    R.id.widget_clock,
                    R.id.widget_date,
                    R.id.widget_zone_1,
                    R.id.widget_zone_2,
                    R.id.widget_zone_3,
                )
                .forEach {
                    assertEquals(
                        android.view.View.GONE,
                        root.findViewById<android.view.View>(it).visibility,
                    )
                }
        }
        val legacy =
            FlowRepository.decode(
                org.json.JSONObject(
                    """{"style":"ONE","background":4281558681,"note":"Conservar","fontWeight":600}"""
                )
            )
        assertEquals(WidgetStyle.CLASSIC, legacy.style)
        assertEquals(4281558681L, legacy.background)
        assertEquals("Conservar", legacy.note)
        assertEquals(600, legacy.fontWeight)
        assertEquals(listOf("CLASSIC", "GLASS", "MATERIAL"), WidgetStyle.entries.map { it.name })
    }

    @Test
    fun compactNativeClockCentersInsideHostEvenWhenReportedDpDiffer() {
        val config = WidgetConfig()
        val plan = AdaptiveWidgetLayoutEngine.plan(config.kind, 180f, 180f)
        for ((width, height) in listOf(150 to 150, 220 to 200, 300 to 280)) {
            val root =
                AdaptiveWidgetProvider.buildVariant(
                        context,
                        config,
                        plan,
                        VisualWidgetRenderer.State(),
                    )
                    .apply(context, FrameLayout(context))
            root.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(
                    width,
                    android.view.View.MeasureSpec.EXACTLY,
                ),
                android.view.View.MeasureSpec.makeMeasureSpec(
                    height,
                    android.view.View.MeasureSpec.EXACTLY,
                ),
            )
            root.layout(0, 0, width, height)
            val clock = root.findViewById<android.view.View>(R.id.widget_analog)
            assertEquals(width / 2f, clock.left + clock.width / 2f, 1f)
            assertEquals(height / 2f, clock.top + clock.height / 2f, 1f)
            assertTrue(
                clock.left >= 0 && clock.right <= width && clock.top >= 0 && clock.bottom <= height
            )
        }
    }

    @Test
    fun compactCalendarGlyphIsVisuallyCentered() {
        for (size in listOf(80, 132, 160)) {
            val bitmap =
                VisualWidgetRenderer.render(
                    WidgetConfig(
                        kind = WidgetKind.CALENDAR,
                        background = 0xFF000000,
                        textColor = 0xFFFFFFFF,
                        automaticColors = false,
                    ),
                    size,
                    size,
                    VisualWidgetRenderer.State(now = 1760522940000),
                )
            var left = bitmap.width
            var top = bitmap.height
            var right = 0
            var bottom = 0
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) if (
                Color.red(bitmap.getPixel(x, y)) > 210
            ) {
                left = minOf(left, x)
                right = maxOf(right, x)
                top = minOf(top, y)
                bottom = maxOf(bottom, y)
            }
            assertTrue(right > left && bottom > top)
            assertEquals(bitmap.width / 2f, (left + right) / 2f, 3f)
            assertEquals(bitmap.height / 2f, (top + bottom) / 2f, 3f)
        }
    }

    @Test
    fun exportCorrectedCompactClocksAndInterWeights() {
        val board = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888)
        val cv = Canvas(board)
        cv.drawColor(Color.rgb(23, 35, 34))
        val label =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 18f
                typeface = context.resources.getFont(R.font.inter_medium)
            }
        for ((index, w) in WidgetTypography.weights.withIndex()) {
            val config =
                WidgetConfig(
                    kind = WidgetKind.CLOCK,
                    style = WidgetStyle.GLASS,
                    background = 0xFF203A46,
                    textColor = 0xFFF2EBDD,
                    accent = 0xFF9DC6C0,
                    automaticColors = false,
                    fontWeight = w,
                    analog = false,
                )
            val b =
                VisualWidgetRenderer.render(
                    config,
                    180,
                    180,
                    VisualWidgetRenderer.State(now = 1760522940000),
                )
            cv.drawBitmap(b, null, RectF(20f + index * 235, 45f, 220f + index * 235, 245f), null)
            cv.drawText("Inter $w", 20f + index * 235, 28f, label)
            val analog =
                VisualWidgetRenderer.render(
                    config.copy(analog = true),
                    184,
                    184,
                    VisualWidgetRenderer.State(now = 1760522940000),
                )
            cv.drawBitmap(
                analog,
                null,
                RectF(20f + index * 235, 285f, 220f + index * 235, 485f),
                null,
            )
        }
        for (i in 0..2) {
            val size = listOf(80, 132, 160)[i]
            val bitmap =
                VisualWidgetRenderer.render(
                    WidgetConfig(kind = WidgetKind.CALENDAR),
                    size,
                    size,
                    VisualWidgetRenderer.State(now = 1760522940000),
                )
            cv.drawBitmap(bitmap, null, RectF(20f + i * 235, 520f, 220f + i * 235, 720f), null)
        }
        cv.drawText(
            "Diseños de prueba del motor real · Inter oficial · reloj centrado en región cuadrada",
            20f,
            800f,
            label,
        )
        cv.drawText(
            "Las celdas y la colocación final dependen del launcher. Validación física pendiente.",
            20f,
            835f,
            label,
        )
        File("build/reports/visual/FlowWidgets-Inter.png")
            .apply { parentFile?.mkdirs() }
            .outputStream()
            .use { board.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
