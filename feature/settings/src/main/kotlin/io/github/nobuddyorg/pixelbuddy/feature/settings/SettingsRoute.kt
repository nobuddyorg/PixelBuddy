package io.github.nobuddyorg.pixelbuddy.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nobuddyorg.pixelbuddy.core.data.apps.LauncherApp
import io.github.nobuddyorg.pixelbuddy.core.data.crash.CrashReport
import io.github.nobuddyorg.pixelbuddy.core.data.crash.CrashReportStore
import io.github.nobuddyorg.pixelbuddy.core.data.icons.CustomIconStore
import io.github.nobuddyorg.pixelbuddy.core.data.icons.IconRepository
import io.github.nobuddyorg.pixelbuddy.core.data.lock.ScreenLock
import io.github.nobuddyorg.pixelbuddy.core.data.settings.IconChoice
import io.github.nobuddyorg.pixelbuddy.core.data.settings.Settings
import io.github.nobuddyorg.pixelbuddy.core.data.settings.SettingsArchive
import io.github.nobuddyorg.pixelbuddy.core.data.settings.TemperatureUnit
import io.github.nobuddyorg.pixelbuddy.core.data.settings.WeatherPlace
import io.github.nobuddyorg.pixelbuddy.core.data.settings.WeatherSettings
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyTheme
import io.github.nobuddyorg.pixelbuddy.core.weather.isFahrenheit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate

/**
 * Settings with state. [settings] are changed through [onUpdate] and apply on home at once. [favoriteApps] and
 * [hiddenApps] are the apps behind those settings, [apps] every app (to open with a tap on the weather). Crash
 * reports are loaded from [store] off the main thread; places are found with [searchPlaces] (network, only while
 * the user searches). "Set as wallpaper" renders the sky of the current [PixelBuddyTheme].
 */
