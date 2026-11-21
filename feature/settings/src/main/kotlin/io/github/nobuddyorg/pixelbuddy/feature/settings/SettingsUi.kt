package io.github.nobuddyorg.pixelbuddy.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyColors

// Minimal building blocks until the design system (#5) brings the pixel font and components.

internal val TextColor = Color(0xFFF2EEFF)
internal val MutedTextColor = Color(0xFFB4ADCF)

internal val TitleStyle = TextStyle(color = TextColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)
internal val SectionStyle = TextStyle(color = PixelBuddyColors.Orange, fontSize = 14.sp, fontWeight = FontWeight.Bold)
internal val BodyStyle = TextStyle(color = TextColor, fontSize = 16.sp)
internal val MutedStyle = TextStyle(color = MutedTextColor, fontSize = 14.sp)

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
        style = SectionStyle,
        modifier = modifier.padding(top = 24.dp, bottom = 8.dp).semantics { heading() },
    )
}

/** Text button with at least a 48 dp touch target. */
@Composable
internal fun TextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = PixelBuddyColors.Orange,
) {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier =
            modifier
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(end = 16.dp),
    ) {
        BasicText("▶ $text", style = BodyStyle.copy(color = color, fontWeight = FontWeight.Bold))
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
            BasicText(subtitle, style = MutedStyle, maxLines = 2)
        }
    }
}
