package io.github.nobuddyorg.pixelbuddy.core.data.media

import android.graphics.Bitmap
import kotlinx.coroutines.flow.Flow

/** What is playing (#25): the active media session that plays or is paused. */
data class NowPlaying(
    /** The player app, e.g. to open it. */
    val packageName: String,
    val title: String,
    val artist: String,
    val playing: Boolean,
    val canSkipPrevious: Boolean,
    val canSkipNext: Boolean,
    /** The album art as the app provides it; home pixelates it. */
    val art: Bitmap?,
)

enum class MediaAction { PlayPause, Previous, Next }

/** The playing media, through the opt-in notification access (#24, #25). */
interface MediaPlayback {
    /** What is playing now, updated live while collected; null when nothing plays or the access isn't granted. */
    fun nowPlaying(): Flow<NowPlaying?>

    /** Controls the session [nowPlaying] last showed. */
    fun control(action: MediaAction)
}
