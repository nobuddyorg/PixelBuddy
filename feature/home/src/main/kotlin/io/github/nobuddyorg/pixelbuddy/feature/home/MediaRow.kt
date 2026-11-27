package io.github.nobuddyorg.pixelbuddy.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.nobuddyorg.pixelbuddy.core.data.media.MediaAction
import io.github.nobuddyorg.pixelbuddy.core.data.media.NowPlaying
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyColors
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyTheme
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelText
import kotlin.math.roundToInt

/**
 * What is playing (#25), below the info line: pixelated [art], title, artist and the controls. Skip buttons only
 * when the player offers them.
 */
@Composable
internal fun MediaRow(
    nowPlaying: NowPlaying,
    art: ImageBitmap?,
    onAction: (MediaAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val description =
        listOf(nowPlaying.title, nowPlaying.artist).filter { it.isNotBlank() }.joinToString(", ")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        Box(Modifier.size(ArtSize)) {
            art?.let {
                Image(
                    it,
                    contentDescription = null,
                    filterQuality = FilterQuality.None,
                    modifier = Modifier.size(ArtSize),
                )
            }
        }
        Column(
            Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) { contentDescription = description },
        ) {
            BasicText(
                nowPlaying.title,
                style = PixelText.small.copy(color = PixelBuddyColors.Text),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (nowPlaying.artist.isNotBlank()) {
                BasicText(nowPlaying.artist, style = PixelText.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (nowPlaying.canSkipPrevious) {
            ControlButton(PREVIOUS, stringResource(R.string.home_media_previous)) { onAction(MediaAction.Previous) }
        }
        ControlButton(
            if (nowPlaying.playing) PAUSE else PLAY,
            stringResource(if (nowPlaying.playing) R.string.home_media_pause else R.string.home_media_play),
        ) { onAction(MediaAction.PlayPause) }
        if (nowPlaying.canSkipNext) {
            ControlButton(NEXT, stringResource(R.string.home_media_next)) { onAction(MediaAction.Next) }
        }
    }
}

/** A control drawn as pixel art from [glyph] (rows of '#'), so it matches the font and never becomes an emoji. */
@Composable
private fun ControlButton(
    glyph: List<String>,
    label: String,
    onClick: () -> Unit,
) {
    val accent = PixelBuddyTheme.style.accent
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(48.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label },
    ) {
        Canvas(Modifier.size(GlyphPixel * glyph.first().length, GlyphPixel * glyph.size)) {
            // Whole screen pixels, so no seams show between the glyph's pixels.
            val pixel = GlyphPixel.toPx().roundToInt().toFloat()
            for (shadow in listOf(true, false)) {
                val shift = if (shadow) (pixel / 2).roundToInt().toFloat() else 0f
                glyph.forEachIndexed { y, row ->
                    row.forEachIndexed { x, c ->
                        if (c == '#') {
                            drawRect(
                                color = if (shadow) PixelBuddyColors.Shadow else accent,
                                topLeft = Offset(x * pixel + shift, y * pixel + shift),
                                size = Size(pixel, pixel),
                            )
                        }
                    }
                }
            }
        }
    }
}

private val ArtSize = 40.dp
private val GlyphPixel = 2.dp

private val PLAY =
    listOf(
        "#....",
        "##...",
        "###..",
        "####.",
        "#####",
        "####.",
        "###..",
        "##...",
        "#....",
    )
private val PAUSE =
    listOf(
        "##.##",
        "##.##",
        "##.##",
        "##.##",
        "##.##",
        "##.##",
        "##.##",
        "##.##",
        "##.##",
    )
private val NEXT =
    listOf(
        "#.....#",
        "##....#",
        "###...#",
        "####..#",
        "#####.#",
        "####..#",
        "###...#",
        "##....#",
        "#.....#",
    )
private val PREVIOUS = NEXT.map { it.reversed() }
