package com.flowwidgets.app.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.*
import com.flowwidgets.app.data.*
import kotlinx.coroutines.*

object WidgetActions {
    /** True means the launcher accepted the pin request; final allocation happens in callback. */
    suspend fun pin(context: Context, config: WidgetConfig): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false
        val draftId =
            if (config.id < 0) config.id
            else -((System.currentTimeMillis() % 2000000000).toInt().coerceAtLeast(1))
        FlowRepository.save(context, config.copy(id = draftId))
        val type =
            AdaptiveWidgetProvider.providers.first {
                it.simpleName.equals(config.kind.name + "Widget", true)
            }
        val callback =
            PendingIntent.getBroadcast(
                context,
                draftId,
                Intent(context, WidgetPinnedReceiver::class.java).putExtra("draft_id", draftId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
        return manager.requestPinAppWidget(ComponentName(context, type), null, callback)
    }

    fun update(context: Context) = AdaptiveWidgetProvider.updateAll(context)
}

class WidgetPinnedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id =
            intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            )
        val draftId = intent.getIntExtra("draft_id", 0)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID || draftId >= 0) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                FlowRepository.read(context, draftId)?.let {
                    FlowRepository.save(context, it.copy(id = id))
                    FlowRepository.delete(context, draftId)
                }
                AdaptiveWidgetProvider.updateAll(context)
            } finally {
                pending.finish()
            }
        }
    }
}

class WidgetMediaReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                MediaRepository.action(context, intent.getStringExtra("media_action") ?: "toggle")
                AdaptiveWidgetProvider.updateAll(context)
            } finally {
                pending.finish()
            }
        }
    }
}

class WidgetNoteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("widget_id", AppWidgetManager.INVALID_APPWIDGET_ID)
        val noteId = intent.getStringExtra("note_id") ?: return
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                FlowRepository.update(context, id) { config ->
                    config.copy(
                        noteItems =
                            config.noteItems.map {
                                if (it.id == noteId) it.copy(checked = !it.checked) else it
                            }
                    )
                }
                AdaptiveWidgetProvider.updateAll(context)
            } finally {
                pending.finish()
            }
        }
    }
}
