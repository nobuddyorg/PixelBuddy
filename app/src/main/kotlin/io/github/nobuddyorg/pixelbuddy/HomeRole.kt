package io.github.nobuddyorg.pixelbuddy

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

/** What to do on first start to become the default home app. */
sealed interface HomeRoleAction {
    /** PixelBuddy already holds the home role. */
    data object None : HomeRoleAction

    /** Ask for the role with the system dialog. */
    data class RequestRole(
        val intent: Intent,
    ) : HomeRoleAction

    /** The role can't be requested: open the default-apps settings screen instead. */
    data class OpenDefaultAppsSettings(
        val intent: Intent,
    ) : HomeRoleAction
}

object HomeRole {
    fun nextAction(context: Context): HomeRoleAction {
        val roleManager = context.getSystemService(RoleManager::class.java)
        return when {
            roleManager == null || !roleManager.isRoleAvailable(RoleManager.ROLE_HOME) -> {
                HomeRoleAction.OpenDefaultAppsSettings(defaultAppsSettingsIntent())
            }

            roleManager.isRoleHeld(RoleManager.ROLE_HOME) -> {
                HomeRoleAction.None
            }

            else -> {
                HomeRoleAction.RequestRole(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
            }
        }
    }

    fun defaultAppsSettingsIntent(): Intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)

    /** Older or customised systems without the default-apps screen still have this one. */
    fun homeSettingsIntent(): Intent = Intent(Settings.ACTION_HOME_SETTINGS)
}
