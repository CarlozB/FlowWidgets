package com.flowwidgets.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class RepositoryTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun configurationRoundTripsEveryField() {
        val c =
            WidgetConfig(
                id = 73,
                kind = WidgetKind.PHOTO,
                style = WidgetStyle.GLASS,
                title = "Álbum",
                accent = 0xFFFF1133,
                background = 0xFF223344,
                textColor = 0xFFAABBCC,
                textScale = 1.4f,
                size = "4×2",
                note = "Hola\nMundo",
                photoUri = "content://photos/1",
                photoUris = listOf("content://photos/1", "content://photos/2"),
                rotatePhotos = true,
                shortcutPackage = "com.example",
                shortcutUri = "intent:test",
                clock24 = false,
                analog = true,
                showDate = false,
                showSeconds = true,
                weekStartsMonday = false,
                showEvents = true,
                city = "Lima",
                latitude = -12.04,
                longitude = -77.03,
                font = "serif",
                alignment = "center",
                photoScale = "fit",
            )
        assertEquals(c, FlowRepository.decode(JSONObject(FlowRepository.encode(c).toString())))
        assertEquals(WidgetConfig(), FlowRepository.decode(JSONObject()))
    }

    @Test
    fun unknownEnumsAndNullFieldsUseSafeDefaults() {
        val c =
            FlowRepository.decode(
                JSONObject("""{"kind":"OLD","style":"OLD","photoUri":null,"latitude":null}""")
            )
        assertEquals(WidgetKind.CLOCK, c.kind)
        assertEquals(WidgetStyle.CLASSIC, c.style)
        assertNull(c.photoUri)
        assertNull(c.latitude)
    }

    @Test
    fun independentWidgetsCanUpdateDeleteAndReloadWithoutLosingSettings() = runTest {
        val one = WidgetConfig(id = 101, kind = WidgetKind.NOTE, note = "Primero")
        val two = WidgetConfig(id = 102, kind = WidgetKind.CLOCK, clock24 = false)
        FlowRepository.save(context, one)
        FlowRepository.save(context, two)
        FlowRepository.save(context, one.copy(note = "Actualizado"))
        assertEquals("Actualizado", FlowRepository.read(context, 101)?.note)
        assertEquals(two, FlowRepository.read(context, 102))
        assertEquals(
            2,
            FlowRepository.configs(context).first().count { it.id == 101 || it.id == 102 },
        )
        FlowRepository.setTheme(context, "dark")
        assertEquals("dark", FlowRepository.theme(context).first())
        FlowRepository.delete(context, 101)
        assertNull(FlowRepository.read(context, 101))
        assertEquals(two, FlowRepository.read(context, 102))
        assertEquals("dark", FlowRepository.theme(context).first())
        FlowRepository.delete(context, 102)
    }

    @Test
    fun corruptedConfigListDoesNotCrashOrDiscardOtherValidWidgets() {
        assertTrue(FlowRepository.decodeConfigs("broken{").isEmpty())
        val raw = """[null,"bad",{"id":23,"kind":"NOTE","note":"Saved"}]"""
        assertEquals(
            listOf(WidgetConfig(id = 23, kind = WidgetKind.NOTE, note = "Saved")),
            FlowRepository.decodeConfigs(raw),
        )
    }

    @Test
    fun calendarWithoutPermissionIsEmpty() {
        assertTrue(DeviceData.events(context).isEmpty())
    }

    @Test
    fun weatherCacheKeepsTimestampCoordinatesAndOfflineData() {
        val a = WidgetConfig(id = 7, latitude = -12.0, longitude = -77.0)
        val b = a.copy(id = 8, latitude = 40.0)
        context
            .getSharedPreferences("weather_cache", Context.MODE_PRIVATE)
            .edit()
            .putString(
                "weather_-12.0_-77.0",
                """{"temp":19.5,"description":"Nublado","high":22,"low":17,"updatedAt":123456,"hours":[{"time":"14:00","temp":20,"code":3}]}""",
            )
            .commit()
        val saved = WeatherRepository.cached(context, a)
        assertNotNull(saved)
        assertEquals(123456L, saved!!.updatedAt)
        assertEquals(19.5, saved.temp, 0.001)
        assertEquals(WeatherHour("14:00", 20.0, 3), saved.hours.single())
        assertNull(WeatherRepository.cached(context, b))
        assertEquals(saved, WeatherRepository.cached(context, a.copy(id = 99)))
        context
            .getSharedPreferences("weather_cache", Context.MODE_PRIVATE)
            .edit()
            .putString("weather_40.0_-77.0", "corrupt")
            .commit()
        assertNull(WeatherRepository.cached(context, b))
    }

    @Test
    fun refreshReturnsCachedSnapshotWhenOfflineWithoutChangingTimestamp() = runTest {
        val config = WidgetConfig(id = 901, latitude = 2.0, longitude = 3.0)
        context
            .getSharedPreferences("weather_cache", Context.MODE_PRIVATE)
            .edit()
            .putString(
                "weather_2.0_3.0",
                """{"temp":10,"description":"Nublado","high":12,"low":7,"updatedAt":42,"hours":[]}""",
            )
            .commit()
        var attempted = false
        val snapshot =
            WeatherRepository.refreshWith(context, config) {
                attempted = true
                throw java.io.IOException("Offline")
            }
        assertTrue(attempted)
        assertEquals(42L, snapshot?.updatedAt)
        assertEquals(WeatherRepository.cached(context, config), snapshot)
        assertNull(
            WeatherRepository.refreshWith(context, config.copy(latitude = 4.0)) {
                throw java.io.IOException("Offline")
            }
        )
    }

    @Test
    fun weatherCodesCoverConditions() {
        assertEquals("Despejado", WeatherRepository.description(0))
        assertEquals("Tormenta", WeatherRepository.description(95))
        assertEquals("Lluvia", WeatherRepository.description(63))
        assertEquals("Nieve", WeatherRepository.description(73))
        assertEquals("Tiempo variable", WeatherRepository.description(-1))
    }
}
