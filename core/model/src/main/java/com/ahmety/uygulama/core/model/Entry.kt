package com.ahmety.uygulama.core.model

import kotlinx.serialization.Serializable

/**
 * Uygulamanın tüm modüllerinin paylaştığı tek kayıt tipi.
 *
 * Not, makale, PDF, alıntı, kelime, görev ve haber — hepsi aynı çekirdeğin
 * farklı yüzleri. Ortak çekirdek sayesinde tek arama, tek etiket sistemi,
 * modüller arası bağlantı ve tek yedek mümkün oluyor.
 */
data class Entry(
    val id: Long = 0L,
    /**
     * Cihazdan bağımsız kimlik. Bugün tek cihaz kullanıyoruz ama ileride ikinci bir
     * cihaz veya bir senkron katmanı eklenirse, otomatik artan `id` çakışır — `uuid`
     * çakışmaz. Şimdi eklemenin maliyeti sıfır, sonra eklemenin maliyeti veri göçü.
     */
    val uuid: String,
    val type: EntryType,
    val title: String,
    val body: String = "",
    /** Kaynak URL, dosya yolu veya kaydın nereden geldiğini anlatan serbest metin. */
    val source: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    /** Kullanıcının arşivlediği kayıt: silinmedi, sadece listelerden çekildi. */
    val archived: Boolean = false,
    /**
     * Mezar taşı. Kayıt silindiğinde satır hemen yok edilmez, işaretlenir;
     * böylece yedekten dönüşte ve olası bir senkronda "silindi" bilgisi kaybolmaz.
     */
    val deletedAt: Long? = null,
    val tags: List<Tag> = emptyList(),
)

@Serializable
enum class EntryType {
    NOTE,
    ARTICLE,
    DOCUMENT,
    HIGHLIGHT,
    WORD,
    TASK,
    NEWS,
}

data class Tag(
    val id: Long = 0L,
    val uuid: String,
    val name: String,
    /** ARGB; null ise tema rengi kullanılır. */
    val color: Int? = null,
)

/**
 * İki kayıt arasındaki yönlü bağlantı. Bir notun bir makaleye atıf vermesi,
 * bir alıntının kaynak dokümanına bağlanması bu tabloda tutulur.
 */
data class EntryLink(
    val id: Long = 0L,
    val fromEntryId: Long,
    val toEntryId: Long,
    val relation: LinkRelation = LinkRelation.REFERENCES,
)

@Serializable
enum class LinkRelation {
    /** Kaynak kayıt hedefe atıf veriyor. */
    REFERENCES,

    /** Hedef kayıt, kaynağın içinden çıkarıldı (alıntı → makale, kelime → alıntı). */
    EXTRACTED_FROM,
}
