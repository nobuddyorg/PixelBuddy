package io.github.nobuddyorg.pixelbuddy.feature.home

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/** Galaxy S24: 1080 × 2340 px at 2.625 density. */
private const val S24 = "w411dp-h891dp-420dpi"

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = S24)
class HomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `pane title is English by default`() {
        compose.setContent { HomeScreen(onOpenSettings = {}) }

        compose.onNode(hasPaneTitle("Home screen")).assertExists()
        compose.onRoot().captureRoboImage("HomeScreen_en.png")
    }

    @Test
    @Config(qualifiers = "+de")
    fun `pane title is German on a German device`() {
        compose.setContent { HomeScreen(onOpenSettings = {}) }

        compose.onNode(hasPaneTitle("Startbildschirm")).assertExists()
        compose.onRoot().captureRoboImage("HomeScreen_de.png")
    }

    @Test
    fun `long press opens the settings`() {
        var opened = 0
        compose.setContent { HomeScreen(onOpenSettings = { opened++ }) }

        compose.onRoot().performTouchInput { longClick() }

        assertEquals(1, opened)
    }

    @Test
    fun `settings can be opened with accessibility services`() {
        var opened = 0
        compose.setContent { HomeScreen(onOpenSettings = { opened++ }) }

        compose.onNode(hasPaneTitle("Home screen")).performSemanticsAction(SemanticsActions.OnLongClick)

        assertEquals(1, opened)
    }

    private fun hasPaneTitle(title: String) = SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, title)
}
