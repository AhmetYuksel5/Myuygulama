package com.ahmety.uygulama.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Uygulamanın kendi çizdiği simgeler.
 *
 * `material-icons-extended` iki binden fazla simge taşıyor; biz yedisini
 * kullanıyorduk ve küçültme kapalı olduğu için tamamı APK'ya giriyordu —
 * paketin en büyük parçası oydu.
 *
 * Küçük kardeşi `material-icons-core`'a düşmek de bir seçenekti ama
 * Material3 1.4 onu artık kendiliğinden getirmiyor; ayrıca eklemek yerine
 * yedi simgeyi burada çizmek hem bağımlılığı tamamen kaldırıyor hem de
 * hepsini aynı çizgi kalınlığında tutuyor.
 */
object MerkezIcons {

    /** Kitaplık sekmesi: açık kitap. */
    val Book: ImageVector by lazy {
        outlined("MerkezBook") {
            // Sırt ortada; iki yaprak simetrik iki yamuk.
            moveTo(12f, 8f)
            lineTo(3f, 6f)
            lineTo(3f, 17f)
            lineTo(12f, 19f)
            close()
            moveTo(12f, 8f)
            lineTo(21f, 6f)
            lineTo(21f, 17f)
            lineTo(12f, 19f)
            close()
        }
    }

    /** Kelimeler sekmesi: iki ayrı yazının yan yana durması. */
    val Translate: ImageVector by lazy {
        outlined("MerkezTranslate") {
            // Solda Latin olmayan bir yazı izlenimi veren çizgiler.
            moveTo(2.5f, 5.5f)
            lineTo(10.5f, 5.5f)
            moveTo(6.5f, 3.2f)
            lineTo(6.5f, 7f)
            moveTo(6.5f, 7f)
            lineTo(3f, 12.5f)
            moveTo(6.5f, 7f)
            lineTo(10f, 12.5f)
            // Sağda "A": ikisi bir arada olunca çeviri okunuyor.
            moveTo(13f, 21f)
            lineTo(17.5f, 10.5f)
            lineTo(22f, 21f)
            moveTo(14.6f, 17.6f)
            lineTo(20.4f, 17.6f)
        }
    }

    /** Güncelleme: aşağı ok, altında tabla. */
    val Download: ImageVector by lazy {
        outlined("MerkezDownload") {
            moveTo(12f, 3.5f)
            lineTo(12f, 14.5f)
            moveTo(7.5f, 10.5f)
            lineTo(12f, 15f)
            lineTo(16.5f, 10.5f)
            // Tabla: okun indiği yer.
            moveTo(4.5f, 18.5f)
            lineTo(19.5f, 18.5f)
        }
    }

    /** Kapat: çarpı. */
    val Close: ImageVector by lazy {
        outlined("MerkezClose") {
            moveTo(6f, 6f)
            lineTo(18f, 18f)
            moveTo(18f, 6f)
            lineTo(6f, 18f)
        }
    }

    /** Pocket sekmesi: yer imi. */
    val Bookmark: ImageVector by lazy {
        outlined("MerkezBookmark") {
            moveTo(6.5f, 3.8f)
            lineTo(17.5f, 3.8f)
            lineTo(17.5f, 20.2f)
            lineTo(12f, 15.6f)
            lineTo(6.5f, 20.2f)
            close()
        }
    }

    /**
     * Pocket sekmesi: kapanmış uygulamanın kendi biçimi — üstü düz, altı
     * yuvarlanan bir cep ve içinde aşağı bakan çengel.
     *
     * Yer imi simgesi duruyordu; bu sekmenin adı Pocket ve o uygulamanın
     * hatırası kastediliyor, simgenin de onu söylemesi gerekiyor.
     */
    val Pocket: ImageVector by lazy {
        outlined("MerkezPocket") {
            // Cebin ağzı: iki üst köşe hafif yuvarlak.
            moveTo(3f, 7.2f)
            curveTo(3f, 5.9f, 3.9f, 5f, 5.2f, 5f)
            lineTo(18.8f, 5f)
            curveTo(20.1f, 5f, 21f, 5.9f, 21f, 7.2f)
            // Yanlar aşağı inip altta birleşiyor.
            lineTo(21f, 10.6f)
            curveTo(21f, 15.6f, 16.9f, 19.6f, 12f, 19.6f)
            curveTo(7.1f, 19.6f, 3f, 15.6f, 3f, 10.6f)
            close()
            // Aşağı bakan çengel.
            moveTo(8.1f, 9.9f)
            lineTo(12f, 13.7f)
            lineTo(15.9f, 9.9f)
        }
    }

    /** Ekle: artı. */
    val Add: ImageVector by lazy {
        outlined("MerkezAdd") {
            moveTo(12f, 5f)
            lineTo(12f, 19f)
            moveTo(5f, 12f)
            lineTo(19f, 12f)
        }
    }

    /** Onay: tik. */
    val Check: ImageVector by lazy {
        outlined("MerkezCheck") {
            moveTo(5f, 12.8f)
            lineTo(9.8f, 17.6f)
            lineTo(19f, 6.6f)
        }
    }

    /** Verilmiş izin: daire içinde tik. */
    val CheckCircle: ImageVector by lazy {
        outlined("MerkezCheckCircle") {
            // Daire: iki yarım yay. Yol komutlarında hazır bir çember yok.
            moveTo(3f, 12f)
            arcToRelative(9f, 9f, 0f, true, true, 18f, 0f)
            arcToRelative(9f, 9f, 0f, true, true, -18f, 0f)
            close()
            moveTo(7.6f, 12.3f)
            lineTo(10.8f, 15.5f)
            lineTo(16.4f, 8.9f)
        }
    }

    /** Satır menüsü: alt alta üç nokta. */
    val MoreVert: ImageVector by lazy {
        filled("MerkezMoreVert") {
            dot(12f, 6f)
            dot(12f, 12f)
            dot(12f, 18f)
        }
    }

    /** "Daha" sekmesi: yan yana üç nokta. */
    val MoreHoriz: ImageVector by lazy {
        filled("MerkezMoreHoriz") {
            dot(6f, 12f)
            dot(12f, 12f)
            dot(18f, 12f)
        }
    }

    /** Dolu bir daire. Yay komutlarıyla; `addOval` vektör yolunda yok. */
    private fun PathBuilder.dot(centerX: Float, centerY: Float, radius: Float = 1.9f) {
        moveTo(centerX - radius, centerY)
        arcToRelative(radius, radius, 0f, true, true, radius * 2, 0f)
        arcToRelative(radius, radius, 0f, true, true, -radius * 2, 0f)
        close()
    }

    /**
     * İçi boş, çizgiyle çizilen simge. Rengi çağıran taraf veriyor:
     * [Color.Black] burada yalnızca "ne verilirse onunla boya" demek —
     * `Icon` bileşeni tint uyguluyor.
     */
    private fun outlined(name: String, pathData: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = { pathData() },
            )
        }.build()

    private fun filled(name: String, pathData: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black), pathBuilder = { pathData() })
        }.build()
}
