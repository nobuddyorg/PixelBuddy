package io.github.nobuddyorg.pixelbuddy.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.nobuddyorg.pixelbuddy.core.data.apps.AppKey
import io.github.nobuddyorg.pixelbuddy.core.data.apps.LauncherApp
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyColors
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyTheme
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelText

/** The list order after moving the entry at [from] one place [by] (−1 up, +1 down), clamped to the list. */
internal fun <T> List<T>.moved(
    from: Int,
    by: Int,
): List<T> {
    val to = (from + by).coerceIn(0, lastIndex)
    if (from !in indices || to == from) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

/**
 * The favorites while they are being reordered (#69): each row has a grip; dragging it moves the app, the others
 * make room. [onMove] gets the app and how many places it moved when the finger lifts. TalkBack moves an app with
 * the grip's actions instead.
 */
@Composable
internal fun ReorderFavorites(
    favorites: List<LauncherApp>,
    icon: @Composable (LauncherApp) -> Unit,
    onMove: (LauncherApp, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The order shown while dragging; stored when the finger lifts.
    var order by remember(favorites) { mutableStateOf(favorites) }
    var dragged by remember { mutableStateOf<AppKey?>(null) }
    var start by remember { mutableIntStateOf(0) }
    val offset = remember { mutableFloatStateOf(0f) }
    val move by rememberUpdatedState(onMove)

    fun finish() {
        val key = dragged ?: return
        val end = order.indexOfFirst { it.key == key }
        val app = order.getOrNull(end)
        if (app != null && end != start) move(app, end - start)
        dragged = null
        offset.floatValue = 0f
    }

    val upLabel = stringResource(R.string.home_menu_move_up)
    val downLabel = stringResource(R.string.home_menu_move_down)
    LazyColumn(
        contentPadding = PaddingValues(start = 32.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        itemsIndexed(order, key = { _, app -> "reorder/" + app.key.id }) { index, app ->
            var rowHeight by remember { mutableIntStateOf(1) }
            val lifted = app.key == dragged
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .onSizeChanged { rowHeight = it.height }
                        .zIndex(if (lifted) 1f else 0f)
                        .graphicsLayer { translationY = if (lifted) offset.floatValue else 0f }
                        .then(
                            if (lifted) {
                                Modifier.background(PixelBuddyTheme.style.accent.copy(alpha = LIFTED_ALPHA))
                            } else {
                                Modifier
                            },
                        ).padding(vertical = 4.dp),
            ) {
                icon(app)
                BasicText(app.label, style = PixelText.body, maxLines = 1, modifier = Modifier.weight(1f))
                val grip = stringResource(R.string.home_reorder_grip, app.label)
                Grip(
                    Modifier
                        .clearAndSetSemantics {
                            contentDescription = grip
                            customActions =
                                listOfNotNull(
                                    CustomAccessibilityAction(upLabel) {
                                        move(app, -1)
                                        true
                                    }.takeIf { index > 0 },
                                    CustomAccessibilityAction(downLabel) {
                                        move(app, 1)
                                        true
                                    }.takeIf { index < order.lastIndex },
                                )
                        }.pointerInput(app.key) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    dragged = app.key
                                    start = order.indexOfFirst { it.key == app.key }
                                    offset.floatValue = 0f
                                },
                                onDragEnd = { finish() },
                                onDragCancel = { finish() },
                                onVerticalDrag = { change, dy ->
                                    change.consume()
                                    offset.floatValue += dy
                                    val at = order.indexOfFirst { it.key == app.key }
                                    val half = rowHeight / 2f
                                    // Past half a row the app swaps places with its neighbour.
                                    if (offset.floatValue > half && at < order.lastIndex) {
                                        order = order.moved(at, 1)
                                        offset.floatValue -= rowHeight
                                    } else if (offset.floatValue < -half && at > 0) {
                                        order = order.moved(at, -1)
                                        offset.floatValue += rowHeight
                                    }
                                },
                            )
                        },
                )
            }
        }
    }
}

/** Three pixel bars to grab, in a 48 dp touch target. */
@Composable
private fun Grip(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(48.dp)
            .drawBehind {
                val bar = 4.dp.toPx()
                val width = 24.dp.toPx()
                val left = (size.width - width) / 2f
                val top = (size.height - 5 * bar) / 2f
                for (i in 0 until 3) {
                    drawRect(
                        PixelBuddyColors.Text,
                        topLeft = Offset(left, top + 2 * i * bar),
                        size = Size(width, bar),
                    )
                }
            },
    )
}

private const val LIFTED_ALPHA = 0.16f
