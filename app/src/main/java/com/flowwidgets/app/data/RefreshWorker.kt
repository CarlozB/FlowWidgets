package com.flowwidgets.app.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.flowwidgets.app.widgets.AdaptiveWidgetProvider
import kotlinx.coroutines.flow.first

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        FlowRepository.configs(applicationContext)
            .first()
            .filter { it.kind == WidgetKind.WEATHER }
            .forEach { WeatherRepository.refresh(applicationContext, it) }
        AdaptiveWidgetProvider.updateAll(applicationContext)
        return Result.success()
    }
}
