package io.github.nobuddyorg.pixelbuddy.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.github.nobuddyorg.pixelbuddy.core.data.apps.AppShortcut
import io.github.nobuddyorg.pixelbuddy.core.data.apps.AppShortcuts
import io.github.nobuddyorg.pixelbuddy.core.data.apps.LauncherApp
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyColors
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyTheme
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelText

/** Something the user picked in an app's long-press menu. */
sealed interface AppAction {
    val app: LauncherApp

    data class AddFavorite(
        override val app: LauncherApp,
    ) : AppAction

    data class RemoveFavorite(
        override val app: LauncherApp,
    ) : AppAction

    /** Moves a favorite by [offset] places; negative is up. */
    data class MoveFavorite(
        override val app: LauncherApp,
        val offset: Int,
    ) : AppAction

    data class Hide(
        override val app: LauncherApp,
    ) : AppAction

    /** Switches home to reordering the favorites by dragging (#69). */
    data class ReorderFavorites(
        override val app: LauncherApp,
    ) : AppAction

    /** Shows a hidden app (found by the search) on home again. */
    data class Unhide(
        override val app: LauncherApp,
    ) : AppAction

    /** Opens the editor for this app's own icon (#14). */
    data class EditIcon(
        override val app: LauncherApp,
    ) : AppAction

    data class AppInfo(
        override val app: LauncherApp,
    ) : AppAction

    data class Uninstall(
        override val app: LauncherApp,
    ) : AppAction

    /** Starts one of the app's shortcuts (#17). */
    data class OpenShortcut(
        val shortcut: AppShortcut,
    ) : AppAction {
        override val app: LauncherApp get() = shortcut.app
    }
}

/** Where [app] stands in the favorites, for the menu entries. */
internal data class FavoritePosition(
    val index: Int,
    val count: Int,
) {
    val isFirst: Boolean get() = index == 0
    val isLast: Boolean get() = index == count - 1
}

/**
 * The long-press menu of [app], shown below its row (or above, near the bottom of the screen). [favorite] is its
 * place among the favorites, or null if it is none. The app's [shortcuts] come first (null while loading), each
 * with its [shortcutIcon]. Back or a tap outside closes it.
 */
@Composable
internal fun AppMenu(
    app: LauncherApp,
    favorite: FavoritePosition?,
    onAction: (AppAction) -> Unit,
    onDismiss: () -> Unit,
    shortcuts: AppShortcuts? = AppShortcuts.None,
    shortcutIcon: @Composable (AppShortcut) -> Unit = {},
    /** A hidden app, found by the search: it can be shown again, but is no favorite until then. */
    hidden: Boolean = false,
) {
    Popup(
        popupPositionProvider = BelowOrAboveAnchor,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        val pick: (AppAction) -> Unit = { action ->
            onDismiss()
            onAction(action)
        }
        Column(
            modifier =
                Modifier
                    .padding(end = ShadowOffset, bottom = ShadowOffset)
                    .drawBehind {
                        val offset = ShadowOffset.toPx()
                        drawRect(PixelBuddyColors.Shadow, topLeft = Offset(offset, offset), size = size)
                    }.background(MenuBackground)
                    .border(3.dp, PixelBuddyColors.Text)
                    .semantics { paneTitle = app.label }
                    .widthIn(min = 220.dp)
                    .width(IntrinsicSize.Max)
                    .padding(vertical = 8.dp),
        ) {
            BasicText(
                text = app.label,
                style = PixelText.small,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            when (shortcuts) {
                is AppShortcuts.Available -> {
                    if (shortcuts.shortcuts.isNotEmpty()) {
                        shortcuts.shortcuts.forEach { shortcut ->
                            ShortcutItem(shortcut, shortcutIcon) { pick(AppAction.OpenShortcut(shortcut)) }
                        }
                        MenuDivider()
                    }
                }

                AppShortcuts.NeedsHomeApp -> {
                    BasicText(
                        text = stringResource(R.string.home_menu_shortcuts_need_home),
                        style = PixelText.small.copy(color = PixelBuddyColors.TextMuted),
                        // Wraps within the menu instead of widening it.
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).widthIn(max = 220.dp),
                    )
                    MenuDivider()
                }

                // Still loading: the rest of the menu is there already.
                null -> {}
            }
            if (hidden) {
                MenuItem(stringResource(R.string.home_menu_unhide)) { pick(AppAction.Unhide(app)) }
            } else if (favorite == null) {
                MenuItem(stringResource(R.string.home_menu_add_favorite)) { pick(AppAction.AddFavorite(app)) }
            } else {
                if (!favorite.isFirst) {
                    MenuItem(stringResource(R.string.home_menu_move_up)) { pick(AppAction.MoveFavorite(app, -1)) }
                }
                if (!favorite.isLast) {
                    MenuItem(stringResource(R.string.home_menu_move_down)) { pick(AppAction.MoveFavorite(app, 1)) }
                }
                if (!(favorite.isFirst && favorite.isLast)) {
                    MenuItem(stringResource(R.string.home_menu_reorder)) { pick(AppAction.ReorderFavorites(app)) }
                }
                MenuItem(stringResource(R.string.home_menu_remove_favorite)) { pick(AppAction.RemoveFavorite(app)) }
            }
            MenuItem(stringResource(R.string.home_menu_edit_icon)) { pick(AppAction.EditIcon(app)) }
            if (!hidden) MenuItem(stringResource(R.string.home_menu_hide)) { pick(AppAction.Hide(app)) }
            MenuItem(stringResource(R.string.home_menu_app_info)) { pick(AppAction.AppInfo(app)) }
            if (app.canUninstall) {
                MenuItem(stringResource(R.string.home_menu_uninstall)) { pick(AppAction.Uninstall(app)) }
            }
        }
    }
}

@Composable
private fun MenuItem(
    text: String,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp),
    ) {
        BasicText("► $text", style = PixelText.body.copy(color = PixelBuddyTheme.style.accent), maxLines = 1)
    }
}

@Composable
private fun ShortcutItem(
    shortcut: AppShortcut,
    icon: @Composable (AppShortcut) -> Unit,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) {}
                .padding(horizontal = 16.dp),
    ) {
        icon(shortcut)
        BasicText(shortcut.label, style = PixelText.body.copy(color = PixelBuddyColors.Text), maxLines = 1)
    }
}

/** A dotted pixel line between the shortcuts and the menu's own entries. */
@Composable
private fun MenuDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .height(3.dp)
            .drawBehind {
                val dot = 3.dp.toPx()
                var x = 0f
                while (x < size.width) {
                    drawRect(PixelBuddyColors.TextMuted, topLeft = Offset(x, 0f), size = Size(dot, dot))
                    x += dot * 2
                }
            },
    )
}

private val ShadowOffset = 4.dp
private val MenuBackground = Color(0xFF0D0F24)

/** Below the anchor if it fits, otherwise above it; always inside the window. */
internal object BelowOrAboveAnchor : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = maxOf(0, windowSize.width - popupContentSize.width)
        val x =
            if (layoutDirection == LayoutDirection.Ltr) {
                anchorBounds.left
            } else {
                anchorBounds.right - popupContentSize.width
            }
        val fitsBelow = anchorBounds.bottom + popupContentSize.height <= windowSize.height
        val y = if (fitsBelow) anchorBounds.bottom else maxOf(0, anchorBounds.top - popupContentSize.height)
        return IntOffset(x.coerceIn(0, maxX), y)
    }
}
