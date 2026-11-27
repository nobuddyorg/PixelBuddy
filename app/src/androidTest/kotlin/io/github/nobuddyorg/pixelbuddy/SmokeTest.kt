package io.github.nobuddyorg.pixelbuddy

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import leakcanary.DetectLeaksAfterTestSuccess
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The launcher on a real Android (#15), run on emulators in CI: as the home app, it lists the apps, starts one and
 * opens its settings. Drives the phone like a person, through the screen, with UiAutomator.
 */
@RunWith(AndroidJUnit4::class)
class SmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val pkg = instrumentation.targetContext.packageName

    // Fails a test that leaves a leaked activity, view or bitmap behind (#56).
    @get:Rule
    val leaks = DetectLeaksAfterTestSuccess()

    @Before
    fun becomeHome() {
        // The role on Android 10+; the shell may grant it, as a person would in the system dialog.
        device.executeShellCommand("cmd role add-role-holder --user 0 android.app.role.HOME $pkg")
        device.pressHome()
        assertTrue(device.wait(Until.hasObject(By.pkg(pkg).depth(0)), TIMEOUT_MS), "PixelBuddy isn't home")
        // A fresh install starts with the setup; skipping it shows home.
        device.wait(Until.findObject(By.textContains("Skip setup")), SHORT_MS)?.click()
        assertNotNull(device.wait(Until.findObject(By.textContains("SEARCH")), TIMEOUT_MS), "home didn't show")
    }

    @Test
    fun homeListsTheAppsAndStartsOne() {
        device.findObject(By.textContains("SEARCH")).click()
        // Every image has the system settings app; the list only draws the rows on screen, so search for it.
        val field = device.wait(Until.findObject(By.focused(true)), TIMEOUT_MS)
        assertNotNull(field, "the search field isn't focused")
        field.text = "Sett"
        val settingsApp = device.wait(Until.findObject(By.desc("Settings")), TIMEOUT_MS)
        assertNotNull(settingsApp, "the app list doesn't show the Settings app")

        settingsApp.click()

        assertTrue(
            device.wait(Until.hasObject(By.pkg("com.android.settings").depth(0)), TIMEOUT_MS),
            "Settings didn't start",
        )
        device.pressHome()
        assertTrue(device.wait(Until.hasObject(By.pkg(pkg).depth(0)), TIMEOUT_MS), "Home didn't come back")
    }

    @Test
    fun aLongPressOnEmptySpaceOpensTheSettings() {
        // Empty space below the favorites hint.
        device.swipe(
            device.displayWidth / 2,
            device.displayHeight * 2 / 3,
            device.displayWidth / 2,
            device.displayHeight * 2 / 3,
            LONG_PRESS_STEPS,
        )

        assertNotNull(device.wait(Until.findObject(By.text("Appearance & themes")), TIMEOUT_MS), "settings didn't open")
    }

    @Test
    fun menusSettingsAndTheIconEditorLeaveNothingBehind() {
        // The long-press menu of an app.
        device.findObject(By.textContains("SEARCH")).click()
        device.wait(Until.findObject(By.focused(true)), TIMEOUT_MS).text = "Sett"
        assertNotNull(device.wait(Until.findObject(By.desc("Settings")), TIMEOUT_MS), "no search result")
        // The keyboard and the results may still move: press where the result is once the screen is still, not
        // where it was first found.
        device.waitForIdle()
        device.findObject(By.desc("Settings")).longClick()
        assertNotNull(device.wait(Until.findObject(By.textContains("App info")), TIMEOUT_MS), "the menu didn't open")
        device.pressBack()
        device.pressBack()
        device.pressBack()

        // The settings and the icon editor, opened and closed.
        device.swipe(
            device.displayWidth / 2,
            device.displayHeight * 2 / 3,
            device.displayWidth / 2,
            device.displayHeight * 2 / 3,
            LONG_PRESS_STEPS,
        )
        assertNotNull(device.wait(Until.findObject(By.text("App icons")), TIMEOUT_MS), "settings didn't open")
        device.findObject(By.text("App icons")).click()
        device.wait(Until.hasObject(By.textContains("Apply")), TIMEOUT_MS)
        device.pressBack()
        device.pressBack()
        device.pressHome()
        assertTrue(device.wait(Until.hasObject(By.pkg(pkg).depth(0)), TIMEOUT_MS), "Home didn't come back")
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val SHORT_MS = 3_000L

        /** Swipe steps of about 5 ms each, standing still: a long press. */
        const val LONG_PRESS_STEPS = 200
    }
}
