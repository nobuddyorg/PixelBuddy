package io.github.nobuddyorg.pixelbuddy.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.github.nobuddyorg.pixelbuddy.core.data.apps.HomeApps
import io.github.nobuddyorg.pixelbuddy.core.data.apps.LauncherApp
import io.github.nobuddyorg.pixelbuddy.core.designsystem.DitheredSky
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyColors
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyTheme
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelText

/** The view with the search field; without a query it lists every app. */
internal const val SEARCH = "search"

/**
 * Home screen: the dithered sky, the [header] (clock and info line), the app list and the [AlphabetRail].
 *
 * The list shows the favorites; a rail letter swaps in that letter's apps until a tap on empty space, Back or
 * Home ([resetKey] changes). Swiping up (or "Search") opens the search with the keyboard; swiping down calls
 * [onSwipeDown] (the notification shade) and opens the search if that returns false. Tapping an app starts it, a
 * long press opens its menu ([AppMenu]); a long press on empty space opens the settings.
 */
@Composable
fun HomeScreen(
    home: HomeApps,
    onAppClick: (LauncherApp) -> Unit,
    onAppAction: (AppAction) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    resetKey: Int = 0,
    onSwipeDown: () -> Boolean = { false },
    header: @Composable () -> Unit = {},
    icon: @Composable (LauncherApp) -> Unit = {},
) {
    val title = stringResource(R.string.home_screen_title)
    val openSettingsLabel = stringResource(R.string.home_open_settings)
    val openSettings by rememberUpdatedState(onOpenSettings)
    // Rail.FAVORITES, a section letter or SEARCH; Home (a new resetKey) goes back to the favorites.
    var view by rememberSaveable(resetKey) { mutableStateOf(Rail.FAVORITES) }
    // The row whose menu is open; an app can have two rows (favorite and in the list).
    var menuRow by rememberSaveable(resetKey) { mutableStateOf<String?>(null) }
    val sections = remember(home.apps) { home.apps.mapTo(LinkedHashSet()) { it.section } }
    val railEntries = remember(sections) { Rail.entries(sections) }
    // A letter whose last app was removed or hidden has nothing to show.
    if (view != Rail.FAVORITES && view != SEARCH && home.apps.isNotEmpty() && view !in sections) {
        view = Rail.FAVORITES
    }
    BackHandler(enabled = view != Rail.FAVORITES) { view = Rail.FAVORITES }
    val gestures by rememberUpdatedState(
        VerticalSwipes(
            onUp = { if (view != SEARCH) view = SEARCH },
            onDown = { if (view != SEARCH && !onSwipeDown()) view = SEARCH },
        ),
    )

    val entry: SearchEntry = { rowId, app, highlight, top ->
        AppEntry(
            app = app,
            home = home,
            menuOpen = rowId == menuRow,
            icon = icon,
            onAppClick = {
                onAppClick(it)
                // A search is done once an app starts.
                if (view == SEARCH) view = Rail.FAVORITES
            },
            onAppAction = onAppAction,
            onMenu = { menuRow = it?.let { rowId } },
            highlight = highlight,
            top = top,
        )
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(PixelBuddyColors.Navy)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { view = Rail.FAVORITES }, onLongPress = { openSettings() })
                }.verticalSwipes { gestures }
                .semantics {
                    paneTitle = title
                    onLongClick(label = openSettingsLabel) {
                        openSettings()
                        true
                    }
                },
    ) {
        DitheredSky(Modifier.fillMaxSize())
        if (view == SEARCH) {
            Box(Modifier.fillMaxSize().background(SearchScrim))
            SearchPanel(
                apps = home.apps,
                onLaunch = { app ->
                    onAppClick(app)
                    view = Rail.FAVORITES
                },
                entry = entry,
                modifier = Modifier.safeDrawingPadding(),
            )
            return@Box
        }
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Box(Modifier.padding(start = 32.dp, end = 32.dp, top = 32.dp, bottom = 16.dp)) { header() }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.fillMaxSize().padding(end = 40.dp)) {
                    // Each view starts at its top.
                    key(view) {
                        AppList(
                            view = view,
                            home = home,
                            swipes = { gestures },
                            entry = { rowId, app -> entry(rowId, app, emptyList(), false) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (home.apps.isNotEmpty()) {
                        SearchButton(onClick = { view = SEARCH })
                    }
                }
                AlphabetRail(
                    entries = railEntries,
                    selected = view,
                    onSelect = { view = it },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(top = 8.dp, bottom = 8.dp, end = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun AppList(
    view: String,
    home: HomeApps,
    swipes: () -> VerticalSwipes,
    entry: @Composable (rowId: String, app: LauncherApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val overscroll = rememberOverscrollSwipes(swipes)
    LazyColumn(
        contentPadding = PaddingValues(start = 32.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        modifier = modifier.fillMaxWidth().nestedScroll(overscroll),
    ) {
        when (view) {
            Rail.FAVORITES -> {
                items(home.favorites, key = { "favorite/" + it.key.id }) { app -> entry("favorite/" + app.key.id, app) }
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
            }

            else -> {
                item(key = "section/$view") { SectionLabel(view) }
                val apps = home.apps.filter { it.section == view }
                items(apps, key = { "app/" + it.app.key.id }) { entry("app/" + it.app.key.id, it.app) }
            }
        }
    }
}

/** What vertical swipes on home do. */
internal class VerticalSwipes(
    val onUp: () -> Unit,
    val onDown: () -> Unit,
)

/**
 * Swipes on parts of home that don't scroll (header, empty space). Only drags that start away from the screen
 * edges reach an app, so system gesture navigation keeps working.
 */
private fun Modifier.verticalSwipes(swipes: () -> VerticalSwipes): Modifier =
    pointerInput(Unit) {
        var total = 0f
        val threshold = SwipeDistance.toPx()
        detectVerticalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = {
                when {
                    total < -threshold -> swipes().onUp()
                    total > threshold -> swipes().onDown()
                }
            },
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                total += dragAmount
            },
        )
    }

/** Dragging past the end of the list (up) or its top (down), or flinging there, counts as a swipe. */
@Composable
private fun rememberOverscrollSwipes(swipes: () -> VerticalSwipes): NestedScrollConnection {
    val current by rememberUpdatedState(swipes)
    val threshold = with(LocalDensity.current) { SwipeDistance.toPx() }
    return remember(threshold) {
        object : NestedScrollConnection {
            private var pulled = 0f

            // One swipe per gesture, however far the finger goes.
            private var fired = false

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput || fired) return Offset.Zero
                // Finger up = negative y. Keep summing in one direction; a change of direction starts over.
                pulled =
                    if (available.y == 0f || (available.y < 0f) != (pulled < 0f)) available.y else pulled + available.y
                when {
                    pulled < -threshold -> current().onUp()
                    pulled > threshold -> current().onDown()
                    else -> return Offset.Zero
                }
                fired = true
                return Offset.Zero
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity {
                if (!fired) {
                    when {
                        available.y < -SWIPE_VELOCITY -> current().onUp()
                        available.y > SWIPE_VELOCITY -> current().onDown()
                    }
                }
                fired = false
                pulled = 0f
                return Velocity.Zero
            }
        }
    }
}

@Composable
internal fun SectionLabel(section: String) {
    BasicText(
        section,
        style = PixelText.small.copy(color = PixelBuddyTheme.style.accent),
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp).semantics { heading() },
    )
}

@Composable
private fun SearchButton(onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onClick),
    ) {
        BasicText(
            "▲ " + stringResource(R.string.home_search).uppercase(),
            style = PixelText.small.copy(color = PixelBuddyColors.Text),
        )
    }
}

private val SwipeDistance = 48.dp
private const val SWIPE_VELOCITY = 1500f
private val SearchScrim = PixelBuddyColors.Navy.copy(alpha = 0.82f)

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
    highlight: List<IntRange> = emptyList(),
    top: Boolean = false,
) {
    AppRow(
        app = app,
        icon = { icon(app) },
        onClick = { onAppClick(app) },
        onLongClick = { onMenu(app) },
        highlight = highlight,
        top = top,
    )
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
    highlight: List<IntRange> = emptyList(),
    top: Boolean = false,
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
                // The top search result is what Enter opens.
                .then(if (top) Modifier.background(PixelBuddyTheme.style.accent.copy(alpha = 0.16f)) else Modifier)
                .padding(vertical = 4.dp),
    ) {
        icon()
        val style = PixelText.body
        val badge = SpanStyle(color = PixelBuddyTheme.style.accent, fontSize = PixelText.small.fontSize)
        val matched = SpanStyle(color = PixelBuddyTheme.style.accent)
        BasicText(
            text =
                buildAnnotatedString {
                    append(app.label)
                    highlight.forEach { addStyle(matched, it.first, it.last + 1) }
                    if (!app.isMainProfile) {
                        withStyle(badge) { append("  $workLabel") }
                    }
                },
            style = style,
            maxLines = 1,
        )
    }
}
