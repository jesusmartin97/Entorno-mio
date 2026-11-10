package com.cecapi.app.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val CecapiColorScheme = darkColorScheme(
    background = CecapiBackground,
    surface = CecapiSurface,
    surfaceVariant = CecapiSurfaceElevated,
    primary = CecapiAccent,
    onPrimary = CecapiBackground,
    onBackground = CecapiTextPrimary,
    onSurface = CecapiTextPrimary,
    error = CecapiError,
    outline = CecapiBorder,
)

@Composable
fun CecapiTheme(content: @Composable () -> Unit) {
    // isSystemInDarkTheme() is intentionally unused for the color scheme (see Color.kt);
    // kept only so screen readers/system UI still report the correct theme mode.
    isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = CecapiColorScheme,
        typography = CecapiTypography,
        content = content,
    )
}
