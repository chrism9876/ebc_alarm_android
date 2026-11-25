package com.ebcalarm.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** AlarmManager fires this at alarm time: start ringing and queue the next occurrence. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val index = intent.getIntExtra(EXTRA_INDEX, -1)
        val service = Intent(context, AlarmService::class.java)
            .setAction(AlarmService.ACTION_RING)
            .putExtra(EXTRA_INDEX, index)
        ContextCompat.startForegroundService(context, service)

        if (!intent.getBooleanExtra(EXTRA_SNOOZE, false))
            AlarmScheduler.scheduleAll(context)
    }

    companion object {
        const val ACTION_ALARM = "com.ebcalarm.app.ALARM"
        const val EXTRA_INDEX = "index"
        const val EXTRA_SNOOZE = "snooze"
    }
}

/** Alarms are lost on reboot and need recalculating when the time or time zone changes. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmScheduler.scheduleAll(context)
        SyncWorker.schedule(context)
    }
}
