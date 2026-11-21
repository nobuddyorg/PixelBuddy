package io.github.nobuddyorg.pixelbuddy.feature.home

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

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
        compose.setContent { HomeScreen() }

        compose.onNode(hasPaneTitle("Home screen")).assertExists()
        compose.onRoot().captureRoboImage("HomeScreen_en.png")
    }

    @Test
    @Config(qualifiers = "+de")
    fun `pane title is German on a German device`() {
        compose.setContent { HomeScreen() }

        compose.onNode(hasPaneTitle("Startbildschirm")).assertExists()
        compose.onRoot().captureRoboImage("HomeScreen_de.png")
    }

    private fun hasPaneTitle(title: String) = SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, title)
}
