package com.flowwidgets.app.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.flowwidgets.app.widgets.WidgetUpdateDispatcher

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val configs = WidgetWorkScheduler.activeConfigs(applicationContext)
        val ids =
            configs
                .filter { WidgetWorkScheduler.needsLocalRefresh(applicationContext, it) }
                .map { it.id }
                .toSet()
        if (ids.isNotEmpty()) WidgetUpdateDispatcher.requestIds(applicationContext, ids).await()
        WidgetWorkScheduler.reconcile(applicationContext)
        return Result.success()
    }
}

class WeatherRefreshWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val configs =
            WidgetWorkScheduler.activeConfigs(applicationContext).filter {
                it.kind == WidgetKind.WEATHER && it.latitude != null && it.longitude != null
            }
        WeatherRefreshPolicy.refresh(applicationContext, configs)
        if (configs.isNotEmpty())
            WidgetUpdateDispatcher.requestIds(applicationContext, configs.map { it.id }.toSet())
                .await()
        WidgetWorkScheduler.reconcile(applicationContext)
        return Result.success()
    }
}

internal object WeatherRefreshPolicy {
    const val MAX_AGE_MS = 60 * 60 * 1000L

    suspend fun refresh(
        context: Context,
        configs: List<WidgetConfig>,
        now: Long = System.currentTimeMillis(),
        fetch: suspend (WidgetConfig) -> WeatherSnapshot? = {
            WeatherRepository.refresh(context, it)
        },
    ) {
        configs
            .filter { it.latitude != null && it.longitude != null }
            .distinctBy { it.latitude to it.longitude }
            .forEach { config ->
                val cached = WeatherRepository.cached(context, config)
                if (cached == null || now - cached.updatedAt !in 0 until MAX_AGE_MS) fetch(config)
            }
    }
}
