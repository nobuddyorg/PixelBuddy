package io.github.nobuddyorg.pixelbuddy.feature.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Returns a function that calls [onGranted] once the coarse location permission is there, asking for it first if
 * needed; [onDenied] if the user says no.
 */
@Composable
internal fun rememberApproximateLocation(
    onGranted: () -> Unit,
    onDenied: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val granted by rememberUpdatedState(onGranted)
    val denied by rememberUpdatedState(onDenied)
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            if (ok) granted() else denied()
        }
    return {
        if (context.hasCoarseLocation()) {
            granted()
        } else {
            permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }
}

private fun Context.hasCoarseLocation() =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
