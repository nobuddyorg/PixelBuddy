package io.github.nobuddyorg.pixelbuddy

import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import leakcanary.DetectLeaksAfterTestSuccess
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Google's Accessibility Test Framework on the real screens (#15): every check runs on each screen and after every
 * action. It needs a real Android; the local tests check names and touch targets themselves.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityTest {
    private val compose = createAndroidComposeRule<SettingsActivity>()

    // Leaks are checked once the activity is gone (#56).
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(DetectLeaksAfterTestSuccess()).around(compose)

    @Before
    fun checkEverything() = compose.enableAccessibilityChecks()

    @Test
    fun theSettingsPagesPassTheChecks() {
        compose.onRoot().tryPerformAccessibilityChecks()
        listOf("Appearance & themes", "Clock & typography", "Gestures", "Wallpaper & background").forEach { page ->
            compose.onNodeWithText(page).performScrollTo().performClick()
            compose.onRoot().tryPerformAccessibilityChecks()
            compose.onNodeWithText("▶ Back").performClick()
        }
    }

    @Test
    fun theIconEditorPassesTheChecks() {
        compose.onNodeWithText("App icons").performScrollTo().performClick()
        compose.onRoot().tryPerformAccessibilityChecks()
    }
}
