package com.ebcalarm.app.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * When an alarm triggers. Mirrors checkNonRecur() in the firmware (12_alarms.ino):
 * the time must match and every filter that is set (month, day, weekday, weekdays/weekend) must match too.
 * The clock's "then repeat every ..." recurring option is not used on the phone.
 */
object Schedule {
    val DAYS = listOf("", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    val MONTHS = listOf("", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    fun matches(a: Alarm, date: LocalDate): Boolean {
        val wday = date.dayOfWeek.value % 7                    // firmware tm_wday: 0 = Sunday
        if (a.month > 0 && date.monthValue != a.month) return false
        if (a.day > 0 && date.dayOfMonth != a.day) return false
        if (a.weekDay > 0 && wday != a.weekDay - 1) return false
        if (!(a.weekDays && a.weekendDays)) {
            if (a.weekDays && (wday == 0 || wday == 6)) return false
            if (a.weekendDays && wday != 0 && wday != 6) return false
        }
        return true
    }

    /** next trigger strictly after [after], or null when inactive / never matches within a year */
    fun next(a: Alarm, after: ZonedDateTime): ZonedDateTime? {
        if (!a.active) return null
        var date = after.toLocalDate()
        repeat(400) {
            if (matches(a, date)) {
                val t = ZonedDateTime.of(date, LocalTime.of(a.hour, a.min, a.sec), after.zone)
                if (t.isAfter(after)) return t
            }
            date = date.plusDays(1)
        }
        return null
    }

    /** same as the web UI repeat select */
    fun repeatMode(a: Alarm): String {
        val filters = listOf(a.weekDays || a.weekendDays, a.weekDay > 0, a.month > 0 || a.day > 0).count { it }
        return when {
            filters > 1 -> "custom"
            a.weekDays && a.weekendDays -> "daily"
            a.weekDays -> "weekdays"
            a.weekendDays -> "weekend"
            a.weekDay > 0 -> "weekday"
            a.month > 0 || a.day > 0 -> "date"
            else -> "daily"
        }
    }

    fun applyRepeat(a: Alarm, mode: String): Alarm {
        if (mode == "custom") return a
        var r = a.copy(weekDays = mode == "weekdays", weekendDays = mode == "weekend")
        if (mode != "weekday") r = r.copy(weekDay = 0)
        if (mode != "date") r = r.copy(month = 0, day = 0)
        if (mode == "weekday" && r.weekDay == 0) r = r.copy(weekDay = 2)
        return r
    }

    fun timeText(a: Alarm, seconds: Boolean = a.sec != 0): String =
        "%02d:%02d".format(a.hour, a.min) + if (seconds) ":%02d".format(a.sec) else ""

    fun describe(a: Alarm): String {
        if (!a.active) return "Off"
        var s = timeText(a)
        s += when (repeatMode(a)) {
            "daily" -> " every day"
            "weekdays" -> " on weekdays"
            "weekend" -> " at the weekend"
            "weekday" -> " every " + DAYS[a.weekDay]
            "date" -> if (a.day > 0) " on ${a.day} " + (if (a.month > 0) MONTHS[a.month] else "of every month")
                      else " every day in " + MONTHS[a.month]
            else -> " (custom)"
        }
        if (a.recurringType > 0 && a.recurringInterval > 0)
            s += ", then every ${a.recurringInterval} " + listOf("", "s", "min", "h", "days", "months")[a.recurringType]
        if (a.fx == 1 && a.message.isNotEmpty()) s += " · note: blink is ignored with a message"
        return s
    }
}
