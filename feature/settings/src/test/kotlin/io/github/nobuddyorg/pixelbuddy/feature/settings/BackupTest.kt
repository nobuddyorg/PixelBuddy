package io.github.nobuddyorg.pixelbuddy.feature.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.nobuddyorg.pixelbuddy.core.data.apps.AppKey
import io.github.nobuddyorg.pixelbuddy.core.data.crash.CrashReportStore
import io.github.nobuddyorg.pixelbuddy.core.data.icons.CustomIconStore
import io.github.nobuddyorg.pixelbuddy.core.data.icons.IconLayer
import io.github.nobuddyorg.pixelbuddy.core.data.settings.Accent
import io.github.nobuddyorg.pixelbuddy.core.data.settings.Appearance
import io.github.nobuddyorg.pixelbuddy.core.data.settings.IconChoice
import io.github.nobuddyorg.pixelbuddy.core.data.settings.Settings
import io.github.nobuddyorg.pixelbuddy.core.data.settings.SetupStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-420dpi")
class BackupTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val folder = TemporaryFolder()

    private val maps = AppKey("org.maps", "org.maps.Main", 0)
    private val bahn = AppKey("org.bahn", "org.bahn.Main", 0)

    private val oldPhone =
        Settings(
            favorites = listOf(maps, bahn),
            hidden = setOf(AppKey("org.bloat", "org.bloat.Main", 0)),
            appearance = Appearance(accent = Accent.Cyan),
            setup = SetupStep.Done,
        ).withIconOverride(bahn, IconChoice(layer = IconLayer.Foreground))

    /** The phone the settings route runs on; changing [phone] shows another one. */
    private var phone by mutableIntStateOf(0)
    private val phones = listOf(MutableStateFlow(oldPhone), MutableStateFlow(Settings(setup = SetupStep.Done)))
    private var iconChanges = 0

    private fun show() {
        compose.setContent {
            key(phone) {
                val settings = phones[phone]
                SettingsRoute(
                    store = CrashReportStore(folder.newFolder()),
                    appVersion = "1.2.3",
                    settings = settings,
                    onUpdate = { change -> settings.value = change(settings.value) },
                    customIcons = CustomIconStore(folder.newFolder()),
                    onIconsChanged = { iconChanges++ },
                    ioDispatcher = Dispatchers.Unconfined,
                )
            }
        }
        openBackup()
    }

    private fun openBackup() {
        compose.onNodeWithText("Back up & move").performScrollTo().performClick()
    }

    /** Answers the document picker the last tap opened with [file]. */
    private fun answerPicker(file: File) {
        val activity = shadowOf(compose.activity)
        val request = activity.nextStartedActivityForResult
        activity.receiveResult(request.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
        compose.waitForIdle()
    }

    private fun export(): File {
        val file = File(folder.root, "pixelbuddy-settings.zip")
        compose.onNodeWithText("▶ Export settings").performClick()
        val request = shadowOf(compose.activity).nextStartedActivityForResult
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, request.intent.action)
        shadowOf(
            compose.activity,
        ).receiveResult(request.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
        compose.waitForIdle()
        return file
    }

    @Test
    fun `export on one phone and import on another restores the same home`() {
        show()
        val file = export()
        compose.onNodeWithText("Settings exported").assertIsDisplayed()
        assertTrue(file.length() > 0)

        phone = 1
        compose.waitForIdle()
        openBackup()
        compose.onNodeWithText("▶ Import settings").performClick()
        answerPicker(file)

        compose.onNodeWithText("Replace your settings?").assertIsDisplayed()
        compose
            .onNodeWithText(
                "favorites: 2 · hidden: 1 · own icons: 1",
                substring = true,
            ).assertIsDisplayed()
        // Nothing changes before the answer.
        assertEquals(Settings(setup = SetupStep.Done), phones[1].value)
        compose.onNodeWithText("▶ Replace").performClick()

        compose.onNodeWithText("Settings imported").assertIsDisplayed()
        assertEquals(oldPhone.copy(waitingApps = oldPhone.appKeys), phones[1].value)
        assertEquals(1, iconChanges)
    }

    @Test
    fun `cancelling an import keeps the settings`() {
        show()
        val file = export()
        phones[0].value = Settings(setup = SetupStep.Done)

        compose.onNodeWithText("▶ Import settings").performClick()
        answerPicker(file)
        compose.onNodeWithText("▶ Cancel").performClick()

        assertEquals(Settings(setup = SetupStep.Done), phones[0].value)
        compose.onNodeWithText("Replace your settings?").assertDoesNotExist()
    }

    @Test
    fun `a file that isn't PixelBuddy settings is explained`() {
        show()
        val notSettings = folder.newFile("holiday.zip").apply { writeText("just text") }

        compose.onNodeWithText("▶ Import settings").performClick()
        answerPicker(notSettings)

        compose.onNodeWithText("That isn't a PixelBuddy settings file.").assertIsDisplayed()
        assertEquals(oldPhone, phones[0].value)
    }

    @Test
    fun `screenshot import question`() {
        show()
        val file = export()
        compose.onNodeWithText("▶ Import settings").performClick()
        answerPicker(file)
        compose.onRoot().captureRoboImage("Backup_import.png")
    }
}
