package com.kronos.focus.ui.theme

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kronos.focus.R

/*
 * Sistema de diseño de Task Manager IA llevado a Android: teal como color principal, naranja para
 * las acciones, fondo teal muy claro, texto verde oscuro, tipografía Plus Jakarta Sans, estilo plano
 * (bordes finos en vez de sombras). Variante oscura para usarlo de noche.
 * Nota de accesibilidad: los rellenos usan teal-700 / naranja-600 para que el texto blanco tenga contraste suficiente.
 */

object Palette {
    val Teal950 = Color(0xFF042F2E); val Teal900 = Color(0xFF134E4A); val Teal800 = Color(0xFF115E59)
    val Teal700 = Color(0xFF0F766E); val Teal600 = Color(0xFF0D9488); val Teal500 = Color(0xFF14B8A6)
    val Teal400 = Color(0xFF2DD4BF); val Teal100 = Color(0xFFCCFBF1); val Teal50 = Color(0xFFF0FDFA)
    val Orange600 = Color(0xFFEA580C); val Orange400 = Color(0xFFFB923C); val Orange100 = Color(0xFFFFEDD5)
    val Slate900 = Color(0xFF0F172A); val Slate800 = Color(0xFF1E293B); val Slate700 = Color(0xFF334155)
    val Slate600 = Color(0xFF475569); val Slate400 = Color(0xFF94A3B8); val Slate300 = Color(0xFFCBD5E1)
    val Slate200 = Color(0xFFE2E8F0); val Slate100 = Color(0xFFF1F5F9)
    val Red600 = Color(0xFFDC2626); val Red400 = Color(0xFFF87171); val Red100 = Color(0xFFFEE2E2)
    val Green700 = Color(0xFF15803D); val Green100 = Color(0xFFDCFCE7); val Amber700 = Color(0xFFB45309); val Amber100 = Color(0xFFFEF3C7)
}

@Immutable
data class KronosColors(
    val cta: Color, val onCta: Color,
    val successContainer: Color, val onSuccessContainer: Color,
    val warningContainer: Color, val onWarningContainer: Color,
    val heroStart: Color, val heroEnd: Color,
)

private val LightKronos = KronosColors(
    Palette.Orange600, Color.White, Palette.Green100, Palette.Green700, Palette.Amber100, Palette.Amber700, Palette.Teal800, Palette.Teal700,
)
private val DarkKronos = KronosColors(
    Palette.Orange400, Palette.Slate900, Color(0xFF14532D), Color(0xFF86EFAC), Color(0xFF78350F), Color(0xFFFCD34D), Palette.Teal900, Palette.Teal800,
)
// Diseño "Medianoche": casi negro, acento lima, violeta de apoyo, esquinas muy redondeadas.
private object Mid {
    val Bg = Color(0xFF0B0D12); val Surface = Color(0xFF151922); val SurfaceHi = Color(0xFF1E2430)
    val Outline = Color(0xFF2A3140); val Text = Color(0xFFF2F5FA); val Muted = Color(0xFF9AA4B5)
    val Lime = Color(0xFFC8F751); val OnLime = Color(0xFF10140A); val LimeDeep = Color(0xFF26301A); val LimeSoft = Color(0xFFE4FFA3)
    val Violet = Color(0xFFA78BFA)
}
private val MidnightKronos = KronosColors(
    Mid.Lime, Mid.OnLime, Color(0xFF1B3322), Color(0xFF9BF0B0), Color(0xFF3A2E10), Color(0xFFFCD34D), Color(0xFF1B2230), Color(0xFF121722),
)
private val MidnightScheme = darkColorScheme(
    primary = Mid.Lime, onPrimary = Mid.OnLime,
    primaryContainer = Mid.LimeDeep, onPrimaryContainer = Mid.LimeSoft,
    secondary = Mid.Violet, onSecondary = Mid.OnLime,
    tertiary = Mid.Violet, onTertiary = Mid.OnLime,
    background = Mid.Bg, onBackground = Mid.Text,
    surface = Mid.Surface, onSurface = Mid.Text,
    surfaceVariant = Mid.SurfaceHi, onSurfaceVariant = Mid.Muted,
    outline = Mid.Outline, outlineVariant = Mid.Outline,
    error = Palette.Red400, onError = Palette.Slate900, errorContainer = Color(0xFF4A1D1D), onErrorContainer = Palette.Red100,
)
private val MidnightShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp), small = RoundedCornerShape(14.dp), medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(36.dp),
)

