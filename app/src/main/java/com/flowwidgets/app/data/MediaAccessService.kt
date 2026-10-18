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
import com.flowwidgets.app.widgets.AdaptiveWidgetProvider

class MediaAccessService : NotificationListenerService() {
    private val observed = mutableListOf<MediaController>()
    private val callback =
        object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) {
                AdaptiveWidgetProvider.updateAll(this@MediaAccessService)
            }

            override fun onPlaybackStateChanged(state: PlaybackState?) {
                AdaptiveWidgetProvider.updateAll(this@MediaAccessService)
            }

            override fun onSessionDestroyed() {
                AdaptiveWidgetProvider.updateAll(this@MediaAccessService)
            }
        }
    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { sessions ->
            observe(sessions.orEmpty())
        }

    private fun observe(sessions: List<MediaController>) {
        observed.forEach { it.unregisterCallback(callback) }
        observed.clear()
        observed.addAll(sessions)
        observed.forEach { it.registerCallback(callback, Handler(Looper.getMainLooper())) }
        AdaptiveWidgetProvider.updateAll(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (
            sbn?.notification?.extras?.containsKey(android.app.Notification.EXTRA_MEDIA_SESSION) ==
                true
        )
            AdaptiveWidgetProvider.updateAll(this)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (
            sbn?.notification?.extras?.containsKey(android.app.Notification.EXTRA_MEDIA_SESSION) ==
                true
        )
            AdaptiveWidgetProvider.updateAll(this)
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
        observed.forEach { it.unregisterCallback(callback) }
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
        )
    }

    fun action(context: Context, action: String) {
        val c = controller(context) ?: return
        val p = c.playbackState ?: return
        val controls = c.transportControls
        fun supports(flag: Long) = p.actions and flag != 0L
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
