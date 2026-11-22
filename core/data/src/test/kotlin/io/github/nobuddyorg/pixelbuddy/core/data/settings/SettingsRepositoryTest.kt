package io.github.nobuddyorg.pixelbuddy.core.data.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.github.nobuddyorg.pixelbuddy.core.data.apps.AppKey
import io.github.nobuddyorg.pixelbuddy.core.data.icons.IconLayer
import io.github.nobuddyorg.pixelbuddy.core.pixelengine.IconPreset
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class SettingsRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val file: File by lazy { File(folder.root, SettingsRepository.FILE_PATH) }

    private val maps = key("maps")
    private val bahn = key("bahn")
    private val zug = key("zug")

    private fun key(name: String) = AppKey("pkg.$name", "pkg.$name.Main", 0)

    /** A store that lives until [job] is cancelled, like one per app process. */
    private fun TestScope.open(job: Job = Job()): SettingsRepository =
        SettingsRepository.create(file, StandardTestDispatcher(testScheduler) + job)

    @Test
    fun `a new install starts with defaults`() =
        runTest {
            val job = Job()

            assertEquals(Settings(), open(job).settings.first())
            job.cancel()
        }

    @Test
    fun `favorites are added at the end, once`() =
        runTest {
            val job = Job()
            val settings = open(job)

            settings.favorites.test {
                assertEquals(emptyList(), awaitItem())
                settings.addFavorite(maps)
                assertEquals(listOf(maps), awaitItem())
                settings.addFavorite(bahn)
                assertEquals(listOf(maps, bahn), awaitItem())
                settings.addFavorite(maps)
                settings.removeFavorite(maps)
                assertEquals(listOf(bahn), awaitItem())
            }
            job.cancel()
        }

    @Test
    fun `favorites can be reordered`() =
        runTest {
            val job = Job()
            val settings = open(job)
            listOf(maps, bahn, zug).forEach { settings.addFavorite(it) }

            settings.moveFavorite(zug, -1)
            assertEquals(listOf(maps, zug, bahn), settings.favorites.first())
            settings.moveFavorite(maps, 5)
            assertEquals(listOf(zug, bahn, maps), settings.favorites.first())
            settings.moveFavorite(zug, -1)
            settings.moveFavorite(key("unknown"), 1)
            assertEquals(listOf(zug, bahn, maps), settings.favorites.first())
            job.cancel()
        }

    @Test
    fun `hiding removes the favorite and unhiding brings the app back`() =
        runTest {
            val job = Job()
            val settings = open(job)
            settings.addFavorite(maps)

            settings.hidden.test {
                assertEquals(emptySet(), awaitItem())
                settings.hide(maps)
                assertEquals(setOf(maps), awaitItem())
                assertEquals(emptyList(), settings.favorites.first())
                settings.unhide(maps)
                assertEquals(emptySet(), awaitItem())
            }
            job.cancel()
        }

    @Test
    fun `forgotten apps disappear everywhere`() =
        runTest {
            val job = Job()
            val settings = open(job)
            settings.addFavorite(maps)
            settings.addFavorite(zug)
            settings.hide(bahn)
            settings.update {
                it.copy(
                    iconOverrides = listOf(IconOverride(maps, IconStyleSetting.of(IconPreset.Mono))),
                )
            }
            assertEquals(setOf(maps, zug, bahn), settings.storedApps())

            settings.forget(setOf(maps, bahn))
            settings.forget(emptySet())

            val after = settings.settings.first()
            assertEquals(listOf(zug), after.favorites)
            assertEquals(emptySet(), after.hidden)
            assertEquals(emptyList(), after.iconOverrides)
            assertEquals(null, after.weather.tapApp)
            job.cancel()
        }

    @Test
    fun `appearance and gestures are observed on their own`() =
        runTest {
            val job = Job()
            val settings = open(job)

            settings.appearance.test {
                assertEquals(Appearance(), awaitItem())
                settings.addFavorite(maps)
                settings.update { it.copy(appearance = it.appearance.copy(accent = Accent.Cyan, sky = Sky.Ocean)) }
                assertEquals(Appearance(accent = Accent.Cyan, sky = Sky.Ocean), awaitItem())
            }
            settings.gestures.test {
                assertEquals(Gestures(), awaitItem())
                settings.update { it.copy(gestures = Gestures(swipeUp = GestureAction.Nothing)) }
                assertEquals(GestureAction.Nothing, awaitItem().swipeUp)
            }
            job.cancel()
        }

    @Test
    fun `the setup starts at the home app and its steps go both ways`() =
        runTest {
            val job = Job()
            val settings = open(job)

            settings.setupStep.test {
                assertEquals(SetupStep.HomeApp, awaitItem())
                settings.update { it.copy(setup = it.setup.next) }
                assertEquals(SetupStep.Favorites, awaitItem())
            }
            job.cancel()
            assertEquals(SetupStep.Done, SetupStep.Done.next)
            assertEquals(SetupStep.HomeApp, SetupStep.HomeApp.previous)
            assertEquals(SetupStep.Favorites, SetupStep.Weather.previous)
        }

    @Test
    fun `the long press opens the settings while no swipe does`() {
        assertEquals(GestureAction.Settings, Gestures(longPress = GestureAction.Search).effectiveLongPress)
        assertEquals(
            GestureAction.Search,
            Gestures(swipeDown = GestureAction.Settings, longPress = GestureAction.Search).effectiveLongPress,
        )
        assertEquals(
            GestureAction.Nothing,
            Gestures(swipeUp = GestureAction.Settings, longPress = GestureAction.Nothing).effectiveLongPress,
        )
    }

    @Test
    fun `every sky stays readable and has four stops`() {
        Sky.entries.forEach { sky ->
            assertEquals(4, sky.stops.size, sky.name)
            // The bottom is no brighter than the default orange, so light text stays readable.
            assertTrue(luminance(sky.stops.last()) <= luminance(Sky.Dusk.stops.last()) + 1e-6, sky.name)
        }
    }

    private fun luminance(argb: Int): Double {
        fun linear(channel: Int): Double {
            val c = channel / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * linear(argb shr 16 and 0xFF) + 0.7152 * linear(argb shr 8 and 0xFF) +
            0.0722 * linear(argb and 0xFF)
    }

    @Test
    fun `imported apps wait for their install instead of being forgotten`() =
        runTest {
            val job = Job()
            val settings = open(job)
            settings.update { it.copy(favorites = listOf(maps, bahn), waitingApps = setOf(maps, bahn)) }

            assertEquals(emptySet(), settings.storedApps())
            settings.installed(setOf(maps))
            assertEquals(setOf(maps), settings.storedApps())
            assertEquals(setOf(bahn), settings.settings.first().waitingApps)
            // Nothing waiting: no write needed.
            settings.installed(setOf(zug))

            settings.forget(setOf(maps))
            assertEquals(listOf(bahn), settings.favorites.first())
            job.cancel()
        }

    @Test
    fun `weather settings are updated on their own`() =
        runTest {
            val job = Job()
            val settings = open(job)

            settings.weather.test {
                assertEquals(WeatherSettings(), awaitItem())
                settings.updateWeather { it.copy(enabled = true, approximateLocation = true) }
                val on = awaitItem()
                assertTrue(on.enabled && on.hasLocation)
            }
            job.cancel()
        }

    @Test
    fun `icon styles combine the global style and overrides`() =
        runTest {
            val job = Job()
            val settings = open(job)

            settings.update {
                it.copy(
                    iconStyle = IconStyleSetting.of(IconPreset.Neon),
                    iconOverrides = listOf(IconOverride(maps, IconStyleSetting.of(IconPreset.Mono))),
                )
            }

            val styles = settings.iconStyles.first()
            assertEquals(IconPreset.Mono.style, styles.styleFor(maps))
            assertEquals(IconPreset.Neon.style, styles.styleFor(bahn))
            job.cancel()
        }

    @Test
    fun `an app's own icon wins over the global style and only for that app`() {
        val picture = "0".repeat(64)
        val settings =
            Settings(iconStyle = IconStyleSetting.of(IconPreset.Neon))
                .withIconOverride(maps, IconChoice(IconStyleSetting.of(IconPreset.Mono), IconLayer.Foreground))
                .withIconOverride(maps, IconChoice(IconStyleSetting.of(IconPreset.Pocket), image = picture))
        val styles = IconStyles(settings.iconStyle.style, settings.iconOverrides.associate { it.app to it.look })

        // Setting it again replaced the first override.
        assertEquals(1, settings.iconOverrides.size)
        assertEquals(IconLook(IconPreset.Pocket.style, image = picture), styles.lookFor(maps))
        assertEquals(IconLook(IconPreset.Neon.style), styles.lookFor(bahn))
        assertEquals(setOf(picture), settings.iconImages)
        assertEquals(settings.iconOverrideFor(maps)?.choice?.image, picture)

        val reset = settings.withoutIconOverride(maps)
        assertEquals(null, reset.iconOverrideFor(maps))
        assertEquals(emptySet(), reset.iconImages)
    }

    @Test
    fun `a picture name that isn't one of ours is ignored`() {
        val override = IconOverride(maps, image = "../../shared_prefs/x")

        assertEquals(null, override.look.image)
    }

    @Test
    fun `settings survive a restart`() =
        runTest {
            val first = Job()
            open(first).apply {
                addFavorite(zug)
                addFavorite(maps)
                hide(bahn)
            }
            first.cancel()
            first.join()

            val second = Job()
            val settings = open(second).settings.first()

            assertEquals(listOf(zug, maps), settings.favorites)
            assertEquals(setOf(bahn), settings.hidden)
            second.cancel()
        }

    @Test
    fun `a corrupt file is replaced with defaults and kept for inspection`() =
        runTest {
            file.parentFile!!.mkdirs()
            file.writeText("{\"favorites\": [garbage")
            val job = Job()
            val settings = open(job)

            assertEquals(Settings(), settings.settings.first())
            settings.addFavorite(maps)
            assertEquals(listOf(maps), settings.favorites.first())
            assertEquals("{\"favorites\": [garbage", File(file.path + ".corrupt").readText())
            assertTrue(file.readText().contains("pkg.maps"))
            job.cancel()
        }
}
