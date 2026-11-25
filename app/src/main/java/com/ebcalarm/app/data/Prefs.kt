package com.ebcalarm.app.data

import android.content.Context
import org.json.JSONObject

/** Settings that live on this phone only. */
class Prefs(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("ebcalarm", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = p.getString("serverUrl", "") ?: ""
        set(v) = p.edit().putString("serverUrl", v.trim()).apply()

    var token: String
        get() = p.getString("token", "") ?: ""
        set(v) = p.edit().putString("token", v.trim()).apply()

    /** ring the alarms on this phone as well as on the clock */
    var ringOnPhone: Boolean
        get() = p.getBoolean("ringOnPhone", true)
        set(v) = p.edit().putBoolean("ringOnPhone", v).apply()

    var snoozeMinutes: Int
        get() = p.getInt("snoozeMinutes", 10)
        set(v) = p.edit().putInt("snoozeMinutes", v.coerceIn(1, 60)).apply()

    /** stop ringing after this many minutes if nobody reacts */
    var ringMinutes: Int
        get() = p.getInt("ringMinutes", 10)
        set(v) = p.edit().putInt("ringMinutes", v.coerceIn(1, 60)).apply()

    /** alarm numbers (0-based) that should NOT ring on this phone */
    var mutedOnPhone: Set<Int>
        get() = (p.getStringSet("mutedOnPhone", emptySet()) ?: emptySet()).mapNotNull { it.toIntOrNull() }.toSet()
        set(v) = p.edit().putStringSet("mutedOnPhone", v.map { it.toString() }.toSet()).apply()

    fun ringsOnPhone(index: Int) = index !in mutedOnPhone

    /** last config received from the server, used offline and by the alarm scheduler */
    var cachedConfig: Config?
        get() = p.getString("cachedConfig", null)?.let { runCatching { Config.fromJson(JSONObject(it)) }.getOrNull() }
        set(v) = p.edit().putString("cachedConfig", v?.toJson()?.toString()).apply()
}
