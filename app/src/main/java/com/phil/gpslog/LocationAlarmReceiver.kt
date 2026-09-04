package com.phil.gpslog

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The heart of the tracker. Fired by an exact alarm every ~5 min:
 *   1. immediately re-arms the next alarm (so the chain never breaks),
 *   2. grabs a single GPS fix and stores it,
 *   3. every 6 h, builds a GPX file and emails it, then clears the buffer.
 *
 * No foreground service, no notification.
 */
class LocationAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TICK = "com.phil.gpslog.TICK"
        private const val FIX_TIMEOUT_MS = 40_000L
    }

    override fun onReceive(context: Context, intent: Intent?) {
        // Always keep the chain alive first.
        if (!Prefs.isEnabled(context)) return
        Scheduler.scheduleNext(context)

        val appContext = context.applicationContext
        val pending = goAsync()

        // Short wake lock so the CPU stays up while we wait for a fix / send mail.
        val pm = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sync:tick")
        wl.setReferenceCounted(false)
        wl.acquire(FIX_TIMEOUT_MS + 20_000L)

        val finished = AtomicBoolean(false)
        fun done() {
            if (finished.compareAndSet(false, true)) {
                try { if (wl.isHeld) wl.release() } catch (_: Exception) {}
                try { pending.finish() } catch (_: Exception) {}
            }
        }

        requestSingleFix(appContext) { location ->
            if (location != null) {
                PointStore.append(
                    appContext,
                    PointStore.Point(
                        timeMs = System.currentTimeMillis(),
                        lat = location.latitude,
                        lon = location.longitude,
                        accuracy = if (location.hasAccuracy()) location.accuracy else 0f,
                        altitude = if (location.hasAltitude()) location.altitude else 0.0
                    )
                )
            }
            maybeSend(appContext)
            done()
        }
    }

    /** Send the buffered track by email if 6 h have elapsed and we have points. */
    private fun maybeSend(context: Context) {
        val now = System.currentTimeMillis()
        val last = Prefs.lastSend(context)
        // Initialise the clock on the very first run so we don't send instantly.
        if (last == 0L) {
            Prefs.setLastSend(context, now)
            return
        }
        if (now - last < Scheduler.SEND_INTERVAL_MS) return
        if (PointStore.count(context) == 0) {
            Prefs.setLastSend(context, now)
            return
        }
        if (!Prefs.isConfigured(context)) return

        val points = PointStore.readAll(context)
        val gpx = GpxBuilder.build(points)
        val ok = try {
            EmailSender.sendGpx(
                fromEmail = Prefs.fromEmail(context),
                appPassword = Prefs.appPassword(context),
                toEmail = Prefs.toEmail(context),
                gpx = gpx,
                points = points
            )
        } catch (e: Exception) {
            false
        }
        if (ok) {
            PointStore.clear(context)
            Prefs.setLastSend(context, now)
        }
        // On failure we keep the buffer and retry at the next tick that crosses 6 h.
    }

    /** One-shot location request that works from a background alarm. */
    private fun requestSingleFix(context: Context, callback: (Location?) -> Unit) {
        val fineGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) {
            callback(null)
            return
        }

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val provider = when {
            fineGranted && lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            fineGranted && lm.isProviderEnabled(LocationManager.PASSIVE_PROVIDER) -> LocationManager.PASSIVE_PROVIDER
            else -> null
        }
        if (provider == null) {
            // No provider available; fall back to last known if any.
            val last = try {
                lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            } catch (e: SecurityException) { null }
            callback(last)
            return
        }

        val thread = HandlerThread("fix").apply { start() }
        val handler = Handler(thread.looper)
        val delivered = AtomicBoolean(false)

        fun deliver(loc: Location?) {
            if (delivered.compareAndSet(false, true)) {
                try { thread.quitSafely() } catch (_: Exception) {}
                callback(loc)
            }
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                try { lm.removeUpdates(this) } catch (_: Exception) {}
                deliver(location)
            }
            override fun onProviderDisabled(p: String) {}
            override fun onProviderEnabled(p: String) {}
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(p: String?, status: Int, extras: Bundle?) {}
        }

        try {
            lm.requestLocationUpdates(provider, 0L, 0f, listener, thread.looper)
        } catch (e: SecurityException) {
            deliver(null)
            return
        }

        // Timeout: use last known location if no fresh fix arrives in time.
        handler.postDelayed({
            try { lm.removeUpdates(listener) } catch (_: Exception) {}
            val fallback = try {
                lm.getLastKnownLocation(provider)
            } catch (e: SecurityException) { null }
            deliver(fallback)
        }, FIX_TIMEOUT_MS)
    }
}
