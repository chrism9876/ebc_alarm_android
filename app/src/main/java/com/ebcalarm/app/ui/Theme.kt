package com.ebcalarm.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Same palette as the alarm server's web page. */
data class AppColors(
    val bg: Color, val card: Color, val text: Color, val muted: Color, val line: Color,
    val accent: Color, val ok: Color, val warn: Color, val offlineBar: Color,
)

private val Light = AppColors(
    bg = Color(0xFFF4F5F7), card = Color.White, text = Color(0xFF1D2330), muted = Color(0xFF6B7385),
    line = Color(0xFFDDE1E8), accent = Color(0xFF2F6FDE), ok = Color(0xFF1F8A4C), warn = Color(0xFFB5541A),
    offlineBar = Color(0xFFF6E6DC),
)
private val Dark = AppColors(
    bg = Color(0xFF14171D), card = Color(0xFF1D2129), text = Color(0xFFE6E9EF), muted = Color(0xFF959CAB),
    line = Color(0xFF2F3542), accent = Color(0xFF5B8FF0), ok = Color(0xFF4CC27F), warn = Color(0xFFEF9255),
    offlineBar = Color(0xFF33261F),
)

val LocalAppColors = staticCompositionLocalOf { Light }

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val c = if (dark) Dark else Light
    val scheme = if (dark)
        darkColorScheme(primary = c.accent, background = c.bg, surface = c.card, onBackground = c.text, onSurface = c.text,
                        surfaceVariant = c.card, outline = c.line, outlineVariant = c.line, onSurfaceVariant = c.muted)
    else
        lightColorScheme(primary = c.accent, background = c.bg, surface = c.card, onBackground = c.text, onSurface = c.text,
                         surfaceVariant = c.card, outline = c.line, outlineVariant = c.line, onSurfaceVariant = c.muted)
    CompositionLocalProvider(LocalAppColors provides c) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
