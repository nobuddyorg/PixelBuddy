package io.github.nobuddyorg.pixelbuddy.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.nobuddyorg.pixelbuddy.core.data.apps.AppListEntry
import io.github.nobuddyorg.pixelbuddy.core.data.apps.LauncherApp
import io.github.nobuddyorg.pixelbuddy.core.designsystem.DitheredSky
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyColors
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyTheme
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelText

/**
 * Home screen: the dithered sky, the [header] (clock and info line) and, for now, a plain alphabetical list of all
 * apps; favorites and the rail follow in later issues. Tapping an app starts it; a long press on empty space opens
 * the settings.
 */
@Composable
fun HomeScreen(
    apps: List<AppListEntry>,
    onAppClick: (LauncherApp) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
    icon: @Composable (LauncherApp) -> Unit = {},
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
        DitheredSky(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Box(Modifier.padding(start = 32.dp, end = 32.dp, top = 32.dp, bottom = 16.dp)) { header() }
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(apps, key = { it.app.key.id }) { entry ->
                    AppRow(entry.app, icon = { icon(entry.app) }, onClick = { onAppClick(entry.app) })
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    app: LauncherApp,
    icon: @Composable () -> Unit,
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = description }
                .padding(vertical = 4.dp),
    ) {
        icon()
        val style = PixelText.body
        val badge = SpanStyle(color = PixelBuddyTheme.style.accent, fontSize = PixelText.small.fontSize)
        BasicText(
            text =
                buildAnnotatedString {
                    append(app.label)
                    if (!app.isMainProfile) {
                        withStyle(badge) { append("  $workLabel") }
                    }
                },
            style = style,
            maxLines = 1,
        )
    }
}
