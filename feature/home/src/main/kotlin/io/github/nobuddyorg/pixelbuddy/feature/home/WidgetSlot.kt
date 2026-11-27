package io.github.nobuddyorg.pixelbuddy.feature.home

import android.appwidget.AppWidgetHostView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.nobuddyorg.pixelbuddy.core.data.widgets.HomeWidgets
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyColors

/**
 * The widget in the slot (#26), drawn by its app (not pixelated), in a thin pixel frame like the rest of home.
 * [heightDp] is the height chosen in the settings; the widget is told its size so it can pick its layout.
 */
@Composable
fun WidgetSlot(
    widgets: HomeWidgets,
    id: Int,
    heightDp: Int,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .border(2.dp, PixelBuddyColors.TextMuted.copy(alpha = 0.5f))
            .padding(4.dp)
            .height(heightDp.dp),
    ) {
        val widthDp = maxWidth.value
        // A new widget id needs a new view.
        key(id) {
            AndroidView(
                factory = { context -> widgets.createView(context, id) ?: android.view.View(context) },
                update = { view -> if (view is AppWidgetHostView) widgets.resize(view, widthDp, heightDp.toFloat()) },
                modifier = Modifier.fillMaxWidth().height(heightDp.dp),
            )
        }
    }
}
