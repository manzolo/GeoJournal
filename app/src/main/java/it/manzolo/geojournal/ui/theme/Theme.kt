package it.manzolo.geojournal.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = ForestGreenLight,
    onPrimary = SurfaceDark,
    primaryContainer = ForestGreenDark,
    onPrimaryContainer = ForestGreenContainer,
    secondary = SunsetOrangeLight,
    onSecondary = SurfaceDark,
    secondaryContainer = SunsetOrangeDark,
    onSecondaryContainer = SunsetOrangeContainer,
    tertiary = SkyBlueLight,
    onTertiary = SurfaceDark,
    tertiaryContainer = SkyBlueDark,
    onTertiaryContainer = SkyBlueContainer,
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    error = ErrorRed,
    onError = WarmCream,
    errorContainer = ErrorRedContainerDark,
    onErrorContainer = ErrorRedContainer,
)

private val LightColorScheme = lightColorScheme(
    primary = ForestGreen,
    onPrimary = WarmCream,
    primaryContainer = ForestGreenContainer,
    onPrimaryContainer = OnForestGreenContainer,
    secondary = SunsetOrange,
    onSecondary = WarmCream,
    secondaryContainer = SunsetOrangeContainer,
    onSecondaryContainer = OnSunsetOrangeContainer,
    tertiary = SkyBlue,
    onTertiary = WarmCream,
    tertiaryContainer = SkyBlueContainer,
    onTertiaryContainer = OnSkyBlueContainer,
    background = LightSurface,
    onBackground = EarthBrown,
    surface = WarmCream,
    onSurface = EarthBrown,
    surfaceVariant = WarmCreamDark,
    onSurfaceVariant = EarthBrownLight,
    error = ErrorRed,
    onError = WarmCream,
    errorContainer = ErrorRedContainer,
)

@Composable
fun GeoJournalTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Disabilitato per usare la palette GeoJournal personalizzata
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
