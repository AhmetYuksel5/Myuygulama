package com.yesiltemizlik.ofis

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pencere, kart şeridi ve Ayarlar satırı aynı indirme durumunu kullanır. */
class Guncelleyici(private val ctx: Context) {
    /** "", iniyor, kuruluyor, hata */
    var durum by mutableStateOf("")
    var yuzde by mutableIntStateOf(0)

    fun baslat(s: Guncelleme.Surum) {
        if (!Guncelleme.kurmaIzniVar(ctx)) {
            // İzin sayfasından dönünce aynı düğmeye bir kez daha basılır.
            Toast.makeText(ctx, "My Asistan'a izin verip geri dönün", Toast.LENGTH_LONG).show()
            ctx.startActivity(Guncelleme.kurmaIzniNiyeti(ctx))
            return
        }
        durum = "iniyor"
        yuzde = 0
        Guncelleme.indirVeKur(ctx, s, ilerleme = { yuzde = (it * 100).toInt() }) { hata ->
            durum = if (hata == null) "kuruluyor" else "hata"
        }
    }
}

@Composable
fun rememberGuncelleyici(): Guncelleyici {
    val ctx = LocalContext.current
    return remember { Guncelleyici(ctx) }
}

private fun kuruluAd(c: Context): String = try {
    c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: ""
} catch (e: Exception) {
    ""
}

/** Uygulama açılınca yeni sürüm varsa. */
@Composable
fun GuncellemePenceresi(s: Guncelleme.Surum, onKapat: () -> Unit) {
    val ctx = LocalContext.current
    val g = rememberGuncelleyici()
    AlertDialog(
        onDismissRequest = { if (g.durum != "iniyor") onKapat() },
        title = { Text("Yeni güncelleme var", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Sürüm ${s.ad} · kurulu ${kuruluAd(ctx)}")
                when (g.durum) {
                    "iniyor" -> LinearProgressIndicator(
                        progress = { g.yuzde / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    "kuruluyor" -> Text("Kuruluyor", color = MaterialTheme.colorScheme.primary)
                    "hata" -> Text("İndirilemedi", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(enabled = g.durum != "iniyor" && g.durum != "kuruluyor", onClick = { g.baslat(s) }) {
                Text(if (g.durum == "iniyor") "%${g.yuzde}" else "Güncelle")
            }
        },
        dismissButton = {
            if (g.durum != "iniyor") TextButton(onClick = onKapat) { Text("Sonra") }
        },
    )
}

/** Görüşme kartının üstünde ince şerit. */
@Composable
fun KartGuncellemeSeridi(s: Guncelleme.Surum) {
    val g = rememberGuncelleyici()
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
        ) {
            Text(
                if (g.durum == "hata") "İndirilemedi" else "Yeni sürüm ${s.ad}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            when (g.durum) {
                "iniyor" -> Text("%${g.yuzde}", fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp))
                "kuruluyor" -> CircularProgressIndicator(Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
                else -> TextButton(onClick = { g.baslat(s) }) { Text("Güncelle") }
            }
        }
    }
}

/** Ayarlar'daki sürüm satırı: Denetle → (yeni varsa) Güncelle → yüzde → kurulum. */
@Composable
fun GuncellemeSatiri() {
    val ctx = LocalContext.current
    val kapsam = rememberCoroutineScope()
    val g = rememberGuncelleyici()
    var surum by remember { mutableStateOf<Guncelleme.Surum?>(null) }
    var denetim by remember { mutableStateOf("") }
    val kurulu = remember { kuruluAd(ctx) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            val s = surum
            Text(if (s != null) "Yeni sürüm ${s.ad}" else "Sürüm $kurulu", fontWeight = FontWeight.Bold)
            val alt = when {
                g.durum == "hata" -> "İndirilemedi"
                g.durum == "kuruluyor" -> "Kuruluyor"
                s != null -> "Kurulu: $kurulu"
                denetim == "guncel" -> "Güncel"
                denetim == "yok" -> "Denetlenemedi"
                else -> ""
            }
            if (alt.isNotEmpty()) {
                Text(alt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val s = surum
        when {
            g.durum == "iniyor" -> Text("%${g.yuzde}", fontWeight = FontWeight.Bold)
            denetim == "denetleniyor" -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            s != null -> Button(enabled = g.durum != "kuruluyor", onClick = { g.baslat(s) }) { Text("Güncelle") }
            else -> OutlinedButton(onClick = {
                denetim = "denetleniyor"
                kapsam.launch {
                    val bulunan = withContext(Dispatchers.IO) { Guncelleme.sonSurum() }
                    surum = bulunan?.takeIf { it.numara > Guncelleme.kuruluNumara(ctx) }
                    denetim = when {
                        bulunan == null -> "yok"
                        surum != null -> ""
                        else -> "guncel"
                    }
                }
            }) { Text("Denetle") }
        }
    }
}
