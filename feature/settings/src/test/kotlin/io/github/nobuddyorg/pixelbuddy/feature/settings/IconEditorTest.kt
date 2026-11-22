package io.github.nobuddyorg.pixelbuddy.feature.settings

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.net.Uri
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.core.graphics.createBitmap
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.nobuddyorg.pixelbuddy.core.data.apps.AppKey
import io.github.nobuddyorg.pixelbuddy.core.data.apps.LauncherApp
import io.github.nobuddyorg.pixelbuddy.core.data.crash.CrashReportStore
import io.github.nobuddyorg.pixelbuddy.core.data.icons.CustomIconStore
import io.github.nobuddyorg.pixelbuddy.core.data.icons.IconCache
import io.github.nobuddyorg.pixelbuddy.core.data.icons.IconLayer
import io.github.nobuddyorg.pixelbuddy.core.data.icons.IconLoader
import io.github.nobuddyorg.pixelbuddy.core.data.icons.IconRepository
import io.github.nobuddyorg.pixelbuddy.core.data.settings.IconChoice
import io.github.nobuddyorg.pixelbuddy.core.data.settings.IconStyleSetting
import io.github.nobuddyorg.pixelbuddy.core.data.settings.PaletteChoice
import io.github.nobuddyorg.pixelbuddy.core.data.settings.Settings
import io.github.nobuddyorg.pixelbuddy.core.pixelengine.IconPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-420dpi")
class IconEditorTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val folder = TemporaryFolder()

    /** Synthetic icons (no brand icons in tests): a gradient plate with a round glyph, so styles differ visibly. */
    private class GradientIcons : IconLoader {
        var loads = 0

        private val colors =
            listOf(
                0xFFE0413A to 0xFFF7D154,
                0xFF1D4E89 to 0xFF3FA7D6,
                0xFF2E6B3A to 0xFF6CC24A,
                0xFF7D4BA8 to 0xFFE58DB5,
                0xFF8A5A3B to 0xFFF29541,
            ).map { (a, b) -> intArrayOf(a.toInt(), b.toInt()) }

        override fun load(
            app: LauncherApp,
            densityDpi: Int,
        ): Drawable {
            loads++
            val pair = colors[Math.floorMod(app.label.hashCode(), colors.size)]
            val plate = GradientDrawable(GradientDrawable.Orientation.TL_BR, pair)
            val glyph =
                GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    gradientRadius = 60f
                    colors = intArrayOf(0xFFFFFFFF.toInt(), pair[0])
                }
            return AdaptiveIconDrawable(plate, InsetDrawable(glyph, 0.3f))
        }

        override fun version(app: LauncherApp) = 1L
    }

    private val loader = GradientIcons()
    private val icons by lazy { IconRepository(loader, IconCache(folder.newFolder("icons")), Dispatchers.Unconfined) }
    private val apps = listOf("Signal", "Maps", "Bahn", "Notes", "Music", "Camera").map(::launcherApp)
    private val settings = MutableStateFlow(Settings(favorites = listOf(apps[1].key)))
    private var changes = 0
    private val images by lazy { CustomIconStore(folder.newFolder("images")) }
    private var done = 0

    private fun launcherApp(label: String): LauncherApp {
        val pkg = "pkg." + label.lowercase()
        return LauncherApp(
            key = AppKey(pkg, "$pkg.Main", 0),
            label = label,
            component = ComponentName(pkg, "$pkg.Main"),
            user = Process.myUserHandle(),
            isMainProfile = true,
        )
    }

    private val favoriteApps = settings.map { s -> s.favorites.map { key -> apps.single { it.key == key } } }

    private fun showSettings(editIconOf: String? = null) {
        compose.setContent {
            SettingsRoute(
                store = CrashReportStore(folder.newFolder("crashes")),
                appVersion = "1.2.3",
                settings = settings,
                onUpdate = { change -> settings.value = change(settings.value) },
                favoriteApps = favoriteApps,
                apps = flowOf(apps),
                icons = icons,
                customIcons = images,
                onIconsChanged = { changes++ },
                editIconOf = editIconOf,
                onDone = { done++ },
                ioDispatcher = Dispatchers.Unconfined,
            )
        }
    }

    private fun showEditor() {
        showSettings()
        compose.onNodeWithText("App icons").performClick()
    }

    private fun showAppEditor(label: String) {
        showSettings()
        compose.onNodeWithText("Icons for single apps").performScrollTo().performClick()
        compose.onNodeWithText(label).performScrollTo().performClick()
    }

    private val maps get() = apps.single { it.label == "Maps" }

    private val stored get() = settings.value.iconStyle

    @Test
    fun `a control updates the preview but nothing is stored before Apply`() {
        showEditor()
        val loadsBefore = loader.loads
        compose.onNodeWithText("▶ Apply").assertIsNotEnabled()

        compose.onNodeWithText("12").performScrollTo().performClick()

        // The preview drew sources for the new grid, but the stored style is unchanged.
        assertTrue(loader.loads > loadsBefore)
        assertEquals(IconStyleSetting(), stored)
        compose.onNodeWithText("Custom (based on 16-bit)").assertIsDisplayed()

        compose.onNodeWithText("▶ Apply").performClick()
        assertEquals(12, stored.style.grid)
        assertEquals("SixteenBit", stored.preset)
        assertEquals(1, changes)
        compose.onNodeWithText("▶ Apply").assertIsNotEnabled()
    }

    @Test
    fun `a preset card chooses that preset`() {
        showEditor()

        compose.onNodeWithContentDescription("8-bit").performScrollTo().performClick()
        compose.onNodeWithContentDescription("8-bit").assertIsSelected()
        compose.onNodeWithText("▶ Apply").performClick()

        assertEquals(IconStyleSetting.of(IconPreset.EightBit), stored)
    }

    @Test
    fun `sliders have values TalkBack reads and sets`() {
        showEditor()

        compose
            .onNodeWithContentDescription(
                "Colours",
            ).performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) {
                it(8f)
            }
        compose.onNodeWithContentDescription("Palette strength").performScrollTo().performSemanticsAction(
            SemanticsActions.SetProgress,
        ) { it(5f) }
        compose.onNodeWithText("50 %").assertIsDisplayed()
        compose.onNodeWithText("▶ Apply").performClick()

        assertEquals(PaletteChoice.Adaptive.toPalette(8), stored.style.palette)
        assertEquals(0.5f, stored.style.intensity)
    }

    @Test
    fun `the colour count only shows for palettes that have one`() {
        showEditor()

        compose.onNodeWithText("Pocket").performScrollTo().performClick()

        compose.onNodeWithContentDescription("Colours").assertDoesNotExist()
        compose.onNodeWithText("Mono").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Colours").assertExists()
    }

    @Test
    fun `reset goes back to the stored style`() {
        settings.value = settings.value.copy(iconStyle = IconStyleSetting.of(IconPreset.Pocket))
        showEditor()

        compose.onNodeWithText("Outline").performScrollTo().performClick()
        compose.onNodeWithText("Custom (based on Pocket)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("▶ Reset").performClick()

        compose.onNodeWithText("Custom (based on Pocket)").assertDoesNotExist()
        compose.onNodeWithText("▶ Apply").assertIsNotEnabled()
        assertEquals(IconStyleSetting.of(IconPreset.Pocket), stored)
    }

    @Test
    fun `leaving with unsaved changes asks to discard them`() {
        showEditor()
        compose.onNodeWithText("Dithering").performScrollTo().performClick()

        compose.onNodeWithText("▶ Back").performScrollTo().performClick()
        compose.onNodeWithText("Discard your changes?").assertIsDisplayed()
        compose.onNodeWithText("▶ Keep editing").performClick()
        compose.onNodeWithText("Discard your changes?").assertDoesNotExist()

        compose.waitForIdle()
        Espresso.pressBack()
        compose.onNodeWithText("▶ Discard").performClick()

        compose.onNodeWithText("Appearance & themes").assertIsDisplayed()
        assertEquals(IconStyleSetting(), stored)
        assertEquals(0, changes)
    }

    @Test
    fun `leaving without changes needs no question`() {
        showEditor()

        compose.onNodeWithText("▶ Back").performScrollTo().performClick()

        compose.onNodeWithText("Appearance & themes").assertIsDisplayed()
    }

    @Test
    fun `the originals can be shown for comparison`() {
        showEditor()
        compose.onNodeWithContentDescription("Preview of your icons in this style").assertIsDisplayed()

        compose.onNodeWithText("Show originals").performScrollTo().performClick()

        compose.onNodeWithContentDescription("Your icons as the apps draw them").assertIsDisplayed()
    }

    @Test
    fun `the main page names the stored style`() {
        settings.value =
            settings.value.copy(iconStyle = IconStyleSetting.of(IconPreset.Neon).edited { it.copy(grid = 32) })
        compose.setContent {
            SettingsRoute(
                store = CrashReportStore(folder.newFolder("crashes")),
                appVersion = "1.2.3",
                settings = settings,
                onUpdate = {},
                ioDispatcher = Dispatchers.Unconfined,
            )
        }

        compose.onNodeWithText("Custom (based on Neon)").assertIsDisplayed()
    }

    // One app's own icon (#14)

    @Test
    fun `an app gets its own icon and can go back to the style of all`() {
        showAppEditor("Maps")
        compose.onNodeWithText("Icon of Maps").assertIsDisplayed()

        compose.onNodeWithText("Foreground only").performScrollTo().performClick()
        compose.onNodeWithText("Neon").performScrollTo().performClick()
        compose.onNodeWithText("▶ Apply").performClick()

        val override = settings.value.iconOverrideFor(maps.key)!!
        assertEquals(IconLayer.Foreground, override.layer)
        assertEquals(PaletteChoice.Neon, override.style.custom?.palette)
        // Only Maps changed.
        assertEquals(IconStyleSetting(), stored)
        assertEquals(1, settings.value.iconOverrides.size)
        assertEquals(1, changes)

        compose.onNodeWithText("▶ Like all icons").performClick()
        assertNull(settings.value.iconOverrideFor(maps.key))
        assertEquals(2, changes)
        compose.onNodeWithText("Icons for single apps").assertIsDisplayed()
    }

    @Test
    fun `apps with their own icon are listed first`() {
        settings.value = settings.value.withIconOverride(apps[4].key, IconChoice(IconStyleSetting.of(IconPreset.Mono)))
        showSettings()
        compose.onNodeWithText("Apps with their own icon: 1").assertIsDisplayed()

        compose.onNodeWithText("Icons for single apps").performScrollTo().performClick()

        compose.onNodeWithText("Own icon · Mono").assertIsDisplayed()
        compose.onAllNodesWithText("Like all icons")[0].assertIsDisplayed()
    }

    private fun pickPicture(file: java.io.File) {
        val activity = shadowOf(compose.activity)
        compose.onNodeWithText("Own picture").performScrollTo().performClick()
        val request = activity.nextStartedActivityForResult
        activity.receiveResult(request.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
        compose.waitForIdle()
    }

    private fun pictureFile(
        name: String,
        bitmap: Bitmap,
    ): java.io.File =
        folder.newFile(name).also { file ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }

    @Test
    fun `a picture from the photo picker becomes the app's icon`() {
        showAppEditor("Maps")
        val picture = createBitmap(200, 200).apply { eraseColor(0xFF39FF14.toInt()) }

        pickPicture(pictureFile("picture.png", picture))
        compose.onNodeWithText("▶ Choose another picture").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("▶ Apply").performClick()

        val image = settings.value.iconOverrideFor(maps.key)!!.image!!
        assertTrue(CustomIconStore.isValidName(image))
        assertEquals(32, images.load(image, 32)!!.width)
    }

    @Test
    fun `a file that isn't a picture is explained`() {
        showAppEditor("Maps")
        val corrupt = folder.newFile("broken.png").apply { writeText("not a picture") }

        pickPicture(corrupt)

        compose.onNodeWithText("That file couldn't be read as a picture.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("▶ Apply").assertIsNotEnabled()
    }

    @Test
    fun `the editor opened from home's menu ends the settings when left`() {
        showSettings(editIconOf = maps.key.id)

        compose.onNodeWithText("Icon of Maps").assertIsDisplayed()
        compose.onNodeWithText("▶ Back").performScrollTo().performClick()

        assertEquals(1, done)
    }

    @Test
    fun `screenshot one app's icon`() {
        settings.value = settings.value.withIconOverride(maps.key, IconChoice(IconStyleSetting.of(IconPreset.Neon)))
        showAppEditor("Maps")
        compose.onRoot().captureRoboImage("IconEditor_app.png")
    }

    private fun screenshot(
        preset: IconPreset,
        name: String,
    ) {
        settings.value = settings.value.copy(iconStyle = IconStyleSetting.of(preset))
        showEditor()
        compose.onRoot().captureRoboImage("IconEditor_$name.png")
    }

    @Test
    fun `screenshot 8-bit`() = screenshot(IconPreset.EightBit, "8bit")

    @Test
    fun `screenshot 16-bit`() = screenshot(IconPreset.SixteenBit, "16bit")

    @Test
    fun `screenshot Pocket`() = screenshot(IconPreset.Pocket, "pocket")

    @Test
    fun `screenshot Neon`() = screenshot(IconPreset.Neon, "neon")

    @Test
    fun `screenshot Mono`() = screenshot(IconPreset.Mono, "mono")

    @Test
    fun `screenshot custom with the discard question`() {
        showEditor()
        compose.onNodeWithText("32").performScrollTo().performClick()
        compose.onNodeWithText("▶ Back").performScrollTo().performClick()
        compose.onRoot().captureRoboImage("IconEditor_discard.png")
    }
}
