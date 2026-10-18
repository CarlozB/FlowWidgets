package com.flowwidgets.app.data

import android.media.session.PlaybackState
import kotlin.math.abs

/** Position-only callbacks cannot turn a launcher widget into a per-second rendering loop. */
internal class PlaybackUpdateGate {
    data class Signature(
        val state: Int?,
        val actions: Long?,
        val queueId: Long?,
        val speed: Float?,
    )

    private var initialized = false
    private var previous: Signature? = null
    private var lastRender = 0L
    private var sampleTime = 0L
    private var samplePosition = -1L

    fun shouldUpdate(signature: Signature, position: Long, now: Long): Boolean {
        val elapsed = (now - sampleTime).coerceAtLeast(0)
        val expected =
            samplePosition +
                if (signature.state == PlaybackState.STATE_PLAYING)
                    (elapsed * (signature.speed ?: 0f)).toLong()
                else 0L
        val seek =
            initialized &&
                previous == signature &&
                position >= 0 &&
                samplePosition >= 0 &&
                abs(position - expected) > 5000
        val update =
            !initialized ||
                signature != previous ||
                seek ||
                now - lastRender >= 15_000 ||
                now < lastRender
        initialized = true
        previous = signature
        sampleTime = now
        samplePosition = position
        if (update) lastRender = now
        return update
    }
}
