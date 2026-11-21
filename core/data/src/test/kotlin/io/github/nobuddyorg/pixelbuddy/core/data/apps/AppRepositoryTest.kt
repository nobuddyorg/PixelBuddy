package io.github.nobuddyorg.pixelbuddy.core.data.apps

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.graphics.Rect
import android.os.UserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertIs

@RunWith(AndroidJUnit4::class)
class AppRepositoryTest {
    private class FakeSource : AppSource {
        var apps = listOf<LauncherApp>()
        val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
        var failure: RuntimeException? = null
        val started = mutableListOf<ComponentName>()

        override fun loadApps() = apps

        override fun changes() = changes

        override fun start(
            component: ComponentName,
            user: UserHandle,
            sourceBounds: Rect?,
        ) {
            failure?.let { throw it }
            started += component
        }
    }

    private val source = FakeSource()
    private val hidden = MutableStateFlow(emptySet<AppKey>())

    private fun repository(dispatcher: TestDispatcher) =
        AppRepository(source, hidden, locale = { Locale.GERMANY }, dispatcher = dispatcher)

    @Test
    fun `apps are emitted sorted`() =
        runTest {
            source.apps = listOf(app("Zug"), app("Ärzte"), app("Bahn"))

            repository(StandardTestDispatcher(testScheduler)).apps.test {
                assertEquals(listOf("Ärzte", "Bahn", "Zug"), awaitItem().map { it.app.label })
            }
        }

    @Test
    fun `installs and removals are picked up without restart`() =
        runTest {
            source.apps = listOf(app("Bahn"))

            repository(StandardTestDispatcher(testScheduler)).apps.test {
                assertEquals(listOf("Bahn"), awaitItem().map { it.app.label })

                source.apps = listOf(app("Bahn"), app("Maps"))
                source.changes.emit(Unit)
                assertEquals(listOf("Bahn", "Maps"), awaitItem().map { it.app.label })

                source.apps = listOf(app("Maps"))
                source.changes.emit(Unit)
                assertEquals(listOf("Maps"), awaitItem().map { it.app.label })
            }
        }

    @Test
    fun `hidden apps are filtered out`() =
        runTest {
            val maps = app("Maps")
            source.apps = listOf(app("Bahn"), maps)

            repository(StandardTestDispatcher(testScheduler)).apps.test {
                assertEquals(2, awaitItem().size)

                hidden.value = setOf(maps.key)
                assertEquals(listOf("Bahn"), awaitItem().map { it.app.label })
            }
        }

    @Test
    fun `unchanged reloads are not emitted again`() =
        runTest {
            source.apps = listOf(app("Bahn"))

            repository(StandardTestDispatcher(testScheduler)).apps.test {
                awaitItem()
                source.changes.emit(Unit)
                expectNoEvents()
            }
        }

    @Test
    fun `launch starts the app's activity`() {
        val maps = app("Maps")

        val result = AppRepository(source, hidden).launch(maps)

        assertEquals(LaunchResult.Launched, result)
        assertEquals(listOf(maps.component), source.started)
    }

    @Test
    fun `launch of a removed or disabled app fails without crashing`() {
        listOf(
            ActivityNotFoundException("gone"),
            SecurityException("profile locked"),
            IllegalStateException("disabled"),
            IllegalArgumentException("unknown user"),
        ).forEach { error ->
            source.failure = error

            val result = AppRepository(source, hidden).launch(app("Maps"))

            assertIs<LaunchResult.Failed>(result)
            assertEquals(error, result.reason)
        }
    }
}
