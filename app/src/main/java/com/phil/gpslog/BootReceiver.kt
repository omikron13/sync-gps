package com.phil.gpslog

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-arms the tracking alarm after a reboot so it keeps running silently. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (Prefs.isEnabled(context)) {
            Scheduler.scheduleNext(context)
        }
    }
}
