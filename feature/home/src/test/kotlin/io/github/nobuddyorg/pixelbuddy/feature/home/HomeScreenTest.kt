package io.github.nobuddyorg.pixelbuddy.feature.home

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.graphics.Rect
import android.os.Process
import android.os.UserHandle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.nobuddyorg.pixelbuddy.core.data.apps.AppKey
import io.github.nobuddyorg.pixelbuddy.core.data.apps.AppListEntry
import io.github.nobuddyorg.pixelbuddy.core.data.apps.AppRepository
import io.github.nobuddyorg.pixelbuddy.core.data.apps.AppSorter
import io.github.nobuddyorg.pixelbuddy.core.data.apps.AppSource
import io.github.nobuddyorg.pixelbuddy.core.data.apps.HomeApps
import io.github.nobuddyorg.pixelbuddy.core.data.apps.LauncherApp
import io.github.nobuddyorg.pixelbuddy.core.data.settings.SettingsRepository
import io.github.nobuddyorg.pixelbuddy.core.designsystem.BatteryState
import io.github.nobuddyorg.pixelbuddy.core.pixelengine.IconPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast
import java.io.File
import java.time.LocalDateTime
import java.util.Locale
import kotlin.test.assertEquals

/** Galaxy S24: 1080 × 2340 px at 2.625 density. */
private const val S24 = "w411dp-h891dp-420dpi"

private fun app(
    label: String,
    work: Boolean = false,
): LauncherApp {
    val pkg = "pkg." + label.filter(Char::isLetter).lowercase()
    return LauncherApp(
        key = AppKey(pkg, "$pkg.Main", if (work) 10 else 0),
        label = label,
        component = ComponentName(pkg, "$pkg.Main"),
        user = Process.myUserHandle(),
        isMainProfile = !work,
    )
}

