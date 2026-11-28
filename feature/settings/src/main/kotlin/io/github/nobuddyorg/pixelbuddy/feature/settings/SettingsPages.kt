package io.github.nobuddyorg.pixelbuddy.feature.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nobuddyorg.pixelbuddy.core.data.apps.LauncherApp
import io.github.nobuddyorg.pixelbuddy.core.data.calendar.CalendarInfo
import io.github.nobuddyorg.pixelbuddy.core.data.crash.CrashReport
import io.github.nobuddyorg.pixelbuddy.core.data.icons.RenderTimings
import io.github.nobuddyorg.pixelbuddy.core.data.settings.Accent
import io.github.nobuddyorg.pixelbuddy.core.data.settings.Appearance
import io.github.nobuddyorg.pixelbuddy.core.data.settings.CalendarSettings
import io.github.nobuddyorg.pixelbuddy.core.data.settings.ClockFormat
import io.github.nobuddyorg.pixelbuddy.core.data.settings.GestureAction
import io.github.nobuddyorg.pixelbuddy.core.data.settings.Gestures
import io.github.nobuddyorg.pixelbuddy.core.data.settings.IconStyleSetting
import io.github.nobuddyorg.pixelbuddy.core.data.settings.NotificationSettings
import io.github.nobuddyorg.pixelbuddy.core.data.settings.Settings
import io.github.nobuddyorg.pixelbuddy.core.data.settings.SizeStep
import io.github.nobuddyorg.pixelbuddy.core.data.settings.Sky
import io.github.nobuddyorg.pixelbuddy.core.data.settings.SkySteps
import io.github.nobuddyorg.pixelbuddy.core.data.settings.WallpaperSync
import io.github.nobuddyorg.pixelbuddy.core.data.settings.WidgetSettings
import io.github.nobuddyorg.pixelbuddy.core.data.sky.SkyPhase
import io.github.nobuddyorg.pixelbuddy.core.data.widgets.WidgetProvider
import io.github.nobuddyorg.pixelbuddy.core.designsystem.DitheredSky
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyColors
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyTheme
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelClock
import io.github.nobuddyorg.pixelbuddy.core.pixelengine.IconPreset
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private typealias Update = ((Settings) -> Settings) -> Unit

private fun Update.appearance(change: (Appearance) -> Appearance) = this { it.copy(appearance = change(it.appearance)) }

private fun Update.gestures(change: (Gestures) -> Gestures) = this { it.copy(gestures = change(it.gestures)) }

/** The next value of an enum, wrapping around: for rows that cycle through their options. */
private inline fun <reified T : Enum<T>> T.next(): T = enumValues<T>().let { it[(ordinal + 1) % it.size] }

@Composable
internal fun MainPage(
    state: SettingsState,
    onAction: (SettingsAction) -> Unit,
) {
    val settings = state.settings
    val appearance = settings.appearance

    @Composable
    fun Category(
        page: SettingsPage,
        title: Int,
        subtitle: String,
    ) = ListRow(stringResource(title), subtitle, { onAction(SettingsAction.Navigate(page)) })

    Spacer(Modifier.height(16.dp))
    Category(
        SettingsPage.Appearance,
        R.string.settings_appearance,
        listOfNotNull(
            stringResource(appearance.accent.label),
            stringResource(R.string.scanlines).takeIf { appearance.scanlines },
        ).joinToString(" · "),
    )
    Category(
        SettingsPage.Clock,
        R.string.settings_clock,
        clockFormatLabel(appearance.clockFormat, state.systemIs24Hour),
    )
    Category(SettingsPage.Icons, R.string.settings_icons, iconStyleName(settings.iconStyle))
    Category(
        SettingsPage.AppIcons,
        R.string.icons_single_apps,
        stringResource(R.string.icons_single_apps_summary, settings.iconOverrides.size),
    )
    Category(
        SettingsPage.Apps,
        R.string.settings_apps,
        stringResource(R.string.settings_apps_summary, state.favorites.size, state.hiddenApps.size),
    )
    Category(SettingsPage.Gestures, R.string.settings_gestures, stringResource(R.string.settings_gestures_summary))
    if (state.widget != null) {
        Category(
            SettingsPage.Widget,
            R.string.settings_widget,
            state.widget.label ?: stringResource(R.string.widget_none),
        )
    }
    if (state.calendars != null) {
        Category(
            SettingsPage.Calendar,
            R.string.settings_calendar,
            stringResource(
                if (settings.calendar.enabled) R.string.calendar_summary_on else R.string.calendar_summary_off,
            ),
        )
    }
    if (state.notificationAccessOn != null) {
        Category(
            SettingsPage.Notifications,
            R.string.settings_notifications,
            stringResource(
                if (settings.notifications.needsAccess) {
                    R.string.notifications_summary_on
                } else {
                    R.string.notifications_summary_off
                },
            ),
        )
    }
    Category(
        SettingsPage.Wallpaper,
        R.string.settings_wallpaper,
        stringResource(if (appearance.skyFollowsSun) R.string.sky_follows_sun else appearance.sky.label),
    )
    Category(
        SettingsPage.Weather,
        R.string.weather_title,
        stringResource(if (settings.weather.enabled) R.string.weather_on else R.string.weather_off),
    )
    Category(SettingsPage.Backup, R.string.backup_title, stringResource(R.string.backup_summary))
    Category(
        SettingsPage.About,
        R.string.settings_about,
        stringResource(R.string.settings_about_summary, state.appVersion),
    )
    if (state.canRunSetup) {
        ListRow(
            title = stringResource(R.string.settings_run_setup),
            subtitle = stringResource(R.string.settings_run_setup_summary),
            onClick = { onAction(SettingsAction.RunSetup) },
        )
    }
}

