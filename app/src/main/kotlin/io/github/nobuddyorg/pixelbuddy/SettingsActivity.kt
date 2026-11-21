package io.github.nobuddyorg.pixelbuddy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.nobuddyorg.pixelbuddy.feature.settings.SettingsRoute

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = appContainer
        setContent {
            SettingsRoute(
                store = container.crashReports,
                appVersion = container.deviceInfo.appVersionName,
            )
        }
    }
}
