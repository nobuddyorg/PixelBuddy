package io.github.nobuddyorg.pixelbuddy.core.data.apps

import kotlinx.coroutines.flow.Flow

/** The stored choices [AppRepository] needs: favorites, hidden apps and the cleanup after uninstalls. */
interface AppPreferences {
    /** Favorites in the user's order. */
    val favorites: Flow<List<AppKey>>

    val hidden: Flow<Set<AppKey>>

    /** Every app the stored settings refer to, except apps still waiting to be installed. */
    suspend fun storedApps(): Set<AppKey>

    /** Removes all stored entries of [apps] (they were uninstalled). */
    suspend fun forget(apps: Set<AppKey>)

    /** [apps] are installed now; apps waiting for their install (see [storedApps]) stop waiting. */
    suspend fun installed(apps: Set<AppKey>) = Unit
}
