package com.ebcalarm.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ebcalarm.app.data.Alarm
import com.ebcalarm.app.data.Schedule
import kotlinx.coroutines.delay

private val REPEAT = listOf(
    "daily" to "Every day", "weekdays" to "Weekdays (Mon–Fri)", "weekend" to "Weekend (Sat–Sun)",
    "weekday" to "One day of the week", "date" to "On a date", "custom" to "Custom",
)
private val WEEKDAY = listOf(0 to "(any)") + (1..7).map { it to Schedule.DAYS[it] }
private val MONTH = listOf(0 to "Every month") + listOf(
    "January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December",
).mapIndexed { i, m -> (i + 1) to m }
private val SOUND = listOf(0 to "Off") + (1..9).map { it to if (it == 1) "1 beep" else if (it == 4) "4 beeps (alarm clock)" else "$it beeps" } +
    listOf(11 to "Big Ben", 12 to "Avicii")
private val ACTION = listOf(
    0 to "None", 1 to "Set GPIO pin high", 2 to "Set GPIO pin low", 3 to "Neopixel wake-up light",
    4 to "Neopixel wake-up light, chain next alarm", 5 to "Neopixels on (palette colour)", 6 to "Neopixels off (backlight colour)",
)
private val UNIT = listOf(0 to "(no repeat)", 1 to "seconds", 2 to "minutes", 3 to "hours", 4 to "days", 5 to "months")
private val DST = listOf<Pair<Int?, String>>(null to "Set on the clock", 0 to "Off (winter time)", 1 to "On (summer time)", 2 to "Automatic (EU dates)")
private val DST_HINT = mapOf(
    null to "The server leaves daylight saving to the clock's own setting.",
    0 to "The clock always uses standard (winter) time.",
    1 to "The clock always adds one hour (summer time).",
    2 to "Summer time from the last Sunday of March to the last Sunday of October.",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: MainViewModel, onOpenSettings: () -> Unit) {
    val c = LocalAppColors.current
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) { while (true) { delay(15_000); vm.pollStatus() } }

    Scaffold(
        containerColor = c.bg,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = if (vm.offline) c.offlineBar else c.bg),
                title = {
                    Column {
                        Text("Alarm Clock", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                        Text(
                            vm.status, fontSize = 12.sp, maxLines = 2, lineHeight = 14.sp,
                            color = when (vm.statusKind) { StatusKind.OK -> c.ok; StatusKind.WARN -> c.warn; else -> c.muted },
                        )
                    }
                },
                actions = {
                    TextButton(onClick = vm::discard, enabled = vm.dirty) { Text("Discard") }
                    Button(onClick = vm::save, enabled = vm.dirty && !vm.saving) { Text("Save") }
                    TextButton(onClick = onOpenSettings) { Text("⚙", fontSize = 20.sp) }
                },
            )
        },
    ) { padding ->
        val cfg = vm.work
        if (cfg == null) {
            Column(Modifier.padding(padding).padding(24.dp)) {
                Text(if (vm.prefs.serverUrl.isBlank()) "Open Settings (⚙) and enter the address of your alarm server, e.g. http://192.168.1.100:8080"
                     else "No alarms loaded yet.", color = c.muted)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = if (vm.prefs.serverUrl.isBlank()) onOpenSettings else vm::refresh) {
                    Text(if (vm.prefs.serverUrl.isBlank()) "Settings" else "Retry")
                }
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().background(c.bg).padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SettingsCard(cfg.settings.dstMode, vm::setDst) }
            itemsIndexed(cfg.alarms) { i, a ->
                AlarmCard(
                    index = i, alarm = a,
                    ringsOnPhone = i !in vm.mutedOnPhone,
                    onChange = { change -> vm.updateAlarm(i, change) },
                    onRingsOnPhone = { vm.setRingsOnPhone(i, it) },
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(dst: Int?, onDst: (Int?) -> Unit) {
    AppCard {
        Column {
            Text("Clock settings", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Select("Daylight saving time", DST, dst, onDst)
            Spacer(Modifier.height(6.dp))
            Text(DST_HINT[dst] ?: "", fontSize = 13.sp, color = LocalAppColors.current.muted)
        }
    }
}

@Composable
private fun AlarmCard(index: Int, alarm: Alarm, ringsOnPhone: Boolean, onChange: ((Alarm) -> Alarm) -> Unit, onRingsOnPhone: (Boolean) -> Unit) {
    val c = LocalAppColors.current
    var mode by rememberSaveable(index) { mutableStateOf(Schedule.repeatMode(alarm)) }
    LaunchedEffect(alarm) { if (mode != "custom") mode = Schedule.repeatMode(alarm) }
    var advanced by rememberSaveable(index) { mutableStateOf(false) }

    AppCard(alpha = if (alarm.active) 1f else 0.62f) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}", color = c.muted, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(26.dp))
                OutlinedTextField(
                    value = alarm.name, onValueChange = { v -> onChange { it.copy(name = v.take(40)) } },
                    placeholder = { Text("Alarm name") }, singleLine = true, modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                Switch(checked = alarm.active, onCheckedChange = { on ->
                    onChange { it.copy(active = on, duration = if (on && it.duration == 0) 10 else it.duration) }
                })
            }
            Text(Schedule.describe(alarm), fontSize = 13.sp, color = c.muted, modifier = Modifier.padding(start = 26.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeField(alarm.hour, alarm.min, alarm.sec, { h, m, s -> onChange { it.copy(hour = h, min = m, sec = s) } }, Modifier.weight(1f))
                Select("Repeat", REPEAT, mode, { m -> mode = m; onChange { Schedule.applyRepeat(it, m) } }, Modifier.weight(1f))
            }
            if (mode == "weekday" || mode == "custom")
                Select("Day of the week", WEEKDAY, alarm.weekDay, { d -> onChange { it.copy(weekDay = d) } })
            if (mode == "date" || mode == "custom")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Select("Month", MONTH, alarm.month, { m -> onChange { it.copy(month = m) } }, Modifier.weight(1f))
                    NumberField("Day of month", alarm.day, 0..31, { d -> onChange { it.copy(day = d) } }, Modifier.weight(1f), "0 = any")
                }
            if (mode == "custom")
                FlowRow {
                    CheckLabel("Weekdays", alarm.weekDays) { v -> onChange { it.copy(weekDays = v) } }
                    CheckLabel("Weekend days", alarm.weekendDays) { v -> onChange { it.copy(weekendDays = v) } }
                }

            Column {
                FieldLabel("Message")
                OutlinedTextField(
                    value = alarm.message,
                    onValueChange = { v -> onChange { it.copy(message = v.filter { ch -> ch.code in 32..126 }.take(31)) } },
                    placeholder = { Text("Empty = show the time") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField("Duration (seconds)", alarm.duration, 0..32766, { d -> onChange { it.copy(duration = d) } }, Modifier.weight(1f))
                val sounds = if (SOUND.any { it.first == alarm.buzzer }) SOUND else SOUND + (alarm.buzzer to "Sound ${alarm.buzzer}")
                Select("Sound", sounds, alarm.buzzer, { b -> onChange { it.copy(buzzer = b) } }, Modifier.weight(1f))
            }
            FlowRow {
                CheckLabel("Blink display", alarm.fx == 1) { v -> onChange { it.copy(fx = if (v) 1 else 0) } }
                CheckLabel("Bright LEDs", alarm.bright) { v -> onChange { it.copy(bright = v) } }
                CheckLabel("Ring on this phone", ringsOnPhone, onRingsOnPhone)
            }

            Text(
                (if (advanced) "▾ " else "▸ ") + "Action & recurring",
                color = c.muted, fontSize = 14.sp,
                modifier = Modifier.clickable { advanced = !advanced }.padding(vertical = 4.dp),
            )
            if (advanced) {
                Select("Action", ACTION, alarm.actionType, { t -> onChange { it.copy(actionType = t) } })
                if (alarm.actionType in listOf(1, 2, 5))
                    NumberField(if (alarm.actionType == 5) "Palette colour number" else "GPIO pin",
                                alarm.actionNum, 0..99, { n -> onChange { it.copy(actionNum = n) } })
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField("Then repeat every", alarm.recurringInterval, 0..32766,
                                { n -> onChange { it.copy(recurringInterval = n) } }, Modifier.weight(1f), "0 = off")
                    Select("Unit", UNIT, alarm.recurringType, { u -> onChange { it.copy(recurringType = u) } }, Modifier.weight(1f))
                }
                Text("The clock handles the recurring repeat; this phone only rings at the main time.", fontSize = 12.sp, color = c.muted)
            }
        }
    }
}