@Composable
internal fun AppearancePage(
    appearance: Appearance,
    update: Update,
) {
    SectionHeader(stringResource(R.string.accent_title))
    SwatchRow(
        options = Accent.entries,
        selected = appearance.accent,
        color = { Color(it.argb) },
        label = { stringResource(it.label) },
        onSelect = { accent -> update.appearance { it.copy(accent = accent) } },
    )
    Spacer(Modifier.height(8.dp))
    ToggleRow(
        title = stringResource(R.string.scanlines),
        subtitle = stringResource(R.string.scanlines_summary),
        checked = appearance.scanlines,
        onChange = { on -> update.appearance { it.copy(scanlines = on) } },
        on = stringResource(R.string.toggle_on),
        off = stringResource(R.string.toggle_off),
    )
}

@Composable
internal fun ClockPage(
    appearance: Appearance,
    systemIs24Hour: Boolean,
    update: Update,
) {
    val is24Hour =
        when (appearance.clockFormat) {
            ClockFormat.System -> systemIs24Hour
            ClockFormat.TwelveHour -> false
            ClockFormat.TwentyFourHour -> true
        }
    SectionHeader(stringResource(R.string.preview))
    PixelClock(
        text = if (is24Hour) "21:09" else "9:09",
        height = appearance.clockSize.clockHeightDp.dp,
        shadow = if (appearance.clockShadow) PixelBuddyColors.Shadow else null,
    )
    Spacer(Modifier.height(16.dp))
    ListRow(
        title = stringResource(R.string.clock_format),
        subtitle = clockFormatLabel(appearance.clockFormat, systemIs24Hour),
        onClick = { update.appearance { it.copy(clockFormat = it.clockFormat.next()) } },
    )
    ToggleRow(
        title = stringResource(R.string.clock_shadow),
        checked = appearance.clockShadow,
        onChange = { on -> update.appearance { it.copy(clockShadow = on) } },
        on = stringResource(R.string.toggle_on),
        off = stringResource(R.string.toggle_off),
    )
    ListRow(
        title = stringResource(R.string.clock_size),
        subtitle = stringResource(appearance.clockSize.label),
        onClick = { update.appearance { it.copy(clockSize = it.clockSize.next()) } },
    )
    ListRow(
        title = stringResource(R.string.text_size),
        subtitle = stringResource(appearance.textSize.label) + " · " + stringResource(R.string.text_size_summary),
        onClick = { update.appearance { it.copy(textSize = it.textSize.next()) } },
    )
}

