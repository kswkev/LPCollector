package com.lpcollector.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF8C4A00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCC0),
    onPrimaryContainer = Color(0xFF2E1500),
    secondary = Color(0xFF9A2A22),
    secondaryContainer = Color(0xFFFFDAD5),
    background = Color(0xFFFFF8F4),
    surface = Color(0xFFFFF8F4),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB77A),
    onPrimary = Color(0xFF4B2800),
    primaryContainer = Color(0xFF6B3A00),
    onPrimaryContainer = Color(0xFFFFDCC0),
    secondary = Color(0xFFFFB4A9),
    secondaryContainer = Color(0xFF7B1510),
    background = Color(0xFF1A120C),
    surface = Color(0xFF1A120C),
)

@Composable
fun LPCollectorTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