@Composable
fun SettingsRoute(
    store: CrashReportStore,
    appVersion: String,
    settings: Flow<Settings>,
    onUpdate: ((Settings) -> Settings) -> Unit,
    modifier: Modifier = Modifier,
    favoriteApps: Flow<List<LauncherApp>> = flowOf(emptyList()),
    hiddenApps: Flow<List<LauncherApp>> = flowOf(emptyList()),
    apps: Flow<List<LauncherApp>> = flowOf(emptyList()),
    searchPlaces: suspend (String) -> List<WeatherPlace> = { emptyList() },
    /** Runs the setup again (it shows on home); null leaves the entry out. */
    onRunSetup: (() -> Unit)? = null,
    /** Renders the icon editor's previews; none without it. */
    icons: IconRepository? = null,
    /** Keeps pictures picked as app icons (#14); none can be picked without it. */
    customIcons: CustomIconStore? = null,
    /** Icons changed (style applied, app icon set or reset), e.g. to render the remaining icons ahead. */
    onIconsChanged: () -> Unit = {},
    /** Opens the editor for this app's own icon (key id) right away, e.g. from home's long-press menu. */
    editIconOf: String? = null,
    /** Leaving the editor opened with [editIconOf] ends the settings. */
    onDone: () -> Unit = {},
    /** Locks the screen (#16); without it, no gesture can be set to lock. */
    screenLock: ScreenLock? = null,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val style = PixelBuddyTheme.style
    // Null until read, so the stored values never flash up as defaults first.
    val stored by settings.collectAsStateWithLifecycle(initialValue = null)
    val favorites by favoriteApps.collectAsStateWithLifecycle(initialValue = emptyList())
    val hidden by hiddenApps.collectAsStateWithLifecycle(initialValue = emptyList())
    val allApps by apps.collectAsStateWithLifecycle(initialValue = emptyList())
    val lockFlow = remember(screenLock) { screenLock?.available ?: MutableStateFlow(false) }
    val lockServiceOn by lockFlow.collectAsStateWithLifecycle()
    var page by rememberSaveable {
        mutableStateOf(
            if (editIconOf !=
                null
            ) {
                SettingsPage.AppIconEditor
            } else {
                SettingsPage.Main
            },
        )
    }
    var iconApp by rememberSaveable { mutableStateOf(editIconOf) }
    var locationDenied by remember { mutableStateOf(false) }
    var wallpaper by remember { mutableStateOf(WallpaperStatus.Idle) }
    var fontLicense by remember { mutableStateOf<String?>(null) }
    var backupMessage by remember { mutableStateOf<Int?>(null) }
    // A read archive waiting for "Replace"; a configuration change forgets it, so it is picked again.
    var pendingImport by remember { mutableStateOf<SettingsArchive.Archive?>(null) }
    val exporter =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ZIP_TYPE)) { uri ->
            val settingsNow = stored
            if (uri != null && settingsNow != null && customIcons != null) {
                scope.launch {
                    val written = withContext(ioDispatcher) { context.exportTo(uri, settingsNow, customIcons) }
                    backupMessage = if (written) R.string.backup_exported else R.string.backup_export_failed
                }
            }
        }
    val importer =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                scope.launch {
                    when (val result = withContext(ioDispatcher) { context.readArchive(uri) }) {
                        is SettingsArchive.ReadResult.Read -> {
                            backupMessage = null
                            pendingImport = result.archive
                        }

                        is SettingsArchive.ReadResult.Refused -> {
                            backupMessage = result.problem.message
                        }
                    }
                }
            }
        }
    val systemIs24Hour = remember(context) { DateFormat.is24HourFormat(context) }

    val updateWeather: ((WeatherSettings) -> WeatherSettings) -> Unit = { change ->
        onUpdate { it.copy(weather = change(it.weather)) }
    }
    val useApproximate =
        rememberApproximateLocation(
            onGranted = {
                updateWeather { it.copy(enabled = true, approximateLocation = true) }
                page = SettingsPage.Weather
            },
            onDenied = { locationDenied = true },
        )

    var reports by remember { mutableStateOf(emptyList<CrashReport>()) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var openReport by remember { mutableStateOf<OpenCrashReport?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) {
        reports = withContext(ioDispatcher) { store.list() }
    }
    LaunchedEffect(openId) {
        val id = openId
        openReport = id?.let { withContext(ioDispatcher) { store.read(it) } }?.let { OpenCrashReport(id, it) }
        if (openReport == null) openId = null
    }
    if (page == SettingsPage.Licenses) {
        LaunchedEffect(Unit) {
            fontLicense = withContext(ioDispatcher) { context.readAsset(FONT_LICENSE_ASSET) }
        }
    }

    val current = stored
    if (current == null) {
        SettingsFrame(modifier) {}
        return
    }
    val weather = current.weather

    when (page) {
        SettingsPage.WeatherPlace -> {
            SettingsFrame(modifier) {
                WeatherPlaceContent(
                    search = searchPlaces,
                    onPick = { place ->
                        updateWeather { it.copy(enabled = true, place = place, approximateLocation = false) }
                        page = SettingsPage.Weather
                    },
                    onApproximate = useApproximate,
                    onBack = { page = SettingsPage.Weather },
                    message = if (locationDenied) stringResource(R.string.weather_location_denied) else null,
                )
            }
            return
        }

        SettingsPage.Icons -> {
            val favoriteKeys = favorites.mapTo(HashSet()) { it.key }
            IconEditorRoute(
                saved = IconChoice(current.iconStyle),
                icons = icons,
                apps = (favorites + allApps.filterNot { it.key in favoriteKeys }).take(PREVIEW_APPS),
                onApply = { choice ->
                    onUpdate { it.copy(iconStyle = choice.style) }
                    onIconsChanged()
                },
                onLeave = { page = SettingsPage.Main },
                modifier = modifier,
            )
            return
        }

        SettingsPage.AppIconEditor -> {
            val leave = {
                if (editIconOf != null && iconApp == editIconOf) onDone() else page = SettingsPage.AppIcons
            }
            val app = allApps.firstOrNull { it.key.id == iconApp }
            if (app == null) {
                // Still loading, or the app is gone.
                if (allApps.isNotEmpty()) LaunchedEffect(Unit) { leave() }
                SettingsFrame(modifier) {}
                return
            }
            val override = current.iconOverrideFor(app.key)
            // A new key per app, so the editor starts afresh for each.
            key(app.key.id) {
                IconEditorRoute(
                    saved = override?.choice ?: IconChoice(current.iconStyle),
                    icons = icons,
                    apps = listOf(app),
                    onApply = { choice ->
                        onUpdate { it.withIconOverride(app.key, choice) }
                        onIconsChanged()
                    },
                    onLeave = leave,
                    modifier = modifier,
                    forApp =
                        AppIconContext(
                            app = app,
                            global = current.iconStyle.style,
                            hasOverride = override != null,
                            importImage = { uri ->
                                withContext(ioDispatcher) { context.importIcon(customIcons, uri) }
                            },
                            onResetToGlobal = {
                                onUpdate { it.withoutIconOverride(app.key) }
                                onIconsChanged()
                            },
                        ),
                )
            }
            return
        }

        SettingsPage.WeatherTap -> {
            SettingsFrame(modifier) {
                WeatherTapAppContent(
                    apps = allApps,
                    onPick = { app ->
                        updateWeather { it.copy(tapApp = app?.key) }
                        page = SettingsPage.Weather
                    },
                    onBack = { page = SettingsPage.Weather },
                )
            }
            return
        }

        else -> {}
    }

    val onWeather: (WeatherAction) -> Unit = { action ->
        when (action) {
            WeatherAction.Toggle -> {
                val enable = !weather.enabled
                updateWeather { it.copy(enabled = enable) }
                // Weather needs a place first.
                if (enable && !weather.hasLocation) page = SettingsPage.WeatherPlace
            }

            WeatherAction.ChoosePlace -> {
                locationDenied = false
                page = SettingsPage.WeatherPlace
            }

            WeatherAction.CycleUnit -> {
                updateWeather {
                    it.copy(unit = TemperatureUnit.entries[(it.unit.ordinal + 1) % TemperatureUnit.entries.size])
                }
            }

            WeatherAction.ChooseTapApp -> {
                page = SettingsPage.WeatherTap
            }

            WeatherAction.OpenAttribution -> {
                context.openWebPage(OPEN_METEO_URL)
            }
        }
    }

    val state =
        SettingsState(
            appVersion = appVersion,
            settings = current,
            favorites = favorites,
            hiddenApps = hidden,
            weather =
                WeatherOptions(
                    settings = weather,
                    tapAppLabel = weather.tapApp?.let { key -> allApps.firstOrNull { it.key == key }?.label },
                    systemFahrenheit = TemperatureUnit.System.isFahrenheit(LocalConfiguration.current.locales[0]),
                ),
            reports = reports,
            openReport = openReport,
            wallpaper = wallpaper,
            systemIs24Hour = systemIs24Hour,
            fontLicense = fontLicense,
            canRunSetup = onRunSetup != null,
            apps = allApps,
            backupMessage = backupMessage,
            lockServiceOn = if (screenLock != null) lockServiceOn else null,
        )

    Box(modifier) {
        SettingsScreen(
            page = page,
            state = state,
            onAction = { action ->
                when (action) {
                    is SettingsAction.Navigate -> {
                        page = action.page
                        wallpaper = WallpaperStatus.Idle
                    }

                    is SettingsAction.EditAppIcon -> {
                        iconApp = action.app.key.id
                        page = SettingsPage.AppIconEditor
                    }

                    is SettingsAction.Update -> {
                        onUpdate(action.change)
                    }

                    is SettingsAction.SetWallpaper -> {
                        if (wallpaper != WallpaperStatus.Working) {
                            wallpaper = WallpaperStatus.Working
                            scope.launch {
                                wallpaper =
                                    withContext(ioDispatcher) { SkyWallpaper.set(context, style, action.target) }
                            }
                        }
                    }

                    is SettingsAction.Weather -> {
                        onWeather(action.action)
                    }

                    SettingsAction.OpenSource -> {
                        context.openWebPage(SOURCE_URL)
                    }

                    SettingsAction.ExportSettings -> {
                        backupMessage = null
                        exporter.launchSafely("pixelbuddy-settings-${LocalDate.now()}.zip")
                    }

                    SettingsAction.ImportSettings -> {
                        backupMessage = null
                        importer.launchSafely(arrayOf(ZIP_TYPE, "application/octet-stream"))
                    }

                    SettingsAction.TurnOnLockService -> {
                        screenLock?.let { context.startSafely(it.settingsIntent()) }
                    }

                    SettingsAction.RunSetup -> {
                        onRunSetup?.invoke()
                    }

                    is SettingsAction.OpenReport -> {
                        openId = action.id
                    }

                    SettingsAction.CloseReport -> {
                        openId = null
                    }

                    is SettingsAction.ShareReport -> {
                        context.shareCrashReport(action.report.text)
                    }

                    is SettingsAction.DeleteReport -> {
                        scope.launch {
                            withContext(ioDispatcher) { store.delete(action.id) }
                            openId = null
                            reload++
                        }
                    }

                    SettingsAction.DeleteAllReports -> {
                        scope.launch {
                            withContext(ioDispatcher) { store.deleteAll() }
                            reload++
                        }
                    }
                }
            },
        )
        pendingImport?.let { archive ->
            val imported = archive.settings
            ConfirmDialog(
                title = stringResource(R.string.backup_confirm_title),
                text =
                    stringResource(
                        R.string.backup_confirm_text,
                        imported.favorites.size,
                        imported.hidden.size,
                        imported.iconOverrides.size,
                    ),
                confirm = stringResource(R.string.backup_replace),
                cancel = stringResource(R.string.backup_cancel),
                onConfirm = {
                    pendingImport = null
                    scope.launch {
                        val restored =
                            withContext(ioDispatcher) {
                                customIcons?.let { SettingsArchive.restore(archive, current, it) }
                                    ?: archive.settings.copy(
                                        iconOverrides = archive.settings.iconOverrides.map { it.copy(image = null) },
                                        setup = current.setup,
                                        waitingApps = archive.settings.appKeys,
                                    )
                            }
                        onUpdate { now -> restored.copy(setup = now.setup) }
                        onIconsChanged()
                        backupMessage = R.string.backup_imported
                    }
                },
                onCancel = { pendingImport = null },
            )
        }
    }
}

