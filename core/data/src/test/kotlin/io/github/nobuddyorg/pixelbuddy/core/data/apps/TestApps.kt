package io.github.nobuddyorg.pixelbuddy.core.data.apps

import android.content.ComponentName
import android.os.Process

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
