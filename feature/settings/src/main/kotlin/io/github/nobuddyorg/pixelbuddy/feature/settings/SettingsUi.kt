package io.github.nobuddyorg.pixelbuddy.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyColors
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyTheme
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelFont
import kotlin.math.roundToInt

// Settings building blocks in the pixel font. Accent-coloured parts follow the user's accent at once.

internal val TextColor = PixelBuddyColors.Text
internal val MutedTextColor = PixelBuddyColors.TextMuted

internal val TitleStyle = TextStyle(color = TextColor, fontSize = 24.sp, fontFamily = PixelFont)
internal val SectionStyle = TextStyle(color = PixelBuddyColors.Orange, fontSize = 14.sp, fontFamily = PixelFont)
internal val BodyStyle = TextStyle(color = TextColor, fontSize = 16.sp, fontFamily = PixelFont)
internal val MutedStyle = TextStyle(color = MutedTextColor, fontSize = 14.sp, fontFamily = PixelFont)

@Composable
internal fun Title(
    text: String,
    modifier: Modifier = Modifier,
) {
    BasicText(text, style = TitleStyle, modifier = modifier.semantics { heading() })
}

@Composable
internal fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text.uppercase(),
        style = SectionStyle.copy(color = PixelBuddyTheme.style.accent),
        modifier = modifier.padding(top = 24.dp, bottom = 8.dp).semantics { heading() },
    )
}

/** Text button with at least a 48 dp touch target, in the accent colour unless [color] is given; muted if not [enabled]. */
@Composable
internal fun TextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = PixelBuddyTheme.style.accent,
    enabled: Boolean = true,
) {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier =
            modifier
                .heightIn(min = 48.dp)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(end = 16.dp),
    ) {
        BasicText("▶ $text", style = BodyStyle.copy(color = if (enabled) color else MutedTextColor.copy(alpha = 0.6f)))
    }
}

/** A full-width clickable row with a title and a muted second line. */
@Composable
internal fun ListRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
    ) {
        Column {
            BasicText(title, style = BodyStyle)
            if (subtitle.isNotEmpty()) BasicText(subtitle, style = MutedStyle)
        }
    }
}

/** A switch as a row: [title], the state as "ON"/"OFF" and an optional [subtitle]. */
@Composable
internal fun ToggleRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    on: String = "ON",
    off: String = "OFF",
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
                .padding(vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(title, style = BodyStyle)
            if (subtitle.isNotEmpty()) BasicText(subtitle, style = MutedStyle)
        }
        BasicText(
            if (checked) "[$on]" else "[$off]",
            style = BodyStyle.copy(color = if (checked) PixelBuddyTheme.style.accent else MutedTextColor),
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

/** One of several options as a row; [selected] shows the "▶" cursor. */
@Composable
internal fun OptionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = "",
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(vertical = 8.dp),
    ) {
        BasicText(
            if (selected) "▶" else " ",
            style = BodyStyle.copy(color = PixelBuddyTheme.style.accent),
            modifier = Modifier.padding(end = 12.dp),
        )
        Column {
            BasicText(title, style = if (selected) BodyStyle.copy(color = PixelBuddyTheme.style.accent) else BodyStyle)
            if (subtitle.isNotEmpty()) BasicText(subtitle, style = MutedStyle)
        }
    }
}

/** A row of square colour choices; [label] names each for TalkBack. */
@Composable
internal fun <T> SwatchRow(
    options: List<T>,
    selected: T,
    color: (T) -> Color,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    swatch: @Composable (T) -> Unit = { option ->
        Box(Modifier.size(32.dp).background(color(option)))
    },
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier.padding(vertical = 8.dp)) {
        options.forEach { option ->
            val description = label(option)
            val isSelected = option == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .heightIn(min = 48.dp)
                        .widthIn(min = 48.dp)
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(option) })
                        .semantics { contentDescription = description }
                        .border(2.dp, if (isSelected) TextColor else Color.Transparent)
                        .padding(6.dp),
            ) { swatch(option) }
        }
    }
}

