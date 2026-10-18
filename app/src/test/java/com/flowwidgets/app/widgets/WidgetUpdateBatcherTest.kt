package com.flowwidgets.app.widgets

import com.flowwidgets.app.data.WidgetKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WidgetUpdateBatcherTest {
    @Test
    fun mediaBurstsAndIndependentIdsProduceOneTargetedBatch() = runTest {
        val calls = mutableListOf<WidgetUpdateTargets>()
        val batcher = WidgetUpdateBatcher(this) { calls.add(it) }
        val tickets =
            (1..100).map { batcher.request(WidgetUpdateTargets(kinds = setOf(WidgetKind.MEDIA))) }
        batcher.request(WidgetUpdateTargets(ids = setOf(7, 8)))
        advanceTimeBy(249)
        runCurrent()
        assertTrue(calls.isEmpty())
        advanceUntilIdle()
        assertEquals(listOf(WidgetUpdateTargets(setOf(WidgetKind.MEDIA), setOf(7, 8))), calls)
        assertTrue(tickets.all { it.isCompleted })
        assertFalse(calls.single().kinds.contains(WidgetKind.PHOTO))
    }

    @Test
    fun requestsWhileRenderingAreNotLost() = runTest {
        val gate = CompletableDeferred<Unit>()
        val calls = mutableListOf<WidgetUpdateTargets>()
        val batcher =
            WidgetUpdateBatcher(this) {
                calls.add(it)
                if (calls.size == 1) gate.await()
            }
        val first = batcher.request(WidgetUpdateTargets(ids = setOf(1)))
        advanceTimeBy(250)
        runCurrent()
        val second = batcher.request(WidgetUpdateTargets(ids = setOf(2)))
        assertFalse(first.isCompleted)
        assertFalse(second.isCompleted)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf(setOf(1), setOf(2)), calls.map { it.ids })
        assertTrue(first.isCompleted && second.isCompleted)
    }

    @Test
    fun failedFlushCompletesWaitersAndDoesNotBlockFutureUpdates() = runTest {
        var count = 0
        val batcher =
            WidgetUpdateBatcher(this) { if (++count == 1) error("Fixture render failure") }
        val first = batcher.request(WidgetUpdateTargets(ids = setOf(1)))
        advanceUntilIdle()
        assertTrue(runCatching { first.await() }.isFailure)
        val second = batcher.request(WidgetUpdateTargets(ids = setOf(2)))
        advanceUntilIdle()
        second.await()
        assertEquals(2, count)
    }
}
