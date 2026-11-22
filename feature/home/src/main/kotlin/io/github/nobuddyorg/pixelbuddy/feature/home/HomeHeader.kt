package io.github.nobuddyorg.pixelbuddy.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.nobuddyorg.pixelbuddy.core.designsystem.BatteryState
import io.github.nobuddyorg.pixelbuddy.core.designsystem.HpBar
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyColors
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelClock
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelText
import java.time.LocalDateTime

/**
 * Home header: big pixel clock and the info line (date, battery as HP bar, weather slot for #11).
 * Tapping the clock opens the alarms, tapping the date opens the calendar. [clockHeight] and [clockShadow] come
 * from the settings.
 */
@Composable
fun HomeHeader(
    now: LocalDateTime,
    is24Hour: Boolean,
    battery: BatteryState?,
    onClockClick: () -> Unit,
    onDateClick: () -> Unit,
    modifier: Modifier = Modifier,
    clockHeight: Dp = 72.dp,
    clockShadow: Boolean = true,
    weather: (@Composable () -> Unit)? = null,
) {
    val locale = LocalConfiguration.current.locales[0]
    val clockText = HeaderFormat.clock(now.toLocalTime(), is24Hour)
    val small = PixelText.small
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier =
                Modifier.clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.home_open_alarms),
                    onClick = onClockClick,
                ),
        ) {
            PixelClock(
                text = clockText,
                height = clockHeight,
                shadow = if (clockShadow) PixelBuddyColors.Shadow else null,
                contentDescription = clockText,
            )
            if (!is24Hour) {
                Spacer(Modifier.width(8.dp))
                BasicText(HeaderFormat.amPm(now.toLocalTime(), locale), style = small)
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 12.dp),
        ) {
            BasicText(
                text = HeaderFormat.date(now.toLocalDate(), locale),
                style = small.copy(color = PixelBuddyColors.Text),
                modifier =
                    Modifier.clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.home_open_calendar),
                        onClick = onDateClick,
                    ),
            )
            if (battery != null) BatteryIndicator(battery, small)
            weather?.invoke()
        }
    }
}

@Composable
private fun BatteryIndicator(
    battery: BatteryState,
    style: TextStyle,
) {
    val locale = LocalConfiguration.current.locales[0]
    val percent = HeaderFormat.percent(battery.level, locale)
    val description =
        when {
            battery.full -> stringResource(R.string.home_battery_full)
            battery.charging -> stringResource(R.string.home_battery_charging, percent)
            else -> stringResource(R.string.home_battery, percent)
        }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        HpBar(battery)
        BasicText(
            percent,
            style =
                if (battery.isLow) {
                    style.copy(
                        color = PixelBuddyColors.HpLow,
                    )
                } else {
                    style.copy(color = PixelBuddyColors.Text)
                },
        )
    }
}
