package com.yesiltemizlik.ofis

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    /** Ayar sayfasından dönülünce izin durumları yeniden okunsun. */
    private var yenile by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OfisTema { AnaEkran(yenile) } }
    }

    override fun onResume() {
        super.onResume()
        yenile++
    }
}

@Composable
private fun AnaEkran(yenile: Int) {
    val ctx = LocalContext.current
    val depo = remember { Depo(ctx) }
    var jeton by remember { mutableStateOf(depo.jeton) }
    var isSayisi by remember { mutableIntStateOf(depo.isler().size) }
    var islerTs by remember { mutableStateOf(depo.islerTs) }
    var bekleyen by remember { mutableIntStateOf(depo.kuyruk().size) }
    var kartAcik by remember { mutableStateOf(depo.kartAcik) }
    var mesaj by remember { mutableStateOf("") }
    var mesgul by remember { mutableStateOf(false) }
    var yeniSurum by remember { mutableStateOf<Guncelleme.Surum?>(null) }
    var izinTur by remember { mutableIntStateOf(0) }

    fun isleriAl() {
        mesgul = true
        Arkaplan.isleriYenile(ctx) { hata ->
            mesgul = false
            isSayisi = depo.isler().size
            islerTs = depo.islerTs
            mesaj = hata ?: ""
        }
    }

    LaunchedEffect(yenile) {
        bekleyen = depo.kuyruk().size
        izinTur++
        if (jeton.isNotEmpty() && System.currentTimeMillis() - depo.islerTs > 6 * 3_600_000L) isleriAl()
        if (bekleyen > 0) Arkaplan.kuyruguGonder(ctx) { kalan, _ -> bekleyen = kalan }
    }
    LaunchedEffect(Unit) {
        val s = withContext(Dispatchers.IO) { Guncelleme.sonSurum() }
        val kurulu = try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).longVersionCode - 1
        } catch (e: Exception) {
            Long.MAX_VALUE
        }
        if (s != null && s.numara > kurulu) yeniSurum = s
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("My Asistan", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

            yeniSurum?.let { s ->
                Kutu {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Yeni sürüm 0.${s.numara}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Button(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(s.apk))) }) {
                            Text("İndir")
                        }
                    }
                }
            }

            if (jeton.isEmpty()) {
                var kod by remember { mutableStateOf("") }
                Kutu {
                    Text("Panelle eşle", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Panel › Log › Cihazlar › My Asistan › Eşleştirme kodu",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = kod,
                            onValueChange = { kod = it.filter(Char::isDigit).take(6) },
                            label = { Text("6 haneli kod") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(10.dp))
                        Button(
                            enabled = kod.length == 6 && !mesgul,
                            onClick = {
                                mesgul = true
                                Arkaplan.eslestir(ctx, kod, "${Build.BRAND} ${Build.MODEL}") { hata ->
                                    mesgul = false
                                    if (hata == null) {
                                        jeton = depo.jeton
                                        isSayisi = depo.isler().size
                                        islerTs = depo.islerTs
                                        mesaj = ""
                                    } else {
                                        mesaj = hata
                                    }
                                }
                            },
                            modifier = Modifier.height(56.dp),
                        ) { Text("Eşle") }
                    }
                }
            } else {
                Kutu {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("İşler: $isSayisi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            if (islerTs > 0) {
                                Text(
                                    "Güncelleme " + SimpleDateFormat("d MMM HH:mm", Locale("tr", "TR")).format(Date(islerTs)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        OutlinedButton(enabled = !mesgul, onClick = { isleriAl() }) {
                            Text(if (mesgul) "Yenileniyor" else "Yenile")
                        }
                    }
                    if (bekleyen > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Gönderilmeyen kayıt: $bekleyen",
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = {
                                Arkaplan.kuyruguGonder(ctx) { kalan, hata ->
                                    bekleyen = kalan
                                    mesaj = hata ?: ""
                                }
                            }) { Text("Gönder") }
                        }
                    }
                }
            }

            if (mesaj.isNotEmpty()) {
                Text(mesaj, color = MaterialTheme.colorScheme.error)
            }

            Kutu {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Görüşme sonu kartı", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Switch(checked = kartAcik, onCheckedChange = {
                        kartAcik = it
                        depo.kartAcik = it
                    })
                }
                OutlinedButton(
                    onClick = { ctx.startActivity(KartAcici.niyet(ctx, System.currentTimeMillis(), deneme = true)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Kartı dene") }
            }

            IzinListesi(izinTur)

            val surumAdi = remember {
                try {
                    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
                } catch (e: Exception) {
                    ""
                }
            }
            Text(
                "Sürüm $surumAdi",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (jeton.isNotEmpty()) {
                TextButton(onClick = {
                    depo.jeton = ""
                    jeton = ""
                }) { Text("Eşleşmeyi kaldır") }
            }
        }
    }
}

@Composable
private fun IzinListesi(tur: Int) {
    val ctx = LocalContext.current
    var sayac by remember { mutableIntStateOf(0) }
    val iste = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { sayac++ }
    // `tur` ve `sayac` değişince durumlar yeniden okunur (ayardan dönüş, izin cevabı).
    val liste = remember(tur, sayac) { izinler().map { it to it.durum(ctx) } }
    val eksik = liste.count { it.second == false }

    Kutu {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("İzinler", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
                if (eksik == 0) "Tamam" else "$eksik eksik",
                color = if (eksik == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
            )
        }
        liste.forEach { (izin, durum) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(izin.ad, fontWeight = FontWeight.Medium)
                    if (izin.not.isNotEmpty()) {
                        Text(izin.not, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(8.dp))
                if (durum == true) {
                    Text("Verildi", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                } else {
                    OutlinedButton(onClick = {
                        if (izin.calisma.isNotEmpty()) {
                            iste.launch(izin.calisma.toTypedArray())
                        } else {
                            izin.ayar?.let { a ->
                                try {
                                    ctx.startActivity(a(ctx))
                                } catch (e: Exception) {
                                    ctx.startActivity(
                                        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                                    )
                                }
                            }
                        }
                    }) { Text(if (durum == null) "Ayarla" else "Ver") }
                }
            }
        }
    }
}

@Composable
private fun Kutu(icerik: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = icerik)
    }
}
