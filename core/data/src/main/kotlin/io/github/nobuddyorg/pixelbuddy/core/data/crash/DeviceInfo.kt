package io.github.nobuddyorg.pixelbuddy.core.data.crash

import android.content.Context
import android.os.Build

/** App and device details written into crash reports. Contains no personal data. */
data class DeviceInfo(
    val appVersionName: String,
    val appVersionCode: Long,
    val androidRelease: String,
    val sdkInt: Int,
    val oneUiVersion: String?,
    val manufacturer: String,
    val model: String,
) {
    companion object {
        fun from(context: Context): DeviceInfo {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            return DeviceInfo(
                appVersionName = packageInfo.versionName ?: "?",
                appVersionCode = packageInfo.longVersionCode,
                androidRelease = Build.VERSION.RELEASE,
                sdkInt = Build.VERSION.SDK_INT,
                oneUiVersion = oneUiVersion(),
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL,
            )
        }

        /**
         * Samsung devices expose `Build.VERSION.SEM_PLATFORM_INT`, e.g. 90000 + 10000 × major for One UI.
         * Other devices don't have the field.
         */
        private fun oneUiVersion(): String? =
            runCatching { Build.VERSION::class.java.getField("SEM_PLATFORM_INT").getInt(null) }
                .getOrNull()
                ?.let(::oneUiVersionFromSemPlatform)

        internal fun oneUiVersionFromSemPlatform(semPlatformInt: Int): String? {
            val value = semPlatformInt - 90_000
            if (value <= 0) return null
            return "${value / 10_000}.${(value % 10_000) / 100}"
        }
    }
}
