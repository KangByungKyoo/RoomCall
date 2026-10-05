package com.example.roomcall.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val IntercomColors = lightColorScheme(
    primary = Color(0xFF0866FF), onPrimary = Color.White,
    primaryContainer = Color(0xFFE5F0FF), onPrimaryContainer = Color(0xFF071331),
    background = Color(0xFFFBFDFF), onBackground = Color(0xFF071331),
    surface = Color.White, onSurface = Color(0xFF071331),
    surfaceVariant = Color(0xFFF0F7FF), onSurfaceVariant = Color(0xFF647084),
    outline = Color(0xFFD3DDE9)
)

@Composable
fun RoomCallTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = IntercomColors, typography = Typography, content = content)
}