private const val OPEN_METEO_URL = "https://open-meteo.com/"
private const val SOURCE_URL = "https://github.com/nobuddyorg/PixelBuddy"

/** Bundled by the design system together with the font. */
private const val FONT_LICENSE_ASSET = "licenses/departure_mono_OFL.txt"

private const val ZIP_TYPE = "application/zip"

private val SettingsArchive.Problem.message: Int
    get() =
        when (this) {
            SettingsArchive.Problem.NotAnArchive,
            SettingsArchive.Problem.NoSettings,
            -> R.string.backup_problem_not_archive

            SettingsArchive.Problem.NewerVersion -> R.string.backup_problem_newer

            SettingsArchive.Problem.TooLarge -> R.string.backup_problem_too_large

            SettingsArchive.Problem.UnsafeEntry -> R.string.backup_problem_unsafe

            SettingsArchive.Problem.Unreadable -> R.string.backup_problem_unreadable
        }

/** Starts a document picker; phones without one get nothing instead of a crash. */
private fun <I> ActivityResultLauncher<I>.launchSafely(input: I) {
    try {
        launch(input)
    } catch (_: ActivityNotFoundException) {
        // No document picker on this phone.
    }
}

/** Writes the archive to [uri]; false if that failed. */
private fun Context.exportTo(
    uri: Uri,
    settings: Settings,
    images: CustomIconStore,
): Boolean =
    try {
        contentResolver.openOutputStream(uri, "wt")?.use { SettingsArchive.write(settings, images, it) } != null
    } catch (_: IOException) {
        false
    } catch (_: SecurityException) {
        false
    }

