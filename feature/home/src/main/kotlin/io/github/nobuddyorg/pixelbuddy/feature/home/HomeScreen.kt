package io.github.nobuddyorg.pixelbuddy.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.nobuddyorg.pixelbuddy.core.data.apps.HomeApps
import io.github.nobuddyorg.pixelbuddy.core.data.apps.LauncherApp
import io.github.nobuddyorg.pixelbuddy.core.designsystem.DitheredSky
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyColors
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyTheme
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelText

/**
 * Home screen: the dithered sky, the [header] (clock and info line), the favorites and, below them, all apps;
 * the rail follows in #9. Tapping an app starts it, a long press opens its menu ([AppMenu]); a long press on empty
 * space opens the settings.
 */
@Composable
fun HomeScreen(
    home: HomeApps,
    onAppClick: (LauncherApp) -> Unit,
    onAppAction: (AppAction) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
    icon: @Composable (LauncherApp) -> Unit = {},
) {
    val title = stringResource(R.string.home_screen_title)
    val openSettingsLabel = stringResource(R.string.home_open_settings)
    val openSettings by rememberUpdatedState(onOpenSettings)
    // The row whose menu is open; an app can have two rows (favorite and in the list).
    var menuRow by rememberSaveable { mutableStateOf<String?>(null) }
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
                items(home.favorites, key = { "favorite/" + it.key.id }) { app ->
                    val rowId = "favorite/" + app.key.id
                    AppEntry(
                        app,
                        home,
                        rowId == menuRow,
                        icon,
                        onAppClick,
                        onAppAction,
                        { menuRow = it?.let { rowId } },
                    )
                }
                // No apps yet means they are still loading; the hint would only flash.
                if (home.favorites.isEmpty() && home.apps.isNotEmpty()) {
                    item(key = "favorites-hint") {
                        BasicText(
                            stringResource(R.string.home_favorites_hint),
                            style = PixelText.small,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
                if (home.apps.isNotEmpty()) {
                    item(key = "all-apps") {
                        BasicText(
                            stringResource(R.string.home_all_apps).uppercase(),
                            style = PixelText.small.copy(color = PixelBuddyTheme.style.accent),
                            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp).semantics { heading() },
                        )
                    }
                }
                items(home.apps, key = { "app/" + it.app.key.id }) { entry ->
                    val rowId = "app/" + entry.app.key.id
                    AppEntry(entry.app, home, rowId == menuRow, icon, onAppClick, onAppAction, {
                        menuRow =
                            it?.let { rowId }
                    })
                }
            }
        }
    }
}

/** An app row with its menu while [menuOpen]; [onMenu] gets the app to open the menu, null to close it. */
@Composable
private fun AppEntry(
    app: LauncherApp,
    home: HomeApps,
    menuOpen: Boolean,
    icon: @Composable (LauncherApp) -> Unit,
    onAppClick: (LauncherApp) -> Unit,
    onAppAction: (AppAction) -> Unit,
    onMenu: (LauncherApp?) -> Unit,
) {
    AppRow(app = app, icon = { icon(app) }, onClick = { onAppClick(app) }, onLongClick = { onMenu(app) })
    if (menuOpen) {
        AppMenu(
            app = app,
            favorite = home.favoritePosition(app),
            onAction = onAppAction,
            onDismiss = { onMenu(null) },
        )
    }
}

private fun HomeApps.favoritePosition(app: LauncherApp): FavoritePosition? {
    val index = favorites.indexOfFirst { it.key == app.key }
    return if (index < 0) null else FavoritePosition(index, favorites.size)
}

@Composable
private fun AppRow(
    app: LauncherApp,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val workLabel = stringResource(R.string.home_work_app)
    val optionsLabel = stringResource(R.string.home_app_options)
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
                .combinedClickable(
                    role = Role.Button,
                    onLongClickLabel = optionsLabel,
                    onLongClick = onLongClick,
                    onClick = onClick,
                ).semantics(mergeDescendants = true) { contentDescription = description }
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
