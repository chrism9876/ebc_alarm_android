package com.ebcalarm.app.data

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** The alarm server could not be reached: network error, or the reverse proxy answered 5xx. */
class ServerOfflineException(message: String) : Exception(message)
class UnauthorizedException : Exception("missing or wrong API token")
class ServerErrorException(message: String) : Exception(message)

/** Client for alarm_server/server.py. Blocking: call it from a background thread. */
class Api(baseUrl: String, private val token: String) {
    private val base = normalize(baseUrl)

    fun getAlarms(): Config = Config.fromJson(JSONObject(request("GET", "/api/alarms")))

    fun putAlarms(config: Config): Config =
        Config.fromJson(JSONObject(request("PUT", "/api/alarms", config.toSaveJson().toString())))

    fun status(): ServerStatus {
        val o = JSONObject(request("GET", "/api/status"))
        return ServerStatus(
            version = o.optInt("version"),
            lastClockFetch = if (o.isNull("lastClockFetch")) null else o.optDouble("lastClockFetch"),
            lastClockAddress = o.optStringOrEmpty("lastClockAddress").ifBlank { null },
            serverTime = o.optDouble("serverTime"),
            pushEnabled = o.optJSONObject("push")?.optBoolean("enabled") ?: false,
            pushReason = o.optJSONObject("push")?.optStringOrEmpty("reason") ?: "",
        )
    }

    /** what the clock is doing: see RING in alarm_server/server.py */
    fun ringState(): RingState {
        val o = JSONObject(request("GET", "/api/ring"))
        return RingState(
            seq = o.optInt("seq"),
            ringing = o.optBoolean("ringing"),
            index = if (o.isNull("index")) -1 else o.optInt("index", -1),
            event = o.optStringOrEmpty("event"),
            snoozeUntilMillis = if (o.isNull("snoozeUntil")) 0L else (o.optDouble("snoozeUntil") * 1000).toLong(),
        )
    }

    /** the phone dismissed (stop) or snoozed an alarm: the server passes it on to the clock */
    fun postRing(event: String, index: Int, minutes: Int, ownPushToken: String?) {
        val body = JSONObject().put("event", event).put("index", index).put("minutes", minutes).put("source", "phone")
        if (ownPushToken != null) body.put("token", ownPushToken)
        request("POST", "/api/ring", body.toString())
    }

    fun registerPhone(pushToken: String, name: String) {
        request("POST", "/api/devices", JSONObject().put("token", pushToken).put("name", name).toString())
    }

    private fun request(method: String, path: String, body: String? = null): String {
        val conn = try {
            URL(base + path).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw ServerErrorException("invalid server address")
        }
        try {
            conn.requestMethod = method
            conn.connectTimeout = 5000
            conn.readTimeout = 10000
            conn.useCaches = false
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Cache-Control", "no-cache")
            if (token.isNotBlank()) conn.setRequestProperty("X-Api-Token", token)
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            val text = (if (code < 400) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            when {
                code >= 500 -> throw ServerOfflineException("server offline ($code)")   // proxy up, server down
                code == 401 -> throw UnauthorizedException()
                code >= 400 -> throw ServerErrorException(
                    runCatching { JSONObject(text).optString("error") }.getOrNull()?.ifBlank { null } ?: "HTTP $code"
                )
            }
            return text
        } catch (e: IOException) {
            throw ServerOfflineException(e.message ?: "server unreachable")
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        fun normalize(url: String): String {
            var u = url.trim().trimEnd('/')
            if (u.isNotEmpty() && !u.startsWith("http://") && !u.startsWith("https://")) u = "http://$u"
            return u
        }
    }
}