val LocalKronosColors = staticCompositionLocalOf { LightKronos }

object Kronos {
    val colors: KronosColors @Composable get() = LocalKronosColors.current
}

private val LightScheme = lightColorScheme(
    primary = Palette.Teal700, onPrimary = Color.White,
    primaryContainer = Palette.Teal100, onPrimaryContainer = Palette.Teal900,
    secondary = Palette.Teal600, onSecondary = Color.White,
    tertiary = Palette.Orange600, onTertiary = Color.White,
    background = Palette.Teal50, onBackground = Palette.Teal900,
    surface = Color.White, onSurface = Palette.Teal900,
    surfaceVariant = Palette.Slate100, onSurfaceVariant = Palette.Slate600,
    outline = Palette.Slate300, outlineVariant = Palette.Slate200,
    error = Palette.Red600, onError = Color.White, errorContainer = Palette.Red100, onErrorContainer = Color(0xFF7F1D1D),
)

private val DarkScheme = darkColorScheme(
    primary = Palette.Teal400, onPrimary = Palette.Teal950,
    primaryContainer = Palette.Teal900, onPrimaryContainer = Palette.Teal100,
    secondary = Palette.Teal500, onSecondary = Palette.Teal950,
    tertiary = Palette.Orange400, onTertiary = Palette.Slate900,
    background = Palette.Slate900, onBackground = Palette.Slate100,
    surface = Palette.Slate800, onSurface = Palette.Slate100,
    surfaceVariant = Palette.Slate700, onSurfaceVariant = Palette.Slate300,
    outline = Palette.Slate600, outlineVariant = Palette.Slate700,
    error = Palette.Red400, onError = Palette.Slate900, errorContainer = Color(0xFF7F1D1D), onErrorContainer = Palette.Red100,
)

@OptIn(ExperimentalTextApi::class)
private val Jakarta = FontFamily(
    listOf(400, 500, 600, 700).map { w ->
        Font(R.font.plus_jakarta_sans, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    }
)

private fun Typography.withFont(f: FontFamily) = copy(
    displayLarge = displayLarge.copy(fontFamily = f), displayMedium = displayMedium.copy(fontFamily = f, fontWeight = FontWeight.Bold),
    displaySmall = displaySmall.copy(fontFamily = f), headlineLarge = headlineLarge.copy(fontFamily = f, fontWeight = FontWeight.Bold),
    headlineMedium = headlineMedium.copy(fontFamily = f, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
    headlineSmall = headlineSmall.copy(fontFamily = f, fontWeight = FontWeight.SemiBold),
    titleLarge = titleLarge.copy(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = titleMedium.copy(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    titleSmall = titleSmall.copy(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    bodyLarge = bodyLarge.copy(fontFamily = f, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = bodyMedium.copy(fontFamily = f, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = bodySmall.copy(fontFamily = f, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = labelLarge.copy(fontFamily = f, fontWeight = FontWeight.SemiBold),
    labelMedium = labelMedium.copy(fontFamily = f, fontWeight = FontWeight.SemiBold),
    labelSmall = labelSmall.copy(fontFamily = f),
)

private val KronosShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp), extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun KronosTheme(content: @Composable () -> Unit) {
    val midnight = UiDesign.current == UiDesign.MIDNIGHT
    val dark = midnight || isSystemInDarkTheme()
    // Con el diseño oscuro forzado, los iconos de las barras del sistema deben ser claros aunque el teléfono esté en modo claro.
    val activity = LocalContext.current as? ComponentActivity
    SideEffect {
        runCatching {
            if (midnight) activity?.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            ) else activity?.enableEdgeToEdge()
        }
    }
    CompositionLocalProvider(LocalKronosColors provides if (midnight) MidnightKronos else if (dark) DarkKronos else LightKronos) {
        MaterialTheme(
            colorScheme = if (midnight) MidnightScheme else if (dark) DarkScheme else LightScheme,
            typography = Typography().withFont(Jakarta),
            shapes = if (midnight) MidnightShapes else KronosShapes,
            content = content,
        )
    }
}
