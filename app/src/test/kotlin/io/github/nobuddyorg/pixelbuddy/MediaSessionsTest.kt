package io.github.nobuddyorg.pixelbuddy

import android.app.Application
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.github.nobuddyorg.pixelbuddy.core.data.media.MediaAction
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowMediaSessionManager
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class MediaSessionsTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val manager = app.getSystemService(MediaSessionManager::class.java)
    private val sessions = mutableListOf<MediaSession>()
    private val media = MediaSessions(app)

    @After
    fun tearDown() {
        sessions.forEach { it.release() }
        ShadowMediaSessionManager.reset()
    }

    private fun player(
        packageName: String,
        title: String?,
        state: Int,
        actions: Long = 0,
        art: Bitmap? = null,
    ): MediaController {
        val session = MediaSession(app, packageName).also { sessions += it }
        val controller = MediaController(app, session.sessionToken)
        shadowOf(controller).setPackageName(packageName)
        shadowOf(
            controller,
        ).setPlaybackState(
            PlaybackState
                .Builder()
                .setState(state, 0, 1f)
                .setActions(actions)
                .build(),
        )
        if (title != null) {
            shadowOf(controller).setMetadata(
                MediaMetadata
                    .Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, "The Pixels")
                    .apply { if (art != null) putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art) }
                    .build(),
            )
        }
        shadowOf(manager).addController(controller)
        return controller
    }

    @Test
    fun `nothing plays, nothing shows`() =
        runTest {
            media.nowPlaying().test { assertNull(awaitItem()) }
        }

    @Test
    fun `the playing session wins over a paused one`() =
        runTest {
            player("org.example.paused", "Paused song", PlaybackState.STATE_PAUSED)
            val art = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
            player(
                "org.example.music",
                "Chiptune",
                PlaybackState.STATE_PLAYING,
                actions = PlaybackState.ACTION_SKIP_TO_NEXT,
                art = art,
            )

            media.nowPlaying().test {
                val playing = assertNotNull(awaitItem())
                assertEquals("Chiptune", playing.title)
                assertEquals("The Pixels", playing.artist)
                assertEquals("org.example.music", playing.packageName)
                assertTrue(playing.playing)
                assertTrue(playing.canSkipNext)
                assertFalse(playing.canSkipPrevious)
                assertNotNull(playing.art)
            }
        }

    @Test
    fun `a paused session shows so it can be resumed, one without a title doesn't`() =
        runTest {
            player("org.example.podcast", null, PlaybackState.STATE_PLAYING)
            player("org.example.paused", "Episode 12", PlaybackState.STATE_PAUSED)

            media.nowPlaying().test {
                val paused = assertNotNull(awaitItem())
                assertEquals("Episode 12", paused.title)
                assertFalse(paused.playing)
            }
        }

    @Test
    fun `changes of the session show up live`() =
        runTest {
            val controller = player("org.example.music", "Chiptune", PlaybackState.STATE_PLAYING)

            media.nowPlaying().test {
                assertTrue(assertNotNull(awaitItem()).playing)
                val paused = PlaybackState.Builder().setState(PlaybackState.STATE_PAUSED, 0, 0f).build()
                shadowOf(controller).setPlaybackState(paused)
                shadowOf(controller).executeOnPlaybackStateChanged(paused)
                shadowOf(Looper.getMainLooper()).idle()
                assertFalse(assertNotNull(awaitItem()).playing)
            }
        }

    @Test
    fun `play and pause toggle, skips skip`() {
        val calls = mutableListOf<String>()
        val player =
            object : Player {
                override fun play() {
                    calls += "play"
                }

                override fun pause() {
                    calls += "pause"
                }

                override fun previous() {
                    calls += "previous"
                }

                override fun next() {
                    calls += "next"
                }
            }

        perform(MediaAction.PlayPause, playing = true, player)
        perform(MediaAction.PlayPause, playing = false, player)
        perform(MediaAction.Next, playing = true, player)
        perform(MediaAction.Previous, playing = true, player)

        assertEquals(listOf("pause", "play", "next", "previous"), calls)
    }

    @Test
    fun `controls without a shown session do nothing`() {
        media.control(MediaAction.PlayPause)
    }

    @Test
    fun `controls reach the shown session without failing`() =
        runTest {
            player("org.example.music", "Chiptune", PlaybackState.STATE_PLAYING)

            media.nowPlaying().test {
                awaitItem()
                MediaAction.entries.forEach(media::control)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
