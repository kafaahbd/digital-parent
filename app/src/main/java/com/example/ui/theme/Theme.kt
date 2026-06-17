package com.example.ui.theme

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

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldPrimaryDark,
    onPrimary = EmeraldOnPrimaryDark,
    primaryContainer = EmeraldPrimaryContainerDark,
    onPrimaryContainer = EmeraldOnPrimaryContainerDark,
    secondary = EmeraldSecondaryDark,
    onSecondary = EmeraldOnSecondaryDark,
    secondaryContainer = EmeraldSecondaryContainerDark,
    tertiary = EmeraldPrimaryDark,
    background = EmeraldBackgroundDark,
    onBackground = EmeraldOnBackgroundDark,
    surface = EmeraldSurfaceDark,
    onSurface = EmeraldOnSurfaceDark,
    surfaceVariant = EmeraldSurfaceVariantDark,
    onSurfaceVariant = EmeraldOnSurfaceVariantDark,
    outline = EmeraldOutlineDark,
    outlineVariant = EmeraldOutlineVariantDark,
    error = EmeraldErrorDark,
    onError = EmeraldOnErrorDark,
    errorContainer = EmeraldErrorContainerDark,
    onErrorContainer = EmeraldOnErrorContainerDark
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimaryLight,
    onPrimary = EmeraldOnPrimaryLight,
    primaryContainer = EmeraldPrimaryContainerLight,
    onPrimaryContainer = EmeraldOnPrimaryContainerLight,
    secondary = EmeraldSecondaryLight,
    onSecondary = EmeraldOnSecondaryLight,
    secondaryContainer = EmeraldSecondaryContainerLight,
    tertiary = EmeraldSecondaryLight,
    background = EmeraldBackgroundLight,
    onBackground = EmeraldOnBackgroundLight,
    surface = EmeraldSurfaceLight,
    onSurface = EmeraldOnSurfaceLight,
    surfaceVariant = EmeraldSurfaceVariantLight,
    onSurfaceVariant = EmeraldOnSurfaceVariantLight,
    outline = EmeraldOutlineLight,
    outlineVariant = EmeraldOutlineVariantLight,
    error = EmeraldErrorLight,
    onError = EmeraldOnErrorLight,
    errorContainer = EmeraldErrorContainerLight,
    onErrorContainer = EmeraldOnErrorContainerLight
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is set to false by default to showcase our beautiful custom Whitish-Green palette
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
