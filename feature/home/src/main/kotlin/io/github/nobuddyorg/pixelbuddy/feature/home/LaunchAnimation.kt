package io.github.nobuddyorg.pixelbuddy.feature.home

import android.app.ActivityOptions
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import androidx.compose.ui.unit.IntRect

/** Where a tapped app's icon is on home, in window coordinates, so the app can open from there. */
data class LaunchFrom(
    val iconInWindow: IntRect,
)

/** The icon's bounds on screen and the clip-reveal animation from them, for `LauncherApps.startMainActivity`. */
internal class LaunchAnimation(
    val sourceBounds: Rect,
    val options: Bundle,
)

/**
 * The opening animation for an app tapped on home: the app grows out of its icon ([from]). [view] is home's view;
 * the animation's start is relative to it, the source bounds are on screen.
 */
internal fun launchAnimation(
    view: View,
    from: LaunchFrom,
): LaunchAnimation? {
    val icon = from.iconInWindow
    if (icon.width <= 0 || icon.height <= 0) return null
    val inWindow = IntArray(2).also(view::getLocationInWindow)
    val onScreen = IntArray(2).also(view::getLocationOnScreen)
    val x = icon.left - inWindow[0]
    val y = icon.top - inWindow[1]
    val options = ActivityOptions.makeClipRevealAnimation(view, x, y, icon.width, icon.height).toBundle()
    val screenLeft = onScreen[0] + x
    val screenTop = onScreen[1] + y
    return LaunchAnimation(Rect(screenLeft, screenTop, screenLeft + icon.width, screenTop + icon.height), options)
}
