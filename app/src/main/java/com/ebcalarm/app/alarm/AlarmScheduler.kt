package com.ebcalarm.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ebcalarm.app.MainActivity
import com.ebcalarm.app.data.Prefs
import com.ebcalarm.app.data.Schedule
import java.time.ZonedDateTime

/** Puts the next occurrence of every active alarm into AlarmManager (as an alarm clock, exact and Doze-proof). */
object AlarmScheduler {
    private const val MAX = 16                    // the clock has 12, leave room
    private const val SNOOZE_BASE = 100

    fun scheduleAll(context: Context) {
        val prefs = Prefs(context)
        val config = prefs.cachedConfig
        val am = context.getSystemService(AlarmManager::class.java)
        val now = ZonedDateTime.now()

        for (i in 0 until MAX) {
            val pi = alarmIntent(context, i, snooze = false)
            val alarm = config?.alarms?.getOrNull(i)
            val next = if (prefs.ringOnPhone && alarm != null && prefs.ringsOnPhone(i)) Schedule.next(alarm, now) else null
            if (next == null) am.cancel(pi) else setExact(context, am, next.toInstant().toEpochMilli(), pi)
        }
    }

    fun snooze(context: Context, index: Int, minutes: Int) =
        snoozeAt(context, index, System.currentTimeMillis() + minutes * 60_000L)

    /** ring [index] again at [atMillis] (also used when the clock was snoozed, so the phone rings with it) */
    fun snoozeAt(context: Context, index: Int, atMillis: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        setExact(context, am, atMillis, alarmIntent(context, index, snooze = true))
    }

    fun cancelSnooze(context: Context, index: Int) {
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context, index, snooze = true))
    }

    /** the next alarm this phone will ring, for display */
    fun nextOnPhone(context: Context): Pair<Int, ZonedDateTime>? {
        val prefs = Prefs(context)
        if (!prefs.ringOnPhone) return null
        val now = ZonedDateTime.now()
        return prefs.cachedConfig?.alarms?.mapIndexedNotNull { i, a ->
            if (!prefs.ringsOnPhone(i)) null else Schedule.next(a, now)?.let { i to it }
        }?.minByOrNull { it.second }
    }

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun setExact(context: Context, am: AlarmManager, atMillis: Long, pi: PendingIntent) {
        if (!canScheduleExact(context)) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            return
        }
        val show = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        am.setAlarmClock(AlarmManager.AlarmClockInfo(atMillis, show), pi)
    }

    private fun alarmIntent(context: Context, index: Int, snooze: Boolean): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_ALARM)
            .putExtra(AlarmReceiver.EXTRA_INDEX, index)
            .putExtra(AlarmReceiver.EXTRA_SNOOZE, snooze)
        return PendingIntent.getBroadcast(
            context, if (snooze) SNOOZE_BASE + index else index, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
