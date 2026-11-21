package io.github.nobuddyorg.pixelbuddy.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-420dpi")
class ComponentsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `sky without scanlines`() {
        compose.setContent { DitheredSky(Modifier.size(200.dp, 300.dp), scanlines = false) }
        compose.onRoot().captureRoboImage("DitheredSky.png")
    }

    @Test
    fun `sky with scanlines`() {
        compose.setContent { DitheredSky(Modifier.size(200.dp, 300.dp), scanlines = true) }
        compose.onRoot().captureRoboImage("DitheredSky_scanlines.png")
    }

    @Test
    fun `hp bar states and clock`() {
        compose.setContent {
            PixelBuddyTheme {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.background(PixelBuddyColors.Navy).padding(16.dp),
                ) {
                    PixelClock("0123456789", height = 40.dp)
                    HpBar(BatteryState(0, charging = false))
                    HpBar(BatteryState(9, charging = false))
                    HpBar(BatteryState(58, charging = false))
                    HpBar(BatteryState(40, charging = true))
                    HpBar(BatteryState(100, charging = false, full = true))
                }
            }
        }
        compose.onRoot().captureRoboImage("Components.png")
    }

    @Test
    fun `accent colour is configurable`() {
        compose.setContent {
            PixelBuddyTheme(PixelBuddyStyle(accent = PixelBuddyColors.HpFull)) {
                Column(Modifier.fillMaxSize().background(PixelBuddyColors.Navy)) {
                    HpBar(BatteryState(40, charging = true), height = 24.dp)
                }
            }
        }
        compose.onRoot().captureRoboImage("HpBar_green_accent.png")
    }
}
