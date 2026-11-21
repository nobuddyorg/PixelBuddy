package io.github.nobuddyorg.pixelbuddy.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyColors

/** Home screen. Empty for now; header, app list and rail follow in later issues. */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val title = stringResource(R.string.home_screen_title)
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(PixelBuddyColors.Navy)
                .semantics { paneTitle = title },
    )
}
