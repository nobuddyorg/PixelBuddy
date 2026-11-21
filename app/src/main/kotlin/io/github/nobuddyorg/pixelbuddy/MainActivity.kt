package io.github.nobuddyorg.pixelbuddy

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import io.github.nobuddyorg.pixelbuddy.feature.home.HomeScreen

class MainActivity : ComponentActivity() {
    private val roleRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // Declining is a valid answer; the user can change the home app in system settings.
        }

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

        setContent {
            HomeScreen(onOpenSettings = ::openSettings)
        }

        if (savedInstanceState == null) {
            requestHomeRoleOnFirstStart()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isHomeIntent(intent)) {
            onHomePressed()
        }
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    /** Home pressed while already on home. Later issues reset the list, rail and search here. */
    private fun onHomePressed() = Unit

    private fun requestHomeRoleOnFirstStart() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_HOME_ROLE_ASKED, false)) return

        when (val action = HomeRole.nextAction(this)) {
            HomeRoleAction.None -> {
                // Already the default home app.
            }

            is HomeRoleAction.RequestRole -> {
                try {
                    roleRequest.launch(action.intent)
                } catch (_: ActivityNotFoundException) {
                    openDefaultAppsSettings()
                }
            }

            is HomeRoleAction.OpenDefaultAppsSettings -> {
                openDefaultAppsSettings()
            }
        }
        prefs.edit { putBoolean(KEY_HOME_ROLE_ASKED, true) }
    }

    private fun openDefaultAppsSettings() {
        val intents = listOf(HomeRole.defaultAppsSettingsIntent(), HomeRole.homeSettingsIntent())
        for (intent in intents) {
            try {
                startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
                // Try the next screen.
            }
        }
    }

    companion object {
        internal const val PREFS_NAME = "first_start"
        internal const val KEY_HOME_ROLE_ASKED = "home_role_asked"

        internal fun isHomeIntent(intent: Intent): Boolean =
            intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)
    }
}
