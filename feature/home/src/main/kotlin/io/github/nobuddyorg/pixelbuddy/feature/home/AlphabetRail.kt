package io.github.nobuddyorg.pixelbuddy.feature.home

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyColors
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyTheme
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelText
import kotlin.math.roundToInt

/**
 * The alphabet rail: ★ (favorites), A–Z and #. Touching or dragging selects the entry under the finger (skipping
 * letters without apps) through [onSelect]; while dragging, entries near the finger grow in a stepped wave, a
 * pixel speech bubble shows the letter and every change ticks. Lifting keeps the selection.
 *
 * Drag positions are only read in the draw and layout phases, so a drag redraws the rail without recomposing it;
 * only a letter change recomposes.
 */
@Composable
internal fun AlphabetRail(
    entries: List<RailEntry>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Letter size in sp: a setting of its own, not scaled with the text. */
    letterSp: Float = DEFAULT_LETTER_SP,
) {
    val haptics = LocalHapticFeedback.current
    val reducedMotion = rememberReducedMotion()
    val currentEntries by rememberUpdatedState(entries)
    val select by rememberUpdatedState(onSelect)
    var height by remember { mutableIntStateOf(0) }
    val fingerY = remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var dragKey by remember { mutableStateOf<String?>(null) }

    val railLabel = stringResource(R.string.home_rail)
    val actions =
        entries.filter { it.active }.map { entry ->
            val label =
                if (entry.key == Rail.FAVORITES) {
                    stringResource(R.string.home_rail_favorites)
                } else {
                    stringResource(R.string.home_rail_letter, entry.key)
                }
            CustomAccessibilityAction(label) {
                onSelect(entry.key)
                true
            }
        }
    val selectedLabel =
        if (selected == Rail.FAVORITES) stringResource(R.string.home_rail_favorites) else selected

    Box(modifier.width(RailWidth).fillMaxHeight()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .onSizeChanged { height = it.height }
                    .clearAndSetSemantics {
                        contentDescription = railLabel
                        stateDescription = selectedLabel
                        customActions = actions
                    }.pointerInput(Unit) {
                        fun update(y: Float) {
                            fingerY.floatValue = y
                            val list = currentEntries
                            val index =
                                Rail.nearestActive(Rail.indexAt(y, size.height.toFloat(), list.size), list)
                                    ?: return
                            val key = list[index].key
                            if (key != dragKey) {
                                if (dragKey != null) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                dragKey = key
                                select(key)
                            }
                        }
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            dragging = true
                            dragKey = null
                            update(down.position.y)
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                change.consume()
                                update(change.position.y)
                            } while (true)
                            dragging = false
                        }
                    },
        ) {
            entries.forEachIndexed { index, entry ->
                val color =
                    when {
                        entry.key == selected -> PixelBuddyTheme.style.accent
                        entry.active -> PixelBuddyColors.Text
                        else -> PixelBuddyColors.Text.copy(alpha = 0.4f)
                    }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .graphicsLayer {
                                val cell = if (entries.isEmpty()) 1f else height.toFloat() / entries.size
                                val distance = (fingerY.floatValue / cell) - (index + 0.5f)
                                val scale = if (dragging) Rail.waveScale(distance, reducedMotion) else 1f
                                scaleX = scale
                                scaleY = scale
                                transformOrigin = TransformOrigin(1f, 0.5f)
                                translationX = -(scale - 1f) * WaveShift.toPx()
                            },
                ) {
                    // Letters without apps are faded; without their drop shadow, which stays dark and made them look
                    // like dark smudges instead of faded letters.
                    val style = PixelText.style(letterSp, color, scaled = false).copy(textAlign = TextAlign.Center)
                    BasicText(entry.key, style = if (entry.active) style else style.copy(shadow = null))
                }
            }
        }
        if (dragging) {
            val key = dragKey
            if (key != null) SpeechBubble(key, fingerY = { fingerY.floatValue })
        }
    }
}

/** A pixel speech bubble left of the rail at the finger's height, pointing at the rail. */
@Composable
private fun SpeechBubble(
    key: String,
    fingerY: () -> Float,
) {
    val density = LocalDensity.current
    val side = with(density) { BubbleSize.roundToPx() }
    val gap = with(density) { (BubbleGap + WaveShift).roundToPx() }
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                // Wider than the rail: lay out at full size from the rail's top left corner, then move left.
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .offset { IntOffset(-side - gap, (fingerY() - side / 2f).roundToInt()) }
                .size(BubbleSize)
                .drawBehind {
                    val step = 4.dp.toPx()
                    drawRect(PixelBuddyColors.Shadow, topLeft = Offset(step, step), size = size)
                    // Stepped pointer towards the rail.
                    val midY = size.height / 2f
                    drawRect(PixelBuddyColors.Text, Offset(size.width, midY - 1.5f * step), size.copy(step, 3 * step))
                    drawRect(PixelBuddyColors.Text, Offset(size.width + step, midY - step / 2f), size.copy(step, step))
                }.background(PixelBuddyColors.Navy)
                .border(4.dp, PixelBuddyColors.Text),
    ) {
        BasicText(key, style = PixelText.style(36f, PixelBuddyTheme.style.accent))
    }
}

/** True if the user turned animations off (developer options or accessibility "remove animations"). */
@Composable
internal fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

internal const val DEFAULT_LETTER_SP = 12f
private val RailWidth = 40.dp
private val WaveShift = 16.dp
private val BubbleSize = 64.dp
private val BubbleGap = 20.dp
