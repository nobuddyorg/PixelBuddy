package io.github.nobuddyorg.pixelbuddy.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import io.github.nobuddyorg.pixelbuddy.core.data.apps.AppShortcut
import io.github.nobuddyorg.pixelbuddy.core.data.apps.AppShortcuts
import io.github.nobuddyorg.pixelbuddy.core.data.apps.HomeApps
import io.github.nobuddyorg.pixelbuddy.core.data.apps.LauncherApp
import io.github.nobuddyorg.pixelbuddy.core.data.notifications.AppNotification
import io.github.nobuddyorg.pixelbuddy.core.data.notifications.of
import io.github.nobuddyorg.pixelbuddy.core.data.settings.GestureAction
import io.github.nobuddyorg.pixelbuddy.core.data.settings.Gestures
import io.github.nobuddyorg.pixelbuddy.core.designsystem.DitheredSky
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyColors
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyTheme
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The view with the search field; without a query it lists every app. */
internal const val SEARCH = "search"

/** Every app, A to Z with section labels; the rail jumps between the sections. */
internal const val ALL_APPS = "all"

/**
 * Home screen: the dithered sky, the [header] (clock and info line), the app list and the [AlphabetRail].
 *
 * The list shows the favorites. A rail letter shows every app, scrolled to that letter, with a back button; there
 * the list scrolls freely and swipes do nothing, until ★, the back button, a tap on empty space, Back or Home
 * ([resetKey] changes) go back to the favorites. "Search" opens the search with the keyboard. On the favorites,
 * swipes up and down and a long press on empty space do what [gestures] say; a swipe follows the finger and acts on
 * release. [onOpenNotifications] opens the notification shade and returns false if it can't, then the search opens
 * instead. A double tap does nothing unless [gestures] give it an action, so single
 * taps aren't delayed. [onLock] locks the screen (#16). Tapping an app starts it, a long press opens its menu
 * ([AppMenu]). TalkBack always offers opening the settings, and locking when a gesture locks.
 */
