package com.contestproject.app.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 색 토큰. Pleos 디자인 가이드의 토큰 이름 규칙(용도가 드러나는 이름)을 따르고,
 * 주간/야간은 시스템 uiMode(AAOS가 조도·전조등·시간으로 전환)를 그대로 따른다.
 * 쉬는 화면·깨우는 화면·경보 화면은 목적이 분명해서 주야간과 상관없이 고정 팔레트를 쓴다.
 */
@Immutable
data class ShimpyoColors(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val onAccent: Color,
    val accentContainer: Color,
    val accentText: Color,
    val accentBorder: Color,
    val check: Color,
    val cautionContainer: Color,
    val cautionBorder: Color,
    val cautionText: Color,
    val cautionTitle: Color,
    val track: Color,
    val primaryButton: Color,
    val onPrimaryButton: Color,
    val isDark: Boolean,
)

val NightColors = ShimpyoColors(
    background = Color(0xFF07090D),
    surface = Color(0xFF0E1218),
    surfaceRaised = Color(0xFF151A22),
    border = Color(0xFF1C222C),
    textPrimary = Color(0xFFE6EAF0),
    textSecondary = Color(0xFF8B95A3),
    accent = Color(0xFFF2A65A),
    onAccent = Color(0xFF1A1206),
    accentContainer = Color(0xFF1F170D),
    accentText = Color(0xFFF7C68F),
    accentBorder = Color(0xFF6B4A26),
    check = Color(0xFF7CC4F5),
    cautionContainer = Color(0xFF17130C),
    cautionBorder = Color(0xFF3B2E18),
    cautionText = Color(0xFFEBD3B0),
    cautionTitle = Color(0xFFF7C68F),
    track = Color(0xFF151A22),
    primaryButton = Color(0xFFF2A65A),
    onPrimaryButton = Color(0xFF1A1206),
    isDark = true,
)

val DayColors = ShimpyoColors(
    background = Color(0xFFF3F4F6),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFF7F8FA),
    border = Color(0xFFDDE1E7),
    textPrimary = Color(0xFF14181F),
    textSecondary = Color(0xFF59616E),
    accent = Color(0xFFE8913A),
    onAccent = Color(0xFF1A1206),
    accentContainer = Color(0xFFFCEBD8),
    accentText = Color(0xFF8A4A0E),
    accentBorder = Color(0xFFE8913A),
    check = Color(0xFF1F6DB0),
    cautionContainer = Color(0xFFFBF1E4),
    cautionBorder = Color(0xFFEBD6B8),
    cautionText = Color(0xFF5E3D14),
    cautionTitle = Color(0xFF8A4A0E),
    track = Color(0xFFE4E7EC),
    primaryButton = Color(0xFFE8913A),
    onPrimaryButton = Color(0xFF1A1206),
    isDark = false,
)

/** 쉬는 동안: 눈부시지 않게 가장 어둡게 */
val SleepColors = NightColors.copy(
    background = Color(0xFF040507),
    surface = Color(0xFF0A0D12),
    surfaceRaised = Color(0xFF11151C),
    border = Color(0xFF171C25),
    textPrimary = Color(0xFFC9D0DA),
    accent = Color(0xFFC98A4B),
    accentContainer = Color(0xFF17110A),
    track = Color(0xFF151A22),
)

/** 기상 연출: 어둠에서 조금 밝아진 단계 */
val DawnColors = NightColors.copy(
    background = Color(0xFF141A22),
    surface = Color(0xFF0E1218),
    border = Color(0xFF252D39),
    textSecondary = Color(0xFFA3ADBB),
)

/** 깨우기·출발 확인: 밝은 화면도 깨우는 수단이라 주야간과 상관없이 밝게 */
val WakeColors = DayColors.copy(
    background = Color(0xFFF4F1EA),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFFBF0E4),
    border = Color(0xFFE2DDD2),
    textPrimary = Color(0xFF1B1F24),
    textSecondary = Color(0xFF5B6370),
    accentText = Color(0xFF7A4612),
    primaryButton = Color(0xFF1B1F24),
    onPrimaryButton = Color(0xFFF4F1EA),
)

val AlertColors = NightColors.copy(
    background = Color(0xFF130A0B),
    surface = Color(0xFF1E1012),
    surfaceRaised = Color(0xFF33161A),
    border = Color(0xFF3A1D21),
    textPrimary = Color(0xFFF6E9E8),
    textSecondary = Color(0xFFF2B8B5),
    accent = Color(0xFFFF8A80),
    primaryButton = Color(0xFFF6E9E8),
    onPrimaryButton = Color(0xFF130A0B),
)

val LocalShimpyoColors = staticCompositionLocalOf { NightColors }

object Shimpyo {
    val colors: ShimpyoColors @Composable get() = LocalShimpyoColors.current
}

@Composable
fun ShimpyoTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) NightColors else DayColors
    ShimpyoSurfaceTheme(colors, content)
}

/** 화면 하나만 다른 팔레트로 그릴 때 */
@Composable
fun ShimpyoSurfaceTheme(colors: ShimpyoColors, content: @Composable () -> Unit) {
    val scheme = if (colors.isDark) {
        darkColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent,
            background = colors.background, onBackground = colors.textPrimary,
            surface = colors.surface, onSurface = colors.textPrimary, onSurfaceVariant = colors.textSecondary,
        )
    } else {
        lightColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent,
            background = colors.background, onBackground = colors.textPrimary,
            surface = colors.surface, onSurface = colors.textPrimary, onSurfaceVariant = colors.textSecondary,
        )
    }
    CompositionLocalProvider(LocalShimpyoColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = ShimpyoTypography, content = content)
    }
}
