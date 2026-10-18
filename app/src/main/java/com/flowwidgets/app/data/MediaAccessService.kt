package com.flowwidgets.app.data

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class MediaAccessService : NotificationListenerService() {
    private val observed = mutableListOf<MediaController>()
    private val callbacks =
        mutableMapOf<android.media.session.MediaSession.Token, MediaController.Callback>()

    private fun newCallback(): MediaController.Callback {
        val playbackGate = PlaybackUpdateGate()
        return object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) {
                com.flowwidgets.app.widgets.WidgetUpdateDispatcher.request(
                    this@MediaAccessService,
                    setOf(WidgetKind.MEDIA),
                )
            }

            override fun onPlaybackStateChanged(state: PlaybackState?) {
                if (
                    playbackGate.shouldUpdate(
                        PlaybackUpdateGate.Signature(
                            state?.state,
                            state?.actions,
                            state?.activeQueueItemId,
                            state?.playbackSpeed,
                        ),
                        state?.position ?: -1,
                        android.os.SystemClock.elapsedRealtime(),
                    )
                ) {
                    com.flowwidgets.app.widgets.WidgetUpdateDispatcher.request(
                        this@MediaAccessService,
                        setOf(WidgetKind.MEDIA),
                    )
                }
            }

            override fun onQueueChanged(
                queue: MutableList<android.media.session.MediaSession.QueueItem>?
            ) {
                com.flowwidgets.app.widgets.WidgetUpdateDispatcher.request(
                    this@MediaAccessService,
                    setOf(WidgetKind.MEDIA),
                )
            }

            override fun onSessionDestroyed() {
                com.flowwidgets.app.widgets.WidgetUpdateDispatcher.request(
                    this@MediaAccessService,
                    setOf(WidgetKind.MEDIA),
                )
            }
        }
    }

    private fun forget(controller: MediaController) {
        callbacks.remove(controller.sessionToken)?.let { controller.unregisterCallback(it) }
    }

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { sessions ->
            observe(sessions.orEmpty())
        }

    private fun observe(sessions: List<MediaController>) {
        val tokens = sessions.map { it.sessionToken }.toSet()
        observed.filter { it.sessionToken !in tokens }.forEach { forget(it) }
        observed.removeAll { it.sessionToken !in tokens }
        val existing = observed.map { it.sessionToken }.toSet()
        sessions
            .filter { it.sessionToken !in existing }
            .forEach {
                val callback = newCallback()
                callbacks[it.sessionToken] = callback
                it.registerCallback(callback, Handler(Looper.getMainLooper()))
                observed.add(it)
            }
        com.flowwidgets.app.widgets.WidgetUpdateDispatcher.request(this, setOf(WidgetKind.MEDIA))
    }

    private fun notificationChanged(sbn: StatusBarNotification?) {
        if (
            observed.isEmpty() &&
                sbn?.notification
                    ?.extras
                    ?.containsKey(android.app.Notification.EXTRA_MEDIA_SESSION) == true
        ) {
            try {
                observe(
                    getSystemService(MediaSessionManager::class.java)
                        .getActiveSessions(ComponentName(this, MediaAccessService::class.java))
                )
            } catch (_: SecurityException) {
                observed.clear()
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        notificationChanged(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        notificationChanged(sbn)
    }

    override fun onListenerDisconnected() {
        observed.forEach { forget(it) }
        observed.clear()
        com.flowwidgets.app.widgets.WidgetUpdateDispatcher.request(this, setOf(WidgetKind.MEDIA))
        super.onListenerDisconnected()
    }

    override fun onListenerConnected() {
        val manager = getSystemService(MediaSessionManager::class.java)
        try {
            val component = ComponentName(this, MediaAccessService::class.java)
            manager.addOnActiveSessionsChangedListener(
                sessionsListener,
                component,
                Handler(Looper.getMainLooper()),
            )
            observe(manager.getActiveSessions(component))
        } catch (_: SecurityException) {
            observed.clear()
        }
    }

    override fun onDestroy() {
        getSystemService(MediaSessionManager::class.java)
            .removeOnActiveSessionsChangedListener(sessionsListener)
        observed.forEach { forget(it) }
        observed.clear()
        super.onDestroy()
    }
}

object MediaRepository {
    private fun controllers(context: Context): List<MediaController> =
        try {
            context
                .getSystemService(MediaSessionManager::class.java)
                .getActiveSessions(ComponentName(context, MediaAccessService::class.java))
        } catch (e: SecurityException) {
            emptyList()
        }

    private fun controller(context: Context): MediaController? {
        val sessions = controllers(context)
        return sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull()
    }

    fun current(context: Context): MediaInfo? {
        val c = controller(context) ?: return null
        val m = c.metadata
        val p = c.playbackState
        val actions = p?.actions ?: 0L
        return MediaInfo(
            m?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Contenido multimedia",
            m?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: m?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: "",
            m?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART),
            p?.state == PlaybackState.STATE_PLAYING,
            actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
            actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
            actions and
                (if (p?.state == PlaybackState.STATE_PLAYING)
                    (PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE)
                else (PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PLAY_PAUSE)) != 0L,
            p?.position
                ?.takeIf { it >= 0 }
                ?.let { position ->
                    val elapsed =
                        (android.os.SystemClock.elapsedRealtime() - (p.lastPositionUpdateTime))
                            .coerceAtLeast(0)
                    val current =
                        if (p.state == PlaybackState.STATE_PLAYING && p.lastPositionUpdateTime > 0)
                            position + (elapsed * p.playbackSpeed).toLong()
                        else position
                    val duration =
                        m?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0 }
                    current.coerceAtLeast(0).let {
                        if (duration != null) it.coerceAtMost(duration) else it
                    }
                },
            m?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0 },
            m?.getString(MediaMetadata.METADATA_KEY_ALBUM),
            c.queue.orEmpty().let { q ->
                val index = q.indexOfFirst { it.queueId == p?.activeQueueItemId }
                (if (index >= 0) q.drop(index + 1) else q).take(4).map {
                    MediaQueueEntry(
                        it.queueId,
                        it.description.title?.toString() ?: "Contenido multimedia",
                    )
                }
            },
            actions and PlaybackState.ACTION_SKIP_TO_QUEUE_ITEM != 0L,
        )
    }

    fun action(context: Context, action: String) {
        val c = controller(context) ?: return
        val p = c.playbackState ?: return
        val controls = c.transportControls
        fun supports(flag: Long) = p.actions and flag != 0L
        if (action.startsWith("queue:")) {
            val id = action.substringAfter(":").toLongOrNull() ?: return
            if (
                supports(PlaybackState.ACTION_SKIP_TO_QUEUE_ITEM) &&
                    c.queue.orEmpty().any { it.queueId == id }
            )
                controls.skipToQueueItem(id)
            return
        }
        when (action.lowercase()) {
            "previous",
            "prev" -> if (supports(PlaybackState.ACTION_SKIP_TO_PREVIOUS)) controls.skipToPrevious()
            "next" -> if (supports(PlaybackState.ACTION_SKIP_TO_NEXT)) controls.skipToNext()
            "play" ->
                if (
                    supports(PlaybackState.ACTION_PLAY) || supports(PlaybackState.ACTION_PLAY_PAUSE)
                )
                    controls.play()
            "pause" ->
                if (
                    supports(PlaybackState.ACTION_PAUSE) ||
                        supports(PlaybackState.ACTION_PLAY_PAUSE)
                )
                    controls.pause()
            "toggle",
            "play_pause" ->
                if (p.state == PlaybackState.STATE_PLAYING) {
                    if (
                        supports(PlaybackState.ACTION_PAUSE) ||
                            supports(PlaybackState.ACTION_PLAY_PAUSE)
                    )
                        controls.pause()
                } else if (
                    supports(PlaybackState.ACTION_PLAY) || supports(PlaybackState.ACTION_PLAY_PAUSE)
                )
                    controls.play()
        }
    }
}
