package com.ebcalarm.app.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ebcalarm.app.alarm.AlarmScheduler
import com.ebcalarm.app.alarm.AlarmService
import com.ebcalarm.app.alarm.Push
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val c = LocalAppColors.current
    val ctx = LocalContext.current
    val prefs = vm.prefs
    var url by remember { mutableStateOf(prefs.serverUrl) }
    var token by remember { mutableStateOf(prefs.token) }
    var ring by remember { mutableStateOf(prefs.ringOnPhone) }
    var snooze by remember { mutableIntStateOf(prefs.snoozeMinutes) }
    var ringMinutes by remember { mutableIntStateOf(prefs.ringMinutes) }

    // re-check permissions whenever the user comes back from the system settings
    var tick by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { tick++ } }

    fun close() {
        prefs.serverUrl = url
        prefs.token = token
        prefs.ringOnPhone = ring
        prefs.snoozeMinutes = snooze
        prefs.ringMinutes = ringMinutes
        vm.settingsChanged()
        onBack()
    }
    BackHandler { close() }

    Scaffold(
        containerColor = c.bg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.bg),
                navigationIcon = { TextButton(onClick = ::close) { Text("←", fontSize = 22.sp) } },
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Alarm server", fontWeight = FontWeight.SemiBold)
                    Column {
                        FieldLabel("Address")
                        OutlinedTextField(
                            value = url, onValueChange = { url = it }, singleLine = true,
                            placeholder = { Text("http://192.168.1.100:8080") },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Column {
                        FieldLabel("API token (only if the server sets ALARM_API_TOKEN)")
                        OutlinedTextField(value = token, onValueChange = { token = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Ring on this phone", fontWeight = FontWeight.SemiBold)
                            Text("Active alarms also ring here, full screen, with Snooze and Dismiss. Untick “Ring on this phone” on an alarm to leave it to the clock.",
                                 fontSize = 13.sp, color = c.muted)
                        }
                        Switch(checked = ring, onCheckedChange = { ring = it })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NumberField("Snooze (minutes)", snooze, 1..60, { snooze = it }, Modifier.weight(1f))
                        NumberField("Stop ringing after (min)", ringMinutes, 1..60, { ringMinutes = it }, Modifier.weight(1f))
                    }
                    val next = remember(tick, vm.work, vm.mutedOnPhone) { AlarmScheduler.nextOnPhone(ctx) }
                    Text(
                        next?.let { (i, t) -> "Next on this phone: alarm ${i + 1}, " + t.format(DateTimeFormatter.ofPattern("EEE d MMM HH:mm")) }
                            ?: "No alarms scheduled on this phone",
                        fontSize = 13.sp, color = c.muted,
                    )
                    val appPush = Push.available(ctx)
                    val server = vm.serverPush
                    Text(
                        "Ring in step with the clock: " + when {
                            !appPush -> "only while ringing (this app was built without google-services.json)"
                            server == null -> "checking the server…"
                            !server.first -> "only while ringing (server: ${server.second})"
                            else -> "on (Firebase push)"
                        },
                        fontSize = 13.sp,
                        color = if (appPush && server?.first == true) c.ok else c.muted,
                    )
                    OutlinedButton(onClick = {
                        prefs.snoozeMinutes = snooze; prefs.ringMinutes = ringMinutes
                        AlarmService.test(ctx, next?.first ?: 0)
                    }) { Text("Test: ring now") }
                }
            }

            key(tick) { PermissionsCard(ctx) }
        }
    }
}

@Composable
private fun PermissionsCard(ctx: Context) {
    val c = LocalAppColors.current
    val nm = ctx.getSystemService(NotificationManager::class.java)
    val notifications = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val fullScreen = Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent()
    val exact = AlarmScheduler.canScheduleExact(ctx)
    val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))

    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Permissions for ringing", fontWeight = FontWeight.SemiBold)
            PermissionRow("Notifications", notifications) {
                ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
            }
            PermissionRow("Full-screen alarms over the lock screen", fullScreen) {
                if (Build.VERSION.SDK_INT >= 34)
                    ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${ctx.packageName}")))
            }
            PermissionRow("Exact alarm times", exact) {
                if (Build.VERSION.SDK_INT >= 31)
                    ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
            }
            Spacer(Modifier.height(4.dp))
            Text("Samsung: set Battery to “Unrestricted” for this app so background syncing isn't stopped. The alarms themselves ring either way.",
                 fontSize = 13.sp, color = c.muted)
            OutlinedButton(onClick = { ctx.startActivity(appDetails) }) { Text("Open app settings") }
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onFix: () -> Unit) {
    val c = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (granted) "✓" else "!", color = if (granted) c.ok else c.warn, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 10.dp))
        Text(label, modifier = Modifier.weight(1f), fontSize = 14.sp)
        if (!granted) Button(onClick = onFix) { Text("Allow") }
    }
}
