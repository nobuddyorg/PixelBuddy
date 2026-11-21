package io.github.nobuddyorg.pixelbuddy.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nobuddyorg.pixelbuddy.core.data.apps.AppListEntry
import io.github.nobuddyorg.pixelbuddy.core.data.apps.LauncherApp
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyColors

private val AppNameStyle = TextStyle(color = Color(0xFFF2EEFF), fontSize = 20.sp)
private val BadgeStyle = SpanStyle(color = PixelBuddyColors.Orange, fontSize = 14.sp)

/**
 * Home screen: for now a plain alphabetical list of all apps (#4); header, favorites and rail follow in later
 * issues. Tapping an app starts it; a long press on empty space opens the settings.
 */
@Composable
fun HomeScreen(
    apps: List<AppListEntry>,
    onAppClick: (LauncherApp) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.home_screen_title)
    val openSettingsLabel = stringResource(R.string.home_open_settings)
    val openSettings by rememberUpdatedState(onOpenSettings)
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(PixelBuddyColors.Navy)
                .pointerInput(Unit) { detectTapGestures(onLongPress = { openSettings() }) }
                .semantics {
                    paneTitle = title
                    onLongClick(label = openSettingsLabel) {
                        openSettings()
                        true
                    }
                },
    ) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 24.dp),
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        ) {
            items(apps, key = { it.app.key.id }) { entry ->
                AppRow(entry.app, onClick = { onAppClick(entry.app) })
            }
        }
    }
}

@Composable
private fun AppRow(
    app: LauncherApp,
    onClick: () -> Unit,
) {
    val workLabel = stringResource(R.string.home_work_app)
    val description =
        if (app.isMainProfile) {
            app.label
        } else {
            stringResource(
                R.string.home_work_app_description,
                app.label,
            )
        }
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description }
                .padding(vertical = 6.dp),
    ) {
        BasicText(
            text =
                buildAnnotatedString {
                    append(app.label)
                    if (!app.isMainProfile) {
                        withStyle(BadgeStyle) { append("  $workLabel") }
                    }
                },
            style = AppNameStyle,
            maxLines = 1,
        )
    }
}