@Composable
fun HomeScreen(
    home: HomeApps,
    onAppClick: (LauncherApp, LaunchFrom?) -> Unit,
    onAppAction: (AppAction) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    resetKey: Int = 0,
    gestures: Gestures = Gestures(),
    onOpenNotifications: () -> Boolean = { false },
    onLock: () -> Unit = {},
    header: @Composable () -> Unit = {},
    icon: @Composable (LauncherApp) -> Unit = {},
    /** An app's shortcuts for its menu (#17), read when the menu opens. */
    shortcuts: suspend (LauncherApp) -> AppShortcuts = { AppShortcuts.None },
    shortcutIcon: @Composable (AppShortcut) -> Unit = {},
    /** Notifications for the dots and previews (#24); empty without the opt-in access. */
    notifications: List<AppNotification> = emptyList(),
    onNotification: (NotificationAction) -> Unit = {},
    /** The widget in the slot with the favorites (#26), if there is one. */
    widget: (@Composable () -> Unit)? = null,
    widgetBelow: Boolean = false,
    /** Searches the web for a query typed in the search; null leaves that out. */
    onWebSearch: ((String) -> Unit)? = null,
    /** Letter size on the alphabet rail, in sp. */
    railLetterSp: Float = DEFAULT_LETTER_SP,
) {
    val title = stringResource(R.string.home_screen_title)
    val openSettingsLabel = stringResource(R.string.home_open_settings)
    val lockLabel = stringResource(R.string.home_lock_screen)
    val openSettings by rememberUpdatedState(onOpenSettings)
    // Rail.FAVORITES, ALL_APPS or SEARCH; Home (a new resetKey) goes back to the favorites.
    var view by rememberSaveable(resetKey) { mutableStateOf(Rail.FAVORITES) }
    val allApps = remember(resetKey) { LazyListState() }
    val scope = rememberCoroutineScope()
    val reducedMotion = rememberReducedMotion()
    // How far an upward swipe has pulled the search in, in pixels; it opens on release past the threshold.
    val pull = remember { Animatable(0f) }
    val threshold = with(LocalDensity.current) { SwipeDistance.toPx() }
    // Pulled far enough: letting go opens the search.
    val armed by remember { derivedStateOf { pull.value >= threshold } }
    val edge = PixelBuddyTheme.style.accent.copy(alpha = EDGE_ALPHA)
    val searching = view == SEARCH
    // Once the search opens, the blocks finish rising from where the pull left them.
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(searching) {
        if (searching) {
            reveal.snapTo(if (reducedMotion) 1f else pullProgress(pull.value, threshold))
            pull.snapTo(0f)
            reveal.animateTo(1f, tween(((1f - reveal.value) * REVEAL_MILLIS).toInt(), easing = LinearEasing))
        } else {
            reveal.snapTo(0f)
        }
    }
    // How far the search covers home, 0 to 1. The larger of both, so the hand-over from the pull doesn't flicker.
    val cover = { maxOf(pullProgress(pull.value, threshold), if (searching) reveal.value else 0f) }
    // Home stays until it has faded out under the search.
    val homeShown by remember(searching) { derivedStateOf { !searching || reveal.value < 1f } }
    // The row whose menu is open; an app can have two rows (favorite and in the list).
    var menuRow by rememberSaveable(resetKey) { mutableStateOf<String?>(null) }
    // The row whose notification preview is open.
    var previewRow by rememberSaveable(resetKey) { mutableStateOf<String?>(null) }
    val sections = remember(home.apps) { home.apps.mapTo(LinkedHashSet()) { it.section } }
    val railEntries = remember(sections) { Rail.entries(sections) }
    // Where each section starts in the list of all apps: its label, then its apps.
    val sectionStarts = remember(home.apps) { sectionStarts(home) }
    // The letter picked on the rail; it stays marked while its section is on screen.
    var railLetter by rememberSaveable(resetKey) { mutableStateOf(Rail.FAVORITES) }
    // Otherwise the rail marks the section at the top of the list as it scrolls.
    val visibleSection by remember(sectionStarts) {
        derivedStateOf {
            val picked = sectionStarts[railLetter]
            if (picked != null && allApps.layoutInfo.visibleItemsInfo.any { it.index == picked }) {
                railLetter
            } else {
                sectionStarts.entries.lastOrNull { it.value <= allApps.firstVisibleItemIndex }?.key ?: railLetter
            }
        }
    }
    BackHandler(enabled = view != Rail.FAVORITES) { view = Rail.FAVORITES }
    val showSection: (String) -> Unit = { key ->
        railLetter = key
        if (key == Rail.FAVORITES) {
            view = Rail.FAVORITES
        } else {
            view = ALL_APPS
            sectionStarts[key]?.let { index -> scope.launch { allApps.scrollToItem(index) } }
        }
    }
    val perform: (GestureAction) -> Unit = { action ->
        when (action) {
            GestureAction.Nothing -> Unit
            GestureAction.Search -> view = SEARCH
            GestureAction.Notifications -> if (!onOpenNotifications()) view = SEARCH
            GestureAction.Settings -> openSettings()
            GestureAction.Lock -> onLock()
        }
    }
    val pullsSearch = view == Rail.FAVORITES && gestures.swipeUp == GestureAction.Search
    val swipes by rememberUpdatedState(
        VerticalSwipes(
            // Only on the favorites: in the search and the list of all apps, swipes move the list.
            onUp = { if (view == Rail.FAVORITES) perform(gestures.swipeUp) },
            onDown = { if (view == Rail.FAVORITES) perform(gestures.swipeDown) },
            onPull = { px -> if (pullsSearch) scope.launch { pull.snapTo(px.coerceAtLeast(0f)) } },
            onRelease = { scope.springBack(pull) },
        ),
    )
    val longPress by rememberUpdatedState { perform(gestures.effectiveLongPress) }
    val doubleTap by rememberUpdatedState { perform(gestures.doubleTap) }
    val hasDoubleTap = gestures.doubleTap != GestureAction.Nothing

    val entry: SearchEntry = { rowId, app, highlight, top ->
        AppEntry(
            app = app,
            home = home,
            menuOpen = rowId == menuRow,
            icon = icon,
            onAppClick = { app, from ->
                onAppClick(app, from)
                // A search is done once an app starts.
                if (view == SEARCH) view = Rail.FAVORITES
            },
            onAppAction = onAppAction,
            onMenu = { menuRow = it?.let { rowId } },
            highlight = highlight,
            top = top,
            shortcuts = shortcuts,
            shortcutIcon = shortcutIcon,
            notifications = notifications.of(app),
            previewOpen = rowId == previewRow,
            onPreview = { previewRow = if (previewRow == rowId) null else rowId },
            onNotification = { action ->
                // Opening or dismissing the last one closes the preview.
                if (action is NotificationAction.Open) previewRow = null
                onNotification(action)
            },
        )
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(PixelBuddyColors.Navy)
                .pointerInput(hasDoubleTap) {
                    detectTapGestures(
                        onTap = { view = Rail.FAVORITES },
                        onDoubleTap = if (hasDoubleTap) ({ doubleTap() }) else null,
                        onLongPress = { longPress() },
                    )
                }.verticalSwipes { swipes }
                .semantics {
                    paneTitle = title
                    onLongClick(label = openSettingsLabel) {
                        openSettings()
                        true
                    }
                    if (gestures.usesLock) {
                        customActions =
                            listOf(
                                CustomAccessibilityAction(lockLabel) {
                                    onLock()
                                    true
                                },
                            )
                    }
                },
    ) {
        DitheredSky(Modifier.fillMaxSize())
        // The search's backdrop rises behind home in blocks and home fades out, following the pull and then the rise.
        // Read while drawing only, so a pull doesn't recompose home.
        Box(Modifier.fillMaxSize().pixelDissolve(cover, SearchScrim, edge))
        if (homeShown) {
            Column(
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .graphicsLayer {
                        val covered = cover()
                        translationY = -covered * HomeLift.toPx()
                        alpha = (1f - covered / PANEL_FROM).coerceAtLeast(0f)
                    }
                    // Fading out under the search: only the search is there for TalkBack.
                    .then(if (searching) Modifier.clearAndSetSemantics {} else Modifier),
            ) {
                // Full width, so the header can sit left, centred or right.
                Box(
                    Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 32.dp, bottom = 16.dp),
                ) { header() }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Column(Modifier.fillMaxSize().padding(end = 40.dp)) {
                        if (view == ALL_APPS) {
                            AllApps(
                                home = home,
                                state = allApps,
                                entry = { rowId, app -> entry(rowId, app, emptyList(), false) },
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            Favorites(
                                home = home,
                                swipes = { swipes },
                                entry = { rowId, app -> entry(rowId, app, emptyList(), false) },
                                modifier = Modifier.weight(1f),
                                widget = widget,
                                widgetBelow = widgetBelow,
                            )
                        }
                        if (home.apps.isNotEmpty()) {
                            Row(Modifier.fillMaxWidth()) {
                                if (view == ALL_APPS) {
                                    BottomButton(
                                        "◄ " + stringResource(R.string.home_back).uppercase(),
                                        Modifier.weight(1f),
                                    ) { view = Rail.FAVORITES }
                                }
                                BottomButton(
                                    "▲ " +
                                        stringResource(
                                            if (armed) R.string.home_search_release else R.string.home_search,
                                        ).uppercase(),
                                    Modifier.weight(1f).graphicsLayer {
                                        // The button rises with an upward swipe that pulls the search in.
                                        translationY = -pull.value / 2
                                    },
                                    color = if (armed) PixelBuddyTheme.style.accent else PixelBuddyColors.Text,
                                ) { view = SEARCH }
                            }
                        }
                    }
                    AlphabetRail(
                        entries = railEntries,
                        selected = if (view == ALL_APPS) visibleSection else view,
                        onSelect = showSection,
                        modifier = Modifier.align(Alignment.CenterEnd).padding(top = 8.dp, bottom = 8.dp, end = 4.dp),
                        letterSp = railLetterSp,
                    )
                }
            }
        }
        if (searching) {
            SearchPanel(
                apps = home.apps,
                onLaunch = { app ->
                    // Enter in the search: no icon was tapped.
                    onAppClick(app, null)
                    view = Rail.FAVORITES
                },
                entry = entry,
                modifier =
                    Modifier.safeDrawingPadding().graphicsLayer {
                        // The panel glides and fades in over the end of the rise.
                        val shown = ((cover() - PANEL_FROM) / (1f - PANEL_FROM)).coerceIn(0f, 1f)
                        alpha = shown
                        translationY = (1f - shown) * PanelRise.toPx()
                    },
                onWebSearch =
                    onWebSearch?.let { search ->
                        { query ->
                            search(query)
                            view = Rail.FAVORITES
                        }
                    },
            )
        }
    }
}

