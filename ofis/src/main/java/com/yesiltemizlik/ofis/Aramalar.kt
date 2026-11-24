package com.yesiltemizlik.ofis

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog

/** Arama kaydından bir satır. [yon]: gelen, giden, cevapsiz. [zaman] görüşmenin başladığı an, kaydın anahtarı. */
data class Arama(val tel: String, val ad: String, val sure: Long, val yon: String, val zaman: Long)

private val SUTUN = arrayOf(
    CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.DURATION, CallLog.Calls.TYPE, CallLog.Calls.DATE,
)

private fun yonu(tip: Int) = when (tip) {
    CallLog.Calls.OUTGOING_TYPE -> "giden"
    CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE, CallLog.Calls.BLOCKED_TYPE -> "cevapsiz"
    else -> "gelen"
}

fun Context.aramaIzniVar() = checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED

/** Son [adet] arama, yeniden eskiye. İzin yoksa boş. */
fun sonAramalar(c: Context, adet: Int = 80): List<Arama> {
    if (!c.aramaIzniVar()) return emptyList()
    return try {
        c.contentResolver.query(CallLog.Calls.CONTENT_URI, SUTUN, null, null, CallLog.Calls.DATE + " DESC")?.use { k ->
            val out = ArrayList<Arama>()
            while (k.moveToNext() && out.size < adet) {
                out += Arama(k.getString(0) ?: "", k.getString(1) ?: "", k.getLong(2), yonu(k.getInt(3)), k.getLong(4))
            }
            out
        } ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}

/**
 * Biten görüşmeyi bulur. Kayıt, telefon IDLE'a geçtikten biraz sonra
 * yazılıyor; en son satırın bu görüşme olup olmadığına bakılır: bitişi
 * açılma anından sonraysa ya da başlangıcı o ana denk geliyorsa (cevapsız
 * giden arama, süre 0) bu görüşmedir.
 */
fun bitenArama(c: Context, bas: Long): Arama? {
    val a = sonAramalar(c, 1).firstOrNull() ?: return null
    val bu = a.zaman >= bas - 3_000 || a.zaman + a.sure * 1000 > bas + 1_000
    return if (bu) a else null
}

/** 0532… / +90532… / 532… → 90532… (wa.me biçimi). */
fun waNumara(tel: String): String {
    var d = tel.filter { it.isDigit() }
    if (d.startsWith("0")) d = d.substring(1)
    if (d.length == 10) d = "90$d"
    return d
}

/**
 * Numarayla WhatsApp sohbetini açar. Ofiste çoğu zaman WhatsApp Business
 * kurulu; önce o, sonra normal WhatsApp, ikisi de yoksa tarayıcı.
 */
fun whatsappAc(c: Context, tel: String): Boolean {
    val n = waNumara(tel)
    if (n.length < 10) return false
    val uri = Uri.parse("https://wa.me/$n")
    for (paket in listOf("com.whatsapp.w4b", "com.whatsapp", null)) {
        try {
            val i = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (paket != null) i.setPackage(paket)
            c.startActivity(i)
            return true
        } catch (_: ActivityNotFoundException) {
        }
    }
    return false
}
