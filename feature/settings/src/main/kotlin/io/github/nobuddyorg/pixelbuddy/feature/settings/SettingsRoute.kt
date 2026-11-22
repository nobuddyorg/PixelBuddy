package io.github.nobuddyorg.pixelbuddy.feature.settings

import android.content.Context
import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nobuddyorg.pixelbuddy.core.data.apps.LauncherApp
import io.github.nobuddyorg.pixelbuddy.core.data.crash.CrashReport
import io.github.nobuddyorg.pixelbuddy.core.data.crash.CrashReportStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings with state: loads crash reports from [store] off the main thread and lists [hiddenApps];
 * [onShowApp] shows a hidden app on home again.
 */
@Composable
fun SettingsRoute(
    store: CrashReportStore,
    appVersion: String,
    hiddenApps: Flow<List<LauncherApp>>,
    onShowApp: (LauncherApp) -> Unit,
    modifier: Modifier = Modifier,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    val context = LocalContext.current
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

/** Opens the system share sheet. Only ever called from the user's tap on "Share". */
private fun Context.shareCrashReport(text: String) {
    val send =
        Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.crash_report_title))
            .putExtra(Intent.EXTRA_TEXT, text)
    startActivity(Intent.createChooser(send, getString(R.string.crash_report_share_title)))
}
