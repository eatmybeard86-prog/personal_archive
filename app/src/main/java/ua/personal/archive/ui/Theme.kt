package ua.personal.archive.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import ua.personal.archive.security.Palette
import ua.personal.archive.security.ThemeMode

private data class Seeds(
    val primary: Color, val primaryContainer: Color, val onPrimaryContainer: Color,
    val secondary: Color, val tertiary: Color,
    val darkPrimary: Color, val darkPrimaryContainer: Color, val darkOnPrimaryContainer: Color,
    val darkSecondary: Color, val darkTertiary: Color,
)

private fun seeds(p: Palette): Seeds = when (p) {
    Palette.BLUE, Palette.DYNAMIC, Palette.NAVY -> Seeds(
        Color(0xFF1F4E79), Color(0xFFD3E4FF), Color(0xFF001C38), Color(0xFF545F71), Color(0xFFE0A100),
        Color(0xFFA3C9FF), Color(0xFF00477F), Color(0xFFD3E4FF), Color(0xFFBCC7DC), Color(0xFFFFD36B),
    )
    Palette.GREEN -> Seeds(
        Color(0xFF2E6B3A), Color(0xFFB1F1B6), Color(0xFF002109), Color(0xFF516351), Color(0xFF39656B),
        Color(0xFF95D59B), Color(0xFF145225), Color(0xFFB1F1B6), Color(0xFFB8CCB6), Color(0xFFA1CED5),
    )
    Palette.BURGUNDY -> Seeds(
        Color(0xFF8C1D40), Color(0xFFFFD9DF), Color(0xFF3F0016), Color(0xFF75565C), Color(0xFF7C5635),
        Color(0xFFFFB1C1), Color(0xFF6E0A2C), Color(0xFFFFD9DF), Color(0xFFE5BDC3), Color(0xFFEFBD94),
    )
    Palette.AMBER -> Seeds(
        Color(0xFF8A5100), Color(0xFFFFDCBE), Color(0xFF2C1600), Color(0xFF725A42), Color(0xFF56633B),
        Color(0xFFFFB871), Color(0xFF693C00), Color(0xFFFFDCBE), Color(0xFFE1C1A4), Color(0xFFBECB9C),
    )
    Palette.VIOLET -> Seeds(
        Color(0xFF6538A8), Color(0xFFEBDCFF), Color(0xFF22005D), Color(0xFF645A70), Color(0xFF7F525D),
        Color(0xFFD3BBFF), Color(0xFF4D1D8F), Color(0xFFEBDCFF), Color(0xFFCEC2DB), Color(0xFFF1B7C4),
    )
    Palette.GRAPHITE -> Seeds(
        Color(0xFF3C4858), Color(0xFFD7E3F7), Color(0xFF101C2B), Color(0xFF5B5F66), Color(0xFF6E5676),
        Color(0xFFBBC7DB), Color(0xFF394555), Color(0xFFD7E3F7), Color(0xFFC4C6CF), Color(0xFFDABDE2),
    )
}

/** Тема з макета: глибокий темно-синій фон, яскраво-синій акцент, жовтий і зелений. */
private fun navy(dark: Boolean): ColorScheme = if (dark) darkColorScheme(
    primary = Color(0xFF2F80FF), onPrimary = Color.White,
    primaryContainer = Color(0xFF1A3A73), onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFFFFD43B), onSecondary = Color(0xFF241A00),
    tertiary = Color(0xFF22C55E), onTertiary = Color(0xFF00210B),
    background = Color(0xFF07101F), onBackground = Color(0xFFE6ECF7),
    surface = Color(0xFF07101F), onSurface = Color(0xFFE6ECF7),
    surfaceVariant = Color(0xFF17243D), onSurfaceVariant = Color(0xFF9AA8C2),
    surfaceContainerLowest = Color(0xFF050C18),
    surfaceContainerLow = Color(0xFF0C182C),
    surfaceContainer = Color(0xFF111E35),
    surfaceContainerHigh = Color(0xFF16243E),
    surfaceContainerHighest = Color(0xFF1C2B48),
    outline = Color(0xFF2B3A58), outlineVariant = Color(0xFF1E2C47),
    error = Color(0xFFFF6B6B),
) else lightColorScheme(
    primary = Color(0xFF1E63E9), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE7FF), onPrimaryContainer = Color(0xFF001A43),
    secondary = Color(0xFFB98900), tertiary = Color(0xFF15803D),
    background = Color(0xFFF4F7FC), surface = Color(0xFFF4F7FC),
    surfaceContainer = Color(0xFFFFFFFF), surfaceContainerHigh = Color(0xFFEFF3FA), surfaceContainerLow = Color(0xFFFFFFFF),
)

private fun scheme(p: Palette, dark: Boolean): ColorScheme {
    if (p == Palette.NAVY) return navy(dark)
    val s = seeds(p)
    return if (dark) darkColorScheme(
        primary = s.darkPrimary, onPrimary = Color(0xFF0B1220),
        primaryContainer = s.darkPrimaryContainer, onPrimaryContainer = s.darkOnPrimaryContainer,
        secondary = s.darkSecondary, tertiary = s.darkTertiary,
        background = Color(0xFF111318), surface = Color(0xFF111318),
    ) else lightColorScheme(
        primary = s.primary, onPrimary = Color.White,
        primaryContainer = s.primaryContainer, onPrimaryContainer = s.onPrimaryContainer,
        secondary = s.secondary, tertiary = s.tertiary,
    )
}

@Composable
fun ArchiveTheme(mode: ThemeMode, palette: Palette, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (palette == Palette.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val ctx = LocalContext.current
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else scheme(palette, dark)
    MaterialTheme(colorScheme = colors, content = content)
}
