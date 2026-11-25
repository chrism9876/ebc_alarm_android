package com.ebcalarm.app.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.ebcalarm.app.R
import com.ebcalarm.app.data.Alarm
import com.ebcalarm.app.data.Api
import com.ebcalarm.app.data.Prefs
import com.ebcalarm.app.data.Schedule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.concurrent.thread

/**
 * Rings an alarm: plays the alarm sound, vibrates, and shows a call-like notification with Snooze and
 * Dismiss that opens the full-screen [AlarmActivity] over the lock screen.
 */
class AlarmService : Service() {

    data class RingInfo(val index: Int, val title: String, val time: String, val message: String)

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { stopRinging() }
    @Volatile private var session = 0                      // bumped when ringing stops, ends the polling thread

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val index = intent?.getIntExtra(AlarmReceiver.EXTRA_INDEX, -1) ?: -1
        when (intent?.action) {
            ACTION_RING -> ring(index)

            // pressed on this phone: act here and tell the server, which tells the clock
            ACTION_SNOOZE -> {
                _ringing.value?.let {
                    val minutes = Prefs(this).snoozeMinutes
                    AlarmScheduler.snooze(this, it.index, minutes)
                    report("snooze", it.index, minutes)
                }
                stopRinging()
            }
            ACTION_DISMISS -> {
                _ringing.value?.let { report("stop", it.index, 0) }
                stopRinging()
            }

            // done on the clock (push or poll): follow it, don't report back
            ACTION_REMOTE_STOP -> stopRinging()
            ACTION_REMOTE_SNOOZE -> {
                val until = intent.getLongExtra(EXTRA_UNTIL, 0L)
                if (index >= 0 && until > System.currentTimeMillis()) AlarmScheduler.snoozeAt(this, index, until)
                stopRinging()
            }
            else -> stopRinging()                         // restarted without an intent
        }
        return START_NOT_STICKY
    }

    private fun ring(index: Int) {
        val prefs = Prefs(this)
        val alarm = prefs.cachedConfig?.alarms?.getOrNull(index) ?: Alarm()
        val info = RingInfo(
            index = index,
            title = alarm.name.ifBlank { "Alarm ${index + 1}" },
            time = Schedule.timeText(alarm, seconds = false),
            message = alarm.message,
        )

        createChannel()
        val alreadyRinging = _ringing.value?.index == index   // e.g. the phone's own alarm and the clock's push
        _ringing.value = info                               // before posting: the full-screen screen reads it
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(info, prefs.snoozeMinutes),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED else 0,
        )
        if (alreadyRinging) return
        startPolling(index)

        startSound()
        startVibration()
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, prefs.ringMinutes * 60_000L)
    }

    private fun buildNotification(info: RingInfo, snoozeMinutes: Int): Notification {
        val fullScreen = PendingIntent.getActivity(
            this, 0,
            Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = listOf(info.time, info.message).filter { it.isNotBlank() }.joinToString(" · ")
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(info.title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setColor(0xFF2F6FDE.toInt())
            .setColorized(true)                              // big coloured call-like card
            .setFullScreenIntent(fullScreen, true)           // full screen over the lock screen
            .setContentIntent(fullScreen)
            .addAction(0, "Snooze $snoozeMinutes min", serviceIntent(this, ACTION_SNOOZE, 1))
            .addAction(0, "Dismiss", serviceIntent(this, ACTION_DISMISS, 2))
            .build()
    }

    private fun startSound() {
        stopSound()
        val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)      // alarm volume, allowed in Do Not Disturb "alarms"
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmService, uri)
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()
    }

    private fun stopSound() {
        player?.runCatching { stop(); release() }
        player = null
    }

    private fun startVibration() {
        val v = if (Build.VERSION.SDK_INT >= 31)
            getSystemService(VibratorManager::class.java).defaultVibrator
        else
            @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator = v
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0)
        if (Build.VERSION.SDK_INT >= 33)
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        else
            @Suppress("DEPRECATION") v.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
    }

    /** tell the server what was pressed on this phone; it passes it on to the clock */
    private fun report(event: String, index: Int, minutes: Int) {
        val prefs = Prefs(this)
        if (prefs.serverUrl.isBlank()) return
        thread(name = "ring-report") {
            runCatching { Api(prefs.serverUrl, prefs.token).postRing(event, index, minutes, Push.token) }
        }
    }

    /**
     * While ringing, ask the server every few seconds whether the clock was dismissed or snoozed.
     * Covers the case where no push arrives (Firebase not set up, or a delayed message).
     */
    private fun startPolling(index: Int) {
        val prefs = Prefs(this)
        if (prefs.serverUrl.isBlank()) return
        val mySession = ++session
        thread(name = "ring-poll") {
            val api = Api(prefs.serverUrl, prefs.token)
            var baseSeq = -1
            while (session == mySession) {
                runCatching { api.ringState() }.onSuccess { st ->
                    if (baseSeq < 0 || st.event == "start") baseSeq = maxOf(baseSeq, st.seq)
                    else if (st.seq > baseSeq && st.index == index) {
                        handler.post {
                            if (session != mySession) return@post
                            if (st.event == "snooze" && st.snoozeUntilMillis > System.currentTimeMillis())
                                AlarmScheduler.snoozeAt(this, index, st.snoozeUntilMillis)
                            stopRinging()
                        }
                    }
                }
                Thread.sleep(3000)
            }
        }
    }

    private fun stopRinging() {
        session++
        handler.removeCallbacks(timeout)
        stopSound()
        vibrator?.cancel()
        vibrator = null
        _ringing.value = null                               // closes the full-screen screen
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        session++
        handler.removeCallbacks(timeout)
        stopSound()
        vibrator?.cancel()
        _ringing.value = null
        super.onDestroy()
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, "Ringing alarms", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Shown full screen while an alarm rings"
            setSound(null, null)                            // the service plays the alarm sound itself
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_RING = "com.ebcalarm.app.RING"
        const val ACTION_SNOOZE = "com.ebcalarm.app.SNOOZE"
        const val ACTION_DISMISS = "com.ebcalarm.app.DISMISS"
        const val ACTION_REMOTE_STOP = "com.ebcalarm.app.REMOTE_STOP"
        const val ACTION_REMOTE_SNOOZE = "com.ebcalarm.app.REMOTE_SNOOZE"
        const val EXTRA_UNTIL = "until"
        private const val CHANNEL_ID = "ringing"
        private const val NOTIFICATION_ID = 42

        private val _ringing = MutableStateFlow<RingInfo?>(null)
        /** the alarm that is ringing now, or null */
        val ringing: StateFlow<RingInfo?> = _ringing

        fun serviceIntent(context: Context, action: String, requestCode: Int): PendingIntent =
            PendingIntent.getService(
                context, requestCode, Intent(context, AlarmService::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        /** the clock stopped or snoozed: follow it without reporting back */
        fun remote(context: Context, action: String, index: Int, untilMillis: Long) {
            context.startService(
                Intent(context, AlarmService::class.java).setAction(action)
                    .putExtra(AlarmReceiver.EXTRA_INDEX, index).putExtra(EXTRA_UNTIL, untilMillis)
            )
        }

        fun send(context: Context, action: String) {
            context.startService(Intent(context, AlarmService::class.java).setAction(action))
        }

        /** ring now, for the "Test alarm" button */
        fun test(context: Context, index: Int) {
            androidx.core.content.ContextCompat.startForegroundService(
                context,
                Intent(context, AlarmService::class.java).setAction(ACTION_RING).putExtra(AlarmReceiver.EXTRA_INDEX, index),
            )
        }
    }
}
