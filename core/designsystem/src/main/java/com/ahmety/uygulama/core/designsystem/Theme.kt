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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Uygulamanın görsel kimliği: "mürekkep ve kâğıt".
 *
 * Gündüz zemini ılık bir kâğıt (`#FBF9F5`), gece zemini serin bir mürekkep
 * (`#101216`). Önceki açık zemin lavanta tonundaydı — Material şablonunun
 * dokunulmamış hâlinin en belirgin işareti oydu.
 *
 * Ana renk çivit mor kalıyor ama daha derin ve doygun. Zorunlu bir seçim:
 * mavi "bilmediğim kelime", kırmızı "ifade", sarı ve yeşil de işaretleme
 * renkleri — mor, hiçbir anlamla çakışmayan tek renk.
 *
 * Android 12+ cihazlarda duvar kâğıdı rengi devralınabiliyor ama varsayılan
 * kendi paletimiz; "köhne" görünümün bir sebebi de her cihazda farklı,
 * çoğunda soluk çıkan dinamik renklerdi.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF4B3FD6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4E0FF),
    onPrimaryContainer = Color(0xFF171067),
    secondary = Color(0xFF96590F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE1BE),
    onSecondaryContainer = Color(0xFF3A1F00),
    tertiary = Color(0xFF106B62),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFB6F0E5),
    onTertiaryContainer = Color(0xFF00201C),
    background = Color(0xFFFBF9F5),
    onBackground = Color(0xFF1C1B1A),
    surface = Color(0xFFFBF9F5),
    onSurface = Color(0xFF1C1B1A),
    surfaceVariant = Color(0xFFE8E3DA),
    onSurfaceVariant = Color(0xFF55524D),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F3ED),
    surfaceContainer = Color(0xFFF1EDE6),
    surfaceContainerHigh = Color(0xFFEBE7DF),
    surfaceContainerHighest = Color(0xFFE5E0D7),
    surfaceDim = Color(0xFFDED9D0),
    surfaceBright = Color(0xFFFFFFFF),
    outline = Color(0xFF86827B),
    outlineVariant = Color(0xFFE9E4DA),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD5),
    onErrorContainer = Color(0xFF410E0B),
    inversePrimary = Color(0xFFB9B2FF),
    inverseSurface = Color(0xFF31302E),
    inverseOnSurface = Color(0xFFF4F0EA),
    // Material'ın yükseklikle birlikte yüzeye kattığı mor tonu kapatıyoruz:
    // her yükselen yüzeyi sessizce moraltıyor ve "şablon Material" görüntüsünün
    // ana sebebi bu. Derinlik surfaceContainer katmanlarından geliyor.
    surfaceTint = Color.Transparent,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9B2FF),
    onPrimary = Color(0xFF251C86),
    primaryContainer = Color(0xFF3A2FB0),
    onPrimaryContainer = Color(0xFFE4E0FF),
    secondary = Color(0xFFF2B65C),
    onSecondary = Color(0xFF452A00),
    secondaryContainer = Color(0xFF6A3E00),
    onSecondaryContainer = Color(0xFFFFE1BE),
    tertiary = Color(0xFF6FD8C8),
    onTertiary = Color(0xFF00382F),
    tertiaryContainer = Color(0xFF005046),
    onTertiaryContainer = Color(0xFFB6F0E5),
    background = Color(0xFF101216),
    onBackground = Color(0xFFE6E3DE),
    surface = Color(0xFF101216),
    onSurface = Color(0xFFE6E3DE),
    surfaceVariant = Color(0xFF3A3D45),
    onSurfaceVariant = Color(0xFFB7B3AC),
    surfaceContainerLowest = Color(0xFF0A0C0F),
    surfaceContainerLow = Color(0xFF16181D),
    surfaceContainer = Color(0xFF1A1D23),
    surfaceContainerHigh = Color(0xFF232630),
    surfaceContainerHighest = Color(0xFF2C3038),
    surfaceDim = Color(0xFF0C0E11),
    surfaceBright = Color(0xFF363A43),
    outline = Color(0xFF7C7972),
    outlineVariant = Color(0xFF2A2D35),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inversePrimary = Color(0xFF4B3FD6),
    inverseSurface = Color(0xFFE6E3DE),
    inverseOnSurface = Color(0xFF2A2D31),
    surfaceTint = Color.Transparent,
)

/**
 * Arayüz yazı tipi: Manrope.
 *
 * Yarı yuvarlak geometrik bir grotesk — başlık boyunda karakteri var, sistem
 * yazı tipinin görünmezliği yok. Dosyalar Türkçe harfler (Ğ İ ı Ş Ç Ö Ü),
 * tırnaklar ve ₺ için budandı: üç kalınlık ~147 KB.
 */
private val Manrope = FontFamily(
    Font(R.font.manrope_400, FontWeight.Normal),
    Font(R.font.manrope_600, FontWeight.SemiBold),
    Font(R.font.manrope_800, FontWeight.ExtraBold),
)

/**
 * Okuma yazı tipi: Literata.
 *
 * Google'ın Play Books için yaptırdığı ekran serifi; x-yüksekliği geniş,
 * 16-20 punto arasında sağlam duruyor. Kitap ve makale gövdesi, kelime
 * kartının yüzü bununla diziliyor — bir okuma uygulamasının kitaba
 * benzemesi en çok buradan geliyor.
 */
private val Literata = FontFamily(
    Font(R.font.literata_400, FontWeight.Normal),
    Font(R.font.literata_600, FontWeight.SemiBold),
)

/** Dışarıdan da kullanılabilsin: okuyucu kendi gövdesini serifle diziyor. */
val MerkezSerif: FontFamily = Literata

/**
 * Ölçek.
 *
 * Üç kural: yirmi punto üstündeki her şeyde negatif harf aralığı (Google
 * Fonts groteskleri web için gevşek gelir, büyük boyda dizilmiş görünmesi
 * için sıkılması gerekiyor), başlıklarda kalınlık 700-800, ve gövde metni
 * 17/27 serif — 1,59 satır oranı. Bir web sayfasıyla bir kitap arasındaki
 * fark büyük ölçüde bu oran.
 */
private val MerkezTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Manrope,
        fontSize = 44.sp,
        lineHeight = 48.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-1.2).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = Manrope,
        fontSize = 36.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-1.0).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = Manrope,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.8).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = Manrope,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.6).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Manrope,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Manrope,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.3).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Manrope,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Manrope,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleSmall = TextStyle(
        fontFamily = Manrope,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.1.sp,
    ),
    // Gövde metni serif: kitap, makale, kart yüzü.
    bodyLarge = TextStyle(
        fontFamily = Literata,
        fontSize = 17.sp,
        lineHeight = 27.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodyMedium = TextStyle(
        fontFamily = Manrope,
        fontSize = 14.5.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.05.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Manrope,
        fontSize = 12.5.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.1.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Manrope,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.2.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Manrope,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Manrope,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
    ),
)

private val MerkezShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * Boşluk ölçüsü. Dörder punto artıyor.
 *
 * Ekran kenarı 20 — Android'in 16'sı "varsayılan" diye okunuyor. Kart içi
 * dolgu da 20 olunca metinle ekran kenarı arasında 40 kalıyor; iyi
 * tasarlanmış uygulamaların kullandığı oran bu.
 *
 * İç içe yuvarlak köşelerde kural: içteki yarıçap = dıştaki − dolgu.
 * Yanlış olduğunda kimse sebebini söyleyemiyor ama göze batıyor.
 */
object MerkezSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 28.dp
    val xxl = 40.dp
    val huge = 64.dp
}

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