/** Animates [pull] back to rest when a swipe ends without acting. */
private fun CoroutineScope.springBack(pull: Animatable<Float, *>) {
    launch { pull.animateTo(0f) }
}

/** The list index where each section's label is in [AllApps], in A-to-Z order. */
private fun sectionStarts(home: HomeApps): Map<String, Int> {
    val starts = LinkedHashMap<String, Int>()
    var index = 0
    home.apps.forEach { entry ->
        if (entry.section !in starts) starts[entry.section] = index++
        index++
    }
    return starts
}

/** Every app with a label per section. It scrolls freely: no swipes. */
@Composable
private fun AllApps(
    home: HomeApps,
    state: LazyListState,
    entry: @Composable (rowId: String, app: LauncherApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = state,
        contentPadding = PaddingValues(start = 32.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        home.apps.groupBy { it.section }.forEach { (section, apps) ->
            item(key = "section/$section") { SectionLabel(section) }
            items(apps, key = { "app/" + it.app.key.id }) { entry("app/" + it.app.key.id, it.app) }
        }
    }
}

/** The favorites (and the widget); dragging past either end is a swipe. */
@Composable
private fun Favorites(
    home: HomeApps,
    swipes: () -> VerticalSwipes,
    entry: @Composable (rowId: String, app: LauncherApp) -> Unit,
    modifier: Modifier = Modifier,
    widget: (@Composable () -> Unit)? = null,
    widgetBelow: Boolean = false,
) {
    val overscroll = rememberOverscrollSwipes(swipes)
    LazyColumn(
        contentPadding = PaddingValues(start = 32.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        modifier = modifier.fillMaxWidth().nestedScroll(overscroll),
    ) {
        if (widget != null && !widgetBelow) item(key = "widget") { widget() }
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
        if (widget != null && widgetBelow) item(key = "widget") { widget() }
    }
}

/**
 * What vertical swipes on home do: [onUp] and [onDown] on release past the swipe distance. [onPull] follows an
 * upward drag (pixels, finger up is positive) and [onRelease] ends one that didn't act.
 */
internal class VerticalSwipes(
    val onUp: () -> Unit,
    val onDown: () -> Unit,
    val onPull: (Float) -> Unit = {},
    val onRelease: () -> Unit = {},
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
                    else -> swipes().onRelease()
                }
            },
            onDragCancel = { swipes().onRelease() },
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                total += dragAmount
                swipes().onPull(-total)
            },
        )
    }