/** Every app, those with their own icon first; a tap opens the editor for that app. */
@Composable
internal fun AppIconsPage(
    apps: List<LauncherApp>,
    settings: Settings,
    onAction: (SettingsAction) -> Unit,
) {
    Column {
        Spacer(Modifier.height(8.dp))
        BasicText(stringResource(R.string.icons_single_apps_text), style = MutedStyle)
        Spacer(Modifier.height(8.dp))
        val (own, others) = apps.partition { settings.iconOverrideFor(it.key) != null }
        (own + others).forEach { app ->
            val override = settings.iconOverrideFor(app.key)
            ListRow(
                title = app.displayLabel(),
                subtitle =
                    if (override == null) {
                        stringResource(R.string.icons_app_global)
                    } else {
                        stringResource(R.string.icons_app_own) + " · " + iconStyleName(override.style)
                    },
                onClick = { onAction(SettingsAction.EditAppIcon(app)) },
            )
        }
    }
}

@Composable
internal fun AppsPage(
    favorites: List<LauncherApp>,
    hiddenApps: List<LauncherApp>,
    update: Update,
) {
    SectionHeader(stringResource(R.string.favorites_title))
    if (favorites.isEmpty()) {
        BasicText(stringResource(R.string.favorites_empty), style = BodyStyle)
    }
    favorites.forEachIndexed { index, app ->
        val label = app.displayLabel()
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 56.dp)) {
            BasicText(label, style = BodyStyle, maxLines = 1, modifier = Modifier.weight(1f))
            if (index > 0) {
                IconButton("▲", stringResource(R.string.favorite_up, label)) {
                    update { it.withFavoriteMoved(app.key, -1) }
                }
            } else {
                Spacer(Modifier.size(48.dp))
            }
            if (index < favorites.lastIndex) {
                IconButton("▼", stringResource(R.string.favorite_down, label)) {
                    update { it.withFavoriteMoved(app.key, 1) }
                }
            } else {
                Spacer(Modifier.size(48.dp))
            }
            IconButton("✕", stringResource(R.string.favorite_remove, label)) { update { it.withoutFavorite(app.key) } }
        }
    }

    SectionHeader(stringResource(R.string.settings_hidden_apps))
    if (hiddenApps.isEmpty()) {
        BasicText(stringResource(R.string.settings_hidden_apps_empty), style = BodyStyle)
    } else {
        val showAgain = stringResource(R.string.settings_hidden_app_show)
        hiddenApps.forEach { app ->
            ListRow(title = app.displayLabel(), subtitle = showAgain, onClick = { update { it.withShown(app.key) } })
        }
    }
}

@Composable
private fun LauncherApp.displayLabel(): String =
    if (isMainProfile) label else stringResource(R.string.settings_work_app, label)

/** A 48 dp button showing a single [symbol]; [description] is what TalkBack reads. */
@Composable
private fun IconButton(
    symbol: String,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(48.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description },
    ) {
        BasicText(symbol, style = BodyStyle)
    }
}

/**
 * What the gestures do. "Lock" is offered only if [lockServiceOn] isn't null (locking is available at all); while a
 * gesture locks, the page explains the lock service and, while it is off, offers turning it on.
 */
@Composable
internal fun GesturesPage(
    gestures: Gestures,
    lockServiceOn: Boolean?,
    update: Update,
    onAction: (SettingsAction) -> Unit,
) {
    val next: (GestureAction) -> GestureAction = { it.nextGesture(canLock = lockServiceOn != null) }
    Spacer(Modifier.height(16.dp))
    ListRow(
        title = stringResource(R.string.gesture_swipe_up),
        subtitle = stringResource(gestures.swipeUp.label),
        onClick = { update.gestures { it.copy(swipeUp = next(it.swipeUp)) } },
    )
    ListRow(
        title = stringResource(R.string.gesture_swipe_down),
        subtitle = stringResource(gestures.swipeDown.label),
        onClick = { update.gestures { it.copy(swipeDown = next(it.swipeDown)) } },
    )
    ListRow(
        title = stringResource(R.string.gesture_double_tap),
        subtitle = stringResource(gestures.doubleTap.label),
        onClick = { update.gestures { it.copy(doubleTap = next(it.doubleTap)) } },
    )
    if (gestures.effectiveLongPress == gestures.longPress) {
        ListRow(
            title = stringResource(R.string.gesture_long_press),
            subtitle = stringResource(gestures.longPress.label),
            onClick = { update.gestures { it.copy(longPress = next(it.longPress)) } },
        )
    } else {
        // Without a swipe to the settings, the long press is the way there: not changeable.
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(vertical = 8.dp)
                .semantics(true) {},
        ) {
            BasicText(stringResource(R.string.gesture_long_press), style = BodyStyle)
            BasicText(
                stringResource(GestureAction.Settings.label) + " · " +
                    stringResource(R.string.gesture_long_press_forced),
                style = MutedStyle,
            )
        }
    }
    if (gestures.usesLock && lockServiceOn != null) {
        LockServiceInfo(on = lockServiceOn, onTurnOn = { onAction(SettingsAction.TurnOnLockService) })
    }
}

