package io.github.nobuddyorg.pixelbuddy.core.data.apps

import android.content.ComponentName
import android.os.Process
import kotlinx.coroutines.flow.MutableStateFlow

internal fun app(
    label: String,
    packageName: String = "pkg." + label.filter(Char::isLetterOrDigit).lowercase().ifEmpty { "x" },
    userSerial: Long = 0,
) = LauncherApp(
    key = AppKey(packageName, "$packageName.Main", userSerial),
    label = label,
    component = ComponentName(packageName, "$packageName.Main"),
    user = Process.myUserHandle(),
    isMainProfile = userSerial == 0L,
)

/** In-memory [AppPreferences]. */
internal class FakeAppPreferences : AppPreferences {
    override val favorites = MutableStateFlow(emptyList<AppKey>())
    override val hidden = MutableStateFlow(emptySet<AppKey>())
    val forgotten = mutableListOf<Set<AppKey>>()

    override suspend fun storedApps(): Set<AppKey> = favorites.value.toSet() + hidden.value

    override suspend fun forget(apps: Set<AppKey>) {
        if (apps.isEmpty()) return
        forgotten += apps
        favorites.value -= apps
        hidden.value -= apps
    }
}
