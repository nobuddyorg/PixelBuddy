package io.github.nobuddyorg.pixelbuddy

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.nobuddyorg.pixelbuddy.core.data.settings.SetupStep
import io.github.nobuddyorg.pixelbuddy.feature.home.HomeRoute
import io.github.nobuddyorg.pixelbuddy.feature.settings.SetupRoute
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** Counts Home presses while home is shown; each one resets home to the favorites. */
    private var homePresses by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Back on home does nothing, like on any launcher.
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = Unit
            },
        )

        val container = appContainer
        val homeApp = RoleHomeAppAccess(this)
        val launchableApps = container.appRepository.home.map { home -> home.apps.map { it.app } }
        setContent {
            StoredAppearanceTheme(container.settings, container.weatherRepository.report) { appearance ->
                val setup by container.settings.setupStep.collectAsStateWithLifecycle(initialValue = null)
                when (setup) {
                    // Still reading: the theme's plain background shows.
                    null -> {}

                    SetupStep.Done -> {
                        HomeRoute(
                            repository = container.appRepository,
                            settings = container.settings,
                            icons = container.iconRepository,
                            onOpenSettings = ::openSettings,
                            resetKey = homePresses,
                            weather = container.weatherRepository,
                            appearance = appearance,
                            onEditIcon = { app -> openSettings(editIconOf = app.key.id) },
                            screenLock = LockService,
                            appNotifications = NotificationService,
                            mediaPlayback = container.mediaPlayback,
                            calendarSource = container.calendar,
                            homeWidgets = container.widgets,
                        )
                    }

                    else -> {
                        SetupRoute(
                            settings = container.settings.settings,
                            onUpdate = { change -> lifecycleScope.launch { container.settings.update(change) } },
                            homeApp = homeApp,
                            apps = launchableApps,
                            searchPlaces = container::searchPlaces,
                        )
                    }
                }
            }
        }
    }

    // The widget updates only while home is visible.
    override fun onStart() {
        super.onStart()
        appContainer.widgets.startListening()
    }

    override fun onStop() {
        appContainer.widgets.stopListening()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isHomeIntent(intent)) {
            onHomePressed()
        }
    }

    private fun openSettings() = openSettings(editIconOf = null)

    private fun openSettings(editIconOf: String?) {
        startActivity(
            Intent(this, SettingsActivity::class.java).apply {
                if (editIconOf != null) putExtra(SettingsActivity.EXTRA_EDIT_ICON, editIconOf)
            },
        )
    }

    /** Home pressed (also when coming back from an app): show the favorites again. */
    private fun onHomePressed() {
        homePresses++
    }

    companion object {
        internal fun isHomeIntent(intent: Intent): Boolean =
            intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)
    }
}
