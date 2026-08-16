package com.ahmety.uygulama.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Uygulamanın görsel kimliği: çivit mor ana renk, yumuşak tonlu yüzeyler,
 * geniş köşe yarıçapları ve kalın başlık hiyerarşisi.
 *
 * Android 12+ cihazlarda kullanıcı isterse duvar kâğıdı rengi (dinamik renk)
 * devralınabiliyor; ama varsayılan artık kendi paletimiz — "köhne" görünümün
 * ana sebebi her cihazda farklı, çoğunda soluk görünen dinamik renklerdi.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF5A5BD8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2E0FF),
    onPrimaryContainer = Color(0xFF15134A),
    secondary = Color(0xFF5D5C72),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E0F9),
    onSecondaryContainer = Color(0xFF1A192C),
    tertiary = Color(0xFF9A4058),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD9DF),
    onTertiaryContainer = Color(0xFF3F0018),
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE3E1EC),
    onSurfaceVariant = Color(0xFF46464F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F2FA),
    surfaceContainer = Color(0xFFEFECF4),
    surfaceContainerHigh = Color(0xFFE9E7EF),
    surfaceContainerHighest = Color(0xFFE3E1E9),
    outline = Color(0xFF777680),
    outlineVariant = Color(0xFFC7C5D0),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC2C1FF),
    onPrimary = Color(0xFF2B2A77),
    primaryContainer = Color(0xFF42428F),
    onPrimaryContainer = Color(0xFFE2E0FF),
    secondary = Color(0xFFC6C4DD),
    onSecondary = Color(0xFF2F2E42),
    secondaryContainer = Color(0xFF454459),
    onSecondaryContainer = Color(0xFFE2E0F9),
    tertiary = Color(0xFFFFB1C0),
    onTertiary = Color(0xFF5F112B),
    tertiaryContainer = Color(0xFF7C2941),
    onTertiaryContainer = Color(0xFFFFD9DF),
    background = Color(0xFF131318),
    onBackground = Color(0xFFE4E1E9),
    surface = Color(0xFF131318),
    onSurface = Color(0xFFE4E1E9),
    surfaceVariant = Color(0xFF46464F),
    onSurfaceVariant = Color(0xFFC7C5D0),
    surfaceContainerLowest = Color(0xFF0D0D12),
    surfaceContainerLow = Color(0xFF1B1B21),
    surfaceContainer = Color(0xFF1F1F25),
    surfaceContainerHigh = Color(0xFF2A292F),
    surfaceContainerHighest = Color(0xFF35343A),
    outline = Color(0xFF91909A),
    outlineVariant = Color(0xFF46464F),
    error = Color(0xFFFFB4AB),
)

/** Kalın, sıkı başlıklar; rahat okunan gövde metni. */
private val MerkezTypography = Typography(
    headlineLarge = TextStyle(
        fontSize = 32.sp,
        lineHeight = 38.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontSize = 26.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.3).sp,
    ),
    headlineSmall = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.Medium,
    ),
)

/** Geniş yarıçaplar: kartlar 20, diyaloglar 28. */
private val MerkezShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Alışkanlık/etiket gibi öğelere atanabilen hazır renk paleti. */
object MerkezPalette {
    val accentColors: List<Color> = listOf(
        Color(0xFF5A5BD8), // çivit
        Color(0xFF00897B), // çam yeşili
        Color(0xFFE65100), // turuncu
        Color(0xFFC2185B), // fuşya
        Color(0xFF2E7D32), // yeşil
        Color(0xFF0277BD), // okyanus
        Color(0xFF8E24AA), // mor
        Color(0xFFF9A825), // hardal
        Color(0xFF6D4C41), // kahve
        Color(0xFF455A64), // arduvaz
    )

    /** Kayda kalıcı renk atamak için: aynı uuid her zaman aynı rengi alır. */
    fun colorFor(seed: String): Color =
        accentColors[(seed.hashCode() and Int.MAX_VALUE) % accentColors.size]
}

@Composable
fun MerkezTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** Duvar kâğıdından türeyen sistem renkleri; artık varsayılan olarak kapalı. */
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MerkezTypography,
        shapes = MerkezShapes,
        content = content,
    )
}
