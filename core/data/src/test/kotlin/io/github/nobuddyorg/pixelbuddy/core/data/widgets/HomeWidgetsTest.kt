package io.github.nobuddyorg.pixelbuddy.core.data.widgets

import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.util.ReflectionHelpers
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class HomeWidgetsTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val manager = AppWidgetManager.getInstance(app)
    private val widgets = HomeWidgets(app)
    private val density = app.resources.displayMetrics.density

    private fun provider(
        name: String,
        label: String,
        configure: ComponentName? = null,
        minHeightDp: Int = 100,
        resize: Int = AppWidgetProviderInfo.RESIZE_NONE,
        features: Int = 0,
    ): AppWidgetProviderInfo =
        AppWidgetProviderInfo().apply {
            provider = ComponentName("org.example.$name", "org.example.$name.Widget")
            this.configure = configure
            minHeight = (minHeightDp * density).toInt()
            minResizeHeight = (60 * density).toInt()
            maxResizeHeight = (300 * density).toInt()
            resizeMode = resize
            widgetFeatures = features
            // The label and profile come from the hidden provider info, as on a phone.
            ReflectionHelpers.setField(
                this,
                "providerInfo",
                ActivityInfo().apply {
                    packageName = "org.example.$name"
                    this.name = "org.example.$name.Widget"
                    nonLocalizedLabel = label
                    applicationInfo =
                        ApplicationInfo().apply {
                            packageName = "org.example.$name"
                            uid = Process.myUid()
                        }
                },
            )
        }

    @Test
    fun `providers are listed by app and name`() {
        shadowOf(manager).addInstalledProvidersForProfile(Process.myUserHandle(), provider("notes", "Notes"))
        shadowOf(manager).addInstalledProvidersForProfile(Process.myUserHandle(), provider("clock", "World clock"))

        assertEquals(listOf("World clock", "Notes"), widgets.providers().map { it.label })
    }

    @Test
    fun `a widget binds right away when PixelBuddy may`() {
        val info = provider("clock", "Clock")
        shadowOf(manager).addInstalledProvidersForProfile(Process.myUserHandle(), info)
        shadowOf(manager).setAllowedToBindAppWidgets(true)

        val binding = widgets.add(widgets.providers().single())

        assertIs<WidgetBinding.Bound>(binding)
    }

    @Test
    fun `otherwise the user is asked to allow it`() {
        shadowOf(manager).addInstalledProvidersForProfile(Process.myUserHandle(), provider("clock", "Clock"))
        shadowOf(manager).setAllowedToBindAppWidgets(false)

        val binding = assertIs<WidgetBinding.NeedsPermission>(widgets.add(widgets.providers().single()))

        assertEquals(AppWidgetManager.ACTION_APPWIDGET_BIND, binding.intent.action)
        assertEquals(binding.id, binding.intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
        assertEquals(
            ComponentName("org.example.clock", "org.example.clock.Widget"),
            binding.intent.getParcelableExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, ComponentName::class.java),
        )
        assertEquals(
            Process.myUserHandle(),
            binding.intent.getParcelableExtra(
                AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE,
                android.os.UserHandle::class.java,
            ),
        )
    }

    @Test
    fun `a widget with a setup screen is set up first, unless setup is optional`() {
        val setup = ComponentName("org.example.notes", "org.example.notes.Setup")
        shadowOf(manager).addBoundWidget(1, provider("notes", "Notes", configure = setup))
        shadowOf(manager).addBoundWidget(
            2,
            provider(
                "notes",
                "Notes",
                configure = setup,
                features = AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL,
            ),
        )
        shadowOf(manager).addBoundWidget(3, provider("clock", "Clock"))

        val intent = assertNotNull(widgets.configureIntent(1))
        assertEquals(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE, intent.action)
        assertEquals(setup, intent.component)
        assertEquals(1, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
        assertNull(widgets.configureIntent(2))
        assertNull(widgets.configureIntent(3))
    }

    @Test
    fun `heights come from the widget, resizable ones within their limits`() {
        shadowOf(manager).addBoundWidget(1, provider("clock", "Clock", minHeightDp = 100))
        shadowOf(
            manager,
        ).addBoundWidget(
            2,
            provider("notes", "Notes", minHeightDp = 100, resize = AppWidgetProviderInfo.RESIZE_VERTICAL),
        )

        assertEquals(WidgetHeights(100, 100, 100), widgets.heights(1))
        val resizable = assertNotNull(widgets.heights(2))
        assertEquals(100, resizable.default)
        assertEquals(60, resizable.min)
        assertEquals(300, resizable.max)
        assertTrue(resizable.resizable)
    }

    @Test
    fun `a widget whose app is gone has no name, height or view`() {
        assertNull(widgets.label(99))
        assertNull(widgets.heights(99))
        assertNull(widgets.createView(app, 99))
    }

    @Test
    fun `the slot shows the widget's name and view`() {
        shadowOf(manager).addBoundWidget(1, provider("clock", "Clock"))

        assertEquals("Clock", widgets.label(1))
        assertNotNull(widgets.createView(app, 1))
        widgets.startListening()
        widgets.stopListening()
        widgets.remove(1)
    }
}