/** Reads the archive at [uri]; a file that can't be opened counts as no archive. */
private suspend fun Context.readArchive(uri: Uri): SettingsArchive.ReadResult =
    try {
        contentResolver.openInputStream(uri)?.use { SettingsArchive.read(it) }
            ?: SettingsArchive.ReadResult.Refused(SettingsArchive.Problem.NotAnArchive)
    } catch (_: IOException) {
        SettingsArchive.ReadResult.Refused(SettingsArchive.Problem.NotAnArchive)
    } catch (_: SecurityException) {
        SettingsArchive.ReadResult.Refused(SettingsArchive.Problem.NotAnArchive)
    }

/** Imports the picture at [uri] into [store], reading it once; anything that goes wrong means unreadable. */
private fun Context.importIcon(
    store: CustomIconStore?,
    uri: Uri,
): CustomIconStore.ImportResult {
    if (store == null) return CustomIconStore.ImportResult.Unreadable
    return try {
        contentResolver.openInputStream(uri)?.use(store::import) ?: CustomIconStore.ImportResult.Unreadable
    } catch (_: IOException) {
        CustomIconStore.ImportResult.Unreadable
    } catch (_: SecurityException) {
        CustomIconStore.ImportResult.Unreadable
    }
}

private fun Context.readAsset(path: String): String? =
    try {
        assets.open(path).bufferedReader().use { it.readText() }
    } catch (_: IOException) {
        null
    }

private fun Context.openWebPage(url: String) {
    // No browser: nothing to open.
    startSafely(Intent(Intent.ACTION_VIEW, url.toUri()))
}

private fun Context.startSafely(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Nothing can open it.
    }
}

/** Opens the system share sheet. Only ever called from the user's tap on "Share". */
private fun Context.shareCrashReport(text: String) {
    val send =
        Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.crash_report_title))
            .putExtra(Intent.EXTRA_TEXT, text)
    startActivity(Intent.createChooser(send, getString(R.string.crash_report_share_title)))
}