/**
 * Opt-in features that need the notification access (#24): what they show, what the access means, and while it
 * isn't granted how to grant it.
 */
@Composable
internal fun NotificationsPage(
    notifications: NotificationSettings,
    accessOn: Boolean?,
    update: Update,
    onAction: (SettingsAction) -> Unit,
) {
    Spacer(Modifier.height(16.dp))
    ToggleRow(
        title = stringResource(R.string.notification_dots),
        subtitle = stringResource(R.string.notification_dots_summary),
        checked = notifications.dots,
        onChange = { on -> update { it.copy(notifications = it.notifications.copy(dots = on)) } },
        on = stringResource(R.string.toggle_on),
        off = stringResource(R.string.toggle_off),
    )
    ToggleRow(
        title = stringResource(R.string.media_controls),
        subtitle = stringResource(R.string.media_controls_summary),
        checked = notifications.media,
        onChange = { on -> update { it.copy(notifications = it.notifications.copy(media = on)) } },
        on = stringResource(R.string.toggle_on),
        off = stringResource(R.string.toggle_off),
    )
    if (notifications.needsAccess && accessOn != null) {
        Column {
            SectionHeader(stringResource(R.string.notification_access_title))
            BasicText(
                stringResource(if (accessOn) R.string.notification_access_on else R.string.notification_access_off),
                style = BodyStyle.copy(color = if (accessOn) PixelBuddyTheme.style.accent else BodyStyle.color),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Spacer(Modifier.height(8.dp))
            BasicText(stringResource(R.string.notification_access_explanation), style = MutedStyle)
            if (!accessOn) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    stringResource(R.string.notification_access_allow),
                    { onAction(SettingsAction.AllowNotificationAccess) },
                )
                BasicText(stringResource(R.string.notification_access_restricted), style = MutedStyle)
            }
        }
    }
}

