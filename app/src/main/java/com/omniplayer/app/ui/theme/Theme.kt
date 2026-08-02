package com.omniplayer.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

private val DarkScheme = darkColorScheme(
    primary = OmniOrange,
    secondary = OmniPink,
    tertiary = OmniPurple,
    background = OmniBlack,
    surface = OmniSurface,
    surfaceVariant = OmniSurfaceHigh,
    onPrimary = Color.White,
    onBackground = OmniText,
    onSurface = OmniText,
    onSurfaceVariant = OmniTextMuted,
    outline = OmniOutline,
    outlineVariant = OmniOutline.copy(alpha = 0.65f),
)

private val LightScheme = lightColorScheme(
    primary = OmniOrange,
    secondary = OmniPink,
    background = Color(0xFFF5F5F5),
    surface = Color.White,
    onPrimary = Color.White,
    onBackground = Color(0xFF101010),
    onSurface = Color(0xFF101010),
)

private val AmoledScheme = DarkScheme.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF0A0A0A),
)

private val EditorialScheme = darkColorScheme(
    primary = LuxeCopper,
    secondary = LuxeRose,
    tertiary = Color(0xFFD3B28F),
    background = LuxeBackground,
    surface = LuxeSurface,
    surfaceVariant = LuxeSurfaceHigh,
    onPrimary = Color(0xFF1B100B),
    onBackground = LuxeIvory,
    onSurface = LuxeIvory,
    onSurfaceVariant = LuxeMuted,
    outline = Color(0xFF4B3C36),
    outlineVariant = Color(0xFF382D29),
)

private val OmniTypography = androidx.compose.material3.Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 25.sp,
        lineHeight = 31.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    ),
)

private val EditorialTypography = OmniTypography.copy(
    headlineLarge = OmniTypography.headlineLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium),
    headlineMedium = OmniTypography.headlineMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium),
    headlineSmall = OmniTypography.headlineSmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium),
    titleLarge = OmniTypography.titleLarge.copy(fontFamily = FontFamily.Serif),
)

@Composable
fun OmniPlayerTheme(
    mode: String = "dark",
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
    }
    MaterialTheme(
        colorScheme = when (mode) {
            "editorial" -> EditorialScheme
            "light" -> LightScheme
            "amoled" -> AmoledScheme
            else -> AmoledScheme
        },
        typography = if (mode == "editorial") EditorialTypography else OmniTypography,
        content = content,
    )
}
