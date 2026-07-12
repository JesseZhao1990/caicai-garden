package com.caicai.garden.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F6F4E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9EADD),
    onPrimaryContainer = Color(0xFF173A29),
    secondary = Color(0xFF5B6C87),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE5F2),
    onSecondaryContainer = Color(0xFF233047),
    tertiary = Color(0xFFAD6C2B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE0B6),
    onTertiaryContainer = Color(0xFF4B2804),
    background = Color(0xFFF7FAF6),
    onBackground = Color(0xFF1D211D),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1D211D),
    surfaceVariant = Color(0xFFE4E9E2),
    onSurfaceVariant = Color(0xFF424A42),
    outline = Color(0xFF768076),
    error = Color(0xFFB3261E)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(8.dp)
)

@Composable
fun CaiCaiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        shapes = AppShapes,
        content = content
    )
}
