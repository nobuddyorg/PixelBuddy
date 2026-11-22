package io.github.nobuddyorg.pixelbuddy.core.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** The phone's approximate location, if the user allowed it. */
fun interface LocationSource {
    suspend fun approximate(): Coordinates?
}

/**
 * [LocationSource] on the platform [LocationManager] with the coarse location permission only: a recent last
 * known location, or else one fresh fix. No Google Play services, no updates in the background.
 */
class PlatformLocationSource(
    private val context: Context,
) : LocationSource {
    private val manager = context.getSystemService(LocationManager::class.java)

    override suspend fun approximate(): Coordinates? {
        if (manager == null || !hasPermission()) return null
        val location = lastKnown() ?: currentFix() ?: return null
        return Coordinates(location.latitude, location.longitude).rounded()
    }

    private fun hasPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun providers(): List<String> =
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }

    @Suppress("MissingPermission") // Checked in hasPermission().
    private fun lastKnown(): Location? {
        val now = SystemClock.elapsedRealtimeNanos()
        return providers()
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .filter { now - it.elapsedRealtimeNanos < MAX_AGE_NANOS }
            .maxByOrNull { it.elapsedRealtimeNanos }
    }

    @Suppress("MissingPermission") // Checked in hasPermission().
    private suspend fun currentFix(): Location? {
        val provider =
            providers().firstOrNull {
                it != LocationManager.PASSIVE_PROVIDER &&
                    runCatching { manager.isProviderEnabled(it) }.getOrDefault(false)
            }
                ?: return null
        return withTimeoutOrNull(FIX_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { continuation ->
                val cancel = CancellationSignal()
                continuation.invokeOnCancellation { cancel.cancel() }
                LocationManagerCompat.getCurrentLocation(
                    manager,
                    provider,
                    cancel,
                    ContextCompat.getMainExecutor(context),
                ) {
                    if (continuation.isActive) continuation.resume(it)
                }
            }
        }
    }

    private companion object {
        val MAX_AGE_NANOS = TimeUnit.HOURS.toNanos(1)
        const val FIX_TIMEOUT_MILLIS = 10_000L
    }
}
