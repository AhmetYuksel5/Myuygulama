package com.ahmety.arapca

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

/**
 * Tarayıcı sürümünü kendi penceresinde açan kabuk.
 *
 * Sayfanın kendisi GitHub Pages'te duruyor ve orada güncelleniyor; bu APK
 * yalnızca onu adres çubuğu olmadan açmaya yarıyor. Bu yüzden sayfaya bir
 * özellik eklendiğinde APK'yı yeniden kurmak gerekmiyor — sayfanın servis
 * çalışanı yeni dosyaları kendisi alıyor.
 */
class OkuyucuActivity : Activity() {

    private lateinit var web: WebView

    /** Sayfa dosya seçtirmek istediğinde sistemin cevabını buraya veriyoruz. */
    private var dosyaBekleyen: ValueCallback<Array<Uri>>? = null

    override fun onCreate(durum: Bundle?) {
        super.onCreate(durum)

        web = WebView(this)
        setContentView(web)

        web.settings.javaScriptEnabled = true
        // Sayfa kitapları ve kelimeleri cihazda tutuyor; bu kapalıyken
        // depo hiç açılmıyor ve uygulama boş bir kitaplık gösteriyor.
        web.settings.domStorageEnabled = true

        web.webViewClient = object : WebViewClient() {
            /*
             * Kendi sayfamız pencerede kalıyor, dışarıya giden bağlantı
             * tarayıcıya çıkıyor. Aksi hâlde köprünün açık olduğu bu
             * pencerede yabancı bir sayfa açılabilirdi.
             */
            override fun shouldOverrideUrlLoading(
                gorunum: WebView,
                istek: WebResourceRequest,
            ): Boolean {
                val adres = istek.url
                if (adres.host == Uri.parse(getString(R.string.adres)).host) return false
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, adres))
                    true
                } catch (yok: ActivityNotFoundException) {
                    true
                }
            }

            /*
             * Sayfa her yüklendiğinde köprüyü tanıtan yama basılıyor. Yama
             * sayfanın kendi kodunda durmuyor; iPhone'da gereksiz, orada
             * indirme zaten çalışıyor.
             */
            override fun onPageFinished(gorunum: WebView, adres: String) {
                gorunum.evaluateJavascript(INDIRME_YAMASI, null)
            }

            override fun onReceivedError(
                gorunum: WebView,
                istek: WebResourceRequest,
                hata: WebResourceError,
            ) {
                // Yalnız ana çerçeve için: sayfa içindeki tek bir dosyanın
                // gelmemesi ekrana uyarı basmayı hak etmiyor.
                if (istek.isForMainFrame) bildir(R.string.baglanti_yok)
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            /*
             * Bu olmadan sayfadaki "dosya seç" hiç açılmıyor: WebView dosya
             * seçiciyi kendiliğinden getirmiyor. Kitap eklemek, kapak
             * seçmek ve yedek geri yüklemek buna bağlı.
             */
            override fun onShowFileChooser(
                gorunum: WebView,
                geriBildirim: ValueCallback<Array<Uri>>,
                olcutler: FileChooserParams,
            ): Boolean {
                dosyaBekleyen?.onReceiveValue(null)
                dosyaBekleyen = geriBildirim
                return try {
                    startActivityForResult(olcutler.createIntent(), ISTEK_DOSYA)
                    true
                } catch (yok: ActivityNotFoundException) {
                    dosyaBekleyen = null
                    geriBildirim.onReceiveValue(null)
                    false
                }
            }
        }

        web.addJavascriptInterface(Kopru(), "AndroidKopru")

        if (durum == null) web.loadUrl(getString(R.string.adres))
    }

    /** Ekran döndüğünde sayfa baştan yüklenmesin, kaldığı yerde kalsın. */
    override fun onSaveInstanceState(durum: Bundle) {
        super.onSaveInstanceState(durum)
        web.saveState(durum)
    }

    override fun onRestoreInstanceState(durum: Bundle) {
        super.onRestoreInstanceState(durum)
        web.restoreState(durum)
    }

    override fun onActivityResult(istek: Int, sonuc: Int, veri: Intent?) {
        super.onActivityResult(istek, sonuc, veri)
        if (istek != ISTEK_DOSYA) return
        val bekleyen = dosyaBekleyen ?: return
        dosyaBekleyen = null
        // Vazgeçildiğinde de cevap vermek zorundayız; vermezsek sayfadaki
        // dosya seçici bir daha hiç açılmıyor.
        bekleyen.onReceiveValue(
            WebChromeClient.FileChooserParams.parseResult(sonuc, veri),
        )
    }

    // Geri tuşu önce sayfada geri gidiyor, gidecek yer kalmayınca çıkıyor.
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    /**
     * Sayfa yedeği bellekte üretip indirtiyor. WebView bu tür indirmeleri
     * sessizce yutuyor — düğmeye basılıyor, hiçbir şey olmuyor. Sayfa
     * dosyayı buraya veriyor, biz İndirilenler klasörüne yazıyoruz.
     */
    private inner class Kopru {
        @JavascriptInterface
        fun kaydet(ad: String, veriAdresi: String) {
            val virgul = veriAdresi.indexOf(',')
            if (virgul < 0) {
                bildir(R.string.yedek_kaydedilemedi)
                return
            }
            try {
                val bayt = Base64.decode(veriAdresi.substring(virgul + 1), Base64.DEFAULT)
                val alanlar = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, ad)
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    // Yazma bitene kadar dosya yarım görünmesin.
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val hedef = contentResolver
                    .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, alanlar)
                if (hedef == null) {
                    bildir(R.string.yedek_kaydedilemedi)
                    return
                }
                contentResolver.openOutputStream(hedef)?.use { akis -> akis.write(bayt) }
                alanlar.clear()
                alanlar.put(MediaStore.Downloads.IS_PENDING, 0)
                contentResolver.update(hedef, alanlar, null, null)
                bildir(R.string.yedek_kaydedildi)
            } catch (aksama: Exception) {
                bildir(R.string.yedek_kaydedilemedi)
            }
        }

        @JavascriptInterface
        fun hata() = bildir(R.string.yedek_kaydedilemedi)
    }

    /** Köprü kendi ipliğinde çalışıyor; bildirim arayüz ipliğine taşınmalı. */
    private fun bildir(yazi: Int) = runOnUiThread {
        Toast.makeText(this, yazi, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val ISTEK_DOSYA = 1

        /**
         * Bellekte üretilen dosyanın indirilmesini köprüye çeviriyor.
         * Sayfadaki bağlantıya dokunulduğunda dosya okunup Android'e
         * veriliyor; başka her bağlantı eskisi gibi çalışmaya devam ediyor.
         */
        const val INDIRME_YAMASI = """
(function () {
  if (window.__androidIndirme) return;
  window.__androidIndirme = true;
  var asil = HTMLAnchorElement.prototype.click;
  HTMLAnchorElement.prototype.click = function () {
    var adres = this.href || "";
    if (this.download && adres.indexOf("blob:") === 0) {
      var ad = this.download;
      fetch(adres).then(function (y) { return y.blob(); }).then(function (b) {
        var okuyucu = new FileReader();
        okuyucu.onloadend = function () {
          AndroidKopru.kaydet(ad, String(okuyucu.result));
        };
        okuyucu.readAsDataURL(b);
      }).catch(function () { AndroidKopru.hata(); });
      return;
    }
    return asil.apply(this, arguments);
  };
})();
"""
    }
}
