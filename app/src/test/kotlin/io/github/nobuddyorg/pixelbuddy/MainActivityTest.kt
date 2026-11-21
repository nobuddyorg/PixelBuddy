package io.github.nobuddyorg.pixelbuddy

import android.app.Application
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowRoleManager
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    /** Makes the home role available; an empty holder name means nobody holds it. */
    private fun homeRoleHeldBy(packageName: String) =
        ShadowRoleManager.addRoleHolder(RoleManager.ROLE_HOME, packageName, Process.myUserHandle())

    @Before
    fun resetFirstStart() {
        app
            .getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `manifest exposes MainActivity as home screen`() {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(app.packageName)

        val resolved = app.packageManager.queryIntentActivities(home, 0)

        assertEquals(listOf(MainActivity::class.java.name), resolved.map { it.activityInfo.name })
        assertTrue(resolved.single().activityInfo.exported)
    }

    @Test
    fun `role available and not held requests the home role`() {
        homeRoleHeldBy("")

        val action = HomeRole.nextAction(app)

        assertIs<HomeRoleAction.RequestRole>(action)
    }

    @Test
    fun `role already held needs no action`() {
        homeRoleHeldBy(app.packageName)

        assertEquals(HomeRoleAction.None, HomeRole.nextAction(app))
    }

    // Robolectric starts every test with no roles available.
    @Test
    fun `role unavailable falls back to default apps settings`() {
        val action = HomeRole.nextAction(app)

        assertIs<HomeRoleAction.OpenDefaultAppsSettings>(action)
        assertEquals(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, action.intent.action)
    }

    @Test
    fun `first start opens default apps settings when role is unavailable`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, started.action)
    }

    @Test
    fun `role is only requested on the first start`() {
        val first = Robolectric.buildActivity(MainActivity::class.java).setup()
        // Started activities are recorded app-wide: consume the first start's settings intent.
        assertEquals(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, shadowOf(first.get()).nextStartedActivity.action)
        first.destroy()

        val second = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        assertNull(shadowOf(second).nextStartedActivity)
    }

    @Test
    fun `back on home does nothing`() {
        homeRoleHeldBy(app.packageName)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        activity.onBackPressedDispatcher.onBackPressed()

        assertFalse(activity.isFinishing)
    }

    @Test
    fun `home intent is recognised`() {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

        assertTrue(MainActivity.isHomeIntent(home))
        assertFalse(MainActivity.isHomeIntent(Intent(Intent.ACTION_VIEW)))
    }
}
