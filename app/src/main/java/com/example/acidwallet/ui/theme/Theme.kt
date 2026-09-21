package com.example.acidwallet.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Тёмная тема «финансового» вида: глубокий фон, спокойные поверхности,
 * акценты цветами ACID-свойств.
 */
private val AcidDarkScheme = darkColorScheme(
    primary = Color(0xFF4DA3FF),
    onPrimary = Color(0xFF081726),
    primaryContainer = Color(0xFF16324C),
    onPrimaryContainer = Color(0xFFD3E7FF),
    secondary = Color(0xFF7BE495),
    onSecondary = Color(0xFF06230F),
    secondaryContainer = Color(0xFF14351F),
    onSecondaryContainer = Color(0xFFD8FBE4),
    tertiary = Color(0xFFC792EA),
    onTertiary = Color(0xFF25103A),
    tertiaryContainer = Color(0xFF33204A),
    onTertiaryContainer = Color(0xFFF0DEFF),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF3B0A0A),
    errorContainer = Color(0xFF4A1616),
    onErrorContainer = Color(0xFFFFDADA),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE6EAF2),
    surface = Color(0xFF0E1116),
    onSurface = Color(0xFFE6EAF2),
    surfaceVariant = Color(0xFF1C2330),
    onSurfaceVariant = Color(0xFFAEB9CC),
    outline = Color(0xFF3B4559),
    outlineVariant = Color(0xFF283040)
)

/** Светлая тема — чтобы приложением можно было пользоваться днём. */
private val AcidLightScheme = androidx.compose.material3.lightColorScheme(
    primary = Color(0xFF1359A8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E7FF),
    onPrimaryContainer = Color(0xFF07203C),
    secondary = Color(0xFF1E7B41),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5F5E0),
    onSecondaryContainer = Color(0xFF062B14),
    tertiary = Color(0xFF6B3FA0),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEEDDFF),
    onTertiaryContainer = Color(0xFF2A1140),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFF5F7FB),
    onBackground = Color(0xFF14181F),
    surface = Color(0xFFF5F7FB),
    onSurface = Color(0xFF14181F),
    surfaceVariant = Color(0xFFE4E9F2),
    onSurfaceVariant = Color(0xFF444C5C),
    outline = Color(0xFF9AA3B2)
)

/** Цвета четырёх свойств ACID — ими подсвечены карточки и отчёты. */
object AcidColors {
    val Atomicity = Color(0xFF4DA3FF)      // A
    val Consistency = Color(0xFF7BE495)    // C
    val Isolation = Color(0xFFC792EA)      // I
    val Durability = Color(0xFFFFC46B)     // D
    val Danger = Color(0xFFFF6B6B)
    val Ok = Color(0xFF7BE495)

    /** Цвет поверхности карточки (одинаково хорошо смотрится в двух темах). */
    val CardDark = Color(0xFF151A22)
    val CardDarkAlt = Color(0xFF1B2230)
    val CodeDark = Color(0xFF0B0F15)

    fun card(isDark: Boolean): Color = if (isDark) CardDark else Color(0xFFFFFFFF)
    fun cardAlt(isDark: Boolean): Color = if (isDark) CardDarkAlt else Color(0xFFEDF1F8)
    fun code(isDark: Boolean): Color = if (isDark) CodeDark else Color(0xFF1B2330)
}

private val AcidTypography = Typography().let { base ->
    base.copy(
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = base.labelSmall.copy(letterSpacing = 0.6.sp)
    )
}

@Composable
fun AcidWalletTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) AcidDarkScheme else AcidLightScheme,
        typography = AcidTypography,
        content = content
    )
}
