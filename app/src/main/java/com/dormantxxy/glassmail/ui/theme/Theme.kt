package com.dormantxxy.glassmail.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.dormantxxy.glassmail.data.ThemeMode

val MailBlue = Color(0xFF0A84FF)
val MailCyan = Color(0xFF5AC8FA)
val MailIndigo = Color(0xFF5E5CE6)
val MailGreen = Color(0xFF30D158)
val MailOrange = Color(0xFFFF9F0A)
val MailRed = Color(0xFFFF453A)
val MailPurple = Color(0xFFBF5AF2)

private val LightColors = lightColorScheme(
    primary = Color(0xFF007AFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9ECFF),
    onPrimaryContainer = Color(0xFF003A70),
    secondary = Color(0xFF5E5CE6),
    tertiary = Color(0xFF00A7C4),
    background = Color(0xFFF4F7FC),
    onBackground = Color(0xFF121318),
    surface = Color(0xFFF9FBFF),
    onSurface = Color(0xFF121318),
    surfaceVariant = Color(0xFFE9EEF6),
    onSurfaceVariant = Color(0xFF5A6070),
    outline = Color(0xFFC6CDDA),
    error = Color(0xFFFF3B30)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5EB0FF),
    onPrimary = Color(0xFF002E55),
    primaryContainer = Color(0xFF0A3D68),
    onPrimaryContainer = Color(0xFFD7EAFF),
    secondary = Color(0xFFA7A4FF),
    tertiary = Color(0xFF65D8EF),
    background = Color(0xFF090C12),
    onBackground = Color(0xFFF2F4FA),
    surface = Color(0xFF11151D),
    onSurface = Color(0xFFF2F4FA),
    surfaceVariant = Color(0xFF1B222D),
    onSurfaceVariant = Color(0xFFB6BECC),
    outline = Color(0xFF3A4351),
    error = Color(0xFFFF6961)
)

@Composable
fun GlassMailTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val scheme = if (dark) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        }
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography(
            displaySmall = MaterialTheme.typography.displaySmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
            headlineLarge = MaterialTheme.typography.headlineLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
            titleLarge = MaterialTheme.typography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
            titleMedium = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        ),
        shapes = Shapes(
            extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
            extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(34.dp)
        ),
        content = content
    )
}

