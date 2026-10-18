package com.flowwidgets.app.widgets

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.flowwidgets.app.R
import com.flowwidgets.app.data.*
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AdaptiveLayoutTest {
    @org.junit.Before
    fun initializeTypography() {
        WidgetTypography.initialize(
            androidx.test.core.app.ApplicationProvider.getApplicationContext<
                android.content.Context
            >()
        )
    }

    private val engine = AdaptiveWidgetLayoutEngine

    @Test
    fun sizeMapBuildsAndAppliesForEveryProvider() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = AppWidgetManager.getInstance(context)
        val options =
            Bundle().apply {
                putParcelableArrayList(
                    AppWidgetManager.OPTION_APPWIDGET_SIZES,
                    arrayListOf(SizeF(80f, 80f), SizeF(350f, 180f), SizeF(360f, 360f)),
                )
            }
        WidgetKind.entries.forEachIndexed { index, kind ->
            val config = WidgetConfig(id = 1800 + index, kind = kind)
            FlowRepository.save(context, config)
            assertNotNull(
                AdaptiveWidgetProvider.buildViews(
                        context,
                        manager,
                        config.id,
                        kind.name.lowercase(),
                        options,
                    )
                    .apply(context, FrameLayout(context))
            )
            assertEquals(config, FlowRepository.read(context, config.id))
            FlowRepository.delete(context, config.id)
        }
    }

    @Test
    fun hostSizesAreDeduplicatedAndRespectFrameworkLimit() {
        val options =
            Bundle().apply {
                putParcelableArrayList(
                    AppWidgetManager.OPTION_APPWIDGET_SIZES,
                    ArrayList((1..40).map { SizeF(50f + it * 10, 80f + it * 5) } + SizeF(60f, 85f)),
                )
            }
        val sizes = engine.sizes(options)
        assertEquals(16, sizes.size)
        assertEquals(SizeF(60f, 85f), sizes.first())
        assertEquals(SizeF(450f, 280f), sizes.last())
        assertEquals(
            listOf(SizeF(80f, 360f), SizeF(350f, 170f)),
            engine.sizes(
                Bundle().apply {
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 80)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 170)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 350)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 360)
                }
            ),
        )
    }

    @Test
    fun expansionAddsContentAndGridsStayInsideBounds() {
        assertEquals(1, engine.plan(WidgetKind.SHORTCUT, 80f, 80f).shortcutCapacity)
        assertEquals(8, engine.plan(WidgetKind.SHORTCUT, 350f, 180f).shortcutCapacity)
        assertEquals(16, engine.plan(WidgetKind.SHORTCUT, 360f, 360f).shortcutCapacity)
        for (kind in WidgetKind.entries) for (size in engine.previewSizes.values) {
            val p = engine.plan(kind, size.width, size.height)
            engine.grid(p, p.shortcutCapacity).forEach {
                assertTrue(
                    it.width() > 0 &&
                        it.height() > 0 &&
                        it.left >= 0 &&
                        it.top >= 0 &&
                        it.right <= p.width &&
                        it.bottom <= p.height
                )
            }
        }
        val plans = (1..16).map { engine.plan(WidgetKind.NOTE, 1000f, 1000f) }
        val scale = engine.renderScale(plans, 320, 480)
        assertTrue(plans.sumOf { (it.width * it.height * 4 * scale * scale).toDouble() } <= 512001)
    }

    @Test
    fun shrinkingRemovesZonesAndTaskTargets() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config =
            WidgetConfig(
                id = 1200,
                kind = WidgetKind.CLOCK,
                analog = false,
                timeZones = listOf("UTC", "Europe/Madrid", "Asia/Tokyo"),
            )
        val big =
            AdaptiveWidgetProvider.buildVariant(
                context,
                config,
                engine.plan(config.kind, 360f, 360f),
                VisualWidgetRenderer.State(),
            )
        val root = big.apply(context, FrameLayout(context))
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.widget_zone_3).visibility)
        AdaptiveWidgetProvider.buildVariant(
                context,
                config,
                engine.plan(config.kind, 80f, 80f),
                VisualWidgetRenderer.State(),
            )
            .reapply(context, root)
        assertEquals(View.GONE, root.findViewById<View>(R.id.widget_zone_3).visibility)
        val note = config.copy(kind = WidgetKind.NOTE, noteItems = listOf(NoteEntry("a", "Tarea")))
        val noteRoot =
            AdaptiveWidgetProvider.buildVariant(
                    context,
                    note,
                    engine.plan(note.kind, 360f, 360f),
                    VisualWidgetRenderer.State(),
                )
                .apply(context, FrameLayout(context))
        assertEquals(1, noteRoot.findViewById<FrameLayout>(R.id.widget_hits).childCount)
        AdaptiveWidgetProvider.buildVariant(
                context,
                note,
                engine.plan(note.kind, 80f, 80f),
                VisualWidgetRenderer.State(),
            )
            .reapply(context, noteRoot)
        assertEquals(0, noteRoot.findViewById<FrameLayout>(R.id.widget_hits).childCount)
    }

    @Test
    fun listUpdatesPreserveOtherInstancesAndOldSettings() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val a =
            WidgetConfig(
                id = 1501,
                note = "Texto original",
                noteItems = listOf(NoteEntry("a", "Tarea")),
                glassLighting = .4f,
                shortcuts =
                    listOf(ShortcutEntry("s", "Wi-Fi", uri = "android.settings.WIFI_SETTINGS")),
            )
        val b = a.copy(id = 1502, note = "Otra instancia")
        FlowRepository.save(context, a)
        FlowRepository.save(context, b)
        FlowRepository.update(context, a.id) {
            it.copy(noteItems = it.noteItems.map { n -> n.copy(checked = true) })
        }
        assertEquals(b, FlowRepository.read(context, b.id))
        assertEquals(
            a.copy(noteItems = listOf(NoteEntry("a", "Tarea", true))),
            FlowRepository.read(context, a.id),
        )
        assertEquals(WidgetConfig(), FlowRepository.decode(org.json.JSONObject("{}")))
        FlowRepository.delete(context, a.id)
        FlowRepository.delete(context, b.id)
    }

    @Test
    fun glassCacheAndAllAdaptiveProfilesRender() {
        val c = WidgetConfig(style = WidgetStyle.GLASS)
        assertSame(
            SoftLightGlassRenderer.background(c, 180, 180),
            SoftLightGlassRenderer.background(c, 180, 180),
        )
        val board = Bitmap.createBitmap(1250, 3060, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(board)
        canvas.drawColor(Color.rgb(43, 53, 63))
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 17f
            }
        val photo =
            Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.rgb(220, 164, 105))
            }
        val sample =
            VisualWidgetRenderer.State(
                battery = BatteryInfo(78, true, "USB", 30.5, 4100, "Normal", "Li-ion"),
                weather =
                    WeatherSnapshot(
                        23.0,
                        "Parcialmente nublado",
                        26.0,
                        18.0,
                        (10..15).map { WeatherHour("$it:00", 22.0 + it % 4, it % 3) },
                        System.currentTimeMillis(),
                        2,
                        (1..4).map { WeatherDay("2026-10-${18+it}", 25.0, 17.0, it % 3, 10) },
                        65,
                        24.0,
                        8.0,
                    ),
                media =
                    MediaInfo(
                        "Tu banda sonora",
                        "Sesión de prueba",
                        photo,
                        true,
                        true,
                        true,
                        positionMs = 60000,
                        durationMs = 210000,
                        album = "Álbum de ejemplo",
                        queue = listOf(MediaQueueEntry(1, "Siguiente canción")),
                    ),
                photos = listOf(photo, photo, photo, photo),
                shortcuts =
                    (1..12).map {
                        VisualWidgetRenderer.ShortcutVisual(
                            ShortcutEntry("$it", "Acceso $it", icon = "✦"),
                            "Acceso $it",
                            null,
                            true,
                        )
                    },
                calendarAllowed = true,
                events =
                    listOf(
                        CalendarEvent("Preparar el proyecto", System.currentTimeMillis() + 3600000)
                    ),
            )
        WidgetKind.entries.forEachIndexed { row, kind ->
            canvas.drawText(kind.name, 12f, row * 375f + 24, paint)
            listOf(SizeF(80f, 80f), SizeF(255f, 80f), SizeF(255f, 180f), SizeF(360f, 360f))
                .forEachIndexed { col, size ->
                    val config =
                        c.copy(
                            kind = kind,
                            showEvents = true,
                            note = "Una idea que gana espacio al ampliar el widget.",
                            noteItems =
                                listOf(
                                    NoteEntry("a", "Preparar el proyecto"),
                                    NoteEntry("b", "Probar en el teléfono", true),
                                ),
                        )
                    val bitmap =
                        VisualWidgetRenderer.render(
                            config,
                            size.width.toInt(),
                            size.height.toInt(),
                            sample,
                        )
                    if (kind == WidgetKind.MEDIA) {
                        val plan = engine.plan(kind, size.width, size.height)
                        engine.media(plan).controls?.let { controls ->
                            val native = Canvas(bitmap)
                            val scale = bitmap.width / size.width
                            native.scale(scale, scale)
                            listOf("previous", "pause", "next").forEachIndexed { index, action ->
                                val cell = controls.width() / 3
                                val side = minOf(32f, controls.height() - 4)
                                val cx = controls.left + cell * (index + .5f)
                                native.drawBitmap(
                                    VisualWidgetRenderer.transportIcon(action, Color.WHITE),
                                    null,
                                    RectF(
                                        cx - side / 2,
                                        controls.centerY() - side / 2,
                                        cx + side / 2,
                                        controls.centerY() + side / 2,
                                    ),
                                    null,
                                )
                            }
                        }
                    }
                    val factor = minOf(280f / size.width, 320f / size.height)
                    canvas.drawBitmap(
                        bitmap,
                        null,
                        RectF(
                            12f + col * 310,
                            row * 375f + 35,
                            12f + col * 310 + size.width * factor,
                            row * 375f + 35 + size.height * factor,
                        ),
                        null,
                    )
                }
            engine.previewSizes.values.forEach { size ->
                assertNotNull(
                    VisualWidgetRenderer.render(
                        c.copy(kind = kind),
                        size.width.toInt(),
                        size.height.toInt(),
                        sample,
                    )
                )
            }
        }
        canvas.drawText(
            "Motor de producción · datos de prueba · no captura de un launcher",
            12f,
            3040f,
            paint,
        )
        File("build/reports/visual/FlowWidgets-adaptive.png")
            .apply { parentFile?.mkdirs() }
            .outputStream()
            .use { board.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