/**
 * Dragging past the end of the list (up) or its top (down) is a swipe: it follows the finger and acts on release,
 * as does a fling there.
 */
@Composable
private fun rememberOverscrollSwipes(swipes: () -> VerticalSwipes): NestedScrollConnection {
    val current by rememberUpdatedState(swipes)
    val threshold = with(LocalDensity.current) { SwipeDistance.toPx() }
    return remember(threshold) {
        object : NestedScrollConnection {
            // How far the finger went past the end of the list in this gesture, net (finger up = negative y). A
            // held finger trembles in both directions; resetting on every turn made the pull snap back.
            private var pulled = 0f

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero
                pulled += available.y
                current().onPull(-pulled)
                return Offset.Zero
            }

            // Called when the finger lifts, before any fling.
            override suspend fun onPreFling(available: Velocity): Velocity {
                when {
                    pulled < -threshold || (pulled < 0f && available.y < -SWIPE_VELOCITY) -> current().onUp()
                    pulled > threshold || (pulled > 0f && available.y > SWIPE_VELOCITY) -> current().onDown()
                    else -> current().onRelease()
                }
                pulled = 0f
                return Velocity.Zero
            }
        }
    }
}

/**
 * A section's letter as a small dark pixel tab: in the accent colour on the sky alone it vanished where the sky
 * has the same colour (orange letters on the orange bottom).
 */
@Composable
internal fun SectionLabel(section: String) {
    Box(Modifier.padding(top = 16.dp, bottom = 4.dp)) {
        BasicText(
            section,
            style = PixelText.body.copy(color = PixelBuddyTheme.style.accent),
            modifier =
                Modifier
                    .background(SectionPlate)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .semantics { heading() },
        )
    }
}

