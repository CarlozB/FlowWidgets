package com.flowwidgets.app

import android.appwidget.AppWidgetManager
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flowwidgets.app.data.FlowRepository
import com.flowwidgets.app.data.WidgetConfig
import com.flowwidgets.app.data.WidgetKind
import com.flowwidgets.app.widgets.AdaptiveWidgetProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetPlatformTest {
    @Test
    fun eightWidgetsHaveValidPlatformRemoteViews() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val manager = AppWidgetManager.getInstance(context)
        assertEquals(
            8,
            manager.installedProviders.count { it.provider.packageName == context.packageName },
        )
        for ((index, kind) in WidgetKind.entries.withIndex()) {
            val id = 90000 + index
            FlowRepository.save(
                context,
                WidgetConfig(id = id, kind = kind, note = "Prueba de plataforma"),
            )
            val views =
                AdaptiveWidgetProvider.buildViews(context, manager, id, kind.name.lowercase())
            instrumentation.runOnMainSync {
                val applied = views.apply(context, FrameLayout(context))
                assertNotNull(applied.findViewById<android.view.View>(R.id.widget_root))
            }
            FlowRepository.delete(context, id)
        }
    }

    // ImageDecoder's native file-descriptor path needs Android, rather than Robolectric.
    @Test
    fun selectedPhotoUsesFullSpaceAndSupportsFitOnAndroid() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val manager = AppWidgetManager.getInstance(context)
        val image = java.io.File(context.cacheDir, "widget-photo-test.png")
        val bitmap =
            android.graphics.Bitmap.createBitmap(32, 16, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLUE)
        image.outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        val id = 91000
        FlowRepository.save(
            context,
            WidgetConfig(
                id = id,
                kind = WidgetKind.PHOTO,
                photoUri = android.net.Uri.fromFile(image).toString(),
                photoScale = "fit",
            ),
        )
        val views = AdaptiveWidgetProvider.buildViews(context, manager, id, "photo")
        instrumentation.runOnMainSync {
            val photo = views.apply(context, FrameLayout(context))
            assertEquals(
                android.view.View.GONE,
                photo.findViewById<android.view.View>(R.id.widget_label).visibility,
            )
            assertEquals(
                android.view.View.GONE,
                photo.findViewById<android.view.View>(R.id.widget_title).visibility,
            )
            assertEquals(
                android.view.View.VISIBLE,
                photo.findViewById<android.view.View>(R.id.widget_image).visibility,
            )
            assertEquals(
                android.widget.ImageView.ScaleType.FIT_CENTER,
                photo.findViewById<android.widget.ImageView>(R.id.widget_image).scaleType,
            )
        }
        FlowRepository.delete(context, id)
        image.delete()
        Unit
    }
}
