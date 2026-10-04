package com.lauksatset.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Terracotta = Color(0xFFC9684B)
val Beige = Color(0xFFF3E4D0)
val Brown = Color(0xFF4A332B)
val Olive = Color(0xFF7D8B62)
val WarmWhite = Color(0xFFFFFDF8)

// Alias semantik agar seluruh layar lama mengikuti palet merek yang baru.
val Leaf = Terracotta
val LeafDark = Brown
val Cream = WarmWhite
val Orange = Olive
val Ink = Brown
val Muted = Brown.copy(alpha = 0.72f)

private val Colors = lightColorScheme(
    primary = Terracotta,
    onPrimary = WarmWhite,
    primaryContainer = Beige,
    onPrimaryContainer = Brown,
    secondary = Olive,
    onSecondary = WarmWhite,
    secondaryContainer = Beige,
    onSecondaryContainer = Brown,
    tertiary = Brown,
    onTertiary = WarmWhite,
    background = WarmWhite,
    onBackground = Brown,
    surface = WarmWhite,
    onSurface = Brown,
    surfaceVariant = Beige,
    onSurfaceVariant = Brown,
    outline = Olive,
    error = Terracotta,
    onError = WarmWhite,
    errorContainer = Beige,
    onErrorContainer = Brown,
)

@Composable fun LaukSatSetTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = MaterialTheme.typography, content = content)
}
