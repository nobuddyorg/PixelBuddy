package io.github.nobuddyorg.pixelbuddy.feature.home

import android.content.Intent
import android.os.BatteryManager
import io.github.nobuddyorg.pixelbuddy.core.designsystem.BatteryState
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import android.text.format.DateFormat as AndroidDateFormat

/** Text shown in the home header, formatted for the user's locale and 12/24 h setting. */
object HeaderFormat {
    fun clock(
        time: LocalTime,
        is24Hour: Boolean,
    ): String = time.format(DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm", Locale.ROOT))

    /** AM/PM marker for the 12 h clock, e.g. "PM" or "nachm.". */
    fun amPm(
        time: LocalTime,
        locale: Locale,
    ): String = time.format(DateTimeFormatter.ofPattern("a", locale))

    /** Short weekday, day and month in the locale's order, e.g. "Fri, 06 Nov" or "Fr., 06. Nov.". */
    fun date(
        date: LocalDate,
        locale: Locale,
    ): String =
        date.format(DateTimeFormatter.ofPattern(AndroidDateFormat.getBestDateTimePattern(locale, "EEEddMMM"), locale))

    fun percent(
        level: Int,
        locale: Locale,
    ): String = NumberFormat.getPercentInstance(locale).format(level / 100.0)

    /** Maps the sticky `ACTION_BATTERY_CHANGED` broadcast; null if it carries no level. */
    fun battery(intent: Intent?): BatteryState? {
        if (intent == null) return null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val percent = (level * 100 / scale).coerceIn(0, 100)
        val full = plugged && (status == BatteryManager.BATTERY_STATUS_FULL || percent == 100)
        return BatteryState(
            level = percent,
            charging = !full && (status == BatteryManager.BATTERY_STATUS_CHARGING || plugged),
            full = full,
        )
    }
}
