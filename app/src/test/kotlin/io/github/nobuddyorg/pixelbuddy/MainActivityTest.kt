package io.github.nobuddyorg.pixelbuddy

import android.app.Application
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
    fun `the setup sees whether PixelBuddy is the home app`() {
        homeRoleHeldBy(app.packageName)

        val access = RoleHomeAppAccess(app)

        assertTrue(access.isDefault())
        assertEquals(emptyList(), access.requestIntents())
    }

    @Test
    fun `the setup asks with the role dialog, then the default apps screen`() {
        homeRoleHeldBy("")

        val intents = RoleHomeAppAccess(app).requestIntents()

        assertFalse(RoleHomeAppAccess(app).isDefault())
        assertEquals(2, intents.size)
        assertEquals(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, intents[1].action)
    }

    // Robolectric starts every test with no roles available.
    @Test
    fun `without the role the setup opens the home settings`() {
        val intents = RoleHomeAppAccess(app).requestIntents()

        assertEquals(
            listOf(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, Settings.ACTION_HOME_SETTINGS),
            intents.map { it.action },
        )
    }

    @Test
    fun `nothing is started on its own at the first start`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `back on home does nothing`() {
        homeRoleHeldBy(app.packageName)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        activity.onBackPressedDispatcher.onBackPressed()

        assertFalse(activity.isFinishing)
    }

    @Test
    fun `app list works without the broad package visibility permission`() {
        val info = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)

        assertFalse(info.requestedPermissions.orEmpty().contains("android.permission.QUERY_ALL_PACKAGES"))
    }

    @Test
    fun `home intent is recognised`() {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

        assertTrue(MainActivity.isHomeIntent(home))
        assertFalse(MainActivity.isHomeIntent(Intent(Intent.ACTION_VIEW)))
    }
}
