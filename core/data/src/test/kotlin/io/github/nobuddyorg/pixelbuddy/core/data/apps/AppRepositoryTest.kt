package io.github.nobuddyorg.pixelbuddy.core.data.apps

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.graphics.Rect
import android.os.Bundle
import android.os.UserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class AppRepositoryTest {
    private class FakeSource : AppSource {
        var apps = listOf<LauncherApp>()
        val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
        var failure: RuntimeException? = null
        val removed = mutableSetOf<AppKey>()
        val calls = mutableListOf<String>()

        override fun loadApps() = apps

        override fun changes() = changes

        override fun start(
            component: ComponentName,
            user: UserHandle,
            sourceBounds: Rect?,
            options: Bundle?,
        ) = record("start ${component.packageName}")

        override fun showDetails(
            component: ComponentName,
            user: UserHandle,
            sourceBounds: Rect?,
        ) = record("details ${component.packageName}")

        override fun uninstall(
            packageName: String,
            user: UserHandle,
        ) = record("uninstall $packageName")

        override fun isRemoved(key: AppKey) = key in removed

        var shortcuts: AppShortcuts = AppShortcuts.None

        override fun shortcuts(app: LauncherApp) = shortcuts

        override fun startShortcut(
            shortcut: AppShortcut,
            sourceBounds: Rect?,
        ) = record("shortcut ${shortcut.id}")

        private fun record(call: String) {
            failure?.let { throw it }
            calls += call
        }
    }

    private val source = FakeSource()
    private val preferences = FakeAppPreferences()

    private fun repository(dispatcher: TestDispatcher) =
        AppRepository(source, preferences, locale = { Locale.GERMANY }, dispatcher = dispatcher)

    @Test
    fun `apps are emitted sorted`() =
        runTest {
            source.apps = listOf(app("Zug"), app("Ärzte"), app("Bahn"))

            repository(StandardTestDispatcher(testScheduler)).home.test {
                assertEquals(listOf("Ärzte", "Bahn", "Zug"), awaitItem().apps.map { it.app.label })
            }
        }

    @Test
    fun `installs and removals are picked up without restart`() =
        runTest {
            source.apps = listOf(app("Bahn"))

            repository(StandardTestDispatcher(testScheduler)).home.test {
                assertEquals(listOf("Bahn"), awaitItem().apps.map { it.app.label })

                source.apps = listOf(app("Bahn"), app("Maps"))
                source.changes.emit(Unit)
                assertEquals(listOf("Bahn", "Maps"), awaitItem().apps.map { it.app.label })

                source.apps = listOf(app("Maps"))
                source.changes.emit(Unit)
                assertEquals(listOf("Maps"), awaitItem().apps.map { it.app.label })
            }
        }

    @Test
    fun `hidden apps are filtered out of the list and the favorites`() =
        runTest {
            val maps = app("Maps")
            source.apps = listOf(app("Bahn"), maps)
            preferences.favorites.value = listOf(maps.key)

            repository(StandardTestDispatcher(testScheduler)).home.test {
                assertEquals(2, awaitItem().apps.size)

                preferences.hidden.value = setOf(maps.key)
                val home = awaitItem()
                assertEquals(listOf("Bahn"), home.apps.map { it.app.label })
                assertEquals(emptyList(), home.favorites)
            }
        }

    @Test
    fun `favorites keep the user's order`() =
        runTest {
            val (bahn, maps, zug) = listOf(app("Bahn"), app("Maps"), app("Zug"))
            source.apps = listOf(bahn, maps, zug)
            preferences.favorites.value = listOf(zug.key, bahn.key)

            repository(StandardTestDispatcher(testScheduler)).home.test {
                assertEquals(listOf("Zug", "Bahn"), awaitItem().favorites.map { it.label })

                preferences.favorites.value = listOf(bahn.key, maps.key, zug.key)
                assertEquals(listOf("Bahn", "Maps", "Zug"), awaitItem().favorites.map { it.label })
            }
        }

    @Test
    fun `favorites that aren't launchable right now are skipped but kept`() =
        runTest {
            val maps = app("Maps")
            source.apps = listOf(app("Bahn"))
            preferences.favorites.value = listOf(maps.key)

            repository(StandardTestDispatcher(testScheduler)).home.test {
                assertEquals(emptyList(), awaitItem().favorites)
            }
            assertEquals(listOf(maps.key), preferences.favorites.value)
        }

    @Test
    fun `entries of uninstalled apps are forgotten`() =
        runTest {
            val (maps, gone) = listOf(app("Maps"), app("Gone"))
            source.apps = listOf(maps)
            source.removed += gone.key
            preferences.favorites.value = listOf(gone.key, maps.key)
            preferences.hidden.value = setOf(gone.key)

            repository(StandardTestDispatcher(testScheduler)).home.test {
                assertEquals(listOf("Maps"), awaitItem().favorites.map { it.label })
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(listOf(setOf(gone.key)), preferences.forgotten)
            assertEquals(listOf(maps.key), preferences.favorites.value)
            assertEquals(emptySet(), preferences.hidden.value)
        }

    @Test
    fun `hidden apps are listed for the settings`() =
        runTest {
            val (bahn, maps) = listOf(app("Bahn"), app("Maps"))
            source.apps = listOf(maps, bahn)
            preferences.hidden.value = setOf(maps.key)

            repository(StandardTestDispatcher(testScheduler)).hiddenApps.test {
                assertEquals(listOf("Maps"), awaitItem().map { it.label })

                preferences.hidden.value = setOf(maps.key, bahn.key)
                assertEquals(listOf("Bahn", "Maps"), awaitItem().map { it.label })
            }
        }

    @Test
    fun `unchanged reloads are not emitted again`() =
        runTest {
            source.apps = listOf(app("Bahn"))

            repository(StandardTestDispatcher(testScheduler)).home.test {
                awaitItem()
                source.changes.emit(Unit)
                expectNoEvents()
            }
        }

    @Test
    fun `launch, app info and uninstall reach the system`() {
        val maps = app("Maps")
        val repository = AppRepository(source, preferences)

        assertEquals(LaunchResult.Launched, repository.launch(maps))
        assertEquals(LaunchResult.Launched, repository.openDetails(maps))
        assertEquals(LaunchResult.Launched, repository.uninstall(maps))

        assertEquals(listOf("start pkg.maps", "details pkg.maps", "uninstall pkg.maps"), source.calls)
    }

    @Test
    fun `system actions on a removed or disabled app fail without crashing`() {
        val repository = AppRepository(source, preferences)
        listOf(
            ActivityNotFoundException("gone"),
            SecurityException("profile locked"),
            IllegalStateException("disabled"),
            IllegalArgumentException("unknown user"),
        ).forEach { error ->
            source.failure = error

            listOf<(LauncherApp) -> LaunchResult>(
                { repository.launch(it) },
                { repository.openDetails(it) },
            ).forEach { action ->
                val result = action(app("Maps"))
                assertIs<LaunchResult.Failed>(result)
                assertEquals(error, result.reason)
            }
            assertIs<LaunchResult.Failed>(repository.uninstall(app("Maps")))
        }
        assertTrue(source.calls.isEmpty())
    }

    @Test
    fun `shortcuts come from the source and start through it`() =
        runTest {
            val repository = repository(StandardTestDispatcher(testScheduler))
            val shortcut = AppShortcut(app("Signal"), "compose", "New message")
            source.shortcuts = AppShortcuts.Available(listOf(shortcut))

            assertEquals(AppShortcuts.Available(listOf(shortcut)), repository.shortcuts(shortcut.app))
            assertEquals(LaunchResult.Launched, repository.launch(shortcut))
            assertEquals(listOf("shortcut compose"), source.calls)
        }

    @Test
    fun `a shortcut that is gone fails without throwing`() {
        val repository = AppRepository(source, preferences)
        source.failure = ActivityNotFoundException("gone")

        val result = repository.launch(AppShortcut(app("Signal"), "compose", "New message"))

        assertTrue(result is LaunchResult.Failed)
    }

    @Test
    fun `a source without shortcuts offers none and can't start one`() {
        val plain =
            object : AppSource by source {
                override fun shortcuts(app: LauncherApp) = super.shortcuts(app)

                override fun startShortcut(
                    shortcut: AppShortcut,
                    sourceBounds: Rect?,
                ) = super.startShortcut(shortcut, sourceBounds)
            }

        assertEquals(AppShortcuts.None, plain.shortcuts(app("Signal")))
        val result = AppRepository(plain, preferences).launch(AppShortcut(app("Signal"), "compose", "New message"))
        assertTrue(result is LaunchResult.Failed)
    }
}
