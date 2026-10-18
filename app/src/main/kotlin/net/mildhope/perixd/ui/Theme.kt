package net.mildhope.perixd.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import net.mildhope.perixd.data.ThemeMode

private val Light = lightColorScheme(
    primary = Color(0xFFA0275A), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E3), onPrimaryContainer = Color(0xFF3E0019),
    secondary = Color(0xFF765660), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9E2), onSecondaryContainer = Color(0xFF2B151C),
    tertiary = Color(0xFF00696A), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFB4EFEE), onTertiaryContainer = Color(0xFF002020),
    background = Color(0xFFFFFBF8), onBackground = Color(0xFF201A1B),
    surface = Color(0xFFFFFBF8), onSurface = Color(0xFF201A1B),
    surfaceVariant = Color(0xFFF3DDE1), onSurfaceVariant = Color(0xFF524346),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFCF2F3),
    surfaceContainer = Color(0xFFF8ECEE), surfaceContainerHigh = Color(0xFFF2E6E8),
    surfaceContainerHighest = Color(0xFFECE0E2),
    outline = Color(0xFF847376), outlineVariant = Color(0xFFD6C2C5),
    error = Color(0xFFBA1A1A), onError = Color.White,
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB1C8), onPrimary = Color(0xFF5E1133),
    primaryContainer = Color(0xFF7E2949), onPrimaryContainer = Color(0xFFFFD9E3),
    secondary = Color(0xFFE5BDC6), onSecondary = Color(0xFF432931),
    secondaryContainer = Color(0xFF5C3F47), onSecondaryContainer = Color(0xFFFFD9E2),
    tertiary = Color(0xFF80D5D4), onTertiary = Color(0xFF003737),
    tertiaryContainer = Color(0xFF004F50), onTertiaryContainer = Color(0xFFB4EFEE),
    background = Color(0xFF191113), onBackground = Color(0xFFEFDFE1),
    surface = Color(0xFF191113), onSurface = Color(0xFFEFDFE1),
    surfaceVariant = Color(0xFF524346), onSurfaceVariant = Color(0xFFD6C2C5),
    surfaceContainerLowest = Color(0xFF130C0E), surfaceContainerLow = Color(0xFF22191B),
    surfaceContainer = Color(0xFF261D1F), surfaceContainerHigh = Color(0xFF31282A),
    surfaceContainerHighest = Color(0xFF3C3234),
    outline = Color(0xFF9F8C90), outlineVariant = Color(0xFF524346),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
)

/** Colors of the pill pack, which stay pill-like in both themes. */
@Immutable
data class PackColors(
    val foilTop: Color,
    val foilBottom: Color,
    val bubble: Color,
    val hole: Color,
    val tear: Color,
    val pill: Color,
    val pillShade: Color,
    val placebo: Color,
    val placeboShade: Color,
)

private val LightPack = PackColors(
    foilTop = Color(0xFFF1EEF3), foilBottom = Color(0xFFDCD8E0), bubble = Color(0xFFC9C4CE),
    hole = Color(0xFFB5AFBB), tear = Color(0xFF8E8794),
    pill = Color(0xFFFFF4F7), pillShade = Color(0xFFF2C6D3), placebo = Color(0xFFE3F3EF), placeboShade = Color(0xFFA9D3C9),
)

private val DarkPack = PackColors(
    foilTop = Color(0xFF45414B), foilBottom = Color(0xFF332F38), bubble = Color(0xFF57525E),
    hole = Color(0xFF221F26), tear = Color(0xFF7A7482),
    pill = Color(0xFFF7E6EC), pillShade = Color(0xFFD69AAE), placebo = Color(0xFFD5EBE6), placeboShade = Color(0xFF8FBFB3),
)

val LocalPackColors = staticCompositionLocalOf { LightPack }

@Composable
fun isDark(mode: ThemeMode) = when (mode) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

@Composable
fun PerixdTheme(dark: Boolean, content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalPackColors provides if (dark) DarkPack else LightPack) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
    }
}
