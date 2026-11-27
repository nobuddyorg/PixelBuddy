package io.github.nobuddyorg.pixelbuddy

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import io.github.nobuddyorg.pixelbuddy.core.data.media.MediaAction
import io.github.nobuddyorg.pixelbuddy.core.data.media.MediaPlayback
import io.github.nobuddyorg.pixelbuddy.core.data.media.NowPlaying
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The playing media (#25) through the media sessions Android lets a granted notification listener see. Listens
 * only while [nowPlaying] is collected (home visible); nothing is stored.
 */
class MediaSessions(
    context: Context,
) : MediaPlayback {
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(context, NotificationService::class.java)
    private val main = Handler(Looper.getMainLooper())

    /** The session shown, for the controls; only touched on the main thread. */
    private var shown: MediaController? = null

    override fun nowPlaying(): Flow<NowPlaying?> =
        callbackFlow {
            var controllers = emptyList<MediaController>()
            val callback =
                object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        trySend(pick(controllers))
                    }

                    override fun onMetadataChanged(metadata: MediaMetadata?) {
                        trySend(pick(controllers))
                    }

                    override fun onSessionDestroyed() {
                        trySend(pick(controllers))
                    }
                }

            fun watch(sessions: List<MediaController>?) {
                controllers.forEach { it.unregisterCallback(callback) }
                controllers = sessions.orEmpty()
                controllers.forEach { it.registerCallback(callback, main) }
                trySend(pick(controllers))
            }
            val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener(::watch)
            try {
                watch(manager.getActiveSessions(listener))
                manager.addOnActiveSessionsChangedListener(sessionsChanged, listener, main)
            } catch (_: SecurityException) {
                // No notification access (yet): nothing to show.
                trySend(null)
            }
            awaitClose {
                manager.removeOnActiveSessionsChangedListener(sessionsChanged)
                controllers.forEach { it.unregisterCallback(callback) }
                shown = null
            }
        }.conflate().distinctUntilChanged()

    /** The first session that plays, else the first one paused; null if none has a title. */
    private fun pick(controllers: List<MediaController>): NowPlaying? {
        val candidates = controllers.filter { it.metadata?.title() != null }
        val controller =
            candidates.firstOrNull { it.playbackState?.state in PLAYING }
                ?: candidates.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PAUSED }
        shown = controller
        controller ?: return null
        val metadata = controller.metadata ?: return null
        val state = controller.playbackState
        val actions = state?.actions ?: 0L
        return NowPlaying(
            packageName = controller.packageName,
            title = metadata.title().orEmpty(),
            artist = metadata.artist().orEmpty(),
            playing = state?.state in PLAYING,
            canSkipPrevious = actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
            canSkipNext = actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
            art = metadata.art(),
        )
    }

    override fun control(action: MediaAction) {
        val controller = shown ?: return
        perform(action, playing = controller.playbackState?.state in PLAYING, controller.transportControls.asPlayer())
    }

    private fun MediaController.TransportControls.asPlayer() =
        object : Player {
            override fun play() = this@asPlayer.play()

            override fun pause() = this@asPlayer.pause()

            override fun previous() = skipToPrevious()

            override fun next() = skipToNext()
        }

    private fun MediaMetadata.title(): String? =
        (getText(MediaMetadata.METADATA_KEY_TITLE) ?: getText(MediaMetadata.METADATA_KEY_DISPLAY_TITLE))
            ?.toString()
            ?.takeIf { it.isNotBlank() }

    private fun MediaMetadata.artist(): String? =
        (getText(MediaMetadata.METADATA_KEY_ARTIST) ?: getText(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE))
            ?.toString()

    private fun MediaMetadata.art(): Bitmap? =
        getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

    private companion object {
        val PLAYING = setOf(PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING)
    }
}

/** What the controls do to a player. */
internal interface Player {
    fun play()

    fun pause()

    fun previous()

    fun next()
}

/** Carries out [action] on [player]; play/pause toggles by whether it is [playing]. */
internal fun perform(
    action: MediaAction,
    playing: Boolean,
    player: Player,
) {
    when (action) {
        MediaAction.PlayPause -> if (playing) player.pause() else player.play()
        MediaAction.Previous -> player.previous()
        MediaAction.Next -> player.next()
    }
}
