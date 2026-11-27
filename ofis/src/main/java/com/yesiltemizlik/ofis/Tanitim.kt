package com.yesiltemizlik.ofis

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.net.URLEncoder

/**
 * WhatsApp tanıtımı: telefonla arayan müşteriye WhatsApp'tan dönülürken giden
 * hazır yazı ve dosyalar (referans listesi, tanıtım görseli, video).
 *
 * Tamamen otomatik gönderim yok: WhatsApp başka bir uygulamanın kendi adına
 * mesaj göndermesine izin vermiyor. Sohbet yazı ve dosyalar ekli açılır,
 * Gönder'e kullanıcı basar.
 */
object Tanitim {
    const val VARSAYILAN_MESAJ =
        "Merhaba, Yeşil Temizlik firmasından Esra ben. Referanslarımızı ve tanıtımımızı ekte gönderiyorum. " +
            "Temizlenecek yerin fotoğraf ya da videolarını buradan gönderebilirsiniz."

    private fun klasor(c: Context) = File(c.filesDir, "tanitim").apply { mkdirs() }

    fun mesaj(c: Context): String =
        c.getSharedPreferences("ofis", Context.MODE_PRIVATE).getString("waMesaj", null) ?: VARSAYILAN_MESAJ

    fun mesajYaz(c: Context, m: String) =
        c.getSharedPreferences("ofis", Context.MODE_PRIVATE).edit().putString("waMesaj", m).apply()

    /**
     * Kataloglar (kullanıcı 2026-11-26): kartta / balonda "Referanslar"a basınca
     * hangisinin gideceği seçilir. "genel" eski düzende eklenmiş dosyalar.
     * Uygulamayla gelen katalog PDF'leri `assets/katalog/<anahtar>.pdf`; ilk
     * açılışta bir kez kopyalanır, silinirse geri gelmez.
     */
    data class Grup(val anahtar: String, val ad: String, val gomulu: String? = null)

    val GRUPLAR = listOf(
        // Genel tanıtım ve yalnız referanslar (kullanıcı 2026-11-27); ötekiler hizmete özel.
        Grup("tanitim", "Tanıtım kataloğu", "Yesil Temizlik - Tanitim Katalogu.pdf"),
        Grup("ofis", "Ofis tanıtım", "Yesil Temizlik - Ofis Temizligi.pdf"),
        Grup("bos", "Boş daire temizliği", "Yesil Temizlik - Bos Daire Temizligi.pdf"),
        Grup("insaat", "İnşaat sonrası", "Yesil Temizlik - Insaat Sonrasi Temizlik.pdf"),
        Grup("referans", "Referanslar", "Yesil Temizlik - Referanslar.pdf"),
        Grup("genel", "Diğer"),
    )

    private fun grupKlasoru(c: Context, g: String) =
        if (g == "genel") klasor(c) else File(klasor(c), "k_$g").apply { mkdirs() }

    /**
     * Gömülü katalogların sürümü: katalog PDF'leri yenilenince artırılır.
     * Telefondaki eski katalog yenisiyle değişir; kullanıcı adını değiştirdiyse
     * ad korunur, sildiyse (klasörü yoksa) geri getirilmez.
     */
    private const val KATALOG_SURUM = 4

    private fun gomululeriKopyala(c: Context) {
        val p = c.getSharedPreferences("ofis", Context.MODE_PRIVATE)
        for (g in GRUPLAR) {
            val ad = g.gomulu ?: continue
            // Eski sürüm yalnız "kopyalandı" bayrağı tutuyordu; o bayrak sürüm 1 sayılır.
            val kurulu = p.getInt("gomuluSurum_${g.anahtar}", if (p.getBoolean("gomulu_${g.anahtar}", false)) 1 else 0)
            if (kurulu >= KATALOG_SURUM) continue
            try {
                val d = File(grupKlasoru(c, g.anahtar), "0000000000000")
                if (kurulu > 0 && !d.exists()) {
                    // Kullanıcı kataloğu silmiş: yenisini de getirme.
                    p.edit().putInt("gomuluSurum_${g.anahtar}", KATALOG_SURUM).apply()
                    continue
                }
                d.mkdirs()
                val hedef = d.listFiles()?.firstOrNull { it.isFile } ?: File(d, ad)
                c.assets.open("katalog/${g.anahtar}.pdf").use { giris ->
                    hedef.outputStream().use { giris.copyTo(it) }
                }
                p.edit().putInt("gomuluSurum_${g.anahtar}", KATALOG_SURUM).putBoolean("gomulu_${g.anahtar}", true).apply()
            } catch (_: Exception) {
                // Bu sürümde gömülü katalog yok; sonraki sürümde gelirse kopyalanır.
            }
        }
    }

    /** İçinde dosya olan gruplar, sırasıyla. */
    fun doluGruplar(c: Context): List<Grup> = GRUPLAR.filter { dosyalar(c, it.anahtar).isNotEmpty() }

    fun dosyalar(c: Context): List<File> = GRUPLAR.flatMap { dosyalar(c, it.anahtar) }

