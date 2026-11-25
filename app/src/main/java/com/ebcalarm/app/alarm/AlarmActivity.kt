package com.ebcalarm.app.alarm

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ebcalarm.app.R
import com.ebcalarm.app.data.Prefs
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Full-screen ringing screen, laid out like an incoming call: who is calling, then Snooze / Dismiss. */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(this) { /* back does not silence the alarm */ }

        val snoozeMinutes = Prefs(this).snoozeMinutes
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val info by AlarmService.ringing.collectAsStateWithLifecycle()
                LaunchedEffect(info) { if (info == null) finish() }
                info?.let {
                    RingScreen(
                        info = it,
                        snoozeMinutes = snoozeMinutes,
                        onSnooze = { AlarmService.send(this, AlarmService.ACTION_SNOOZE) },
                        onDismiss = { AlarmService.send(this, AlarmService.ACTION_DISMISS) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RingScreen(info: AlarmService.RingInfo, snoozeMinutes: Int, onSnooze: () -> Unit, onDismiss: () -> Unit) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = LocalTime.now(); delay(1000) } }

    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f, targetValue = 1.12f, label = "scale",
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0E1B3D), Color(0xFF1F56C4))))
            .safeDrawingPadding()
            .padding(24.dp),
    ) {
        Column(Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Alarm", color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            Text(info.title, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            if (info.message.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(info.message, color = Color.White.copy(alpha = 0.85f), fontSize = 18.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(40.dp))
            Box(
                Modifier.size(150.dp).scale(scale).clip(CircleShape).background(Color(0xFF2F6FDE)),
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(150.dp))
            }
            Spacer(Modifier.height(32.dp))
            Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Light)
            Text("set for ${info.time}", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
        }

        Row(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            CallButton("Snooze", "$snoozeMinutes min", "z", Color(0xFF4A5468), onSnooze)
            CallButton("Dismiss", "", "✕", Color(0xFFE5484D), onDismiss)
        }
    }
}

@Composable
private fun CallButton(label: String, sub: String, symbol: String, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(84.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = color, contentColor = Color.White),
        ) {
            Text(symbol, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        Text(label, color = Color.White, fontSize = 16.sp)
        if (sub.isNotEmpty()) Text(sub, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
    }
}