/** A row of chips, one of which is [selected]; scrolls sideways when they don't fit. */
@Composable
internal fun <T> ChoiceRow(
    label: String,
    options: List<T>,
    selected: T,
    text: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(vertical = 8.dp)) {
        BasicText(label, style = BodyStyle)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 4.dp).horizontalScroll(rememberScrollState()).selectableGroup(),
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                val color = if (isSelected) PixelBuddyTheme.style.accent else MutedTextColor
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .heightIn(min = 48.dp)
                            .widthIn(min = 48.dp)
                            .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(option) })
                            .border(2.dp, color)
                            .padding(horizontal = 12.dp),
                ) {
                    BasicText(text(option), style = BodyStyle.copy(color = if (isSelected) color else TextColor))
                }
            }
        }
    }
}

/**
 * A slider of whole steps in [range], drawn as pixel blocks: tap or drag to set it. TalkBack reads [label] and
 * [valueText] and can set it directly.
 */
@Composable
internal fun PixelSlider(
    label: String,
    value: Int,
    range: IntRange,
    valueText: String,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val change by rememberUpdatedState(onChange)
    val steps = range.last - range.first
    val accent = PixelBuddyTheme.style.accent
    Column(modifier.padding(vertical = 8.dp)) {
        Row {
            BasicText(label, style = BodyStyle, modifier = Modifier.weight(1f))
            BasicText(valueText, style = BodyStyle.copy(color = accent))
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .pointerInput(range) {
                        fun set(x: Float) {
                            change(range.first + (x / size.width * steps).roundToInt().coerceIn(0, steps))
                        }
                        detectTapGestures { set(it.x) }
                    }.pointerInput(range) {
                        fun set(x: Float) {
                            change(range.first + (x / size.width * steps).roundToInt().coerceIn(0, steps))
                        }
                        detectHorizontalDragGestures { pointer, _ -> set(pointer.position.x) }
                    }.semantics {
                        contentDescription = label
                        stateDescription = valueText
                        progressBarRangeInfo =
                            ProgressBarRangeInfo(
                                value.toFloat(),
                                range.first.toFloat()..range.last.toFloat(),
                                steps = maxOf(0, steps - 1),
                            )
                        setProgress { target ->
                            change(target.roundToInt().coerceIn(range))
                            true
                        }
                    }.drawBehind {
                        val block = 4.dp.toPx()
                        val top = (size.height - block * 2) / 2
                        val filled = if (steps == 0) 1f else (value - range.first).toFloat() / steps
                        drawRect(MutedTextColor.copy(alpha = 0.4f), Offset(0f, top), Size(size.width, block * 2))
                        drawRect(accent, Offset(0f, top), Size(size.width * filled, block * 2))
                        val thumb = (size.width - block * 3) * filled
                        drawRect(TextColor, Offset(thumb, top - block), Size(block * 3, block * 4))
                    },
        )
    }
}

/** A question over the screen: [onConfirm] or [onCancel]; a tap outside cancels. */
@Composable
internal fun ConfirmDialog(
    title: String,
    confirm: String,
    cancel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .fillMaxSize()
                .background(PixelBuddyColors.Shadow.copy(alpha = 0.8f))
                .pointerInput(Unit) { detectTapGestures { onCancel() } },
    ) {
        Column(
            modifier =
                Modifier
                    .padding(32.dp)
                    .background(PixelBuddyColors.Navy)
                    .border(3.dp, TextColor)
                    .padding(24.dp)
                    .semantics { paneTitle = title },
        ) {
            BasicText(title, style = TitleStyle)
            if (text != null) {
                Spacer(Modifier.height(12.dp))
                BasicText(text, style = BodyStyle)
            }
            Spacer(Modifier.height(16.dp))
            TextButton(confirm, onConfirm)
            TextButton(cancel, onCancel, color = TextColor)
        }
    }
}
