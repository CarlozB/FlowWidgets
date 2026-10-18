package com.flowwidgets.app

import android.app.Application
import android.content.*
import androidx.core.content.ContextCompat
import com.flowwidgets.app.data.*
import com.flowwidgets.app.widgets.*
import kotlinx.coroutines.*

class FlowApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WidgetTypography.initialize(this)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var previousBattery: BatteryInfo? = null
        ContextCompat.registerReceiver(
            this,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val battery = DeviceData.battery(context)
                    if (battery == previousBattery) return
                    previousBattery = battery
                    val pending = goAsync()
                    scope.launch {
                        try {
                            WidgetUpdateDispatcher.request(context, setOf(WidgetKind.BATTERY))
                                .await()
                        } finally {
                            pending.finish()
                        }
                    }
                }
            },
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        scope.launch { WidgetWorkScheduler.reconcile(this@FlowApplication) }
    }
}
