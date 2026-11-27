package io.github.nobuddyorg.pixelbuddy.core.data.apps

/** One of an app's shortcuts (#17), e.g. "New message"; [id] is the app's own shortcut id. */
data class AppShortcut(
    val app: LauncherApp,
    val id: String,
    val label: String,
) {
    /** Unique per app, profile and shortcut, e.g. for list keys and icon caching. */
    val key: String get() = "${app.key.id}#$id"
}

/** An app's shortcuts for its long-press menu. */
sealed interface AppShortcuts {
    /** Only the default home app may read shortcuts. */
    data object NeedsHomeApp : AppShortcuts

    /** At most [MAX] shortcuts: the app's own (manifest) ones first, then its dynamic ones, each by rank. */
    data class Available(
        val shortcuts: List<AppShortcut>,
    ) : AppShortcuts

    companion object {
        /** Like the stock launchers: at most four in the menu. */
        const val MAX: Int = 4

        val None: AppShortcuts = Available(emptyList())
    }
}
