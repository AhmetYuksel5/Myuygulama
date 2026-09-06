package com.ahmety.uygulama.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Bir eserde kaldığın yer.
 *
 * Ayrı bir satır olmasının sebebi: bu bilgi cihazın tercihlerinde
 * duruyordu ve oradan senkrona giremiyordu. Üstelik anahtarı kaydın
 * **yerel** kimliğiydi; o kimlik iki telefonda farklı olduğu için taşınsa
 * bile yanlış kitabı gösterirdi. Birincil anahtar artık kaydın kalıcı
 * kimliği (uuid) — iki telefonda da aynı.
 *
 * Alanlar iki okuyucuyu birden karşılıyor: EPUB'da bölüm ve paragraf,
 * PDF'te sayfa. Yüzde ikisinde de kitaplıktaki çizgi için.
 */
@Serializable
@Entity(tableName = "reading_progress")
data class ReadingProgressEntity(
    /** Eserin kayıt kimliği. */
    @PrimaryKey val entryUuid: String,
    val chapter: Int = 0,
    val paragraph: Int = 0,
    /**
     * Tepedeki paragrafın ne kadarı yukarıda kaldı; piksel.
     *
     * Paragraf numarası tek başına yetmiyor: uzun bir paragrafın
     * ortasındayken kitabı kapatıp açınca o paragrafın başına dönülüyor ve
     * okunan yer bulunana kadar aranıyor. Bu sayı ekranın tepesindeki
     * satırı geri getiriyor.
     *
     * Adı "scrollOffset"; "offset" SQL'in kendi kelimesi.
     */
    val scrollOffset: Int = 0,
    val page: Int = 0,
    val percent: Int = 0,
    val updatedAt: Long,
)
