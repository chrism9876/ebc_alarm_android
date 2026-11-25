package com.ebcalarm.app.data

import org.json.JSONArray
import org.json.JSONObject

/** One alarm. Same fields as struct alarmType in the firmware and the alarm server's JSON. */
data class Alarm(
    val active: Boolean = false,
    val hour: Int = 0,
    val min: Int = 0,
    val sec: Int = 0,
    val month: Int = 0,          // 0 = any month
    val day: Int = 0,            // 0 = any day of the month
    val weekDay: Int = 0,        // 0 = any, 1 = Sunday ... 7 = Saturday
    val weekDays: Boolean = false,
    val weekendDays: Boolean = false,
    val recurringType: Int = 0,  // 0 off, 1 sec, 2 min, 3 hour, 4 day, 5 month
    val recurringInterval: Int = 0,
    val message: String = "",
    val duration: Int = 10,
    val buzzer: Int = 4,
    val bright: Boolean = false,
    val fx: Int = 1,             // 1 = blink
    val actionType: Int = 0,
    val actionNum: Int = 0,
    val name: String = "",       // label for the apps only, ignored by the clock
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("active", active); put("hour", hour); put("min", min); put("sec", sec)
        put("month", month); put("day", day); put("weekDay", weekDay)
        put("weekDays", weekDays); put("weekendDays", weekendDays)
        put("recurringType", recurringType); put("recurringInterval", recurringInterval)
        put("message", message); put("duration", duration); put("buzzer", buzzer)
        put("bright", bright); put("fx", fx); put("actionType", actionType); put("actionNum", actionNum)
        if (name.isNotBlank()) put("name", name)
    }

    companion object {
        fun fromJson(o: JSONObject) = Alarm(
            active = o.optBoolean("active"),
            hour = o.optInt("hour"), min = o.optInt("min"), sec = o.optInt("sec"),
            month = o.optInt("month"), day = o.optInt("day"), weekDay = o.optInt("weekDay"),
            weekDays = o.optBoolean("weekDays"), weekendDays = o.optBoolean("weekendDays"),
            recurringType = o.optInt("recurringType"), recurringInterval = o.optInt("recurringInterval"),
            message = o.optStringOrEmpty("message"),
            duration = o.optInt("duration", 10), buzzer = o.optInt("buzzer", 4),
            bright = o.optBoolean("bright"), fx = o.optInt("fx", 1),
            actionType = o.optInt("actionType"), actionNum = o.optInt("actionNum"),
            name = o.optStringOrEmpty("name"),
        )
    }
}

/** Clock settings the server can set. null = leave it to the clock. */
data class ClockSettings(val dstMode: Int? = null) {
    fun toJson(): JSONObject = JSONObject().put("DSTmode", dstMode ?: JSONObject.NULL)

    companion object {
        fun fromJson(o: JSONObject?): ClockSettings =
            if (o == null || o.isNull("DSTmode")) ClockSettings() else ClockSettings(o.optInt("DSTmode"))
    }
}

data class Config(
    val version: Int,
    val updated: String?,
    val settings: ClockSettings,
    val alarms: List<Alarm>,
) {
    /** what the server stores and returns, also used for the local cache */
    fun toJson(): JSONObject = JSONObject().apply {
        put("version", version)
        put("updated", updated ?: JSONObject.NULL)
        put("settings", settings.toJson())
        put("alarms", JSONArray().apply { alarms.forEach { put(it.toJson()) } })
    }

    /** body of PUT /api/alarms */
    fun toSaveJson(): JSONObject = JSONObject().apply {
        put("settings", settings.toJson())
        put("alarms", JSONArray().apply { alarms.forEach { put(it.toJson()) } })
    }

    companion object {
        fun fromJson(o: JSONObject): Config {
            val arr = o.optJSONArray("alarms") ?: JSONArray()
            return Config(
                version = o.optInt("version", 1),
                updated = o.optStringOrEmpty("updated").ifBlank { null },
                settings = ClockSettings.fromJson(o.optJSONObject("settings")),
                alarms = (0 until arr.length()).map { Alarm.fromJson(arr.getJSONObject(it)) },
            )
        }
    }
}

data class ServerStatus(
    val version: Int,
    val lastClockFetch: Double?,
    val lastClockAddress: String?,
    val serverTime: Double,
    val pushEnabled: Boolean = false,
    val pushReason: String = "",
)

data class RingState(
    val seq: Int,
    val ringing: Boolean,
    val index: Int,
    val event: String,            // last event: start, stop or snooze
    val snoozeUntilMillis: Long,
)

internal fun JSONObject.optStringOrEmpty(key: String): String =
    if (!has(key) || isNull(key)) "" else optString(key, "")
