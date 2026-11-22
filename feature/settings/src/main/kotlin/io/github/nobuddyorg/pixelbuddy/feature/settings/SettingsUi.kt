package io.github.nobuddyorg.pixelbuddy.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyColors
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyTheme
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelFont

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

/** Text button with at least a 48 dp touch target, in the accent colour unless [color] is given. */
@Composable
internal fun TextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = PixelBuddyTheme.style.accent,
) {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier =
            modifier
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(end = 16.dp),
    ) {
        BasicText("▶ $text", style = BodyStyle.copy(color = color))
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
            if (subtitle.isNotEmpty()) BasicText(subtitle, style = MutedStyle, maxLines = 2)
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
            if (subtitle.isNotEmpty()) BasicText(subtitle, style = MutedStyle, maxLines = 2)
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
            if (subtitle.isNotEmpty()) BasicText(subtitle, style = MutedStyle, maxLines = 2)
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
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(option) })
                        .semantics { contentDescription = description }
                        .border(2.dp, if (isSelected) TextColor else Color.Transparent)
                        .padding(6.dp),
            ) { swatch(option) }
        }
    }
}
