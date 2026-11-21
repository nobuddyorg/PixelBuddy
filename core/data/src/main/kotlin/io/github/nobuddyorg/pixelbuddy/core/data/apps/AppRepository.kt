package io.github.nobuddyorg.pixelbuddy.core.data.apps

import android.content.ActivityNotFoundException
import android.graphics.Rect
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.util.Locale

/** All launchable apps, sorted and live-updated, without hidden ones. */
class AppRepository(
    private val source: AppSource,
    private val hiddenApps: StateFlow<Set<AppKey>>,
    private val locale: () -> Locale = Locale::getDefault,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    /** Sorted apps; reloads on every package or profile change. Collect only while home is visible. */
    val apps: Flow<List<AppListEntry>> =
        source
            .changes()
            .onStart { emit(Unit) }
            .conflate()
            .map { AppSorter(locale()).sort(source.loadApps()) }
            .flowOn(dispatcher)
            .combine(hiddenApps) { apps, hidden -> apps.filterNot { it.app.key in hidden } }
            .distinctUntilChanged()

    /** Starts [app]. Never throws: a removed, disabled or unavailable app gives [LaunchResult.Failed]. */
    fun launch(
        app: LauncherApp,
        sourceBounds: Rect? = null,
    ): LaunchResult =
        try {
            source.start(app.component, app.user, sourceBounds)
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