/** The widget slot (#26): which widget, where, how tall; add, change or remove it. */
@Composable
internal fun WidgetPage(
    widget: WidgetSettings,
    ui: WidgetUi,
    update: Update,
    onAction: (SettingsAction) -> Unit,
) {
    Column {
        Spacer(Modifier.height(16.dp))
        val label = ui.label
        when {
            widget.id == null -> {
                BasicText(stringResource(R.string.widget_explain), style = MutedStyle)
            }

            label == null -> {
                BasicText(stringResource(R.string.widget_gone), style = BodyStyle)
            }

            else -> {
                BasicText(label, style = BodyStyle)
            }
        }
        ui.message?.let {
            BasicText(
                stringResource(it),
                style = MutedStyle,
                modifier = Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Spacer(Modifier.height(8.dp))
        TextButton(
            stringResource(if (widget.id == null) R.string.widget_add else R.string.widget_change),
            { onAction(SettingsAction.Navigate(SettingsPage.WidgetPicker)) },
        )
        if (widget.id == null) return@Column
        TextButton(stringResource(R.string.widget_remove), { onAction(SettingsAction.RemoveWidget) })
        if (label == null) return@Column
        ToggleRow(
            title = stringResource(R.string.widget_below),
            subtitle = stringResource(R.string.widget_below_summary),
            checked = widget.below,
            onChange = { on -> update { it.copy(widget = it.widget.copy(below = on)) } },
            on = stringResource(R.string.toggle_on),
            off = stringResource(R.string.toggle_off),
        )
        val heights = ui.heights
        if (heights != null && heights.resizable) {
            // Steps of 8 dp: the pixel grid of home.
            val height = (widget.heightDp ?: heights.default).coerceIn(heights.min, heights.max)
            PixelSlider(
                label = stringResource(R.string.widget_height),
                value = height / HEIGHT_STEP,
                range = (heights.min + HEIGHT_STEP - 1) / HEIGHT_STEP..heights.max / HEIGHT_STEP,
                valueText = "${height / HEIGHT_STEP * HEIGHT_STEP} dp",
                onChange = { steps -> update { it.copy(widget = it.widget.copy(heightDp = steps * HEIGHT_STEP)) } },
            )
        }
    }
}

private const val HEIGHT_STEP = 8

/** Every widget the apps offer, by app; picking one adds it. */
@Composable
internal fun WidgetPickerPage(
    providers: List<WidgetProvider>,
    onAction: (SettingsAction) -> Unit,
) {
    Spacer(Modifier.height(16.dp))
    if (providers.isEmpty()) {
        BasicText(stringResource(R.string.widget_no_providers), style = MutedStyle)
    }
    providers.forEach { provider ->
        ListRow(
            title = provider.label,
            subtitle = provider.appLabel,
            onClick = { onAction(SettingsAction.AddWidget(provider)) },
        )
    }
}

/**
 * The next calendar event on home (#27): on/off (asking for the calendar permission when turned on), all-day events,
 * and which calendars.
 */
@Composable
internal fun CalendarPage(
    calendar: CalendarSettings,
    calendars: List<CalendarInfo>,
    denied: Boolean,
    update: Update,
    onAction: (SettingsAction) -> Unit,
) {
    Spacer(Modifier.height(16.dp))
    ToggleRow(
        title = stringResource(R.string.calendar_next_event),
        subtitle = stringResource(R.string.calendar_next_event_summary),
        checked = calendar.enabled,
        onChange = { on ->
            if (on) {
                onAction(SettingsAction.EnableCalendar)
            } else {
                update { it.copy(calendar = it.calendar.copy(enabled = false)) }
            }
        },
        on = stringResource(R.string.toggle_on),
        off = stringResource(R.string.toggle_off),
    )
    if (denied) {
        BasicText(
            stringResource(R.string.calendar_denied),
            style = MutedStyle,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    if (!calendar.enabled) return
    ToggleRow(
        title = stringResource(R.string.calendar_all_day),
        subtitle = stringResource(R.string.calendar_all_day_summary),
        checked = calendar.allDay,
        onChange = { on -> update { it.copy(calendar = it.calendar.copy(allDay = on)) } },
        on = stringResource(R.string.toggle_on),
        off = stringResource(R.string.toggle_off),
    )
    if (calendars.isEmpty()) return
    SectionHeader(stringResource(R.string.calendar_calendars))
    calendars.forEach { info ->
        val chosen = calendar.calendars
        val shown = chosen == null || info.id in chosen
        ToggleRow(
            title = info.name,
            subtitle = info.account,
            checked = shown,
            onChange = { on ->
                update { settings ->
                    val current = settings.calendar.calendars ?: calendars.mapTo(HashSet()) { it.id }
                    val chosen = if (on) current + info.id else current - info.id
                    // Every calendar chosen is the same as all, which also takes in calendars added later.
                    val stored = chosen.takeUnless { it.containsAll(calendars.map(CalendarInfo::id)) }
                    settings.copy(calendar = settings.calendar.copy(calendars = stored))
                }
            },
            on = stringResource(R.string.toggle_on),
            off = stringResource(R.string.toggle_off),
        )
    }
}

/** What the lock service is and does (#16), and while it is off how to turn it on. */
@Composable
private fun LockServiceInfo(
    on: Boolean,
    onTurnOn: () -> Unit,
) {
    Column {
        SectionHeader(stringResource(R.string.lock_service_title))
        BasicText(
            stringResource(if (on) R.string.lock_service_on else R.string.lock_service_off),
            style = BodyStyle.copy(color = if (on) PixelBuddyTheme.style.accent else BodyStyle.color),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Spacer(Modifier.height(8.dp))
        BasicText(stringResource(R.string.lock_service_explanation), style = MutedStyle)
        if (!on) {
            Spacer(Modifier.height(8.dp))
            TextButton(stringResource(R.string.lock_service_turn_on), onTurnOn)
            BasicText(stringResource(R.string.lock_service_restricted), style = MutedStyle)
        }
    }
}

/** The next gesture action to cycle to; [GestureAction.Lock] only if locking is available. */
private fun GestureAction.nextGesture(canLock: Boolean): GestureAction {
    val next = next()
    return if (next == GestureAction.Lock && !canLock) next.next() else next
}

/**
 * Sky, dithering and the wallpaper. The sky is one of the fixed [Sky] gradients or follows the sun (#28), from the
 * weather's place if [hasWeatherPlace], else from the time zone.
 */
@Composable
internal fun WallpaperPage(
    appearance: Appearance,
    status: WallpaperStatus,
    update: Update,
    onAction: (SettingsAction) -> Unit,
    hasWeatherPlace: Boolean = false,
) {
    SectionHeader(stringResource(R.string.sky_title))
    ToggleRow(
        title = stringResource(R.string.sky_follows_sun),
        subtitle =
            stringResource(
                if (hasWeatherPlace) R.string.sky_follows_sun_place else R.string.sky_follows_sun_zone,
            ),
        checked = appearance.skyFollowsSun,
        onChange = { on -> update.appearance { it.copy(skyFollowsSun = on) } },
        on = stringResource(R.string.toggle_on),
        off = stringResource(R.string.toggle_off),
    )
    if (appearance.skyFollowsSun) {
        SkyPhaseStrip(appearance)
        ChoiceRow(
            label = stringResource(R.string.sky_steps),
            options = SkySteps.entries,
            selected = appearance.skySteps,
            text = { stringResource(if (it == SkySteps.Four) R.string.sky_steps_four else R.string.sky_steps_eight) },
            onSelect = { steps -> update.appearance { it.copy(skySteps = steps) } },
        )
        ToggleRow(
            title = stringResource(R.string.sky_dark_nights),
            subtitle = stringResource(R.string.sky_dark_nights_summary),
            checked = appearance.darkNights,
            onChange = { on -> update.appearance { it.copy(darkNights = on) } },
            on = stringResource(R.string.toggle_on),
            off = stringResource(R.string.toggle_off),
        )
    } else {
        SwatchRow(
            options = Sky.entries,
            selected = appearance.sky,
            color = { Color(it.stops.last()) },
            label = { stringResource(it.label) },
            onSelect = { sky -> update.appearance { it.copy(sky = sky) } },
            swatch = { sky ->
                DitheredSky(
                    colors = sky.stops.map { Color(it) },
                    dithering = appearance.dithering,
                    scanlines = false,
                    modifier = Modifier.size(width = 32.dp, height = 72.dp),
                )
            },
        )
    }
    ToggleRow(
        title = stringResource(R.string.dithering),
        subtitle = stringResource(R.string.dithering_summary),
        checked = appearance.dithering,
        onChange = { on -> update.appearance { it.copy(dithering = on) } },
        on = stringResource(R.string.toggle_on),
        off = stringResource(R.string.toggle_off),
    )

    SectionHeader(stringResource(R.string.wallpaper_set))
    Row {
        // The wallpaper exactly as it will look: sky, dithering and scanlines from the theme.
        DitheredSky(
            modifier =
                Modifier
                    .size(width = 96.dp, height = 200.dp)
                    .border(2.dp, PixelBuddyColors.TextMuted),
        )
        Column(Modifier.padding(start = 16.dp).widthIn(max = 240.dp)) {
            BasicText(stringResource(R.string.wallpaper_explain), style = MutedStyle)
            WallpaperTarget.entries.forEach { target ->
                TextButton(stringResource(target.label), { onAction(SettingsAction.SetWallpaper(target)) })
            }
        }
    }
    ChoiceRow(
        label = stringResource(R.string.wallpaper_sync),
        options = WallpaperSync.entries,
        selected = appearance.wallpaperSync,
        text = { stringResource(it.label) },
        onSelect = { sync -> update.appearance { it.copy(wallpaperSync = sync) } },
    )
    BasicText(stringResource(R.string.wallpaper_sync_summary), style = MutedStyle)
    val message =
        when (status) {
            WallpaperStatus.Idle -> null
            WallpaperStatus.Working -> R.string.wallpaper_working
            WallpaperStatus.Done -> R.string.wallpaper_done
            WallpaperStatus.Failed -> R.string.wallpaper_failed
            WallpaperStatus.NotAllowed -> R.string.wallpaper_not_allowed
        }
    if (message != null) {
        BasicText(
            stringResource(message),
            style = BodyStyle,
            modifier = Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
internal fun BackupPage(
    message: Int?,
    onAction: (SettingsAction) -> Unit,
) {
    Column {
        Spacer(Modifier.height(16.dp))
        BasicText(stringResource(R.string.backup_text), style = BodyStyle)
        Spacer(Modifier.height(8.dp))
        TextButton(stringResource(R.string.backup_export), { onAction(SettingsAction.ExportSettings) })
        TextButton(stringResource(R.string.backup_import), { onAction(SettingsAction.ImportSettings) })
        if (message != null) {
            BasicText(
                stringResource(message),
                style = BodyStyle,
                modifier = Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Composable
internal fun AboutPage(
    state: SettingsState,
    onAction: (SettingsAction) -> Unit,
) {
    Column {
        Spacer(Modifier.height(16.dp))
        BasicText(stringResource(R.string.settings_version, state.appVersion), style = BodyStyle)
        Spacer(Modifier.height(8.dp))
        ListRow(
            title = stringResource(R.string.source_code),
            subtitle = stringResource(R.string.source_code_link),
            onClick = { onAction(SettingsAction.OpenSource) },
        )
        ListRow(
            title = stringResource(R.string.licenses),
            subtitle = stringResource(R.string.licenses_summary),
            onClick = { onAction(SettingsAction.Navigate(SettingsPage.Licenses)) },
        )
        WeatherAttribution(onClick = { onAction(SettingsAction.Weather(WeatherAction.OpenAttribution)) })
        ListRow(
            title = stringResource(R.string.crash_reports_title),
            subtitle =
                if (state.reports.isEmpty()) {
                    stringResource(R.string.crash_reports_empty)
                } else {
                    stringResource(R.string.crash_reports_count, state.reports.size)
                },
            onClick = { onAction(SettingsAction.Navigate(SettingsPage.CrashReports)) },
        )
        IconTimings(state.neonTimings, state.pixelTimings)
    }
}

/** Render times of the icons on this phone (#29), so the budget can be checked without tools. */
@Composable
private fun IconTimings(
    neon: RenderTimings.Summary?,
    pixel: RenderTimings.Summary?,
) {
    if (neon == null && pixel == null) return
    Column(Modifier.padding(vertical = 8.dp).semantics(true) {}) {
        BasicText(stringResource(R.string.icon_timings), style = BodyStyle)
        listOfNotNull(
            neon?.let { R.plurals.icon_timings_neon to it },
            pixel?.let { R.plurals.icon_timings_pixels to it },
        ).forEach { (text, t) ->
            BasicText(pluralStringResource(text, t.count, t.medianMs, t.p90Ms, t.count), style = MutedStyle)
        }
    }
}

@Composable
internal fun LicensesPage(fontLicense: String?) {
    Spacer(Modifier.height(16.dp))

    @Composable
    fun Entry(
        name: String,
        license: String,
    ) = Column(Modifier.padding(vertical = 8.dp).semantics(true) {}) {
        BasicText(name, style = BodyStyle)
        BasicText(license, style = MutedStyle)
    }

    Entry(stringResource(R.string.license_font), stringResource(R.string.license_font_name))
    Entry(stringResource(R.string.weather_attribution), stringResource(R.string.weather_attribution_licence))
    Entry(stringResource(R.string.license_libraries), stringResource(R.string.license_apache))
    if (fontLicense != null) {
        SectionHeader(stringResource(R.string.license_font_text))
        BasicText(
            fontLicense,
            style = MutedStyle.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp),
        )
    }
}

@Composable
internal fun CrashReportsPage(
    reports: List<CrashReport>,
    zone: ZoneId,
    onAction: (SettingsAction) -> Unit,
) {
    Column {
        val locale = LocalConfiguration.current.locales[0]
        val timeFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale).withZone(zone)
        Spacer(Modifier.height(16.dp))
        BasicText(stringResource(R.string.crash_reports_privacy), style = MutedStyle)
        Spacer(Modifier.height(8.dp))
        if (reports.isEmpty()) {
            BasicText(stringResource(R.string.crash_reports_empty), style = BodyStyle)
            return@Column
        }
        reports.forEach { report ->
            ListRow(
                title = timeFormat.format(report.time),
                subtitle = report.summary,
                onClick = { onAction(SettingsAction.OpenReport(report.id)) },
            )
        }
        TextButton(stringResource(R.string.crash_reports_delete_all), { onAction(SettingsAction.DeleteAllReports) })
    }
}

@Composable
private fun clockFormatLabel(
    format: ClockFormat,
    systemIs24Hour: Boolean,
): String =
    when (format) {
        ClockFormat.System -> {
            stringResource(
                R.string.clock_format_system,
                stringResource(if (systemIs24Hour) R.string.clock_format_24 else R.string.clock_format_12),
            )
        }

        ClockFormat.TwelveHour -> {
            stringResource(R.string.clock_format_12)
        }

        ClockFormat.TwentyFourHour -> {
            stringResource(R.string.clock_format_24)
        }
    }

@Composable
internal fun iconStyleName(setting: IconStyleSetting): String {
    val preset = stringResource(setting.basePreset.label)
    return if (setting.custom == null) preset else stringResource(R.string.icons_custom, preset)
}

private val Accent.label: Int
    get() =
        when (this) {
            Accent.Orange -> R.string.accent_orange
            Accent.Pink -> R.string.accent_pink
            Accent.Yellow -> R.string.accent_yellow
            Accent.Green -> R.string.accent_green
            Accent.Cyan -> R.string.accent_cyan
            Accent.Lavender -> R.string.accent_lavender
        }

/** The day's skies from night to night, as a preview of what following the sun looks like with these settings. */
@Composable
private fun SkyPhaseStrip(appearance: Appearance) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.clearAndSetSemantics {},
        ) {
            SkyPhase.day(fourSteps = appearance.skySteps == SkySteps.Four).forEach { phase ->
                DitheredSky(
                    colors = phase.stops(appearance.darkNights).map { Color(it) },
                    dithering = appearance.dithering,
                    scanlines = false,
                    modifier = Modifier.size(width = 24.dp, height = 72.dp),
                )
            }
        }
        BasicText(stringResource(R.string.sky_phases), style = MutedStyle, modifier = Modifier.padding(top = 4.dp))
    }
}

private val Sky.label: Int
    get() =
        when (this) {
            Sky.Dusk -> R.string.sky_dusk
            Sky.Night -> R.string.sky_night
            Sky.Dawn -> R.string.sky_dawn
            Sky.Forest -> R.string.sky_forest
            Sky.Ocean -> R.string.sky_ocean
            Sky.Mono -> R.string.sky_mono
        }

private val SizeStep.label: Int
    get() =
        when (this) {
            SizeStep.Small -> R.string.size_small
            SizeStep.Medium -> R.string.size_medium
            SizeStep.Large -> R.string.size_large
        }

private val GestureAction.label: Int
    get() =
        when (this) {
            GestureAction.Nothing -> R.string.gesture_nothing
            GestureAction.Search -> R.string.gesture_search
            GestureAction.Notifications -> R.string.gesture_notifications
            GestureAction.Settings -> R.string.gesture_settings
            GestureAction.Lock -> R.string.gesture_lock
        }

private val WallpaperSync.label: Int
    get() =
        when (this) {
            WallpaperSync.Off -> R.string.wallpaper_sync_off
            WallpaperSync.Home -> R.string.wallpaper_home
            WallpaperSync.Lock -> R.string.wallpaper_lock
            WallpaperSync.Both -> R.string.wallpaper_both
        }

internal val IconPreset.label: Int
    get() =
        when (this) {
            IconPreset.EightBit -> R.string.icon_preset_eight_bit
            IconPreset.SixteenBit -> R.string.icon_preset_sixteen_bit
            IconPreset.Pocket -> R.string.icon_preset_pocket
            IconPreset.Neon -> R.string.icon_preset_neon
            IconPreset.Mono -> R.string.icon_preset_mono
            IconPreset.NeonLines -> R.string.icon_preset_neon_lines
            IconPreset.NeonSingle -> R.string.icon_preset_neon_single
        }

private val WallpaperTarget.label: Int
    get() =
        when (this) {
            WallpaperTarget.Home -> R.string.wallpaper_home
            WallpaperTarget.Lock -> R.string.wallpaper_lock
            WallpaperTarget.Both -> R.string.wallpaper_both
        }
