package com.claudechat

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFD97757), onPrimary = Color(0xFF1F0E07),
    primaryContainer = Color(0xFF5A2E1E), onPrimaryContainer = Color(0xFFFFDBCF),
    secondary = Color(0xFFC2C0B6), tertiary = Color(0xFFD9C58B),
    background = Color(0xFF262624), onBackground = Color(0xFFF1EFE8),
    surface = Color(0xFF262624), onSurface = Color(0xFFF1EFE8),
    surfaceVariant = Color(0xFF3A3935), onSurfaceVariant = Color(0xFFB7B5AA),
    outlineVariant = Color(0xFF45443F),
    surfaceContainerLowest = Color(0xFF1E1E1C), surfaceContainerLow = Color(0xFF2B2B28),
    surfaceContainer = Color(0xFF30302D), surfaceContainerHigh = Color(0xFF3A3935), surfaceContainerHighest = Color(0xFF45443F),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFFC96442), onPrimary = Color.White,
    primaryContainer = Color(0xFFF4DCD0), onPrimaryContainer = Color(0xFF3A1508),
    secondary = Color(0xFF6B6A63), tertiary = Color(0xFF6A5E2F),
    background = Color(0xFFFAF9F5), onBackground = Color(0xFF1F1E1D),
    surface = Color(0xFFFAF9F5), onSurface = Color(0xFF1F1E1D),
    surfaceVariant = Color(0xFFEDEBE3), onSurfaceVariant = Color(0xFF6B6A63),
    outlineVariant = Color(0xFFE3E0D5),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF5F4EE),
    surfaceContainer = Color(0xFFF0EEE6), surfaceContainerHigh = Color(0xFFEBE8DE), surfaceContainerHighest = Color(0xFFE5E2D7),
)

@Composable
fun isDarkTheme(): Boolean {
    val t by Prefs.theme.flow.collectAsState()
    return when (t) { "light" -> false; "dark", "amoled" -> true; else -> isSystemInDarkTheme() }
}

@Composable
fun ClaudeTheme(content: @Composable () -> Unit) {
    val t by Prefs.theme.flow.collectAsState()
    val dyn by Prefs.dynamic.flow.collectAsState()
    val dark = isDarkTheme()
    val ctx = LocalContext.current
    var scheme = when {
        Build.VERSION.SDK_INT >= 31 && dyn -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> DarkScheme
        else -> LightScheme
    }
    if (t == "amoled") {
        scheme = scheme.copy(
            background = Color.Black, surface = Color.Black, surfaceDim = Color.Black,
            surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF0A0A0A),
            surfaceContainer = Color(0xFF111111), surfaceContainerHigh = Color(0xFF1A1A1A), surfaceContainerHighest = Color(0xFF232323),
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
