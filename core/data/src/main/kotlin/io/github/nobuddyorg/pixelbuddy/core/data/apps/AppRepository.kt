package io.github.nobuddyorg.pixelbuddy.core.data.apps

import android.content.ActivityNotFoundException
import android.graphics.Rect
import android.os.Bundle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import java.util.Locale

/** What home shows: favorites in the user's order and all apps sorted, both without hidden apps. */
data class HomeApps(
    val favorites: List<LauncherApp> = emptyList(),
    val apps: List<AppListEntry> = emptyList(),
)

/** All launchable apps, sorted and live-updated, combined with the user's favorites and hidden apps. */
class AppRepository(
    private val source: AppSource,
    private val preferences: AppPreferences,
    private val locale: () -> Locale = Locale::getDefault,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** Called before every (re)load, e.g. to let the icon cache re-check icons. */
    private val onReload: () -> Unit = {},
) {
    /** Every installed app, hidden ones included; reloads on every package or profile change. */
    private val installed: Flow<List<AppListEntry>> =
        source
            .changes()
            .onStart { emit(Unit) }
            .conflate()
            .map {
                onReload()
                AppSorter(locale()).sort(source.loadApps()).also { forgetRemoved(it) }
            }.flowOn(dispatcher)

    /** Home content. Collect only while home is visible. */
    val home: Flow<HomeApps> =
        combine(installed, preferences.favorites, preferences.hidden) { apps, favorites, hidden ->
            val visible = apps.filterNot { it.app.key in hidden }
            val byKey = visible.associateBy { it.app.key }
            HomeApps(favorites = favorites.mapNotNull { byKey[it]?.app }, apps = visible)
        }.distinctUntilChanged()

    /** Hidden apps that are installed, in list order, e.g. to show them again from the settings. */
    val hiddenApps: Flow<List<LauncherApp>> =
        combine(installed, preferences.hidden) { apps, hidden ->
            apps.map { it.app }.filter { it.key in hidden }
        }.distinctUntilChanged()

    /**
     * Drops stored entries (favorites, hidden apps, overrides) of uninstalled apps. Only apps that are really
     * gone count: not ones on a paused work profile, still being restored, or on unmounted storage.
     */
    private suspend fun forgetRemoved(apps: List<AppListEntry>) {
        val present = apps.mapTo(HashSet()) { it.app.key }
        preferences.installed(present)
        val stored = preferences.storedApps()
        if (stored.isEmpty()) return
        preferences.forget(stored.filterTo(HashSet()) { it !in present && source.isRemoved(it) })
    }

    /** Starts [app]. Never throws: a removed, disabled or unavailable app gives [LaunchResult.Failed]. */
    fun launch(
        app: LauncherApp,
        sourceBounds: Rect? = null,
        options: Bundle? = null,
    ): LaunchResult = attempt { source.start(app.component, app.user, sourceBounds, options) }

    /** Opens the system's app info screen for [app]. */
    fun openDetails(
        app: LauncherApp,
        sourceBounds: Rect? = null,
    ): LaunchResult = attempt { source.showDetails(app.component, app.user, sourceBounds) }

    /** [app]'s shortcuts for its long-press menu (#17), read off the main thread. */
    suspend fun shortcuts(app: LauncherApp): AppShortcuts = withContext(dispatcher) { source.shortcuts(app) }

    /** Starts [shortcut]. Never throws: a removed or disabled shortcut gives [LaunchResult.Failed]. */
    fun launch(
        shortcut: AppShortcut,
        sourceBounds: Rect? = null,
    ): LaunchResult = attempt { source.startShortcut(shortcut, sourceBounds) }

    /** Asks the system to uninstall [app]; the system shows its own confirmation. */
    fun uninstall(app: LauncherApp): LaunchResult = attempt { source.uninstall(app.key.packageName, app.user) }

    private inline fun attempt(action: () -> Unit): LaunchResult =
        try {
            action()
            LaunchResult.Launched
        } catch (e: ActivityNotFoundException) {
            LaunchResult.Failed(e)
        } catch (e: SecurityException) {
            LaunchResult.Failed(e)
        } catch (e: IllegalStateException) {
            LaunchResult.Failed(e)
        } catch (e: IllegalArgumentException) {
            LaunchResult.Failed(e)
        }
}
