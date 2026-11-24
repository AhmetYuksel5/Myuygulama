package com.yesiltemizlik.ofis

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class SunucuHatasi(mesaj: String, val jetonGecersiz: Boolean = false) : Exception(mesaj)

/**
 * Panelin Apps Script ucu (PersonelTakip deposu, Ofis.gs). Uygulamada anahtar
 * yok: yetki, panelden alınan 6 haneli kodla bir kez eşleşince verilen cihaz
 * jetonudur. Jeton yalnız iş adı + ilçe okur ve görüşme kaydı yazar.
 */
object Sunucu {
    private const val ADRES =
        "https://script.google.com/macros/s/AKfycbzLwZ4SaF-Ikz1lWtZuoTpd4TRnDBybP5OtpTSyqlP6jUNOBb-mMOT6XdnjDhqMiva9/exec"

    private fun baglan(adres: String): HttpURLConnection =
        (URL(adres).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            // Apps Script cevabı başka bir adrese yönlendirerek veriyor; POST
            // yönlendirmesini kendimiz GET olarak izliyoruz, yoksa gövde kaybolur.
            instanceFollowRedirects = false
        }

    private fun gonder(ofis: JSONObject): JSONObject {
        val metin: String
        val kod: Int
        try {
            var c = baglan(ADRES)
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("Content-Type", "text/plain;charset=utf-8")
            c.outputStream.use { it.write(JSONObject().put("ofis", ofis).toString().toByteArray(Charsets.UTF_8)) }
            var k = c.responseCode
            var adim = 0
            while (k in 300..399 && adim < 5) {
                val yer = c.getHeaderField("Location") ?: break
                c.disconnect()
                c = baglan(yer)
                k = c.responseCode
                adim++
            }
            kod = k
            metin = (if (k < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            c.disconnect()
        } catch (e: IOException) {
            throw SunucuHatasi("Bağlantı kurulamadı")
        }
        val j = try {
            JSONObject(metin)
        } catch (e: Exception) {
            throw SunucuHatasi("Sunucudan beklenmeyen cevap ($kod)")
        }
        if (j.has("error")) {
            val gecersiz = j.optString("tanim") == "ofis-jeton"
            val m = when {
                gecersiz -> "Eşleşme geçersiz; panelden yeni kodla eşleyin"
                j.optString("error") == "unauthorized" -> "Yetki yok"
                else -> j.optString("error")
            }
            throw SunucuHatasi(m, gecersiz)
        }
        return j
    }

    fun eslestir(kod: String, cihaz: String): String =
        gonder(JSONObject().put("eslestir", kod).put("cihaz", cihaz)).getString("jeton")

    fun isler(jeton: String): List<Is> {
        val a: JSONArray = gonder(JSONObject().put("jeton", jeton).put("isler", 1)).optJSONArray("isler") ?: JSONArray()
        return List(a.length()) { i ->
            val o = a.getJSONObject(i)
            Is(o.optString("id"), o.optString("ad"), o.optString("ilce"), o.optString("yaka"))
        }
    }

    fun kaydet(jeton: String, kayit: JSONObject) {
        gonder(JSONObject().put("jeton", jeton).put("kaydet", kayit))
    }
}
