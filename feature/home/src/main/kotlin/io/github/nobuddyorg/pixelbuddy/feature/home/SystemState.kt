package io.github.nobuddyorg.pixelbuddy.feature.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import io.github.nobuddyorg.pixelbuddy.core.designsystem.BatteryState
import java.time.Clock
import java.time.LocalDateTime

/**
 * The current minute, updated by `ACTION_TIME_TICK` only while home is visible (started); no background work.
 * Time, time-zone and 12/24 h changes also refresh it.
 */
@Composable
fun rememberCurrentTime(clock: Clock = Clock.systemDefaultZone()): State<LocalDateTime> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(LocalDateTime.now(clock)) }
    LifecycleStartEffect(clock) {
        state.value = LocalDateTime.now(clock)
        val receiver = register(context, TIME_EVENTS) { state.value = LocalDateTime.now(clock) }
        onStopOrDispose { context.unregisterReceiver(receiver) }
    }
    return state
}

/** Whether the user chose the 24-hour clock; re-read whenever home becomes visible. */
@Composable
fun rememberIs24Hour(): State<Boolean> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(DateFormat.is24HourFormat(context)) }
    LifecycleStartEffect(Unit) {
        state.value = DateFormat.is24HourFormat(context)
        val receiver = register(context, TIME_EVENTS) { state.value = DateFormat.is24HourFormat(context) }
        onStopOrDispose { context.unregisterReceiver(receiver) }
    }
    return state
}

/** Battery from the sticky `ACTION_BATTERY_CHANGED` broadcast (no permission), only while home is visible. */
@Composable
fun rememberBatteryState(): State<BatteryState?> {
    val context = LocalContext.current
    val state = remember { mutableStateOf<BatteryState?>(null) }
    LifecycleStartEffect(Unit) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    state.value = HeaderFormat.battery(intent)
                }
            }
        val sticky =
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        HeaderFormat.battery(sticky)?.let { state.value = it }
        onStopOrDispose { context.unregisterReceiver(receiver) }
    }
    return state
}

private val TIME_EVENTS =
    IntentFilter().apply {
        addAction(Intent.ACTION_TIME_TICK)
        addAction(Intent.ACTION_TIME_CHANGED)
        addAction(Intent.ACTION_TIMEZONE_CHANGED)
    }

private fun register(
    context: Context,
    filter: IntentFilter,
    onEvent: () -> Unit,
): BroadcastReceiver {
    val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) = onEvent()
        }
    ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    return receiver
}
