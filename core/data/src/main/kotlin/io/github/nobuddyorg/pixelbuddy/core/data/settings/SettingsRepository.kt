package io.github.nobuddyorg.pixelbuddy.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.core.FileStorage
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import io.github.nobuddyorg.pixelbuddy.core.data.apps.AppKey
import io.github.nobuddyorg.pixelbuddy.core.data.apps.AppPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import kotlin.coroutines.CoroutineContext

/** The single settings store. All changes are atomic read-modify-write updates of the whole file. */
class SettingsRepository(
    private val store: DataStore<Settings>,
) : AppPreferences {
    val settings: Flow<Settings> = store.data

    override val favorites: Flow<List<AppKey>> = settings.map { it.favorites }.distinctUntilChanged()

    override val hidden: Flow<Set<AppKey>> = settings.map { it.hidden }.distinctUntilChanged()

    val iconStyles: Flow<IconStyles> =
        settings
            .map { s -> IconStyles(s.iconStyle.style, s.iconOverrides.associate { it.app to it.style.style }) }
            .distinctUntilChanged()

    suspend fun update(transform: (Settings) -> Settings): Settings = store.updateData { transform(it) }

    /** Adds [app] at the end of the favorites; does nothing if it is one already. */
    suspend fun addFavorite(app: AppKey) {
        update { s -> if (app in s.favorites) s else s.copy(favorites = s.favorites + app) }
    }

    suspend fun removeFavorite(app: AppKey) {
        update { s -> s.copy(favorites = s.favorites - app) }
    }

    /** Moves favorite [app] by [offset] places (negative: up), clamped to the list. */
    suspend fun moveFavorite(
        app: AppKey,
        offset: Int,
    ) {
        update { s ->
            val from = s.favorites.indexOf(app)
            if (from < 0) return@update s
            val to = (from + offset).coerceIn(0, s.favorites.lastIndex)
            s.copy(favorites = s.favorites.toMutableList().apply { add(to, removeAt(from)) })
        }
    }

    /** Hides [app] from home; a hidden app is no favorite. */
    suspend fun hide(app: AppKey) {
        update { s -> s.copy(hidden = s.hidden + app, favorites = s.favorites - app) }
    }

    suspend fun unhide(app: AppKey) {
        update { s -> s.copy(hidden = s.hidden - app) }
    }

    override suspend fun storedApps(): Set<AppKey> = settings.first().appKeys

    override suspend fun forget(apps: Set<AppKey>) {
        if (apps.isEmpty()) return
        update { s ->
            s.copy(
                favorites = s.favorites - apps,
                hidden = s.hidden - apps,
                iconOverrides = s.iconOverrides.filterNot { it.app in apps },
            )
        }
    }

    companion object {
        /** File name inside the files directory; the backup rules include it. */
        const val FILE_PATH: String = "datastore/settings.json"

        /**
         * Opens the store at [file]. [context] must be application-wide and contain a Job (e.g.
         * `Dispatchers.IO + SupervisorJob()`); only one store per file may exist in a process.
         *
         * A file that can't be read is replaced with defaults, after keeping a copy next to it (`.corrupt`), so a
         * broken file never stops home from starting.
         */
        fun create(
            file: File,
            context: CoroutineContext,
            serializer: SettingsSerializer = SettingsSerializer(),
        ): SettingsRepository {
            val store =
                DataStore
                    .Builder(FileStorage(serializer) { file }, context)
                    .setCorruptionHandler(
                        ReplaceFileCorruptionHandler {
                            runCatching { file.copyTo(File(file.path + ".corrupt"), overwrite = true) }
                            serializer.defaultValue
                        },
                    ).build()
            return SettingsRepository(store)
        }
    }
}
