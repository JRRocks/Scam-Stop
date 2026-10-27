package com.scamstop.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

// Neutral greys with a single accent: blue only marks actions and controls.
private val Accent = Color(0xFF0B57D0)
private val AccentDark = Color(0xFFA8C7FA)

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E6E6),
    onPrimaryContainer = Color(0xFF1B1B1B),
    inversePrimary = AccentDark,
    secondary = Color(0xFF5E5E5E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E2E2),
    onSecondaryContainer = Color(0xFF1B1B1B),
    tertiary = Color(0xFF5E5E5E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE2E2E2),
    onTertiaryContainer = Color(0xFF1B1B1B),
    background = Color(0xFFF9F9F9),
    onBackground = Color(0xFF1B1B1B),
    surface = Color(0xFFF9F9F9),
    onSurface = Color(0xFF1B1B1B),
    surfaceVariant = Color(0xFFE2E2E2),
    onSurfaceVariant = Color(0xFF474747),
    surfaceTint = Color(0xFF5E5E5E),
    inverseSurface = Color(0xFF303030),
    inverseOnSurface = Color(0xFFF1F1F1),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFF777777),
    outlineVariant = Color(0xFFC7C7C7),
    scrim = Color.Black,
    surfaceBright = Color(0xFFF9F9F9),
    surfaceDim = Color(0xFFDADADA),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF3F3F3),
    surfaceContainer = Color(0xFFEEEEEE),
    surfaceContainerHigh = Color(0xFFE8E8E8),
    surfaceContainerHighest = Color(0xFFE2E2E2),
)

private val DarkColors = darkColorScheme(
    primary = AccentDark,
    onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF353535),
    onPrimaryContainer = Color(0xFFE2E2E2),
    inversePrimary = Accent,
    secondary = Color(0xFFC6C6C6),
    onSecondary = Color(0xFF303030),
    secondaryContainer = Color(0xFF3A3A3A),
    onSecondaryContainer = Color(0xFFE2E2E2),
    tertiary = Color(0xFFC6C6C6),
    onTertiary = Color(0xFF303030),
    tertiaryContainer = Color(0xFF3A3A3A),
    onTertiaryContainer = Color(0xFFE2E2E2),
    background = Color(0xFF131313),
    onBackground = Color(0xFFE2E2E2),
    surface = Color(0xFF131313),
    onSurface = Color(0xFFE2E2E2),
    surfaceVariant = Color(0xFF474747),
    onSurfaceVariant = Color(0xFFC6C6C6),
    surfaceTint = Color(0xFFC6C6C6),
    inverseSurface = Color(0xFFE2E2E2),
    inverseOnSurface = Color(0xFF303030),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    outline = Color(0xFF919191),
    outlineVariant = Color(0xFF474747),
    scrim = Color.Black,
    surfaceBright = Color(0xFF393939),
    surfaceDim = Color(0xFF131313),
    surfaceContainerLowest = Color(0xFF0E0E0E),
    surfaceContainerLow = Color(0xFF1B1B1B),
    surfaceContainer = Color(0xFF1F1F1F),
    surfaceContainerHigh = Color(0xFF2A2A2A),
    surfaceContainerHighest = Color(0xFF353535),
)

/** State colours for the protection banner, kept apart from the accent. */
@Immutable
internal data class StatusColors(
    val ok: Color,
    val onOk: Color,
    val okIcon: Color,
    val warn: Color,
    val onWarn: Color,
)

private val LightStatus = StatusColors(
    ok = Color(0xFFC4EED0),
    onOk = Color(0xFF0F5223),
    okIcon = Color(0xFF146C2E),
    warn = Color(0xFFFFDF99),
    onWarn = Color(0xFF3E2D00),
)

private val DarkStatus = StatusColors(
    ok = Color(0xFF0F5223),
    onOk = Color(0xFFC4EED0),
    okIcon = Color(0xFF6DD58C),
    warn = Color(0xFF5C4300),
    onWarn = Color(0xFFFFDF99),
)

internal val LocalStatusColors = staticCompositionLocalOf { LightStatus }

// Dense, settings-style type: 14 sp body text, 12 sp supporting text.
private val Base = Typography()
private val DenseTypography = Base.copy(
    bodyLarge = Base.bodyLarge.copy(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
)

@Composable
internal fun ScamStopTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStatusColors provides if (darkTheme) DarkStatus else LightStatus) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = DenseTypography,
            content = content,
        )
    }
}
