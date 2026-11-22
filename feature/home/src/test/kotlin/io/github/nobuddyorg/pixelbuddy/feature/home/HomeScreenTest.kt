package io.github.nobuddyorg.pixelbuddy.feature.home

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.graphics.Rect
import android.os.Process
import android.os.UserHandle
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.nobuddyorg.pixelbuddy.core.data.apps.AppKey
import io.github.nobuddyorg.pixelbuddy.core.data.apps.AppListEntry
import io.github.nobuddyorg.pixelbuddy.core.data.apps.AppRepository
import io.github.nobuddyorg.pixelbuddy.core.data.apps.AppSorter
import io.github.nobuddyorg.pixelbuddy.core.data.apps.AppSource
import io.github.nobuddyorg.pixelbuddy.core.data.apps.LauncherApp
import io.github.nobuddyorg.pixelbuddy.core.designsystem.BatteryState
import io.github.nobuddyorg.pixelbuddy.core.pixelengine.IconPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast
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

private val sampleApps =
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

    private fun show(
        apps: List<AppListEntry> = sampleApps,
        onAppClick: (LauncherApp) -> Unit = {},
        onOpenSettings: () -> Unit = {},
    ) = compose.setContent {
        HomeScreen(
            apps = apps,
            onAppClick = onAppClick,
            onOpenSettings = onOpenSettings,
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
        show()

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

        compose.onNodeWithContentDescription("Signal").performClick()

        assertEquals(listOf("Signal"), clicked.map { it.label })
    }

    @Test
    fun `work apps are marked`() {
        show()

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

    @Test
    fun `failed launch shows a message`() {
        val repository = AppRepository(FailingSource, MutableStateFlow(emptySet()))
        compose.setContent { HomeRoute(repository, icons, onOpenSettings = {}) }
        compose.waitUntil { ShadowToast.shownToastCount() > 0 || hasApp("Maps") }

        compose.onNodeWithContentDescription("Maps").performClick()

        assertEquals("Couldn't open Maps", ShadowToast.getTextOfLatestToast())
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
        show()

        compose.onNode(hasPaneTitle("Home screen")).assertIsDisplayed()
        compose.onRoot().captureRoboImage("HomeScreen_en.png")
    }

    @Test
    @Config(qualifiers = "+de")
    fun `screenshot German`() {
        show()

        compose.onNode(hasPaneTitle("Startbildschirm")).assertIsDisplayed()
        compose.onNodeWithContentDescription("Teams, Arbeitsprofil").assertIsDisplayed()
        compose.onRoot().captureRoboImage("HomeScreen_de.png")
    }

    private fun hasPaneTitle(title: String) = SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, title)

    private object FailingSource : AppSource {
        override fun loadApps() = listOf(app("Maps"))

        override fun changes() = emptyFlow<Unit>()

        override fun start(
            component: ComponentName,
            user: UserHandle,
            sourceBounds: Rect?,
        ): Unit = throw ActivityNotFoundException("removed")
    }
}
