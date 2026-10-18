package com.flowwidgets.app.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.flowwidgets.app.data.WidgetKind
import kotlinx.coroutines.*

internal data class WidgetUpdateTargets(
    val kinds: Set<WidgetKind> = emptySet(),
    val ids: Set<Int> = emptySet(),
)

/** One delayed flush for a burst; requests during rendering form the next batch. */
internal class WidgetUpdateBatcher(
    private val scope: CoroutineScope,
    private val perform: suspend (WidgetUpdateTargets) -> Unit,
) {
    private val lock = Any()
    private val kinds = mutableSetOf<WidgetKind>()
    private val ids = mutableSetOf<Int>()
    private val waiting = mutableListOf<CompletableDeferred<Unit>>()
    private var running = false

    fun request(target: WidgetUpdateTargets): Deferred<Unit> {
        val ticket = CompletableDeferred<Unit>()
        synchronized(lock) {
            kinds.addAll(target.kinds)
            ids.addAll(target.ids)
            waiting.add(ticket)
            if (!running) {
                running = true
                scope.launch { flush() }
            }
        }
        return ticket
    }

    private suspend fun flush() {
        while (true) {
            delay(250)
            val (target, tickets) =
                synchronized(lock) {
                    val target = WidgetUpdateTargets(kinds.toSet(), ids.toSet())
                    val tickets = waiting.toList()
                    kinds.clear()
                    ids.clear()
                    waiting.clear()
                    target to tickets
                }
            try {
                perform(target)
                tickets.forEach { it.complete(Unit) }
            } catch (e: Exception) {
                tickets.forEach { it.completeExceptionally(e) }
                if (e is CancellationException) throw e
            }
            synchronized(lock) {
                if (waiting.isEmpty()) {
                    running = false
                    return
                }
            }
        }
    }
}

object WidgetUpdateDispatcher {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var batcher: WidgetUpdateBatcher? = null

    @Synchronized
    private fun batcher(context: Context): WidgetUpdateBatcher =
        batcher
            ?: WidgetUpdateBatcher(scope) { targets ->
                    val app = context.applicationContext
                    val manager = AppWidgetManager.getInstance(app)
                    activeTargets(app).forEach { (id, kind) ->
                        if (kind in targets.kinds || id in targets.ids) {
                            try {
                                AdaptiveWidgetProvider.render(
                                    app,
                                    manager,
                                    id,
                                    kind.name.lowercase(),
                                )
                            } catch (e: RuntimeException) {
                                Log.w("FlowWidgets", "Widget update failed for id $id", e)
                            }
                        }
                    }
                }
                .also { batcher = it }

    fun request(context: Context, kinds: Set<WidgetKind>) =
        batcher(context.applicationContext).request(WidgetUpdateTargets(kinds = kinds))

    fun requestIds(context: Context, ids: Set<Int>) =
        batcher(context.applicationContext).request(WidgetUpdateTargets(ids = ids))

    fun activeTargets(context: Context): Map<Int, WidgetKind> {
        val manager = AppWidgetManager.getInstance(context)
        return buildMap {
            AdaptiveWidgetProvider.providers.forEach { type ->
                val kind = WidgetKind.valueOf(type.simpleName.removeSuffix("Widget").uppercase())
                manager.getAppWidgetIds(ComponentName(context, type)).forEach { put(it, kind) }
            }
        }
    }
}