private val SectionPlate = PixelBuddyColors.Shadow.copy(alpha = 0.85f)

@Composable
private fun BottomButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = PixelBuddyColors.Text,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onClick),
    ) {
        BasicText(text, style = PixelText.small.copy(color = color))
    }
}

private val SwipeDistance = 64.dp
private const val SWIPE_VELOCITY = 1500f

/**
 * How far the blocks have risen for a pull of [px]: a third of the way at the swipe threshold, so the rest rises on
 * release.
 */
private fun pullProgress(
    px: Float,
    threshold: Float,
) = (px / (threshold * PULL_SPAN)).coerceIn(0f, 1f)

private const val PULL_SPAN = 3f

/** The blocks rise over the whole screen in this long; from a pull, only the rest of the way. */
private const val REVEAL_MILLIS = 280

/**
 * Home has faded out by this much of the rise; the panel then glides and fades in over the rest, from [PanelRise]
 * below, so the two never show through each other.
 */
private const val PANEL_FROM = 0.7f
private val PanelRise = 24.dp
private val HomeLift = 24.dp
private const val EDGE_ALPHA = 0.7f
private val SearchScrim = PixelBuddyColors.Navy.copy(alpha = 0.82f)

/** An app row with its menu while [menuOpen]; [onMenu] gets the app to open the menu, null to close it. */
@Composable
private fun AppEntry(
    app: LauncherApp,
    home: HomeApps,
    menuOpen: Boolean,
    icon: @Composable (LauncherApp) -> Unit,
    onAppClick: (LauncherApp, LaunchFrom?) -> Unit,
    onAppAction: (AppAction) -> Unit,
    onMenu: (LauncherApp?) -> Unit,
    highlight: List<IntRange> = emptyList(),
    top: Boolean = false,
    shortcuts: suspend (LauncherApp) -> AppShortcuts = { AppShortcuts.None },
    shortcutIcon: @Composable (AppShortcut) -> Unit = {},
    notifications: List<AppNotification> = emptyList(),
    previewOpen: Boolean = false,
    onPreview: () -> Unit = {},
    onNotification: (NotificationAction) -> Unit = {},
) {
    AppRow(
        app = app,
        icon = { icon(app) },
        onClick = { from -> onAppClick(app, from) },
        onLongClick = { onMenu(app) },
        highlight = highlight,
        top = top,
        trailing = {
            if (notifications.isNotEmpty()) {
                NotificationDot(app.label, notifications.size, previewOpen && notifications.isNotEmpty(), onPreview)
            }
        },
    )
    if (previewOpen && notifications.isNotEmpty()) {
        NotificationPreview(notifications, onNotification)
    }
    if (menuOpen) {
        val appShortcuts by produceState<AppShortcuts?>(null, app) { value = shortcuts(app) }
        AppMenu(
            app = app,
            favorite = home.favoritePosition(app),
            onAction = onAppAction,
            onDismiss = { onMenu(null) },
            shortcuts = appShortcuts,
            shortcutIcon = shortcutIcon,
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
    onClick: (LaunchFrom?) -> Unit,
    onLongClick: () -> Unit,
    highlight: List<IntRange> = emptyList(),
    top: Boolean = false,
    /** After the name, e.g. the notification dot (#24). */
    trailing: @Composable () -> Unit = {},
) {
    // Where the icon is, so the app can open out of it.
    var iconBounds by remember { mutableStateOf<IntRect?>(null) }
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
                // A touch target at least; the icon and its padding set the height (56 dp with 48 dp icons).
                .heightIn(min = 48.dp)
                .combinedClickable(
                    role = Role.Button,
                    onLongClickLabel = optionsLabel,
                    onLongClick = onLongClick,
                    onClick = { onClick(iconBounds?.let(::LaunchFrom)) },
                ).semantics(mergeDescendants = true) { contentDescription = description }
                // The top search result is what Enter opens.
                .then(if (top) Modifier.background(PixelBuddyTheme.style.accent.copy(alpha = 0.16f)) else Modifier)
                .padding(vertical = 4.dp),
    ) {
        Box(Modifier.onGloballyPositioned { iconBounds = it.boundsInWindow().roundToIntRect() }) { icon() }
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
            modifier = Modifier.weight(1f, fill = false),
        )
        trailing()
    }
}
