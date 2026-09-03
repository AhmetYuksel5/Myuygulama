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

    /** Geri: sola bakan ok. */
    val Back: ImageVector by lazy {
        outlined("MerkezBack") {
            moveTo(19f, 12f)
            lineTo(5f, 12f)
            moveTo(11.5f, 5.5f)
            lineTo(5f, 12f)
            lineTo(11.5f, 18.5f)
        }
    }

    /** Bölüm listesi: üç satır ve önlerinde birer nokta. */
    val ListLines: ImageVector by lazy {
        outlined("MerkezList") {
            moveTo(9f, 6.5f)
            lineTo(20f, 6.5f)
            moveTo(9f, 12f)
            lineTo(20f, 12f)
            moveTo(9f, 17.5f)
            lineTo(20f, 17.5f)
            moveTo(4.5f, 6.5f)
            lineTo(4.6f, 6.5f)
            moveTo(4.5f, 12f)
            lineTo(4.6f, 12f)
            moveTo(4.5f, 17.5f)
            lineTo(4.6f, 17.5f)
        }
    }

    /** Görünüm ayarları: büyük ve küçük "A". */
    val TextSize: ImageVector by lazy {
        outlined("MerkezTextSize") {
            // Küçük A
            moveTo(3f, 18f)
            lineTo(6.6f, 9.5f)
            lineTo(10.2f, 18f)
            moveTo(4.3f, 15.2f)
            lineTo(8.9f, 15.2f)
            // Büyük A
            moveTo(12.6f, 18f)
            lineTo(17.3f, 6f)
            lineTo(22f, 18f)
            moveTo(14.4f, 13.6f)
            lineTo(20.2f, 13.6f)
        }
    }

    /** Takvim: ayın kutusu ve iki halka. */
    val Calendar: ImageVector by lazy {
        outlined("MerkezCalendar") {
            moveTo(4f, 6.5f)
            lineTo(20f, 6.5f)
            lineTo(20f, 20f)
            lineTo(4f, 20f)
            close()
            moveTo(4f, 10.5f)
            lineTo(20f, 10.5f)
            moveTo(8f, 4f)
            lineTo(8f, 8f)
            moveTo(16f, 4f)
            lineTo(16f, 8f)
        }
    }

    /** Alışkanlık: alev. */
    val Flame: ImageVector by lazy {
        outlined("MerkezFlame") {
            moveTo(12f, 3f)
            curveTo(12f, 7f, 7.5f, 8f, 7.5f, 13f)
            curveTo(7.5f, 16.6f, 9.5f, 20f, 12f, 20f)
            curveTo(14.5f, 20f, 16.5f, 16.6f, 16.5f, 13f)
            curveTo(16.5f, 10.5f, 15f, 9.5f, 14.5f, 8f)
            curveTo(13.5f, 9.5f, 12.8f, 10f, 12f, 10f)
            curveTo(12.8f, 7.5f, 12f, 5f, 12f, 3f)
            close()
        }
    }

    /** Altyazı: çerçeve ve içinde iki satır. */
    val Subtitle: ImageVector by lazy {
        outlined("MerkezSubtitle") {
            moveTo(3f, 5f)
            lineTo(21f, 5f)
            lineTo(21f, 19f)
            lineTo(3f, 19f)
            close()
            moveTo(6.5f, 13f)
            lineTo(12f, 13f)
            moveTo(14.5f, 13f)
            lineTo(17.5f, 13f)
            moveTo(6.5f, 16f)
            lineTo(10f, 16f)
        }
    }

    /** Kenar hareketleri: ekranın kenarında bir şerit ve ok. */
    val EdgeSwipe: ImageVector by lazy {
        outlined("MerkezEdgeSwipe") {
            moveTo(4.5f, 4f)
            lineTo(4.5f, 20f)
            moveTo(8f, 8f)
            lineTo(8f, 16f)
            moveTo(12.5f, 12f)
            lineTo(20f, 12f)
            moveTo(16.5f, 8.5f)
            lineTo(20f, 12f)
            lineTo(16.5f, 15.5f)
        }
    }

    /** Tek elle imleç: sağ kenarda bir tutamak ve ok ucu. */
    val Cursor: ImageVector by lazy {
        outlined("MerkezCursor") {
            moveTo(7f, 4f)
            lineTo(7f, 15f)
            lineTo(10f, 12.5f)
            lineTo(12f, 17.5f)
            lineTo(14.2f, 16.5f)
            lineTo(12.2f, 11.6f)
            lineTo(16f, 11.2f)
            close()
        }
    }

    /** İzinler: kalkan. */
    val Shield: ImageVector by lazy {
        outlined("MerkezShield") {
            moveTo(12f, 3.5f)
            lineTo(19.5f, 6.2f)
            lineTo(19.5f, 12f)
            curveTo(19.5f, 16.2f, 16.5f, 19.2f, 12f, 20.5f)
            curveTo(7.5f, 19.2f, 4.5f, 16.2f, 4.5f, 12f)
            lineTo(4.5f, 6.2f)
            close()
        }
    }

    /** Senkronizasyon: birbirini kovalayan iki ok. */
    val Sync: ImageVector by lazy {
        outlined("MerkezSync") {
            moveTo(4.5f, 12f)
            curveTo(4.5f, 7.9f, 7.9f, 4.5f, 12f, 4.5f)
            curveTo(14.6f, 4.5f, 16.9f, 5.9f, 18.2f, 8f)
            moveTo(19.5f, 12f)
            curveTo(19.5f, 16.1f, 16.1f, 19.5f, 12f, 19.5f)
            curveTo(9.4f, 19.5f, 7.1f, 18.1f, 5.8f, 16f)
            moveTo(18.6f, 4.4f)
            lineTo(18.6f, 8.3f)
            lineTo(14.7f, 8.3f)
            moveTo(5.4f, 19.6f)
            lineTo(5.4f, 15.7f)
            lineTo(9.3f, 15.7f)
        }
    }

    /** Yapay zekâ: dört uçlu parıltı. */
    val Sparkle: ImageVector by lazy {
        outlined("MerkezSparkle") {
            moveTo(12f, 3.5f)
            curveTo(12.7f, 8.4f, 15.6f, 11.3f, 20.5f, 12f)
            curveTo(15.6f, 12.7f, 12.7f, 15.6f, 12f, 20.5f)
            curveTo(11.3f, 15.6f, 8.4f, 12.7f, 3.5f, 12f)
            curveTo(8.4f, 11.3f, 11.3f, 8.4f, 12f, 3.5f)
            close()
        }
    }

    /** Aşağı bakan küçük ok: açılır seçim. */
    val ChevronDown: ImageVector by lazy {
        outlined("MerkezChevronDown") {
            moveTo(6f, 9.5f)
            lineTo(12f, 15.5f)
            lineTo(18f, 9.5f)
        }
    }

    /** Kilitli asma kilit. */
    val Lock: ImageVector by lazy {
        outlined("MerkezLock") {
            moveTo(5.5f, 10.5f)
            lineTo(18.5f, 10.5f)
            lineTo(18.5f, 20f)
            lineTo(5.5f, 20f)
            close()
            // Kulp kapalı: gövdenin içine iniyor.
            moveTo(8f, 10.5f)
            lineTo(8f, 7.5f)
            curveTo(8f, 5.3f, 9.8f, 3.5f, 12f, 3.5f)
            curveTo(14.2f, 3.5f, 16f, 5.3f, 16f, 7.5f)
            lineTo(16f, 10.5f)
        }
    }

    /** Açık asma kilit: kulp bir yandan kalkmış. */
    val LockOpen: ImageVector by lazy {
        outlined("MerkezLockOpen") {
            moveTo(5.5f, 10.5f)
            lineTo(18.5f, 10.5f)
            lineTo(18.5f, 20f)
            lineTo(5.5f, 20f)
            close()
            moveTo(8f, 10.5f)
            lineTo(8f, 7.5f)
            curveTo(8f, 5.3f, 9.8f, 3.5f, 12f, 3.5f)
            curveTo(14.2f, 3.5f, 16f, 5.3f, 16f, 7.5f)
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
