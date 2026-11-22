package io.github.nobuddyorg.pixelbuddy.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nobuddyorg.pixelbuddy.core.data.apps.LauncherApp
import io.github.nobuddyorg.pixelbuddy.core.data.crash.CrashReport
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyColors
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** A crash report opened for reading. */
data class OpenCrashReport(
    val id: String,
    val text: String,
)

/**
 * Settings. For now the hidden apps (#8), "About" with the app version and the local crash reports (#22); the
 * full settings follow in #12. Stateless: [SettingsRoute] holds the state.
 */
@Composable
fun SettingsScreen(
    appVersion: String,
    hiddenApps: List<LauncherApp>,
    onShowApp: (LauncherApp) -> Unit,
    reports: List<CrashReport>,
    openReport: OpenCrashReport?,
    onOpenReport: (String) -> Unit,
    onCloseReport: () -> Unit,
    onShareReport: (OpenCrashReport) -> Unit,
    onDeleteReport: (String) -> Unit,
    onDeleteAllReports: () -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(PixelBuddyColors.Navy)
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        if (openReport == null) {
            MainContent(appVersion, hiddenApps, onShowApp, reports, zone, onOpenReport, onDeleteAllReports)
        } else {
            BackHandler(onBack = onCloseReport)
            CrashReportContent(openReport, onCloseReport, onShareReport, onDeleteReport)
        }
    }
}

@Composable
private fun MainContent(
    appVersion: String,
    hiddenApps: List<LauncherApp>,
    onShowApp: (LauncherApp) -> Unit,
    reports: List<CrashReport>,
    zone: ZoneId,
    onOpenReport: (String) -> Unit,
    onDeleteAllReports: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val timeFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale).withZone(zone)

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Title(stringResource(R.string.settings_title))

        SectionHeader(stringResource(R.string.settings_hidden_apps))
        if (hiddenApps.isEmpty()) {
            BasicText(stringResource(R.string.settings_hidden_apps_empty), style = BodyStyle)
        } else {
            val showAgain = stringResource(R.string.settings_hidden_app_show)
            hiddenApps.forEach { app ->
                val label =
                    if (app.isMainProfile) app.label else stringResource(R.string.settings_work_app, app.label)
                ListRow(title = label, subtitle = showAgain, onClick = { onShowApp(app) })
            }
        }

        SectionHeader(stringResource(R.string.settings_about))
        BasicText(stringResource(R.string.settings_version, appVersion), style = BodyStyle)

        SectionHeader(stringResource(R.string.crash_reports_title))
        BasicText(stringResource(R.string.crash_reports_privacy), style = MutedStyle)
        Spacer(Modifier.height(8.dp))
        if (reports.isEmpty()) {
            BasicText(stringResource(R.string.crash_reports_empty), style = BodyStyle)
        } else {
            reports.forEach { report ->
                ListRow(
                    title = timeFormat.format(report.time),
                    subtitle = report.summary,
                    onClick = { onOpenReport(report.id) },
                )
            }
            TextButton(stringResource(R.string.crash_reports_delete_all), onDeleteAllReports)
        }
    }
}

@Composable
private fun CrashReportContent(
    report: OpenCrashReport,
    onClose: () -> Unit,
    onShare: (OpenCrashReport) -> Unit,
    onDelete: (String) -> Unit,
) {
    Column {
        Title(stringResource(R.string.crash_report_title))
        Row {
            TextButton(stringResource(R.string.action_back), onClose, color = TextColor)
            TextButton(stringResource(R.string.crash_report_share), { onShare(report) })
            TextButton(stringResource(R.string.crash_report_delete), { onDelete(report.id) })
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
