package io.github.nobuddyorg.pixelbuddy

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Before
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
        // Every image has the system settings app.
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

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val SHORT_MS = 3_000L

        /** Swipe steps of about 5 ms each, standing still: a long press. */
        const val LONG_PRESS_STEPS = 200
    }
}
