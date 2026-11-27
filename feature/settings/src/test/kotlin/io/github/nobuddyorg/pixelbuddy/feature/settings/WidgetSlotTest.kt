package io.github.nobuddyorg.pixelbuddy.feature.settings

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nobuddyorg.pixelbuddy.core.data.crash.CrashReportStore
import io.github.nobuddyorg.pixelbuddy.core.data.settings.Settings
import io.github.nobuddyorg.pixelbuddy.core.data.widgets.HomeWidgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Adding a widget through Android's screens (#26): bind permission and the widget's own setup. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-420dpi")
class WidgetSlotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val folder = TemporaryFolder()

    private val settings = MutableStateFlow(Settings())
    private val manager get() = AppWidgetManager.getInstance(compose.activity)

    private fun info(configure: ComponentName? = null) =
        AppWidgetProviderInfo().apply {
            provider = ComponentName("org.example.notes", "org.example.notes.Widget")
            this.configure = configure
            minHeight = 200
            ReflectionHelpers.setField(
                this,
                "providerInfo",
                ActivityInfo().apply {
                    packageName = "org.example.notes"
                    name = "org.example.notes.Widget"
                    nonLocalizedLabel = "Note"
                    applicationInfo =
                        ApplicationInfo().apply {
                            packageName = "org.example.notes"
                            uid = Process.myUid()
                        }
                },
            )
        }

    private fun addFrom(
        provider: AppWidgetProviderInfo,
        allowed: Boolean,
    ) {
        manager.install(provider)
        shadowOf(manager).setAllowedToBindAppWidgets(allowed)
        compose.setContent {
            SettingsRoute(
                store = CrashReportStore(folder.newFolder()),
                appVersion = "1.2.3",
                settings = settings,
                onUpdate = { change -> settings.value = change(settings.value) },
                homeWidgets = HomeWidgets(compose.activity),
                ioDispatcher = Dispatchers.Unconfined,
            )
        }
        compose.onNodeWithText("Widget").performScrollTo().performClick()
        compose.onNodeWithText("▶ Add a widget").performClick()
        compose.onNodeWithText("Note").performClick()
    }

    private fun answer(result: Int): String? {
        val request = shadowOf(compose.activity).nextStartedActivityForResult
        shadowOf(compose.activity).receiveResult(request.intent, result, null)
        compose.waitForIdle()
        return request.intent.action
    }

    @Test
    fun `when Android asks first, the widget waits for the answer`() {
        addFrom(info(), allowed = false)

        assertNull(settings.value.widget.id)
        assertEquals(AppWidgetManager.ACTION_APPWIDGET_BIND, answer(Activity.RESULT_CANCELED))

        compose.onNodeWithText("Android didn't allow the widget, so it wasn't added.").assertIsDisplayed()
        assertNull(settings.value.widget.id)
    }

    @Test
    fun `allowed by the user, the widget goes into the slot`() {
        addFrom(info(), allowed = false)

        assertEquals(AppWidgetManager.ACTION_APPWIDGET_BIND, answer(Activity.RESULT_OK))

        assertNotNull(settings.value.widget.id)
        compose.onNodeWithText("Note").assertIsDisplayed()
    }

    @Test
    fun `a widget with a setup screen goes into the slot once it is set up`() {
        addFrom(info(configure = ComponentName("org.example.notes", "org.example.notes.Setup")), allowed = true)
        assertNull(settings.value.widget.id)

        assertEquals(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE, answer(Activity.RESULT_OK))

        assertNotNull(settings.value.widget.id)
    }

    @Test
    fun `a cancelled setup leaves the slot empty`() {
        addFrom(info(configure = ComponentName("org.example.notes", "org.example.notes.Setup")), allowed = true)

        assertEquals(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE, answer(Activity.RESULT_CANCELED))

        assertNull(settings.value.widget.id)
        compose.onNodeWithText("The widget's setup was cancelled, so it wasn't added.").assertIsDisplayed()
    }
}