    /**
     * Her dosya kendi klasöründe (klasör adı = eklenme anı, sıra için): WhatsApp
     * belgeyi dosya adıyla gösteriyor, adın önünde sıra numarası durmasın.
     * Eski düzende (önekli, düz klasörde) eklenmiş dosyalar burada taşınır.
     */
    fun dosyalar(c: Context, grup: String): List<File> {
        gomululeriKopyala(c)
        val k = grupKlasoru(c, grup)
        if (grup == "genel") (k.listFiles() ?: emptyArray()).filter { it.isFile }.forEach { eski ->
            val sira = eski.name.substringBefore('_', "")
            val ad = eski.name.substringAfter('_')
            if (sira.isNotEmpty()) {
                val yeniKlasor = File(k, sira).apply { mkdirs() }
                eski.renameTo(File(yeniKlasor, ad))
            }
        }
        return (k.listFiles() ?: emptyArray()).filter { it.isDirectory && !it.name.startsWith("k_") }.sortedBy { it.name }
            .mapNotNull { d -> d.listFiles()?.firstOrNull { it.isFile } }
    }

    fun ad(f: File): String = f.name

    /** Uzantısız ad: düzenleme kutusunda yalnız bu gösterilir, uzantı korunur. */
    fun adGovde(f: File): String = f.nameWithoutExtension

    private fun temizle(ad: String) = ad.replace(Regex("[/\\\\:*?\"<>|]"), "_").trim()

    /** Uzantı aynı kalır; boş ya da aynı ad değiştirmez. */
    fun yenidenAdlandir(f: File, yeniGovde: String): File {
        val g = temizle(yeniGovde).removeSuffix("." + f.extension)
        if (g.isEmpty()) return f
        val yeni = File(f.parentFile, if (f.extension.isEmpty()) g else "$g.${f.extension}")
        if (yeni.name == f.name) return f
        return if (f.renameTo(yeni)) yeni else f
    }

    /**
     * Seçilen dosyayı uygulamanın kendi klasörüne kopyalar: telefondaki asıl
     * dosya taşınsa ya da silinse de tanıtım bozulmasın.
     */
    fun ekle(c: Context, uri: Uri, grup: String): Boolean = try {
        var ad = "dosya"
        c.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) ad = it.getString(0) ?: ad
        }
        ad = temizle(ad).ifEmpty { "dosya" }
        val hedef = File(File(grupKlasoru(c, grup), "%013d".format(System.currentTimeMillis())).apply { mkdirs() }, ad)
        c.contentResolver.openInputStream(uri)?.use { g -> hedef.outputStream().use { g.copyTo(it) } }
        hedef.exists() && hedef.length() > 0
    } catch (e: Exception) {
        false
    }

    fun sil(f: File) {
        val d = f.parentFile
        f.delete()
        d?.delete()
    }

    private fun tur(f: File): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "application/octet-stream"

    /**
     * İKİ AŞAMA (kullanıcı 2026-11-26): WhatsApp dosyayla birlikte gelen yazıyı
     * ayrı mesaj yapmıyor, ilk dosyanın açıklamasına koyuyor ya da PDF'te
     * düşürüyor. Bu yüzden önce selam yazısı, sonra ayrı bir basışla dosyalar.
     */
    fun yaziGonder(c: Context, tel: String): Boolean {
        val n = waNumara(tel)
        if (n.length < 10) return false
        // wa.me sohbeti yazı dolu açar; Gönder'e kullanıcı basar.
        val uri = Uri.parse("https://wa.me/$n?text=" + URLEncoder.encode(mesaj(c), "UTF-8"))
        return paketlerleDene(c) { paket ->
            Intent(Intent.ACTION_VIEW, uri).also { if (paket != null) it.setPackage(paket) }
        }
    }

    /**
     * Dosyaları aynı sohbete, yazısız gönderir. "jid" WhatsApp'ın resmî
     * olmayan ama yıllardır çalışan eki: kişi seçme ekranını atlatıp doğrudan
     * sohbete düşürür. Tanımazsa kişi seçme ekranı çıkar.
     */
    fun dosyaGonder(c: Context, tel: String, grup: String): Boolean {
        val n = waNumara(tel)
        val dosya = dosyalar(c, grup)
        if (n.length < 10 || dosya.isEmpty()) return false
        val urler = ArrayList(dosya.map { FileProvider.getUriForFile(c, "${c.packageName}.dosyalar", it) })
        val turler = dosya.map { tur(it) }
        val ortak = when {
            turler.all { it.startsWith("image/") } -> "image/*"
            turler.all { it.startsWith("video/") } -> "video/*"
            else -> "*/*"
        }
        return paketlerleDene(c) { paket ->
            val i = if (urler.size == 1) {
                Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, urler[0]).setType(turler[0])
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, urler).setType(ortak)
            }
            i.putExtra("jid", "$n@s.whatsapp.net")
            // Birden çok dosyada okuma izni yalnız ClipData ile geçiyor.
            val clip = ClipData.newRawUri(null, urler[0])
            urler.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
            i.clipData = clip
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (paket != null) i.setPackage(paket) else return@paketlerleDene Intent.createChooser(i, "Gönder")
            i
        }
    }

    /** Ofiste çoğu zaman WhatsApp Business kurulu: önce o, sonra WhatsApp, sonra seçici. */
    private fun paketlerleDene(c: Context, niyet: (String?) -> Intent): Boolean {
        for (paket in listOf("com.whatsapp.w4b", "com.whatsapp", null)) {
            try {
                c.startActivity(niyet(paket).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: ActivityNotFoundException) {
            }
        }
        return false
    }
}
