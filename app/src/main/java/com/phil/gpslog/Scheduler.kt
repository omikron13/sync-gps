package com.phil.gpslog

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock

/**
 * Schedules the repeating "wake up, get a GPS fix" alarm using exact alarms.
 * We deliberately avoid a foreground service (and its mandatory notification):
 * the trade-off is that Doze mode may occasionally delay a fix.
 */
object Scheduler {

    // Log a location every 5 minutes.
    const val INTERVAL_MS = 5 * 60 * 1000L

    // Email the accumulated track every 6 hours.
    const val SEND_INTERVAL_MS = 6 * 60 * 60 * 1000L

    private const val REQUEST_CODE = 4210

    private fun alarmIntent(context: Context): PendingIntent {
        val intent = Intent(context, LocationAlarmReceiver::class.java).apply {
            action = LocationAlarmReceiver.ACTION_TICK
        }
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }

    fun scheduleNext(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = SystemClock.elapsedRealtime() + INTERVAL_MS
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    alarmIntent(context)
                )
            } else {
                am.setExact(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    alarmIntent(context)
                )
            }
        } catch (e: SecurityException) {
            // Exact-alarm permission missing on Android 12+; fall back to inexact.
            am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, alarmIntent(context))
        }
    }

    fun start(context: Context) {
        Prefs.setEnabled(context, true)
        scheduleNext(context)
    }

    fun stop(context: Context) {
        Prefs.setEnabled(context, false)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(alarmIntent(context))
    }
}
