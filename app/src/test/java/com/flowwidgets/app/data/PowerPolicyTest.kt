package com.flowwidgets.app.data

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.*
import com.flowwidgets.app.R
import com.flowwidgets.app.widgets.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PowerPolicyTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun workManager() {
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    @Test
    fun positionCallbacksAreThrottledButPauseTrackChangesAndSeekingAreImmediate() {
        val gate = PlaybackUpdateGate()
        val playing = PlaybackUpdateGate.Signature(3, 7, 1, 1f)
        assertTrue(gate.shouldUpdate(playing, 0, 0))
        for (second in 1..14) assertFalse(
            gate.shouldUpdate(playing, second * 1000L, second * 1000L)
        )
        assertTrue(gate.shouldUpdate(playing, 15000, 15000))
        assertTrue(gate.shouldUpdate(playing.copy(state = 2), 15000, 15001))
        assertFalse(gate.shouldUpdate(playing.copy(state = 2), 15000, 15002))
        assertTrue(gate.shouldUpdate(playing.copy(queueId = 2), 0, 15003))
        assertTrue(gate.shouldUpdate(playing.copy(queueId = 2), 60000, 15004))
    }

    @Test
    fun draftOrRemovedWidgetsNeverCountAsActive() = runBlocking {
        val id =
            Shadows.shadowOf(AppWidgetManager.getInstance(context))
                .createWidget(NoteWidget::class.java, R.layout.widget_adaptive_card)
        FlowRepository.save(context, WidgetConfig(id = id, kind = WidgetKind.NOTE))
        FlowRepository.save(
            context,
            WidgetConfig(id = -900, kind = WidgetKind.WEATHER, latitude = 1.0, longitude = 2.0),
        )
        FlowRepository.save(
            context,
            WidgetConfig(id = 900, kind = WidgetKind.WEATHER, latitude = 1.0, longitude = 2.0),
        )
        assertEquals(listOf(id), WidgetWorkScheduler.activeConfigs(context).map { it.id })
        assertEquals(mapOf(id to WidgetKind.NOTE), WidgetUpdateDispatcher.activeTargets(context))
        FlowRepository.delete(context, id)
        assertTrue(WidgetWorkScheduler.activeConfigs(context).isEmpty())
        val manager = WorkManager.getInstance(context)
        val legacy =
            PeriodicWorkRequestBuilder<RefreshWorker>(30, java.util.concurrent.TimeUnit.MINUTES)
                .setInitialDelay(1, java.util.concurrent.TimeUnit.DAYS)
                .build()
        manager
            .enqueueUniquePeriodicWork(
                WidgetWorkScheduler.LOCAL_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                legacy,
            )
            .result
            .get()
        manager
            .enqueueUniquePeriodicWork(
                WidgetWorkScheduler.WEATHER_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                WidgetWorkScheduler.weatherRequest(),
            )
            .result
            .get()
        assertTrue(
            manager.getWorkInfosForUniqueWork(WidgetWorkScheduler.LOCAL_WORK).get().any {
                !it.state.isFinished
            }
        )
        assertTrue(
            manager.getWorkInfosForUniqueWork(WidgetWorkScheduler.WEATHER_WORK).get().any {
                !it.state.isFinished
            }
        )
        WidgetWorkScheduler.reconcile(context)
        assertTrue(
            manager.getWorkInfosForUniqueWork(WidgetWorkScheduler.LOCAL_WORK).get().none {
                !it.state.isFinished
            }
        )
        assertTrue(
            manager.getWorkInfosForUniqueWork(WidgetWorkScheduler.WEATHER_WORK).get().none {
                !it.state.isFinished
            }
        )
    }

    @Test
    fun staticAndNativeClockWidgetsNeedNoPeriodicWorkButRotatingPhotosDo() {
        assertFalse(
            WidgetWorkScheduler.needsLocalRefresh(context, WidgetConfig(kind = WidgetKind.NOTE))
        )
        assertFalse(
            WidgetWorkScheduler.needsLocalRefresh(context, WidgetConfig(kind = WidgetKind.SHORTCUT))
        )
        assertFalse(
            WidgetWorkScheduler.needsLocalRefresh(context, WidgetConfig(kind = WidgetKind.CLOCK))
        )
        assertFalse(
            WidgetWorkScheduler.needsLocalRefresh(
                context,
                WidgetConfig(kind = WidgetKind.PHOTO, rotatePhotos = true, photoUris = listOf("a")),
            )
        )
        assertTrue(
            WidgetWorkScheduler.needsLocalRefresh(
                context,
                WidgetConfig(
                    kind = WidgetKind.PHOTO,
                    rotatePhotos = true,
                    photoUris = listOf("a", "b"),
                ),
            )
        )
        assertTrue(
            WidgetWorkScheduler.needsLocalRefresh(context, WidgetConfig(kind = WidgetKind.CALENDAR))
        )
        assertTrue(
            WidgetWorkScheduler.needsLocalRefresh(context, WidgetConfig(kind = WidgetKind.BATTERY))
        )
    }

    @Test
    fun weatherGroupsCoordinatesAndSkipsFreshCache() = runBlocking {
        val config =
            WidgetConfig(id = 700, kind = WidgetKind.WEATHER, latitude = 1.0, longitude = 2.0)
        val other = config.copy(id = 702, latitude = 3.0)
        val calls = mutableListOf<Int>()
        WeatherRefreshPolicy.refresh(context, listOf(config, config.copy(id = 701), other), 10000) {
            calls.add(it.id)
            null
        }
        assertEquals(listOf(700, 702), calls)
        context
            .getSharedPreferences("weather_cache", Context.MODE_PRIVATE)
            .edit()
            .putString(
                "weather_1.0_2.0",
                """{"temp":20,"description":"Sol","high":24,"low":15,"hours":[],"updatedAt":9999}""",
            )
            .commit()
        calls.clear()
        WeatherRefreshPolicy.refresh(context, listOf(config, config.copy(id = 701)), 10000) {
            calls.add(it.id)
            null
        }
        assertTrue(calls.isEmpty())
        WeatherRefreshPolicy.refresh(
            context,
            listOf(config, config.copy(id = 701)),
            10000 + WeatherRefreshPolicy.MAX_AGE_MS,
        ) {
            calls.add(it.id)
            null
        }
        assertEquals(listOf(700), calls)
    }

    @Test
    fun networkWorkHasConnectivityAndBatteryConstraintsAndLocalWorkDoesNot() {
        val weather = WidgetWorkScheduler.weatherRequest()
        val local = WidgetWorkScheduler.localRequest()
        assertEquals(NetworkType.CONNECTED, weather.workSpec.constraints.requiredNetworkType)
        assertTrue(weather.workSpec.constraints.requiresBatteryNotLow())
        assertEquals(60 * 60 * 1000L, weather.workSpec.intervalDuration)
        assertEquals(15 * 60 * 1000L, weather.workSpec.flexDuration)
        assertEquals(NetworkType.NOT_REQUIRED, local.workSpec.constraints.requiredNetworkType)
        assertEquals(30 * 60 * 1000L, local.workSpec.intervalDuration)
    }
}
