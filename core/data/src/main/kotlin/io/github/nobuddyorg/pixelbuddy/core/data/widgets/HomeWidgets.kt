package io.github.nobuddyorg.pixelbuddy.core.data.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.SizeF
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A widget an app offers, to pick for the slot on home (#26). */
data class WidgetProvider(
    val provider: ComponentName,
    val user: UserHandle,
    val label: String,
    val appLabel: String,
)

/** The outcome of adding a widget: bound right away, or the user must allow PixelBuddy to bind it first. */
sealed interface WidgetBinding {
    val id: Int

    data class Bound(
        override val id: Int,
    ) : WidgetBinding

    /** Start [intent] for a result; on OK the widget is bound. */
    data class NeedsPermission(
        override val id: Int,
        val intent: Intent,
    ) : WidgetBinding
}

/** How tall a widget wants to be and may be, in dp. */
data class WidgetHeights(
    val default: Int,
    val min: Int,
    val max: Int,
) {
    val resizable: Boolean get() = max > min
}

/**
 * The one widget slot on home (#26) through Android's app widget host. Widget ids are kept by Android across
 * restarts as long as [HOST_ID] stays the same; the slot's id is stored in the settings.
 */
class HomeWidgets(
    private val context: Context,
) {
    private val manager = AppWidgetManager.getInstance(context)
    private val users = context.getSystemService(UserManager::class.java)
    private val changeCount = MutableStateFlow(0)

    private val host =
        object : AppWidgetHost(context, HOST_ID) {
            // Widget apps installed, updated or removed: the slot checks its widget again.
            override fun onProvidersChanged() {
                changeCount.value++
            }
        }

    /** Increases when widget apps change, so the slot re-checks whether its widget is still there. */
    val changes: StateFlow<Int> = changeCount.asStateFlow()

    /** Call while home is visible (onStart/onStop), so widgets update only then. */
    fun startListening() = host.startListening()

    fun stopListening() = host.stopListening()

    /** Every widget of the main and work profile, by app and name. */
    fun providers(): List<WidgetProvider> {
        val pm = context.packageManager
        return profiles()
            .flatMap { manager.getInstalledProvidersForProfile(it) }
            .map { info ->
                WidgetProvider(
                    provider = info.provider,
                    user = info.profile,
                    label = info.loadLabel(pm).orEmpty().trim(),
                    appLabel =
                        runCatching {
                            pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString()
                        }.getOrDefault(info.provider.packageName),
                )
            }.sortedWith(compareBy({ it.appLabel.lowercase() }, { it.label.lowercase() }))
    }

    private fun profiles(): List<UserHandle> = users?.userProfiles ?: listOf(Process.myUserHandle())

    /** Reserves an id for [provider] and binds it if PixelBuddy may; otherwise the user is asked. */
    fun add(provider: WidgetProvider): WidgetBinding {
        val id = host.allocateAppWidgetId()
        val bound =
            if (provider.user == Process.myUserHandle()) {
                manager.bindAppWidgetIdIfAllowed(id, provider.provider, null)
            } else {
                // A work profile's widget.
                manager.bindAppWidgetIdIfAllowed(id, provider.user, provider.provider, null)
            }
        if (bound) return WidgetBinding.Bound(id)
        val intent =
            Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, provider.user)
        return WidgetBinding.NeedsPermission(id, intent)
    }

    /**
     * The widget's own setup screen, if it has one that must run before it shows; null if none or optional (it
     * stays reachable through the widget itself).
     */
    fun configureIntent(id: Int): Intent? {
        val info = manager.getAppWidgetInfo(id) ?: return null
        val configure = info.configure ?: return null
        val optional =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0
        if (optional) return null
        return Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
            .setComponent(configure)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
    }

    /** Gives the id back, e.g. when adding was cancelled or the widget is removed from the slot. */
    fun remove(id: Int) = host.deleteAppWidgetId(id)

    /** The widget's name, or null if its app is gone (uninstalled, disabled, profile removed). */
    fun label(id: Int): String? =
        manager
            .getAppWidgetInfo(id)
            ?.loadLabel(context.packageManager)
            ?.trim()

    /** How tall the widget wants to be and may be; null if it is gone. */
    fun heights(id: Int): WidgetHeights? {
        val info = manager.getAppWidgetInfo(id) ?: return null
        val density = context.resources.displayMetrics.density
        // The provider info is in pixels for this screen.
        val default = (info.minHeight / density).toInt().coerceAtLeast(MIN_HEIGHT_DP)
        val vertical = info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0
        if (!vertical) return WidgetHeights(default, default, default)
        val min = (info.minResizeHeight.takeIf { it > 0 } ?: info.minHeight).let { (it / density).toInt() }
        val max =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.maxResizeHeight > 0) {
                (info.maxResizeHeight / density).toInt()
            } else {
                MAX_HEIGHT_DP
            }
        val low = min.coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP)
        return WidgetHeights(default.coerceIn(low, max.coerceAtLeast(low)), low, max.coerceIn(low, MAX_HEIGHT_DP))
    }

    /** The widget's view, drawn by its app; null if it is gone. */
    fun createView(
        context: Context,
        id: Int,
    ): AppWidgetHostView? {
        val info = manager.getAppWidgetInfo(id) ?: return null
        return host.createView(context, id, info)
    }

    /** Tells the widget the size it gets, so it can choose its layout. */
    fun resize(
        view: AppWidgetHostView,
        widthDp: Float,
        heightDp: Float,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.updateAppWidgetSize(Bundle(), listOf(SizeF(widthDp, heightDp)))
        } else {
            val w = widthDp.toInt()
            val h = heightDp.toInt()
            @Suppress("DEPRECATION") // The only way before API 31.
            view.updateAppWidgetSize(Bundle(), w, h, w, h)
        }
    }

    companion object {
        /** Stable forever: Android keeps the widget ids per host id. */
        const val HOST_ID = 4242
        const val MIN_HEIGHT_DP = 48
        const val MAX_HEIGHT_DP = 480
    }
}
