package io.github.nobuddyorg.pixelbuddy.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nobuddyorg.pixelbuddy.core.data.apps.LauncherApp
import io.github.nobuddyorg.pixelbuddy.core.data.crash.CrashReport
import io.github.nobuddyorg.pixelbuddy.core.data.settings.Settings
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyColors
import java.time.ZoneId

/** A crash report opened for reading. */
data class OpenCrashReport(
    val id: String,
    val text: String,
)

/** The pages of the settings; [parent] is where Back goes. */
enum class SettingsPage {
    Main,
    Appearance,
    Clock,
    Icons,
    Apps,
    Gestures,
    Wallpaper,
    Weather,
    WeatherPlace,
    WeatherTap,
    About,
    Licenses,
    CrashReports,
    ;

    val parent: SettingsPage
        get() =
            when (this) {
                Main -> Main
                WeatherPlace, WeatherTap -> Weather
                Licenses, CrashReports -> About
                else -> Main
            }
}

/** Everything the settings show. [systemIs24Hour] is the phone's clock format, for "Like the phone". */
data class SettingsState(
    val appVersion: String,
    val settings: Settings = Settings(),
    val favorites: List<LauncherApp> = emptyList(),
    val hiddenApps: List<LauncherApp> = emptyList(),
    val weather: WeatherOptions = WeatherOptions(settings.weather),
    val reports: List<CrashReport> = emptyList(),
    val openReport: OpenCrashReport? = null,
    val wallpaper: WallpaperStatus = WallpaperStatus.Idle,
    val systemIs24Hour: Boolean = true,
    /** The font licence, read from the app's assets. */
    val fontLicense: String? = null,
)

/** Something the user did in the settings. */
sealed interface SettingsAction {
    data class Navigate(
        val page: SettingsPage,
    ) : SettingsAction

    /** Changes the stored settings; applies on home at once. */
    class Update(
        val change: (Settings) -> Settings,
    ) : SettingsAction

    data class SetWallpaper(
        val target: WallpaperTarget,
    ) : SettingsAction

    data class Weather(
        val action: WeatherAction,
    ) : SettingsAction

    data object OpenSource : SettingsAction

    data class OpenReport(
        val id: String,
    ) : SettingsAction

    data object CloseReport : SettingsAction

    data class ShareReport(
        val report: OpenCrashReport,
    ) : SettingsAction

    data class DeleteReport(
        val id: String,
    ) : SettingsAction

    data object DeleteAllReports : SettingsAction
}

/**
 * Settings (#12): a main page with one row per category, each opening its page. Stateless: [SettingsRoute] holds
 * the state and carries out the [SettingsAction]s.
 */
@Composable
fun SettingsScreen(
    page: SettingsPage,
    state: SettingsState,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val report = state.openReport
    // One handler for every page, so Back always goes to the page around this one.
    BackHandler(enabled = report != null || page != SettingsPage.Main) {
        onAction(if (report != null) SettingsAction.CloseReport else SettingsAction.Navigate(page.parent))
    }
    SettingsFrame(modifier) {
        if (report != null) {
            CrashReportContent(report, onAction)
            return@SettingsFrame
        }
        // Every page starts at its top.
        key(page) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (page != SettingsPage.Main) {
                    TextButton(
                        stringResource(R.string.action_back),
                        { onAction(SettingsAction.Navigate(page.parent)) },
                        color = TextColor,
                    )
                }
                Title(stringResource(page.title))
                PageContent(page, state, onAction, zone)
            }
        }
    }
}

/** The frame of every settings page: background and system bar insets. */
@Composable
internal fun SettingsFrame(
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

@Composable
private fun PageContent(
    page: SettingsPage,
    state: SettingsState,
    onAction: (SettingsAction) -> Unit,
    zone: ZoneId,
) {
    val update: ((Settings) -> Settings) -> Unit = { onAction(SettingsAction.Update(it)) }
    when (page) {
        SettingsPage.Main -> MainPage(state, onAction)

        SettingsPage.Appearance -> AppearancePage(state.settings.appearance, update)

        SettingsPage.Clock -> ClockPage(state.settings.appearance, state.systemIs24Hour, update)

        SettingsPage.Icons -> IconsPage(state.settings.iconStyle, update)

        SettingsPage.Apps -> AppsPage(state.favorites, state.hiddenApps, update)

        SettingsPage.Gestures -> GesturesPage(state.settings.gestures, update)

        SettingsPage.Wallpaper -> WallpaperPage(state.settings.appearance, state.wallpaper, update, onAction)

        SettingsPage.Weather -> WeatherSection(state.weather) { onAction(SettingsAction.Weather(it)) }

        SettingsPage.About -> AboutPage(state, onAction)

        SettingsPage.Licenses -> LicensesPage(state.fontLicense)

        SettingsPage.CrashReports -> CrashReportsPage(state.reports, zone, onAction)

        // Shown by SettingsRoute, which holds their state.
        SettingsPage.WeatherPlace, SettingsPage.WeatherTap -> Unit
    }
}

private val SettingsPage.title: Int
    get() =
        when (this) {
            SettingsPage.Main -> R.string.settings_title
            SettingsPage.Appearance -> R.string.settings_appearance
            SettingsPage.Clock -> R.string.settings_clock
            SettingsPage.Icons -> R.string.settings_icons
            SettingsPage.Apps -> R.string.settings_apps
            SettingsPage.Gestures -> R.string.settings_gestures
            SettingsPage.Wallpaper -> R.string.settings_wallpaper
            SettingsPage.Weather, SettingsPage.WeatherPlace, SettingsPage.WeatherTap -> R.string.weather_title
            SettingsPage.About -> R.string.settings_about
            SettingsPage.Licenses -> R.string.licenses
            SettingsPage.CrashReports -> R.string.crash_reports_title
        }

@Composable
private fun CrashReportContent(
    report: OpenCrashReport,
    onAction: (SettingsAction) -> Unit,
) {
    Column {
        Title(stringResource(R.string.crash_report_title))
        Row {
            TextButton(
                stringResource(R.string.action_back),
                { onAction(SettingsAction.CloseReport) },
                color = TextColor,
            )
            TextButton(stringResource(R.string.crash_report_share), { onAction(SettingsAction.ShareReport(report)) })
            TextButton(
                stringResource(R.string.crash_report_delete),
                { onAction(SettingsAction.DeleteReport(report.id)) },
            )
        }
        BasicText(
            text = report.text,
            style = BodyStyle.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp),
            softWrap = false,
            modifier =
                Modifier
                    .padding(top = 8.dp)
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState()),
        )
    }
}
