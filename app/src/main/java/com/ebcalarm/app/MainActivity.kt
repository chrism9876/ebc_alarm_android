package com.ebcalarm.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.ebcalarm.app.alarm.AlarmScheduler
import com.ebcalarm.app.alarm.Push
import com.ebcalarm.app.alarm.SyncWorker
import com.ebcalarm.app.ui.AppTheme
import com.ebcalarm.app.ui.EditorScreen
import com.ebcalarm.app.ui.MainViewModel
import com.ebcalarm.app.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SyncWorker.schedule(this)
        AlarmScheduler.scheduleAll(this)
        Push.refresh(this)                     // (re)register for the clock's ring pushes

        setContent {
            AppTheme {
                val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }

                var showSettings by rememberSaveable { mutableStateOf(vm.prefs.serverUrl.isBlank()) }
                if (showSettings) SettingsScreen(vm, onBack = { showSettings = false })
                else EditorScreen(vm, onOpenSettings = { showSettings = true })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!vm.dirty) vm.refresh()
    }
}
