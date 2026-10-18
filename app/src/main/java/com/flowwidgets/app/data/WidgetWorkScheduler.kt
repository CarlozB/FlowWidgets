package com.flowwidgets.app.data

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.work.*
import com.flowwidgets.app.widgets.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object WidgetWorkScheduler {
    const val LOCAL_WORK = "flow-widget-refresh"
    const val WEATHER_WORK = "flow-widget-weather"
    private val mutex = Mutex()

    suspend fun activeConfigs(context: Context): List<WidgetConfig> {
        val ids = WidgetUpdateDispatcher.activeTargets(context).keys
        return FlowRepository.configs(context).first().filter { it.id in ids }
    }

    fun needsLocalRefresh(context: Context, c: WidgetConfig): Boolean =
        when (c.kind) {
            WidgetKind.BATTERY,
            WidgetKind.CALENDAR -> true
            WidgetKind.PHOTO -> c.rotatePhotos && c.photoUris.distinct().size > 1
            WidgetKind.CLOCK ->
                AdaptiveWidgetLayoutEngine.sizes(
                        AppWidgetManager.getInstance(context).getAppWidgetOptions(c.id)
                    )
                    .any {
                        val p = AdaptiveWidgetLayoutEngine.plan(c.kind, it.width, it.height)
                        val geometry = AdaptiveWidgetLayoutEngine.clock(c, p)
                        !p.clockOnly &&
                            (geometry.calendar != null || (c.showEvents && geometry.events != null))
                    }
            else -> false
        }

    suspend fun reconcile(context: Context) =
        mutex.withLock {
            val active = activeConfigs(context)
            val work = WorkManager.getInstance(context)
            if (active.any { needsLocalRefresh(context, it) })
                work
                    .enqueueUniquePeriodicWork(
                        LOCAL_WORK,
                        ExistingPeriodicWorkPolicy.UPDATE,
                        localRequest(),
                    )
                    .await()
            else work.cancelUniqueWork(LOCAL_WORK).await()
            if (
                active.any {
                    it.kind == WidgetKind.WEATHER && it.latitude != null && it.longitude != null
                }
            )
                work
                    .enqueueUniquePeriodicWork(
                        WEATHER_WORK,
                        ExistingPeriodicWorkPolicy.KEEP,
                        weatherRequest(),
                    )
                    .await()
            else work.cancelUniqueWork(WEATHER_WORK).await()
        }

    fun localRequest() =
        PeriodicWorkRequestBuilder<RefreshWorker>(30, TimeUnit.MINUTES, 10, TimeUnit.MINUTES)
            .build()

    fun weatherRequest() =
        PeriodicWorkRequestBuilder<WeatherRefreshWorker>(60, TimeUnit.MINUTES, 15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()
}
