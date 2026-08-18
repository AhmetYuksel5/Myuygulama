package com.ahmety.uygulama.feature.subtitles

/**
 * Aynı sürüme ait altyazıyı bulmak.
 *
 * Bir filmin onlarca sürümü var ve altyazı zamanlaması sürüme bağlı: YIFY
 * kopyasına RARBG altyazısı takınca konuşmalar kayıyor. İngilizce altyazıyı
 * seçtikten sonra Türkçesini, aynı sürüm etiketlerini taşıyandan seçiyoruz.
 */
object ReleaseMatch {

    /**
     * Bilinen dağıtım grupları. Küçük harfe indirilmiş sürüm adında
     * aranıyorlar; YTS ve YIFY aynı ekip olduğu için birlikte sayılıyor.
     */
    private val GROUPS = listOf(
        setOf("yify", "yts"),
        setOf("rarbg"),
        setOf("sparks"),
        setOf("geckos"),
        setOf("evo"),
        setOf("fgt"),
        setOf("cmrg"),
        setOf("ntb"),
        setOf("amiable"),
        setOf("drones"),
        setOf("psa"),
        setOf("galaxyrg"),
        setOf("qxr"),
        setOf("tigole"),
    )

    /** Kaynak niteliği: bunlar da eşleşirse zamanlamanın tutma ihtimali artıyor. */
    private val QUALITIES = listOf(
        "bluray", "brrip", "bdrip", "webrip", "web-dl", "webdl", "web",
        "hdtv", "dvdrip", "hdrip", "remux",
    )

    /** Sürüm adındaki dağıtım grubu; bulunamazsa null. */
    fun groupOf(release: String): String? {
        val text = release.lowercase()
        return GROUPS.firstOrNull { aliases -> aliases.any { text.contains(it) } }?.first()
    }

    fun qualityOf(release: String): String? {
        val text = release.lowercase().replace(".", "").replace("-", "")
        return QUALITIES.firstOrNull { text.contains(it.replace("-", "")) }
    }

    /**
     * İki sürüm adının ne kadar uyuştuğu. Büyük daha iyi.
     *
     * Grup eşleşmesi en ağır basan ölçüt: zamanlamayı belirleyen o.
     * Sonra kaynak niteliği, sonra çözünürlük geliyor.
     */
    fun score(a: String, b: String): Int {
        var score = 0
        val groupA = groupOf(a)
        if (groupA != null && groupA == groupOf(b)) score += 100

        val qualityA = qualityOf(a)
        if (qualityA != null && qualityA == qualityOf(b)) score += 20

        listOf("2160p", "1080p", "720p", "480p").forEach { resolution ->
            if (a.contains(resolution, true) && b.contains(resolution, true)) score += 10
        }

        // Ortak sözcükler: sürüm adları genelde nokta ile ayrılıyor.
        val tokensA = tokens(a)
        val tokensB = tokens(b)
        score += tokensA.intersect(tokensB).size

        return score
    }

    private fun tokens(release: String): Set<String> =
        release.lowercase()
            .split('.', ' ', '-', '_', '[', ']', '(', ')')
            .filter { it.length >= 3 }
            .toSet()
}
