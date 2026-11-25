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

    /** Eklenme sırasıyla (dosya adının önündeki sıra numarası). */
    fun dosyalar(c: Context): List<File> = (klasor(c).listFiles() ?: emptyArray()).sortedBy { it.name }

    /** Görünen ad: sıra öneki atılmış dosya adı. */
    fun ad(f: File) = f.name.substringAfter('_')

    /**
     * Seçilen dosyayı uygulamanın kendi klasörüne kopyalar: telefondaki asıl
     * dosya taşınsa ya da silinse de tanıtım bozulmasın.
     */
    fun ekle(c: Context, uri: Uri): Boolean = try {
        var ad = "dosya"
        c.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) ad = it.getString(0) ?: ad
        }
        ad = ad.replace(Regex("[/\\\\:*?\"<>|]"), "_")
        val hedef = File(klasor(c), "%013d_%s".format(System.currentTimeMillis(), ad))
        c.contentResolver.openInputStream(uri)?.use { g -> hedef.outputStream().use { g.copyTo(it) } }
        hedef.exists() && hedef.length() > 0
    } catch (e: Exception) {
        false
    }

    fun sil(f: File) = f.delete()

    private fun tur(f: File): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "application/octet-stream"

    /**
     * WhatsApp'ı o numaranın sohbetinde, yazı ve dosyalar ekli açar. "jid"
     * WhatsApp'ın resmî olmayan ama yıllardır çalışan eki: kişi seçme ekranını
     * atlatıp doğrudan sohbete düşürür. Tanımazsa kişi seçme ekranı çıkar.
     */
    fun gonder(c: Context, tel: String): Boolean {
        val n = waNumara(tel)
        if (n.length < 10) return false
        val metin = mesaj(c)
        val dosya = dosyalar(c)
        if (dosya.isEmpty()) {
            // Dosya yoksa yalnız yazı: wa.me bağlantısı sohbeti yazı dolu açar.
            val uri = Uri.parse("https://wa.me/$n?text=" + URLEncoder.encode(metin, "UTF-8"))
            return paketlerleDene(c) { paket ->
                Intent(Intent.ACTION_VIEW, uri).also { if (paket != null) it.setPackage(paket) }
            }
        }
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
            i.putExtra(Intent.EXTRA_TEXT, metin)
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
