package app.monitor360.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.core.graphics.ColorUtils
import app.monitor360.data.Prefs
import app.monitor360.data.Status
import app.monitor360.data.ThemeMode

private fun hsl(h: Float, s: Float, l: Float): Color =
    Color(ColorUtils.HSLToColor(floatArrayOf(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))))

fun schemeFromSeed(seed: Color, dark: Boolean, amoled: Boolean): ColorScheme {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(seed.value.let { android.graphics.Color.argb(255, (seed.red * 255).toInt(), (seed.green * 255).toInt(), (seed.blue * 255).toInt()) }, hsl)
    val h = hsl[0]
    return if (!dark) lightColorScheme(
        primary = hsl(h, 0.65f, 0.34f),
        onPrimary = Color.White,
        primaryContainer = hsl(h, 0.75f, 0.90f),
        onPrimaryContainer = hsl(h, 0.80f, 0.11f),
        secondary = hsl(h, 0.28f, 0.38f),
        onSecondary = Color.White,
        secondaryContainer = hsl(h, 0.38f, 0.90f),
        onSecondaryContainer = hsl(h, 0.40f, 0.12f),
        tertiary = hsl(h + 55f, 0.42f, 0.38f),
        onTertiary = Color.White,
        tertiaryContainer = hsl(h + 55f, 0.50f, 0.90f),
        onTertiaryContainer = hsl(h + 55f, 0.50f, 0.12f),
        background = hsl(h, 0.30f, 0.975f),
        onBackground = hsl(h, 0.20f, 0.10f),
        surface = hsl(h, 0.30f, 0.975f),
        onSurface = hsl(h, 0.20f, 0.10f),
        surfaceVariant = hsl(h, 0.22f, 0.92f),
        onSurfaceVariant = hsl(h, 0.12f, 0.30f),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = hsl(h, 0.30f, 0.96f),
        surfaceContainer = hsl(h, 0.28f, 0.94f),
        surfaceContainerHigh = hsl(h, 0.26f, 0.92f),
        surfaceContainerHighest = hsl(h, 0.24f, 0.90f),
        outline = hsl(h, 0.10f, 0.50f),
        outlineVariant = hsl(h, 0.16f, 0.82f),
    ) else darkColorScheme(
        primary = hsl(h, 0.75f, 0.72f),
        onPrimary = hsl(h, 0.80f, 0.14f),
        primaryContainer = hsl(h, 0.60f, 0.26f),
        onPrimaryContainer = hsl(h, 0.80f, 0.90f),
        secondary = hsl(h, 0.35f, 0.76f),
        onSecondary = hsl(h, 0.40f, 0.16f),
        secondaryContainer = hsl(h, 0.30f, 0.25f),
        onSecondaryContainer = hsl(h, 0.40f, 0.90f),
        tertiary = hsl(h + 55f, 0.50f, 0.76f),
        onTertiary = hsl(h + 55f, 0.50f, 0.15f),
        tertiaryContainer = hsl(h + 55f, 0.35f, 0.26f),
        onTertiaryContainer = hsl(h + 55f, 0.50f, 0.90f),
        background = if (amoled) Color.Black else hsl(h, 0.28f, 0.065f),
        onBackground = hsl(h, 0.15f, 0.92f),
        surface = if (amoled) Color.Black else hsl(h, 0.28f, 0.065f),
        onSurface = hsl(h, 0.15f, 0.92f),
        surfaceVariant = hsl(h, 0.16f, 0.22f),
        onSurfaceVariant = hsl(h, 0.14f, 0.78f),
        surfaceContainerLowest = if (amoled) Color.Black else hsl(h, 0.28f, 0.04f),
        surfaceContainerLow = if (amoled) hsl(h, 0.20f, 0.05f) else hsl(h, 0.26f, 0.09f),
        surfaceContainer = if (amoled) hsl(h, 0.20f, 0.075f) else hsl(h, 0.24f, 0.11f),
        surfaceContainerHigh = if (amoled) hsl(h, 0.18f, 0.10f) else hsl(h, 0.22f, 0.14f),
        surfaceContainerHighest = if (amoled) hsl(h, 0.16f, 0.13f) else hsl(h, 0.20f, 0.17f),
        outline = hsl(h, 0.10f, 0.56f),
        outlineVariant = hsl(h, 0.14f, 0.28f),
    )
}

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun isDarkFor(prefs: Prefs): Boolean = when (prefs.themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun Monitor360Theme(prefs: Prefs, content: @Composable () -> Unit) {
    val dark = isDarkFor(prefs)
    val ctx = LocalContext.current
    val scheme = if (prefs.dynamicColor && Build.VERSION.SDK_INT >= 31) {
        val base = if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        if (dark && prefs.amoled) base.copy(
            background = Color.Black, surface = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0A0A0A), surfaceContainer = Color(0xFF111111),
            surfaceContainerHigh = Color(0xFF181818), surfaceContainerHighest = Color(0xFF202020),
        ) else base
    } else schemeFromSeed(Color(prefs.accent.seed), dark, prefs.amoled)
    MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
}

object StatusColors {
    @Composable
    private fun dark() = MaterialTheme.colorScheme.background.luminance() < 0.5f

    @Composable
    fun up() = if (dark()) Color(0xFF3DD68C) else Color(0xFF14854A)

    @Composable
    fun down() = if (dark()) Color(0xFFFF6B7D) else Color(0xFFD7263D)

    @Composable
    fun warn() = if (dark()) Color(0xFFFFC857) else Color(0xFFB7791F)

    @Composable
    fun unknown() = MaterialTheme.colorScheme.outline

    @Composable
    fun of(s: Status) = when (s) {
        Status.UP -> up()
        Status.DOWN -> down()
        Status.UNKNOWN -> unknown()
    }
}

val ChartPalette = listOf(
    Color(0xFF3B82F6), Color(0xFF10B981), Color(0xFFF59E0B), Color(0xFFEF4444),
    Color(0xFF8B5CF6), Color(0xFF06B6D4), Color(0xFFEC4899), Color(0xFF84CC16),
    Color(0xFFF97316), Color(0xFF14B8A6), Color(0xFF6366F1), Color(0xFFA855F7),
    Color(0xFF64748B), Color(0xFFD946EF),
)
