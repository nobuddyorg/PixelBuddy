package io.github.nobuddyorg.pixelbuddy.core.data.apps

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Thin wrapper around [LauncherApps], so the repository can be tested with a fake. */
interface AppSource {
    /** Launchable activities of all visible profiles. */
    fun loadApps(): List<LauncherApp>

    /** Emits whenever apps or profiles may have changed. */
    fun changes(): Flow<Unit>

    fun start(
        component: ComponentName,
        user: UserHandle,
        sourceBounds: Rect?,
    )

    /** Opens the system's app info screen. */
    fun showDetails(
        component: ComponentName,
        user: UserHandle,
        sourceBounds: Rect?,
    )

    /** Starts the system's uninstall confirmation. */
    fun uninstall(
        packageName: String,
        user: UserHandle,
    )

    /**
     * True only if [key]'s app is certainly gone: its profile was removed, or its package is uninstalled and not
     * waiting to be (re)installed. Unknown states (locked or paused profile) give false.
     */
    fun isRemoved(key: AppKey): Boolean
}

class LauncherAppsSource(
    private val context: Context,
    /** Packages with a pending install in a profile; by default from all install sessions the launcher sees. */
    private val pendingInstalls: ((UserHandle) -> Set<String>)? = null,
) : AppSource {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    override fun loadApps(): List<LauncherApp> =
        visibleProfiles().flatMap { user ->
            val serial = userManager.getSerialNumberForUser(user)
            launcherApps.getActivityList(null, user).map { it.toLauncherApp(serial) }
        }

    /** Main and work profile. Private Space is never shown (and Samsung's Secure Folder isn't a profile here). */
    private fun visibleProfiles(): List<UserHandle> = launcherApps.profiles.filterNot(::isPrivateProfile)

    private fun isPrivateProfile(user: UserHandle): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return false
        return launcherApps.getLauncherUserInfo(user)?.userType == UserManager.USER_TYPE_PROFILE_PRIVATE
    }

    private fun LauncherActivityInfo.toLauncherApp(userSerial: Long) =
        LauncherApp(
            key = AppKey(componentName.packageName, componentName.className, userSerial),
            label = label.toString().trim(),
            component = componentName,
            user = user,
            isMainProfile = user == Process.myUserHandle(),
            canUninstall = applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0,
        )

    override fun changes(): Flow<Unit> =
        callbackFlow {
            val callback =
                object : LauncherApps.Callback() {
                    override fun onPackageAdded(
                        packageName: String,
                        user: UserHandle,
                    ) {
                        trySend(Unit)
                    }

                    override fun onPackageRemoved(
                        packageName: String,
                        user: UserHandle,
                    ) {
                        trySend(Unit)
                    }

                    override fun onPackageChanged(
                        packageName: String,
                        user: UserHandle,
                    ) {
                        trySend(Unit)
                    }

                    override fun onPackagesAvailable(
                        packageNames: Array<out String>,
                        user: UserHandle,
                        replacing: Boolean,
                    ) {
                        trySend(Unit)
                    }

                    override fun onPackagesUnavailable(
                        packageNames: Array<out String>,
                        user: UserHandle,
                        replacing: Boolean,
                    ) {
                        trySend(Unit)
                    }

                    override fun onPackagesSuspended(
                        packageNames: Array<out String>,
                        user: UserHandle,
                    ) {
                        trySend(Unit)
                    }

                    override fun onPackagesUnsuspended(
                        packageNames: Array<out String>,
                        user: UserHandle,
                    ) {
                        trySend(Unit)
                    }
                }
            // Work profile added, removed, paused or resumed.
            val profileReceiver =
                object : BroadcastReceiver() {
                    override fun onReceive(
                        context: Context,
                        intent: Intent,
                    ) {
                        trySend(Unit)
                    }
                }
            val profileEvents =
                IntentFilter().apply {
                    addAction(Intent.ACTION_MANAGED_PROFILE_ADDED)
                    addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED)
                    addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
                    addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                        addAction(Intent.ACTION_PROFILE_AVAILABLE)
                        addAction(Intent.ACTION_PROFILE_UNAVAILABLE)
                    }
                }
            launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
            ContextCompat.registerReceiver(context, profileReceiver, profileEvents, ContextCompat.RECEIVER_NOT_EXPORTED)
            awaitClose {
                launcherApps.unregisterCallback(callback)
                context.unregisterReceiver(profileReceiver)
            }
        }

    override fun start(
        component: ComponentName,
        user: UserHandle,
        sourceBounds: Rect?,
    ) {
        launcherApps.startMainActivity(component, user, sourceBounds, null)
    }

    override fun showDetails(
        component: ComponentName,
        user: UserHandle,
        sourceBounds: Rect?,
    ) {
        launcherApps.startAppDetailsActivity(component, user, sourceBounds, null)
    }

    override fun uninstall(
        packageName: String,
        user: UserHandle,
    ) {
        val intent =
            Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null))
                .putExtra(Intent.EXTRA_USER, user)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        context.startActivity(intent)
    }

    override fun isRemoved(key: AppKey): Boolean {
        val user = userManager.getUserForSerialNumber(key.userSerial) ?: return true
        if (!userManager.isUserUnlocked(user) || userManager.isQuietModeEnabled(user)) return false
        val installed =
            try {
                val info =
                    launcherApps.getApplicationInfo(
                        key.packageName,
                        PackageManager.MATCH_UNINSTALLED_PACKAGES,
                        user,
                    )
                info != null && info.flags and ApplicationInfo.FLAG_INSTALLED != 0
            } catch (_: PackageManager.NameNotFoundException) {
                false
            } catch (_: SecurityException) {
                return false
            }
        return !installed && !isBeingInstalled(key.packageName, user)
    }

    /** Pending installs, e.g. apps restored from a backup that are still downloading. */
    private fun isBeingInstalled(
        packageName: String,
        user: UserHandle,
    ): Boolean =
        try {
            val pending =
                pendingInstalls?.invoke(user)
                    ?: launcherApps.allPackageInstallerSessions
                        .filter {
                            it.user == user
                        }.mapNotNull { it.appPackageName }
            packageName in pending
        } catch (_: SecurityException) {
            // Only the default home app may list sessions; without them, keep the entry.
            true
        }
}
