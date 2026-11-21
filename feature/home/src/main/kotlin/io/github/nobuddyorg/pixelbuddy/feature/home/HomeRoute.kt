package io.github.nobuddyorg.pixelbuddy.feature.home

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nobuddyorg.pixelbuddy.core.data.apps.AppRepository
import io.github.nobuddyorg.pixelbuddy.core.data.apps.LaunchResult

/** Home with state. Apps are only collected while home is visible (no background work). */
@Composable
fun HomeRoute(
    repository: AppRepository,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val apps by repository.apps.collectAsStateWithLifecycle(initialValue = emptyList())
    HomeScreen(
        apps = apps,
        onAppClick = { app ->
            if (repository.launch(app) is LaunchResult.Failed) {
                Toast
                    .makeText(
                        context,
                        resources.getString(R.string.home_launch_failed, app.label),
                        Toast.LENGTH_SHORT,
                    ).show()
            }
        },
        onOpenSettings = onOpenSettings,
        modifier = modifier,
    )
}
