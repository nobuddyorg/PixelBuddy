package io.github.nobuddyorg.pixelbuddy.core.data.apps

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter.from
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
// LauncherActivityInfo.getLabel() checks that the label has visible glyphs, which needs native graphics.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LauncherAppsSourceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val source = LauncherAppsSource(context)

    /** Builds a real [LauncherActivityInfo] (it has no public constructor). */
    private fun launcherActivity(
        packageName: String,
        activityName: String,
        label: String,
        user: UserHandle = Process.myUserHandle(),
    ): LauncherActivityInfo {
        val activityInfo =
            ActivityInfo().apply {
                this.packageName = packageName
                name = activityName
                nonLocalizedLabel = label
                applicationInfo = ApplicationInfo().apply { this.packageName = packageName }
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
