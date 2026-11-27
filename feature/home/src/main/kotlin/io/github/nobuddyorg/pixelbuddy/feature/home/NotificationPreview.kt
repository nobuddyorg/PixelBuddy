package io.github.nobuddyorg.pixelbuddy.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.nobuddyorg.pixelbuddy.core.data.notifications.AppNotification
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyColors
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyTheme
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelText

/** What the user does with a notification on home (#24). */
sealed interface NotificationAction {
    val key: String

    data class Open(
        override val key: String,
    ) : NotificationAction

    data class Dismiss(
        override val key: String,
    ) : NotificationAction
}

/** The pixel dot of an app with [count] notifications; tapping it shows or hides the preview. */
@Composable
internal fun NotificationDot(
    appLabel: String,
    count: Int,
    open: Boolean,
    onClick: () -> Unit,
) {
    val label =
        if (open) {
            stringResource(R.string.home_notifications_hide, appLabel)
        } else {
            pluralStringResource(R.plurals.home_notifications_show, count, count, appLabel)
        }
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(48.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label },
    ) {
        // A 1 px shadow, like the text, so it stands out on the bright end of the sky.
        Box(Modifier.offset(1.dp, 1.dp).size(DotSize).background(PixelBuddyColors.Shadow))
        Box(Modifier.size(DotSize).background(PixelBuddyTheme.style.accent))
    }
}

/**
 * The newest of an app's [notifications] under its row: tap opens it, ✕ dismisses it (if it can be). The text is
 * only on screen: nothing of it is kept.
 */
@Composable
internal fun NotificationPreview(
    notifications: List<AppNotification>,
    onAction: (NotificationAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latest = notifications.firstOrNull() ?: return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = PreviewIndent, bottom = 4.dp)
                .background(PreviewBackground),
    ) {
        Column(
            Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.home_notification_open),
                ) { onAction(NotificationAction.Open(latest.key)) }
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            if (latest.title.isNotBlank()) {
                BasicText(
                    latest.title,
                    style = PixelText.small.copy(color = PixelBuddyColors.Text),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (latest.text.isNotBlank()) {
                BasicText(latest.text, style = PixelText.small, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (notifications.size > 1) {
                BasicText(
                    pluralStringResource(
                        R.plurals.home_notifications_more,
                        notifications.size - 1,
                        notifications.size - 1,
                    ),
                    style = PixelText.small.copy(color = PixelBuddyTheme.style.accent),
                )
            }
        }
        if (latest.clearable) {
            val dismiss = stringResource(R.string.home_notification_dismiss)
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(48.dp)
                        .clickable(role = Role.Button) { onAction(NotificationAction.Dismiss(latest.key)) }
                        .semantics { contentDescription = dismiss },
            ) {
                BasicText("✕", style = PixelText.body.copy(color = PixelBuddyColors.Text))
            }
        }
    }
}

private val DotSize = 8.dp

/** Lines up with the app name: icon (48 dp) plus the gap. */
private val PreviewIndent = 64.dp
private val PreviewBackground = PixelBuddyColors.Navy.copy(alpha = 0.72f)