private val sampleApps: List<AppListEntry> =
    AppSorter(Locale.GERMANY).sort(
        listOf(
            app("Firefox"),
            app("Signal"),
            app("Spotify"),
            app("Proton Mail"),
            app("Maps"),
            app("Ärzte"),
            app("Telefon"),
            app("Teams", work = true),
            app("1Password"),
        ),
    )

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = S24)
class HomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val icons by lazy { FakeIcons.repository(folder.root) }

    private val favorites =
        listOf("Signal", "Firefox", "Maps").map { label ->
            sampleApps
                .first {
                    it.app.label == label
                }.app
        }

    private val actions = mutableListOf<AppAction>()

    private var resetKey by mutableIntStateOf(0)

    private fun show(
        apps: List<AppListEntry> = sampleApps,
        favorites: List<LauncherApp> = emptyList(),
        onAppClick: (LauncherApp) -> Unit = {},
        onOpenSettings: () -> Unit = {},
    ) = compose.setContent {
        HomeScreen(
            home = HomeApps(favorites, apps),
            onAppClick = onAppClick,
            onAppAction = { actions += it },
            onOpenSettings = onOpenSettings,
            resetKey = resetKey,
            header = {
                HomeHeader(
                    LocalDateTime.of(2026, 11, 6, 21, 45),
                    true,
                    BatteryState(58, charging = false),
                    {},
                    {},
                )
            },
            icon = { app -> AppIcon(app, icons, IconPreset.Default.style) },
        )
    }

    @Test
    fun `apps are listed in order`() {
        showAll()

        val labels =
            compose
                .onAllNodesWithContentDescription("", substring = true)
                .fetchSemanticsNodes()
                .mapNotNull { it.config.getOrNull(SemanticsProperties.ContentDescription)?.single() }
        val expected = sampleApps.map { it.app }.map { if (it.isMainProfile) it.label else "${it.label}, work profile" }
        // The header (clock, battery) has descriptions too; compare only the app rows.
        assertEquals(expected, labels.filter { it in expected })
    }

    @Test
    fun `tapping an app opens it`() {
        val clicked = mutableListOf<LauncherApp>()
        show(onAppClick = { clicked += it })
        openAllApps()

        compose.onNodeWithContentDescription("Signal").performClick()

        assertEquals(listOf("Signal"), clicked.map { it.label })
    }

    @Test
    fun `work apps are marked`() {
        showAll()

        compose.onNodeWithContentDescription("Teams, work profile").assertIsDisplayed()
    }

    @Test
    fun `long press on empty space opens the settings`() {
        var opened = 0
        show(apps = emptyList(), onOpenSettings = { opened++ })

        compose.onRoot().performTouchInput { longClick() }

        assertEquals(1, opened)
    }

    @Test
    fun `settings can be opened with accessibility services`() {
        var opened = 0
        show(onOpenSettings = { opened++ })

        compose.onNode(hasPaneTitle("Home screen")).performSemanticsAction(SemanticsActions.OnLongClick)

        assertEquals(1, opened)
    }

    private val storeJob = Job()

    @After
    fun closeSettings() = storeJob.cancel()

    private fun showRoute(source: AppSource = FailingSource): SettingsRepository {
        val settings = SettingsRepository.create(File(folder.root, "settings.json"), Dispatchers.IO + storeJob)
        val repository = AppRepository(source, settings)
        compose.setContent { HomeRoute(repository, settings, icons, onOpenSettings = {}) }
        compose.waitUntil { compose.onAllNodesWithText("▲ ALL APPS").fetchSemanticsNodes().isNotEmpty() }
        openAllApps()
        return settings
    }

    @Test
    fun `failed launch shows a message`() {
        showRoute()

        compose.onNodeWithContentDescription("Maps").performClick()

        assertEquals("Couldn't open Maps", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `home shows the favorites in their order`() {
        show(favorites = favorites)

        assertEquals(favorites.map { it.label }, appRowLabels())
        allAppsButton().assertIsDisplayed()
    }

    @Test
    fun `swiping up shows all apps and Back returns to the favorites`() {
        show(favorites = favorites)

        compose.onRoot().performTouchInput { swipeUp(startY = height * 0.7f, endY = height * 0.4f) }

        assertEquals(sampleApps.take(6).map { it.app.label }, appRowLabels().take(6))
        allAppsButton().assertDoesNotExist()
        Espresso.pressBack()
        assertEquals(favorites.map { it.label }, appRowLabels())
    }

    @Test
    fun `tapping a rail letter shows that letter's apps until a tap elsewhere`() {
        show(favorites = favorites)

        tapRail("S")

        assertEquals(listOf("Signal", "Spotify"), appRowLabels())
        compose.onNode(hasText("S") and isHeading()).assertIsDisplayed()
        compose.onRoot().performTouchInput { click(Offset(width / 2f, height - 300f)) }
        assertEquals(favorites.map { it.label }, appRowLabels())
    }

    @Test
    fun `letters without apps select the nearest letter`() {
        show()

        // No app starts with R; S is the nearest letter with apps.
        tapRail("R")

        assertEquals(listOf("Signal", "Spotify"), appRowLabels())
    }

    @Test
    fun `dragging along the rail follows the finger and lifting keeps the group`() {
        show(favorites = favorites)

        compose.onNodeWithContentDescription("Alphabet").performTouchInput {
            down(Offset(centerX, railY("A")))
            moveTo(Offset(centerX, railY("F")))
        }
        assertEquals(listOf("Firefox"), appRowLabels())
        compose.onNodeWithContentDescription("Alphabet").performTouchInput {
            moveTo(Offset(centerX, railY("T")))
            up()
        }

        assertEquals(listOf("Teams", "Telefon"), appRowLabels())
    }

    @Test
    fun `Back and Home return to the favorites`() {
        show(favorites = favorites)

        tapRail("M")
        assertEquals(listOf("Maps"), appRowLabels())
        Espresso.pressBack()
        assertEquals(favorites.map { it.label }, appRowLabels())

        tapRail("S")
        resetKey++
        compose.waitForIdle()
        assertEquals(favorites.map { it.label }, appRowLabels())
    }

    @Test
    fun `the rail has an accessibility action per letter with apps`() {
        show(favorites = favorites)
        val rail = compose.onNodeWithContentDescription("Alphabet")
        val custom = rail.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        val actions = custom.map { it.label }

        assertEquals(
            listOf(
                "Favorites",
                "Apps with A",
                "Apps with F",
                "Apps with M",
                "Apps with P",
                "Apps with S",
                "Apps with T",
                "Apps with #",
            ),
            actions,
        )
        compose.runOnIdle { custom.first { it.label == "Apps with P" }.action() }
        assertEquals(listOf("Proton Mail"), appRowLabels())
        rail.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "P"))
    }

    @Test
    fun `without favorites a hint explains how to add one`() {
        show()

        compose.onNodeWithText("Long-press an app to add it to your favorites.").assertIsDisplayed()
    }

    @Test
    fun `no hint while the apps are loading`() {
        show(apps = emptyList())

        compose.onNodeWithText("Long-press an app to add it to your favorites.").assertDoesNotExist()
    }

    @Test
    fun `long press opens the app menu and picking an entry closes it`() {
        showAll()

        compose.onNodeWithContentDescription("Signal").performTouchInput { longClick() }
        compose.onNodeWithText("Uninstall", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Add to favorites", substring = true).performClick()

        assertEquals(listOf<AppAction>(AppAction.AddFavorite(app("Signal"))), actions)
        compose.onNodeWithText("Add to favorites", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the menu is reachable with accessibility services`() {
        showAll()

        compose.onNodeWithContentDescription("Maps").performSemanticsAction(SemanticsActions.OnLongClick)
        compose.onNodeWithText("App info", substring = true).performClick()
        compose.onNodeWithContentDescription("Maps").performSemanticsAction(SemanticsActions.OnLongClick)
        compose.onNodeWithText("Hide", substring = true).performClick()

        assertEquals(listOf(AppAction.AppInfo(app("Maps")), AppAction.Hide(app("Maps"))), actions)
    }

    @Test
    fun `favorites can be moved and removed from their menu`() {
        show(favorites = favorites)

        openMenu("Signal")
        compose.onNodeWithText("Move up", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Move down", substring = true).performClick()
        openMenu("Maps")
        compose.onNodeWithText("Move down", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Move up", substring = true).performClick()
        openMenu("Firefox")
        compose.onNodeWithText("Remove from favorites", substring = true).performClick()

        assertEquals(
            listOf(
                AppAction.MoveFavorite(app("Signal"), 1),
                AppAction.MoveFavorite(app("Maps"), -1),
                AppAction.RemoveFavorite(app("Firefox")),
            ),
            actions,
        )
    }

    @Test
    fun `system apps have no uninstall entry`() {
        val system = app("Telefon").copy(canUninstall = false)
        show(apps = listOf(AppListEntry(system, "T")))
        openAllApps()

        openMenu("Telefon")

        compose.onNodeWithText("App info", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Uninstall", substring = true).assertDoesNotExist()
    }

    @Test
    fun `menu actions change the stored favorites and hidden apps`() {
        val settings = showRoute(source = WorkingSource)

        openMenu("Maps")
        compose.onNodeWithText("Add to favorites", substring = true).performClick()
        openMenu("Signal")
        compose.onNodeWithText("Hide", substring = true).performClick()
        compose.waitUntil { !hasApp("Signal") }
        Espresso.pressBack()
        compose.waitUntil { appRowLabels() == listOf("Maps") }

        assertEquals("Signal hidden. Show it again in the settings.", ShadowToast.getTextOfLatestToast())
        val stored = runBlocking { settings.settings.first() }
        assertEquals(listOf(app("Maps").key), stored.favorites)
        assertEquals(setOf(app("Signal").key), stored.hidden)
    }

    @Test
    fun `menu opens below its row or above it near the bottom`() {
        val window = IntSize(1000, 2000)
        val menu = IntSize(400, 600)

        val below = BelowOrAboveAnchor.calculatePosition(IntRect(100, 200, 900, 300), window, LayoutDirection.Ltr, menu)
        val above =
            BelowOrAboveAnchor.calculatePosition(
                IntRect(100, 1800, 900, 1900),
                window,
                LayoutDirection.Ltr,
                menu,
            )
        val rtl = BelowOrAboveAnchor.calculatePosition(IntRect(100, 200, 900, 300), window, LayoutDirection.Rtl, menu)

        assertEquals(IntOffset(100, 300), below)
        assertEquals(IntOffset(100, 1200), above)
        assertEquals(IntOffset(500, 300), rtl)
    }

    private fun showAll() {
        show()
        openAllApps()
    }

    private fun allAppsButton() = compose.onNodeWithText("▲ ALL APPS")

    private fun openAllApps() {
        allAppsButton().performClick()
    }

    /** Centre of [key]'s cell on the rail, in the rail's coordinates. */
    private fun TouchInjectionScope.railY(key: String) = (Rail.KEYS.indexOf(key) + 0.5f) * height / Rail.KEYS.size

    private fun tapRail(key: String) {
        compose.onNodeWithContentDescription("Alphabet").performTouchInput { click(Offset(centerX, railY(key))) }
    }

    private fun openMenu(
        label: String,
        row: Int = 0,
    ) {
        compose.onAllNodesWithContentDescription(label)[row].performTouchInput { longClick() }
    }

    private fun appRowLabels(): List<String> {
        val apps = sampleApps.map { it.app.label }.toSet()
        return compose
            .onAllNodesWithContentDescription("", substring = true)
            .fetchSemanticsNodes()
            .mapNotNull { it.config.getOrNull(SemanticsProperties.ContentDescription)?.single() }
            .map { it.removeSuffix(", work profile") }
            .filter { it in apps }
    }

    private fun hasApp(label: String) =
        compose.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `icon size snaps to whole pixels per icon pixel`() {
        // S24: 48 dp = 126 px; 24 grid -> 5 px per icon pixel = 120 px.
        assertEquals(120, snappedIconPx(126f, 24))
        assertEquals(112, snappedIconPx(126f, 16))
        assertEquals(12, snappedIconPx(5f, 12))
    }

    @Test
    fun `screenshot English`() {
        show(favorites = favorites)

        compose.onNode(hasPaneTitle("Home screen")).assertIsDisplayed()
        compose.onRoot().captureRoboImage("HomeScreen_en.png")
    }

    @Test
    @Config(qualifiers = "+de")
    fun `screenshot German`() {
        show()

        compose.onNode(hasPaneTitle("Startbildschirm")).assertIsDisplayed()
        compose.onNodeWithText("Halte eine App gedrückt, um sie zu den Favoriten hinzuzufügen.").assertIsDisplayed()
        compose.onNodeWithText("▲ ALLE APPS").performClick()
        compose.onNodeWithContentDescription("Teams, Arbeitsprofil").assertIsDisplayed()
        compose.onRoot().captureRoboImage("HomeScreen_de.png")
    }

    // Robolectric draws popup windows at the screen's corner, so only the menu itself is captured.
    @Test
    fun `screenshot app menu`() {
        show(favorites = favorites)

        openMenu("Firefox")
        compose.onNodeWithText("Move up", substring = true).assertIsDisplayed()
        compose.onNode(hasPaneTitle("Firefox")).captureRoboImage("AppMenu.png")
    }

    @Test
    fun `screenshot rail while dragging`() {
        show(favorites = favorites)

        compose.onNodeWithContentDescription("Alphabet").performTouchInput {
            down(Offset(centerX, railY("P")))
            moveTo(Offset(centerX, railY("S")))
        }

        compose.onRoot().captureRoboImage("HomeScreen_rail.png")
    }

    private fun hasPaneTitle(title: String) = SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, title)

    private open class FakeSource(
        private val apps: List<LauncherApp>,
    ) : AppSource {
        override fun loadApps() = apps

        override fun changes() = emptyFlow<Unit>()

        override fun start(
            component: ComponentName,
            user: UserHandle,
            sourceBounds: Rect?,
        ) = Unit

        override fun showDetails(
            component: ComponentName,
            user: UserHandle,
            sourceBounds: Rect?,
        ) = Unit

        override fun uninstall(
            packageName: String,
            user: UserHandle,
        ) = Unit

        override fun isRemoved(key: AppKey) = false
    }

    private object FailingSource : FakeSource(listOf(app("Maps"))) {
        override fun start(
            component: ComponentName,
            user: UserHandle,
            sourceBounds: Rect?,
        ): Unit = throw ActivityNotFoundException("removed")
    }

    private object WorkingSource : FakeSource(listOf(app("Maps"), app("Signal")))
}
