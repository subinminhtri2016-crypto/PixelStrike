package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val PixelStrikeDarkScheme = darkColorScheme(
    primary = AmberStrike,
    onPrimary = Color(0xFF1A0F00),
    primaryContainer = Color(0xFF522A00),
    onPrimaryContainer = AmberGlow,
    secondary = SandstoneGold,
    onSecondary = Color(0xFF231A0F),
    secondaryContainer = CanyonSurfaceElevated,
    onSecondaryContainer = SandstoneGold,
    tertiary = MilitaryOlive,
    onTertiary = SandCream,
    background = BasaltNight,
    onBackground = SandCream,
    surface = CanyonDark,
    onSurface = SandCream,
    surfaceVariant = CanyonSurfaceElevated,
    onSurfaceVariant = MutedSand,
    outline = CanyonBorder,
    error = SquadRed,
    onError = Color.White,
)

private val TacticalShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(10.dp),
)

@Composable
fun PixelStrikeTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = PixelStrikeDarkScheme,
        typography = Typography,
        shapes = TacticalShapes,
        content = content,
    )
}
