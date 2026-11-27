package io.github.nobuddyorg.pixelbuddy.feature.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.nobuddyorg.pixelbuddy.core.data.media.MediaAction
import io.github.nobuddyorg.pixelbuddy.core.data.media.NowPlaying
import io.github.nobuddyorg.pixelbuddy.core.designsystem.BatteryState
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyColors
import io.github.nobuddyorg.pixelbuddy.core.pixelengine.IconPreset
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-420dpi")
class MediaRowTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val playing =
        NowPlaying(
            packageName = "org.example.music",
            title = "Chiptune Sunrise",
            artist = "The Pixels",
            playing = true,
            canSkipPrevious = true,
            canSkipNext = true,
            art = null,
        )

    /** Synthetic album art (no real covers in tests), pixelated like on home. */
    private fun art(): ImageBitmap {
        val cover = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        Canvas(cover).drawPaint(
            Paint().apply {
                shader =
                    LinearGradient(
                        0f,
                        0f,
                        256f,
                        256f,
                        Color.rgb(255, 111, 174),
                        Color.rgb(79, 214, 232),
                        Shader.TileMode.CLAMP,
                    )
            },
        )
        return runBlocking {
            FakeIcons
                .repository(
                    folder.root,
                ).pixelate(cover, IconPreset.Default.style)
                .asImageBitmap()
        }
    }

    private fun show(
        nowPlaying: NowPlaying = playing,
        art: ImageBitmap? = null,
        onAction: (MediaAction) -> Unit = {},
    ) = compose.setContent {
        Column(Modifier.background(PixelBuddyColors.Navy).padding(32.dp)) {
            HomeHeader(LocalDateTime.of(2026, 11, 6, 21, 45), true, BatteryState(58, charging = false), {}, {})
            MediaRow(nowPlaying, art, onAction)
        }
    }

    @Test
    fun `shows the track and controls it`() {
        val actions = mutableListOf<MediaAction>()
        show(onAction = { actions += it })

        compose.onNodeWithContentDescription("Chiptune Sunrise, The Pixels").assertIsDisplayed()
        compose.onNodeWithContentDescription("Pause").performClick()
        compose.onNodeWithContentDescription("Next").performClick()
        compose.onNodeWithContentDescription("Previous").performClick()

        assertEquals(listOf(MediaAction.PlayPause, MediaAction.Next, MediaAction.Previous), actions)
    }

    @Test
    fun `paused shows play, and skips only when the player offers them`() {
        show(playing.copy(playing = false, canSkipPrevious = false, canSkipNext = false))

        compose.onNodeWithContentDescription("Play").assertIsDisplayed()
        compose.onNodeWithContentDescription("Next").assertDoesNotExist()
        compose.onNodeWithContentDescription("Previous").assertDoesNotExist()
    }

    @Test
    fun `the player is accessible`() {
        show(art = art())

        compose.assertAccessible(48 * compose.density.density)
    }

    @Test
    fun `screenshot media row`() {
        show(art = art())

        compose.onRoot().captureRoboImage("HomeHeader_media.png")
    }
}
