package com.yesiltemizlik.ofis

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Telefonda kalan her şey tek bir SharedPreferences dosyasında: eşleşme jetonu,
 * panelden çekilen işlerin kopyası (kart çevrimdışıyken de anında açılsın diye)
 * ve henüz gönderilemeyen kayıtlar.
 */
class Depo(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("ofis", Context.MODE_PRIVATE)

    var jeton: String
        get() = p.getString("jeton", "") ?: ""
        set(v) = p.edit().putString("jeton", v).apply()

    var kartAcik: Boolean
        get() = p.getBoolean("kartAcik", true)
        set(v) = p.edit().putBoolean("kartAcik", v).apply()

    var sonYaka: String
        get() = p.getString("sonYaka", "avrupa") ?: "avrupa"
        set(v) = p.edit().putString("sonYaka", v).apply()

    /** Görüşme sürüyor mu: telefon durumu yayını süreç ölse de kaybolmasın diye burada. */
    var konusuldu: Boolean
        get() = p.getBoolean("konusuldu", false)
        set(v) = p.edit().putBoolean("konusuldu", v).commit().let { }

    var aramaBas: Long
        get() = p.getLong("aramaBas", 0L)
        set(v) = p.edit().putLong("aramaBas", v).apply()

    var islerTs: Long
        get() = p.getLong("islerTs", 0L)
        private set(v) = p.edit().putLong("islerTs", v).apply()

    fun isler(): List<Is> = try {
        val a = JSONArray(p.getString("isler", "[]"))
        List(a.length()) { i ->
            val o = a.getJSONObject(i)
            Is(o.optString("id"), o.optString("ad"), o.optString("ilce"), o.optString("yaka"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun islerYaz(liste: List<Is>) {
        val a = JSONArray()
        liste.forEach { a.put(JSONObject().put("id", it.id).put("ad", it.ad).put("ilce", it.ilce).put("yaka", it.yaka)) }
        p.edit().putString("isler", a.toString()).apply()
        islerTs = System.currentTimeMillis()
    }

    fun kuyruk(): List<JSONObject> = synchronized(KILIT) {
        try {
            val a = JSONArray(p.getString("kuyruk", "[]"))
            List(a.length()) { a.getJSONObject(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun kuyrugaEkle(kayit: JSONObject) = synchronized(KILIT) {
        val a = JSONArray()
        kuyruk().forEach { a.put(it) }
        a.put(kayit)
        p.edit().putString("kuyruk", a.toString()).commit()
    }

    fun kuyruktanCikar(idler: Set<String>) = synchronized(KILIT) {
        val a = JSONArray()
        kuyruk().filter { it.optString("id") !in idler }.forEach { a.put(it) }
        p.edit().putString("kuyruk", a.toString()).commit()
    }

    companion object {
        private val KILIT = Any()
    }
}
