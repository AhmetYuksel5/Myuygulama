package com.ahmety.uygulama.feature.gestures

import android.content.Context
import android.content.Intent
import java.util.Locale

/**
 * Ekran ajanının hangi banka ve ödeme uygulamalarında çalışabileceği ve
 * para kilidi.
 *
 * Varsayılan olarak ajan bütün banka uygulamalarında duruyor. Kullanıcı
 * Yapay zekâ ayarlarından tek tek açtığı bankalarda ajan okuyup gezinebiliyor
 * ve form doldurabiliyor; para çıkaran düğmelere ise hiçbir zaman kendisi
 * basmıyor. Bu kilit modelin sözüne değil, düğmenin telefonun kendisinden
 * okunan yazısına dayanıyor.
 */
object BankAccess {

    private const val PREFS = "merkez_yapay_zeka"
    private const val KEY_ALLOWED = "agent_bank_allowed"

    /** Paket adı banka ya da ödeme uygulamasına benziyor mu (Ekrana sor ile aynı kural). */
    fun isBankLike(pkg: String): Boolean = ScreenAsk.isSensitive(pkg)

    fun allowed(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_ALLOWED, emptySet()).orEmpty()

    fun setAllowed(context: Context, pkg: String, on: Boolean) {
        val next = allowed(context).toMutableSet().apply { if (on) add(pkg) else remove(pkg) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_ALLOWED, next).apply()
    }

    /** Ajan bu uygulamada durmalı mı: bankaya benziyor ve kullanıcı açmamış. */
    fun blocked(context: Context, pkg: String): Boolean = isBankLike(pkg) && pkg !in allowed(context)

    /** Kullanıcının açtığı bir banka mı: orada para kilidi ve Opus devrede. */
    fun isAllowedBank(context: Context, pkg: String): Boolean = isBankLike(pkg) && pkg in allowed(context)

    /** Telefondaki banka ve ödeme uygulamaları: (paket, görünen ad), ada göre sıralı. */
    fun installedBanks(context: Context): List<Pair<String, String>> = runCatching {
        val pm = context.packageManager
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { isBankLike(it) }
            .map { pkg ->
                pkg to runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }
                    .getOrDefault(pkg)
            }
            .sortedBy { it.second.lowercase(TR) }
    }.getOrDefault(emptyList())

    private val TR = Locale.forLanguageTag("tr")

    // Para çıkaran ya da işlemi bitiren düğmelerin kelimeleri. Geniş tutuldu:
    // yanlışlıkla kilitlenen bir menü düğmesine kullanıcı kendisi basar, ama
    // kaçırılan bir onay düğmesi geri alınamaz.
    private val MONEY_PHRASES = listOf(
        "onay", "gönder", "satın al", "tamamla", "transfer et", "havale yap", "eft yap",
        "fast yap", "virman", "işlemi bitir", "ödemeyi", "ödeme yap", "öde ", "kabul et", "bağışla",
    )
    private val MONEY_WORDS = setOf("öde", "ödeyin", "onayla", "gönder", "tamam", "evet", "devam et")

    /**
     * Bu yazıyı taşıyan düğmeye ajan basmamalı mı. Yazısız düğme de kilitli:
     * bir onay simgesi olabilir; geri dönmek için ajan zaten "geri" hareketini
     * kullanabiliyor.
     */
    fun isMoneyButton(label: String?): Boolean {
        val text = label?.trim()?.lowercase(TR).orEmpty()
        if (text.isEmpty()) return true
        val padded = "$text "
        if (MONEY_PHRASES.any { padded.contains(it) }) return true
        return text.split(Regex("[^\\p{L}]+")).any { it in MONEY_WORDS } || text in MONEY_WORDS
    }
}
