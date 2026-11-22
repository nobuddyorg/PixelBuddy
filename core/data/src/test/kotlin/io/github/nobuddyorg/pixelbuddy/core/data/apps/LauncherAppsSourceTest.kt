package io.github.nobuddyorg.pixelbuddy.core.data.apps

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.core.content.IntentCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowUserManager.UserState
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter.from
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
// LauncherActivityInfo.getLabel() checks that the label has visible glyphs, which needs native graphics.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LauncherAppsSourceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val installing = mutableSetOf<String>()

    // Robolectric doesn't support listing install sessions.
    private val source = LauncherAppsSource(context, pendingInstalls = { installing })

    private val userManager = context.getSystemService(UserManager::class.java)

    @Before
    fun unlockUser() {
        shadowOf(userManager).setUserState(Process.myUserHandle(), UserState.STATE_RUNNING_UNLOCKED)
    }

    /** Builds a real [LauncherActivityInfo] (it has no public constructor). */
    private fun launcherActivity(
        packageName: String,
        activityName: String,
        label: String,
        user: UserHandle = Process.myUserHandle(),
        flags: Int = 0,
    ): LauncherActivityInfo {
        val activityInfo =
            ActivityInfo().apply {
                this.packageName = packageName
                name = activityName
                nonLocalizedLabel = label
                applicationInfo =
                    ApplicationInfo().apply {
                        this.packageName = packageName
                        this.flags = flags
                    }
            }
        val internalClass = Class.forName("android.content.pm.LauncherActivityInfoInternal")
        val internal =
            ReflectionHelpers.callConstructor(
                internalClass,
                from(ActivityInfo::class.java, activityInfo),
                from(Class.forName("android.content.pm.IncrementalStatesInfo"), null),
                from(UserHandle::class.java, user),
                from(Boolean::class.javaPrimitiveType, false),
            )
        return ReflectionHelpers.callConstructor(
            LauncherActivityInfo::class.java,
            from(Context::class.java, context),
            from(internalClass, internal),
        )
    }

    @Test
    fun `launcher activities are mapped to apps with a stable key`() {
        val user = Process.myUserHandle()
        shadowOf(
            launcherApps,
        ).addActivity(user, launcherActivity("org.mozilla.firefox", "org.mozilla.App", " Firefox "))
        val serial = context.getSystemService(UserManager::class.java).getSerialNumberForUser(user)

        val app = source.loadApps().single()

        assertEquals(AppKey("org.mozilla.firefox", "org.mozilla.App", serial), app.key)
        assertEquals("Firefox", app.label)
        assertEquals("org.mozilla.App", app.component.className)
        assertEquals(user, app.user)
        assertTrue(app.isMainProfile)
        assertTrue(app.canUninstall)
    }

    @Test
    fun `system apps can't be uninstalled`() {
        shadowOf(launcherApps).addActivity(
            Process.myUserHandle(),
            launcherActivity(
                "com.android.settings",
                "com.android.Settings",
                "Settings",
                flags = ApplicationInfo.FLAG_SYSTEM,
            ),
        )

        assertFalse(source.loadApps().single().canUninstall)
    }

    private val mainSerial: Long
        get() = context.getSystemService(UserManager::class.java).getSerialNumberForUser(Process.myUserHandle())

    @Test
    fun `an app that is no longer installed is removed`() {
        assertTrue(source.isRemoved(AppKey("org.example.gone", "org.example.Main", mainSerial)))
    }

    @Test
    fun `an app that is being installed or restored is not removed`() {
        installing += "org.example.restoring"

        assertFalse(source.isRemoved(AppKey("org.example.restoring", "org.example.Main", mainSerial)))
    }

    @Test
    fun `an app uninstalled with its data kept is removed`() {
        val info = ApplicationInfo().apply { packageName = "org.example.kept" }
        shadowOf(launcherApps).addApplicationInfo(Process.myUserHandle(), "org.example.kept", info)

        assertTrue(source.isRemoved(AppKey("org.example.kept", "org.example.Main", mainSerial)))
    }

    @Test
    fun `an installed app is not removed, even without a launcher activity`() {
        val info =
            ApplicationInfo().apply {
                packageName = "org.example.there"
                flags = ApplicationInfo.FLAG_INSTALLED
            }
        shadowOf(launcherApps).addApplicationInfo(Process.myUserHandle(), "org.example.there", info)

        assertFalse(source.isRemoved(AppKey("org.example.there", "org.example.Main", mainSerial)))
    }

    @Test
    fun `apps of a removed profile are removed`() {
        assertTrue(source.isRemoved(AppKey("org.example.app", "org.example.Main", 4711)))
    }

    @Test
    fun `nothing counts as removed while the profile is locked`() {
        shadowOf(userManager).setUserState(Process.myUserHandle(), UserState.STATE_RUNNING_LOCKED)

        assertFalse(source.isRemoved(AppKey("org.example.gone", "org.example.Main", mainSerial)))
    }

    @Test
    fun `uninstall opens the system confirmation for the app's profile`() {
        source.uninstall("org.example.app", Process.myUserHandle())

        val intent = shadowOf(context as Application).nextStartedActivity
        assertEquals(Intent.ACTION_DELETE, intent.action)
        assertEquals("package:org.example.app", intent.dataString)
        assertEquals(
            Process.myUserHandle(),
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_USER, UserHandle::class.java),
        )
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun `package events trigger a reload`() =
        runTest {
            source.changes().test {
                shadowOf(launcherApps).notifyPackageAdded("org.example.new")
                ShadowLooper.idleMainLooper()
                assertEquals(Unit, awaitItem())

                shadowOf(launcherApps).notifyPackageRemoved("org.example.new")
                ShadowLooper.idleMainLooper()
                assertEquals(Unit, awaitItem())
            }
        }
}
