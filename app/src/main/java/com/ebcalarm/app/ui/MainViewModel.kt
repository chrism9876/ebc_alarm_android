package com.ebcalarm.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ebcalarm.app.alarm.AlarmScheduler
import com.ebcalarm.app.alarm.Push
import com.ebcalarm.app.data.Alarm
import com.ebcalarm.app.data.Api
import com.ebcalarm.app.data.ClockSettings
import com.ebcalarm.app.data.Config
import com.ebcalarm.app.data.Prefs
import com.ebcalarm.app.data.ServerOfflineException
import com.ebcalarm.app.data.UnauthorizedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

enum class StatusKind { OK, WARN, NEUTRAL }

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)

    /** last config from the server (or the cache) */
    var saved by mutableStateOf<Config?>(null); private set
    /** working copy being edited */
    var work by mutableStateOf<Config?>(null); private set
    var offline by mutableStateOf(false); private set
    var status by mutableStateOf("Loading…"); private set
    var statusKind by mutableStateOf(StatusKind.NEUTRAL); private set
    var saving by mutableStateOf(false); private set
    var mutedOnPhone by mutableStateOf(prefs.mutedOnPhone); private set
    /** null = unknown yet; else whether the server can push to phones, and why not */
    var serverPush by mutableStateOf<Pair<Boolean, String>?>(null); private set

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    val dirty: Boolean get() = saved != null && work != saved

    init {
        prefs.cachedConfig?.let { saved = it; work = it }
        refresh()
    }

    private fun api() = Api(prefs.serverUrl, prefs.token)

    fun refresh() {
        if (prefs.serverUrl.isBlank()) {
            setStatus("Set the alarm server address in Settings", StatusKind.WARN)
            return
        }
        viewModelScope.launch {
            try {
                val cfg = withContext(Dispatchers.IO) { api().getAlarms() }
                adopt(cfg, keepEdits = dirty)
                offline = false
                pollStatus()
            } catch (e: ServerOfflineException) {
                setOffline()
            } catch (e: Exception) {
                setStatus("Could not load alarms: ${e.message}", StatusKind.WARN)
            }
        }
    }

    fun save() {
        val w = work ?: return
        saving = true
        viewModelScope.launch {
            try {
                val cfg = withContext(Dispatchers.IO) { api().putAlarms(w) }
                adopt(cfg, keepEdits = false)
                offline = false
                _messages.emit("Saved. The clock picks it up on its next sync.")
                pollStatus()
            } catch (e: ServerOfflineException) {
                setOffline()
                _messages.emit("Server offline - not saved. Your changes are still here, save again when it is back.")
            } catch (e: UnauthorizedException) {
                _messages.emit("Not saved: the server needs an API token. Set it in Settings.")
            } catch (e: Exception) {
                _messages.emit("Not saved: ${e.message}")
            } finally {
                saving = false
            }
        }
    }

    fun discard() { work = saved }

    fun updateAlarm(index: Int, change: (Alarm) -> Alarm) {
        val c = work ?: return
        work = c.copy(alarms = c.alarms.mapIndexed { i, a -> if (i == index) change(a) else a })
    }

    fun setDst(mode: Int?) {
        work = work?.copy(settings = ClockSettings(mode))
    }

    fun setRingsOnPhone(index: Int, rings: Boolean) {
        prefs.mutedOnPhone = if (rings) prefs.mutedOnPhone - index else prefs.mutedOnPhone + index
        mutedOnPhone = prefs.mutedOnPhone
        AlarmScheduler.scheduleAll(getApplication())
    }

    fun settingsChanged() {
        mutedOnPhone = prefs.mutedOnPhone
        AlarmScheduler.scheduleAll(getApplication())
        Push.refresh(getApplication())
        refresh()
    }

    suspend fun pollStatus() {
        if (prefs.serverUrl.isBlank()) return
        try {
            val s = withContext(Dispatchers.IO) { api().status() }
            serverPush = s.pushEnabled to s.pushReason
            if (offline) {                                   // server is back
                offline = false
                if (!dirty) refresh() else _messages.emit("Server is back - you can save now.")
            }
            val fetch = s.lastClockFetch
            if (fetch == null) {
                setStatus("Clock has not synced since the server started · config v${s.version}", StatusKind.WARN)
            } else {
                val age = s.serverTime - fetch
                setStatus("Clock synced ${ago(age)}${s.lastClockAddress?.let { " ($it)" } ?: ""} · config v${s.version}",
                          if (age < 300) StatusKind.OK else StatusKind.WARN)
            }
        } catch (e: Exception) {
            setOffline()
        }
    }

    private fun adopt(cfg: Config, keepEdits: Boolean) {
        prefs.cachedConfig = cfg
        saved = cfg
        if (!keepEdits) work = cfg
        AlarmScheduler.scheduleAll(getApplication())         // keep the phone's alarms in step with the server
    }

    private fun setOffline() {
        offline = true
        setStatus(
            if (saved != null) "Server offline · showing the last alarms this phone saw, changes can't be saved now"
            else "Server offline and no saved copy on this phone yet",
            StatusKind.WARN,
        )
    }

    private fun setStatus(text: String, kind: StatusKind) { status = text; statusKind = kind }

    private fun ago(sec: Double) = when {
        sec < 90 -> "${sec.roundToInt()} s ago"
        sec < 5400 -> "${(sec / 60).roundToInt()} min ago"
        else -> "${(sec / 3600).roundToInt()} h ago"
    }
}
