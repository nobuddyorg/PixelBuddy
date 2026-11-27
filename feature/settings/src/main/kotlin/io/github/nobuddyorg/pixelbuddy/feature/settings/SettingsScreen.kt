package io.github.nobuddyorg.pixelbuddy.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import io.github.nobuddyorg.pixelbuddy.core.data.calendar.CalendarInfo
import io.github.nobuddyorg.pixelbuddy.core.data.crash.CrashReport
import io.github.nobuddyorg.pixelbuddy.core.data.settings.Settings
import io.github.nobuddyorg.pixelbuddy.core.data.widgets.WidgetHeights
import io.github.nobuddyorg.pixelbuddy.core.data.widgets.WidgetProvider
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
    AppIcons,
    AppIconEditor,
    Apps,
    Gestures,
    Notifications,
    Calendar,
    Widget,
    WidgetPicker,
    Wallpaper,
    Weather,
    Backup,
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
                AppIconEditor -> AppIcons
                Licenses, CrashReports -> About
                WidgetPicker -> Widget
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
    /** Whether "Run setup again" is offered. */
    val canRunSetup: Boolean = true,
    /** Every app, for choosing one to give its own icon. */
    val apps: List<LauncherApp> = emptyList(),
    /** The outcome of the last export or import, as a message. */
    val backupMessage: Int? = null,
    /** Whether the lock service is on (#16); null if locking isn't offered at all. */
    val lockServiceOn: Boolean? = null,
    /** Whether the notification access is granted and connected (#24); null if not offered. */
    val notificationAccessOn: Boolean? = null,
    /** The phone's calendars to choose from (#27); null if the next event isn't offered. */
    val calendars: List<CalendarInfo>? = null,
    /** The user said no to the calendar permission. */
    val calendarDenied: Boolean = false,
    /** The widget slot (#26); null if widgets aren't offered. */
    val widget: WidgetUi? = null,
)

/** The widget slot as the settings show it (#26). */
data class WidgetUi(
    /** The widget's name; null if there is none or its app is gone. */
    val label: String? = null,
    val heights: WidgetHeights? = null,
    /** Widgets to choose from, read when the picker opens. */
    val providers: List<WidgetProvider> = emptyList(),
    /** Why the last widget couldn't be added, if it couldn't. */
    val message: Int? = null,
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

    /** Starts the first-start setup again, on home. */
    data object RunSetup : SettingsAction

    data object ExportSettings : SettingsAction

    data object ImportSettings : SettingsAction

    /** Opens the system screen to turn the lock service on (#16). */
    data object TurnOnLockService : SettingsAction

    /** Opens the system screen to grant the notification access (#24). */
    data object AllowNotificationAccess : SettingsAction

    /** Turns the next calendar event on, asking for the calendar permission first (#27). */
    data object EnableCalendar : SettingsAction

    /** Puts [provider]'s widget into the slot, through Android's bind and setup screens (#26). */
    data class AddWidget(
        val provider: WidgetProvider,
    ) : SettingsAction

    data object RemoveWidget : SettingsAction

    /** Opens the editor for one app's own icon. */
    data class EditAppIcon(
        val app: LauncherApp,
    ) : SettingsAction

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
    content: @Composable ColumnScope.() -> Unit,
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
        SettingsPage.Main -> {
            MainPage(state, onAction)
        }

        SettingsPage.Appearance -> {
            AppearancePage(state.settings.appearance, update)
        }

        SettingsPage.Clock -> {
            ClockPage(state.settings.appearance, state.systemIs24Hour, update)
        }

        SettingsPage.AppIcons -> {
            AppIconsPage(state.apps, state.settings, onAction)
        }

        SettingsPage.Apps -> {
            AppsPage(state.favorites, state.hiddenApps, update)
        }

        SettingsPage.Widget -> {
            WidgetPage(state.settings.widget, state.widget ?: WidgetUi(), update, onAction)
        }

        SettingsPage.WidgetPicker -> {
            WidgetPickerPage(state.widget?.providers.orEmpty(), onAction)
        }

        SettingsPage.Calendar -> {
            CalendarPage(state.settings.calendar, state.calendars.orEmpty(), state.calendarDenied, update, onAction)
        }

        SettingsPage.Notifications -> {
            NotificationsPage(state.settings.notifications, state.notificationAccessOn, update, onAction)
        }

        SettingsPage.Gestures -> {
            GesturesPage(state.settings.gestures, state.lockServiceOn, update, onAction)
        }

        SettingsPage.Wallpaper -> {
            WallpaperPage(
                appearance = state.settings.appearance,
                status = state.wallpaper,
                update = update,
                onAction = onAction,
                hasWeatherPlace = state.settings.weather.hasLocation,
            )
        }

        SettingsPage.Weather -> {
            WeatherSection(state.weather) { onAction(SettingsAction.Weather(it)) }
        }

        SettingsPage.Backup -> {
            BackupPage(state.backupMessage, onAction)
        }

        SettingsPage.About -> {
            AboutPage(state, onAction)
        }

        SettingsPage.Licenses -> {
            LicensesPage(state.fontLicense)
        }

        SettingsPage.CrashReports -> {
            CrashReportsPage(state.reports, zone, onAction)
        }

        // Shown by SettingsRoute, which holds their state.
        SettingsPage.Icons, SettingsPage.AppIconEditor, SettingsPage.WeatherPlace, SettingsPage.WeatherTap -> {}
    }
}

private val SettingsPage.title: Int
    get() =
        when (this) {
            SettingsPage.Main -> R.string.settings_title
            SettingsPage.Appearance -> R.string.settings_appearance
            SettingsPage.Clock -> R.string.settings_clock
            SettingsPage.Icons, SettingsPage.AppIconEditor -> R.string.settings_icons
            SettingsPage.AppIcons -> R.string.icons_single_apps
            SettingsPage.Apps -> R.string.settings_apps
            SettingsPage.Gestures -> R.string.settings_gestures
            SettingsPage.Notifications -> R.string.settings_notifications
            SettingsPage.Calendar -> R.string.settings_calendar
            SettingsPage.Widget -> R.string.settings_widget
            SettingsPage.WidgetPicker -> R.string.widget_pick
            SettingsPage.Wallpaper -> R.string.settings_wallpaper
            SettingsPage.Weather, SettingsPage.WeatherPlace, SettingsPage.WeatherTap -> R.string.weather_title
            SettingsPage.Backup -> R.string.backup_title
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
