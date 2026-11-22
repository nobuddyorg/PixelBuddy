package io.github.nobuddyorg.pixelbuddy.feature.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nobuddyorg.pixelbuddy.core.data.apps.LauncherApp
import io.github.nobuddyorg.pixelbuddy.core.data.crash.CrashReport
import io.github.nobuddyorg.pixelbuddy.core.data.crash.CrashReportStore
import io.github.nobuddyorg.pixelbuddy.core.data.settings.TemperatureUnit
import io.github.nobuddyorg.pixelbuddy.core.data.settings.WeatherPlace
import io.github.nobuddyorg.pixelbuddy.core.data.settings.WeatherSettings
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyColors
import io.github.nobuddyorg.pixelbuddy.core.weather.isFahrenheit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings with state: loads crash reports from [store] off the main thread and lists [hiddenApps];
 * [onShowApp] shows a hidden app on home again. Weather: [weatherSettings] changed through [onUpdateWeather],
 * places found with [searchPlaces] (network, only while the user searches), [apps] to open with a tap on it.
 */
@Composable
fun SettingsRoute(
    store: CrashReportStore,
    appVersion: String,
    hiddenApps: Flow<List<LauncherApp>>,
    onShowApp: (LauncherApp) -> Unit,
    modifier: Modifier = Modifier,
    weatherSettings: Flow<WeatherSettings> = flowOf(WeatherSettings()),
    onUpdateWeather: ((WeatherSettings) -> WeatherSettings) -> Unit = {},
    searchPlaces: suspend (String) -> List<WeatherPlace> = { emptyList() },
    apps: Flow<List<LauncherApp>> = flowOf(emptyList()),
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    val context = LocalContext.current
    val weather by weatherSettings.collectAsStateWithLifecycle(initialValue = WeatherSettings())
    val allApps by apps.collectAsStateWithLifecycle(initialValue = emptyList())
    var page by rememberSaveable { mutableStateOf(PAGE_MAIN) }
    var locationDenied by remember { mutableStateOf(false) }
    val useApproximate = {
        onUpdateWeather { it.copy(enabled = true, approximateLocation = true) }
        page = PAGE_MAIN
    }
    val locationPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) useApproximate() else locationDenied = true
        }

    when (page) {
        PAGE_PLACE -> {
            Page(modifier) {
                WeatherPlaceContent(
                    search = searchPlaces,
                    onPick = { place ->
                        onUpdateWeather { it.copy(enabled = true, place = place, approximateLocation = false) }
                        page = PAGE_MAIN
                    },
                    onApproximate = {
                        if (context.hasCoarseLocation()) {
                            useApproximate()
                        } else {
                            locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        }
                    },
                    onBack = { page = PAGE_MAIN },
                    message = if (locationDenied) stringResource(R.string.weather_location_denied) else null,
                )
            }
            return
        }

        PAGE_TAP -> {
            Page(modifier) {
                WeatherTapAppContent(
                    apps = allApps,
                    onPick = { app ->
                        onUpdateWeather { it.copy(tapApp = app?.key) }
                        page = PAGE_MAIN
                    },
                    onBack = { page = PAGE_MAIN },
                )
            }
            return
        }
    }
    val weatherOptions =
        WeatherOptions(
            settings = weather,
            tapAppLabel = weather.tapApp?.let { key -> allApps.firstOrNull { it.key == key }?.label },
            systemFahrenheit = TemperatureUnit.System.isFahrenheit(LocalConfiguration.current.locales[0]),
        )
    val onWeather: (WeatherAction) -> Unit = { action ->
        when (action) {
            WeatherAction.Toggle -> {
                val enable = !weather.enabled
                onUpdateWeather { it.copy(enabled = enable) }
                // Weather needs a place first.
                if (enable && !weather.hasLocation) page = PAGE_PLACE
            }

            WeatherAction.ChoosePlace -> {
                locationDenied = false
                page = PAGE_PLACE
            }

            WeatherAction.CycleUnit -> {
                onUpdateWeather {
                    it.copy(
                        unit =
                            TemperatureUnit.entries[
                                (it.unit.ordinal + 1) %
                                    TemperatureUnit.entries.size,
                            ],
                    )
                }
            }

            WeatherAction.ChooseTapApp -> {
                page = PAGE_TAP
            }

            WeatherAction.OpenAttribution -> {
                context.openWebPage(OPEN_METEO_URL)
            }
        }
    }
    val scope = rememberCoroutineScope()
    var reports by remember { mutableStateOf(emptyList<CrashReport>()) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var openReport by remember { mutableStateOf<OpenCrashReport?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val hidden by hiddenApps.collectAsStateWithLifecycle(initialValue = emptyList())

    LaunchedEffect(reload) {
        reports = withContext(ioDispatcher) { store.list() }
    }
    LaunchedEffect(openId) {
        val id = openId
        openReport = id?.let { withContext(ioDispatcher) { store.read(it) } }?.let { OpenCrashReport(id, it) }
        if (openReport == null) openId = null
    }

    SettingsScreen(
        appVersion = appVersion,
        hiddenApps = hidden,
        onShowApp = onShowApp,
        weather = weatherOptions,
        onWeather = onWeather,
        reports = reports,
        openReport = openReport,
        onOpenReport = { openId = it },
        onCloseReport = { openId = null },
        onShareReport = { context.shareCrashReport(it.text) },
        onDeleteReport = { id ->
            scope.launch {
                withContext(ioDispatcher) { store.delete(id) }
                openId = null
                reload++
            }
        },
        onDeleteAllReports = {
            scope.launch {
                withContext(ioDispatcher) { store.deleteAll() }
                reload++
            }
        },
        modifier = modifier,
    )
}

/** A plain settings page with the same frame as [SettingsScreen]. */
@Composable
private fun Page(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(PixelBuddyColors.Navy)
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
    ) { content() }
}

private const val PAGE_MAIN = "main"
private const val PAGE_PLACE = "place"
private const val PAGE_TAP = "tap"
private const val OPEN_METEO_URL = "https://open-meteo.com/"

private fun Context.hasCoarseLocation() =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun Context.openWebPage(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        // No browser: nothing to open.
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
