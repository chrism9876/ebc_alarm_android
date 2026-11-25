package com.ebcalarm.app.alarm

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.ebcalarm.app.data.Api
import com.ebcalarm.app.data.Prefs
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlin.concurrent.thread

/**
 * Receives the alarm server's Firebase pushes: the clock started, stopped or snoozed an alarm.
 * High-priority FCM messages may start the ringing service even when the app is asleep.
 */
class PushService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Push.register(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val d = message.data
        val index = d["index"]?.toIntOrNull() ?: return
        Log.i(TAG, "push ${d["type"]} alarm $index from ${d["source"]}")
        val prefs = Prefs(this)

        when (d["type"]) {
            "start" -> if (prefs.ringOnPhone && prefs.ringsOnPhone(index)) {
                ContextCompat.startForegroundService(
                    this,
                    Intent(this, AlarmService::class.java)
                        .setAction(AlarmService.ACTION_RING)
                        .putExtra(AlarmReceiver.EXTRA_INDEX, index),
                )
            }
            "stop" -> {
                AlarmScheduler.cancelSnooze(this, index)            // also ends a snooze started elsewhere
                if (AlarmService.ringing.value?.index == index)
                    AlarmService.remote(this, AlarmService.ACTION_REMOTE_STOP, index, 0)
            }
            "snooze" -> {
                val until = d["snoozeUntil"]?.toLongOrNull() ?: 0L
                if (AlarmService.ringing.value?.index == index)
                    AlarmService.remote(this, AlarmService.ACTION_REMOTE_SNOOZE, index, until)
                else if (until > System.currentTimeMillis() && prefs.ringOnPhone && prefs.ringsOnPhone(index))
                    AlarmScheduler.snoozeAt(this, index, until)
            }
        }
    }

    companion object { const val TAG = "EbcPush" }
}

/** Firebase registration with the alarm server. Does nothing when google-services.json was missing at build time. */
object Push {
    fun available(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()

    /** latest token, for the server to skip this phone when it relays this phone's own action */
    @Volatile var token: String? = null
        private set

    fun refresh(context: Context) {
        if (!available(context)) return
        FirebaseMessaging.getInstance().token.addOnSuccessListener { register(context.applicationContext, it) }
    }

    fun register(context: Context, newToken: String) {
        token = newToken
        val prefs = Prefs(context)
        if (prefs.serverUrl.isBlank()) return
        thread(name = "push-register") {
            runCatching { Api(prefs.serverUrl, prefs.token).registerPhone(newToken, Build.MODEL) }
                .onSuccess { Log.i(PushService.TAG, "registered with the alarm server") }
                .onFailure { Log.w(PushService.TAG, "could not register with the alarm server: ${it.message}") }
        }
    }
}
