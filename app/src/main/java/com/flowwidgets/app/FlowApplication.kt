package com.flowwidgets.app

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.flowwidgets.app.data.RefreshWorker
import com.flowwidgets.app.widgets.AdaptiveWidgetProvider
import java.util.concurrent.TimeUnit

class FlowApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(
            this,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    AdaptiveWidgetProvider.updateAll(context)
                }
            },
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                "flow-widget-refresh",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<RefreshWorker>(30, TimeUnit.MINUTES).build(),
            )
    }
}
