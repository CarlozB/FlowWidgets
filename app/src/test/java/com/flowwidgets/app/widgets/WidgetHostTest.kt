package com.flowwidgets.app.widgets

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.flowwidgets.app.R
import com.flowwidgets.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class WidgetHostTest {
    @org.junit.Before
    fun initializeWorkManager() {
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(
            androidx.test.core.app.ApplicationProvider.getApplicationContext<
                android.content.Context
            >()
        )
    }

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager
        get() = AppWidgetManager.getInstance(context)

    @Test
    fun everyProviderIsRegisteredWithResizableMetadata() {
        assertEquals(8, AdaptiveWidgetProvider.providers.size)
        AdaptiveWidgetProvider.providers.forEach { type ->
            val component = ComponentName(context, type)
            val receiver =
                context.packageManager.getReceiverInfo(
                    component,
                    android.content.pm.PackageManager.GET_META_DATA,
                )
            assertTrue(receiver.exported)
            val parser =
                receiver.loadXmlMetaData(context.packageManager, "android.appwidget.provider")
            assertNotNull(parser)
            parser.use {
                while (it.next() != org.xmlpull.v1.XmlPullParser.START_TAG) {}
                val ns = "http://schemas.android.com/apk/res/android"
                assertEquals("appwidget-provider", it.name)
                assertEquals(3, it.getAttributeIntValue(ns, "resizeMode", 0))
                assertEquals(0, it.getAttributeIntValue(ns, "updatePeriodMillis", -1))
                assertEquals(
                    "com.flowwidgets.app.widgets.WidgetConfigureActivity",
                    it.getAttributeValue(ns, "configure"),
                )
                assertNotNull(it.getAttributeValue(ns, "minResizeWidth"))
                assertNotNull(it.getAttributeValue(ns, "minResizeHeight"))
            }
        }
    }

    @Test
    fun allKindsInflateAndApplyTheirRemoteViewsWithoutPrivateApis() = runBlocking {
        WidgetKind.entries.forEachIndexed { index, kind ->
            val id = 400 + index
            FlowRepository.save(
                context,
                WidgetConfig(id = id, kind = kind, note = "Una nota persistente"),
            )
            val views =
                AdaptiveWidgetProvider.buildViews(context, manager, id, kind.name.lowercase())
            val applied = views.apply(context, FrameLayout(context))
            assertNotNull("RemoteViews for $kind", applied.findViewById<View>(R.id.widget_root))
            assertNotNull(
                applied.findViewById<android.widget.ImageView>(R.id.widget_image).drawable
            )
            if (kind == WidgetKind.NOTE)
                assertTrue(
                    applied
                        .findViewById<View>(R.id.widget_image)
                        .contentDescription
                        .contains("Una nota persistente")
                )
            FlowRepository.delete(context, id)
        }
    }

    @Test
    fun analogClockAndEachVisualFamilyUseAllowedRemoteViewsOperations() = runBlocking {
        WidgetStyle.entries.forEachIndexed { index, style ->
            val id = 500 + index
            FlowRepository.save(
                context,
                WidgetConfig(id = id, kind = WidgetKind.CLOCK, style = style, analog = true),
            )
            val applied =
                AdaptiveWidgetProvider.buildViews(context, manager, id, "clock")
                    .apply(context, FrameLayout(context))
            assertEquals(View.VISIBLE, applied.findViewById<View>(R.id.widget_analog).visibility)
            assertEquals(View.GONE, applied.findViewById<View>(R.id.widget_clock).visibility)
            FlowRepository.delete(context, id)
        }
    }

    @Test
    fun resizingRefreshesWithoutDeletingConfiguration() = runBlocking {
        org.robolectric.Shadows.shadowOf(manager).setAlwaysRecreateViewsDuringUpdate(true)
        val id =
            org.robolectric.Shadows.shadowOf(manager)
                .createWidget(NoteWidget::class.java, R.layout.widget_visual_card)
        val config =
            WidgetConfig(id = id, kind = WidgetKind.NOTE, note = "Conservar al redimensionar")
        FlowRepository.save(context, config)
        NoteWidget()
            .onAppWidgetOptionsChanged(
                context,
                manager,
                id,
                Bundle().apply {
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 48)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 48)
                },
            )
        assertEquals(config, FlowRepository.read(context, id))
        NoteWidget().onDeleted(context, intArrayOf(id))
        assertNull(FlowRepository.read(context, id))
    }

    @Test
    fun compactNotesKeepBodyAndClockFontLayoutsApply() = runBlocking {
        val id =
            org.robolectric.Shadows.shadowOf(manager)
                .createWidget(NoteWidget::class.java, R.layout.widget_visual_card)
        manager.updateAppWidgetOptions(
            id,
            Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 350)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 70)
            },
        )
        FlowRepository.save(
            context,
            WidgetConfig(id = id, kind = WidgetKind.NOTE, note = "Visible en 4×1"),
        )
        val note =
            AdaptiveWidgetProvider.buildViews(context, manager, id, "note")
                .apply(context, FrameLayout(context))
        assertTrue(
            note.findViewById<View>(R.id.widget_image).contentDescription.contains("Visible en 4×1")
        )
        assertNotNull(note.findViewById<android.widget.ImageView>(R.id.widget_image).drawable)
        for (font in listOf("sans", "serif", "monospace")) {
            FlowRepository.save(
                context,
                WidgetConfig(id = id, kind = WidgetKind.CLOCK, font = font, analog = false),
            )
            val clock =
                AdaptiveWidgetProvider.buildViews(context, manager, id, "clock")
                    .apply(context, FrameLayout(context))
            assertEquals(View.VISIBLE, clock.findViewById<View>(R.id.widget_clock).visibility)
        }
        FlowRepository.delete(context, id)
    }
}
