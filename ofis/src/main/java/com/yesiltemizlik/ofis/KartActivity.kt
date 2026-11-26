package com.yesiltemizlik.ofis

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private enum class Adim { SECIM, ILCE, ISLER, MUSTERI }

class KartActivity : ComponentActivity() {
    private var bas by mutableLongStateOf(0L)
    private var deneme by mutableStateOf(false)
    private var otomatik by mutableStateOf(false)
    private var verilen by mutableStateOf<Arama?>(null)
    private var tur by mutableIntStateOf(0)
    private var isler by mutableStateOf(emptyList<Is>())
    private var guncel by mutableStateOf<Guncelleme.Surum?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Kapat düğmesi yok: pencerenin dışına dokununca kapanır.
        setFinishOnTouchOutside(true)
        oku(intent)
        KartAcici.bildirimiSil(this)
        val d = Depo(this)
        isler = d.isler()
        // Bekleyen kayıt varsa her kartta bir kez daha denenir.
        Arkaplan.kuyruguGonder(this)
        if (d.jeton.isNotEmpty() && System.currentTimeMillis() - d.islerTs > 6 * 3_600_000L) {
            Arkaplan.isleriYenile(this) { hata -> if (hata == null) isler = Depo(this).isler() }
        }
        // Yeni sürüm varsa kartın üstünde şerit; saatlik önbellek, her görüşmede GitHub'a gidilmez.
        Thread {
            val s = Guncelleme.bilinenYeni(applicationContext)
            runOnUiThread { guncel = s }
        }.start()
        setContent {
            OfisTema {
                key(tur) {
                    Kart(
                        bas = bas,
                        deneme = deneme,
                        otomatik = otomatik,
                        verilen = verilen,
                        isler = isler,
                        guncel = guncel,
                        onKapat = { finish() },
                        onSurukle = ::surukle,
                        onSurukleBitti = { Depo(this).kartY = window.attributes.y },
                    )
                }
            }
        }
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        // Kart ekranın dışına taşabilsin (kullanıcı 2026-11-25); sistem pencereyi ekrana sığdırmasın.
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        // Konum üstten ölçülür ki sürükleme ilk dokunuşta zıplamasın. Dikey yer
        // hatırlanır; yana kaydırma her kartta ortaya döner, yoksa sonraki kart
        // yarısı ekran dışında açılır.
        val lp = window.attributes
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        val kayitli = d.kartY
        lp.y = if (kayitli >= 0) kayitli else (resources.displayMetrics.heightPixels * 0.18f).toInt()
        lp.x = 0
        window.attributes = lp
        // Kart uzayınca (iş listesi açılınca) sınırlar yeniden uygulanır.
        window.decorView.addOnLayoutChangeListener { _, _, ust, _, alt, _, eskiUst, _, eskiAlt ->
            if (alt - ust != eskiAlt - eskiUst) surukle(0f, 0f)
        }
    }

    /**
     * Kart aşağı ve iki yana ekran dışına taşabilir; tutulabilsin diye kenarda
     * [GORUNUR_DP] kadarı hep kalır. Yukarı taşmaz: tutamak üstte, kaybolursa
     * kart geri çekilemez.
     */
    private fun surukle(dx: Float, dy: Float) {
        val lp = window.attributes
        val ekran = resources.displayMetrics
        val kalan = (GORUNUR_DP * ekran.density).toInt()
        val genis = window.decorView.width.takeIf { it > 0 } ?: ekran.widthPixels
        val yanAzami = (genis - kalan).coerceAtLeast(0)
        lp.x = (lp.x + dx.toInt()).coerceIn(-yanAzami, yanAzami)
        lp.y = (lp.y + dy.toInt()).coerceIn(0, (ekran.heightPixels - kalan).coerceAtLeast(0))
        window.attributes = lp
    }

    companion object {
        private const val GORUNUR_DP = 96
    }

    // Kart kapanınca, sormadan kurulabiliyorsa güncelleme arka planda kurulur (görüşme sırasında değil).
    override fun onStop() {
        super.onStop()
        if (isFinishing) Guncelleme.arkaplandaGuncelle(this)
    }

    // Kart açıkken yeni bir görüşme biterse kart sıfırdan başlar.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        oku(intent)
        tur++
        KartAcici.bildirimiSil(this)
    }

    private fun oku(i: Intent) {
        bas = i.getLongExtra(KartAcici.EK_BAS, System.currentTimeMillis())
        deneme = i.getBooleanExtra(KartAcici.EK_DENEME, false)
        // Deneme kartı da kendiliğinden kapanmayı göstersin.
        otomatik = i.getBooleanExtra(KartAcici.EK_OTOMATIK, false) || deneme
        verilen = KartAcici.aramaOku(i)
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun Kart(
    bas: Long,
    deneme: Boolean,
    otomatik: Boolean,
    verilen: Arama?,
    isler: List<Is>,
    guncel: Guncelleme.Surum?,
    onKapat: () -> Unit,
    onSurukle: (Float, Float) -> Unit,
    onSurukleBitti: () -> Unit,
) {
    val ctx = LocalContext.current
    val depo = remember { Depo(ctx) }
    val kimlik = remember { "o" + System.currentTimeMillis().toString(36) + (1000..9999).random() }
    var arama by remember {
        mutableStateOf(verilen ?: if (deneme) Arama("05321234567", "", 125, "gelen", System.currentTimeMillis()) else null)
    }
    var adim by remember { mutableStateOf(Adim.SECIM) }
    var yaka by remember { mutableStateOf(depo.sonYaka) }
    var ilce by remember { mutableStateOf("") }
    val secili = remember { mutableStateListOf<String>() }
    var ad by remember { mutableStateOf(verilen?.ad ?: "") }
    var not by remember { mutableStateOf("") }
    var digerAcik by remember { mutableStateOf(false) }
    var ara by remember { mutableStateOf("") }
    var engelSor by remember { mutableStateOf(false) }
    var tanitimAsama by remember { mutableIntStateOf(0) }

    LaunchedEffect(bas) {
        if (deneme || verilen != null) return@LaunchedEffect
        repeat(8) {
            val a = withContext(Dispatchers.IO) { bitenArama(ctx, bas) }
            if (a != null) {
                // Listeye alınmış numara: kart hiç görünmesin.
                if (depo.engelliMi(a.tel)) {
                    onKapat()
                    return@LaunchedEffect
                }
                arama = a
                if (ad.isEmpty()) ad = a.ad
                return@LaunchedEffect
            }
            delay(700)
        }
    }

    BackHandler(enabled = adim != Adim.SECIM) {
        adim = if (adim == Adim.ISLER) Adim.ILCE else Adim.SECIM
    }

    fun kaydet(tur: String) {
        val a = arama
        val j = JSONObject()
            .put("id", kimlik)
            .put("ts", a?.zaman ?: bas)
            .put("tel", a?.tel ?: "")
            .put("rehberAd", a?.ad ?: "")
            .put("yon", if (a?.yon == "giden") "giden" else "gelen")
            .put("sure", a?.sure ?: 0L)
            .put("tur", tur)
            .put("ad", ad.trim())
            .put("not", not.trim())
        if (tur == "is") {
            val dizi = JSONArray()
            isler.filter { it.id in secili }.forEach { dizi.put(JSONObject().put("id", it.id).put("ad", it.ad)) }
            j.put("ilce", ilce).put("isler", dizi)
        }
        if (!deneme) {
            if (a != null) depo.siniflandir(a.zaman, tur)
            depo.kuyrugaEkle(j)
            Arkaplan.kuyruguGonder(ctx)
        }
        Toast.makeText(ctx, if (deneme) "Deneme: kaydedilmedi" else "Kaydedildi", Toast.LENGTH_SHORT).show()
        onKapat()
    }

    // KENDİLİĞİNDEN KAPANMA (kullanıcı 2026-11-25): görüşme sonu kartına süre
    // boyunca (Ayarlar, varsayılan 5 sn; 0 = kapanmasın) dokunulmazsa kapanır.
    // Herhangi bir dokunuş (sürükleme dahil) sayacı durdurur.
    val sure = remember { depo.kartSure }
    var dokunuldu by remember { mutableStateOf(!otomatik || sure <= 0) }
    val kalan = remember { Animatable(1f) }
    LaunchedEffect(dokunuldu) {
        if (dokunuldu) return@LaunchedEffect
        kalan.animateTo(0f, tween(durationMillis = sure * 1000, easing = LinearEasing))
        onKapat()
    }

    // Sürükleme ham ekran koordinatıyla: pencere parmakla birlikte kaydığı için
    // pencereye göre konum işe yaramıyor.
    var sonX by remember { mutableFloatStateOf(0f) }
    var sonY by remember { mutableFloatStateOf(0f) }
    val tutamac = Modifier.pointerInteropFilter { e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                sonX = e.rawX
                sonY = e.rawY
                true
            }
            MotionEvent.ACTION_MOVE -> {
                onSurukle(e.rawX - sonX, e.rawY - sonY)
                sonX = e.rawX
                sonY = e.rawY
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                onSurukleBitti()
                true
            }
            else -> false
        }
    }

    val renk = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = renk.surface,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            // İlk geçişte (çocuklardan önce) bakılır, olay tüketilmez: düğmeler yine çalışır.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val olay = awaitPointerEvent(PointerEventPass.Initial)
                        if (olay.changes.any { it.pressed }) dokunuldu = true
                    }
                }
            },
    ) {
        Column(Modifier.heightIn(max = 660.dp)) {
            // Sürükleme tutamağı; kendiliğinden kapanacaksa altında azalan süre çizgisi.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth().height(22.dp).then(tutamac),
            ) {
                Box(
                    Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(renk.outlineVariant),
                )
            }
            if (!dokunuldu) {
                LinearProgressIndicator(
                    progress = { kalan.value },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(2.dp),
                    color = renk.primary,
                    trackColor = Color.Transparent,
                    drawStopIndicator = {},
                )
            }
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, bottom = 18.dp, top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            guncel?.let { KartGuncellemeSeridi(it) }
            // Başlık: kim, ne kadar konuşuldu. Bu satırdan da sürüklenir.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).then(tutamac)) {
                    val a = arama
                    Text(
                        text = when {
                            a == null -> "Görüşme bitti"
                            a.ad.isNotEmpty() -> a.ad
                            else -> telBicim(a.tel)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (a != null) {
                        val alt = listOf(
                            if (a.ad.isNotEmpty()) telBicim(a.tel) else "",
                            if (a.yon == "giden") "Giden" else "Gelen",
                            sureMetni(a.sure),
                        ).filter { it.isNotEmpty() }.joinToString(" · ")
                        Text(alt, style = MaterialTheme.typography.bodySmall, color = renk.onSurfaceVariant)
                    }
                }
                KapatDugmesi(
                    onKapat = onKapat,
                    onUzun = if (numaraAnahtari(arama?.tel ?: "").isNotEmpty()) ({ engelSor = true }) else null,
                )
            }
            if (engelSor) {
                val tel = arama?.tel ?: ""
                AlertDialog(
                    onDismissRequest = { engelSor = false },
                    title = { Text("Bu numarada kart çıkmasın mı?") },
                    text = { Text(telBicim(tel)) },
                    confirmButton = {
                        Button(onClick = {
                            if (!deneme) depo.engelle(tel)
                            engelSor = false
                            Toast.makeText(ctx, "Bu numarada kart çıkmayacak", Toast.LENGTH_SHORT).show()
                            onKapat()
                        }) { Text("Çıkmasın") }
                    },
                    dismissButton = { TextButton(onClick = { engelSor = false }) { Text("Vazgeç") } },
                )
            }

            when (adim) {
                Adim.SECIM -> {
                    // 2×2: üstte görüşmenin türü, altta WhatsApp'tan dönüş.
                    val tel = arama?.tel ?: ""
                    val numaraVar = waNumara(tel).length >= 10
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BuyukSecim(
                            "İş arayan", R.drawable.s_is_arayan, renk.primaryContainer, renk.onPrimaryContainer,
                            Modifier.weight(1f),
                        ) { adim = Adim.ILCE }
                        BuyukSecim(
                            "Müşteri", R.drawable.s_musteri, renk.secondaryContainer, renk.onSecondaryContainer,
                            Modifier.weight(1f),
                        ) { adim = Adim.MUSTERI }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BuyukSecim(
                            "WhatsApp", R.drawable.s_whatsapp, renk.tertiaryContainer, renk.onTertiaryContainer,
                            Modifier.weight(1f), etkin = numaraVar,
                        ) {
                            if (!whatsappAc(ctx, tel)) Toast.makeText(ctx, "WhatsApp açılamadı", Toast.LENGTH_SHORT).show()
                        }
                        // Aşamalı: önce selam yazısı, WhatsApp'tan dönünce dosyalar.
                        val dosyaVar = remember { Tanitim.dosyalar(ctx).isNotEmpty() }
                        val ikinci = tanitimAsama == 1
                        BuyukSecim(
                            when {
                                !dosyaVar -> "Tanıtım"
                                ikinci -> "Dosyalar 2/2"
                                else -> "Tanıtım 1/2"
                            },
                            R.drawable.s_tanitim,
                            if (ikinci) renk.primary else renk.tertiaryContainer,
                            if (ikinci) renk.onPrimary else renk.onTertiaryContainer,
                            Modifier.weight(1f), etkin = numaraVar,
                        ) {
                            val tamam = if (ikinci) Tanitim.dosyaGonder(ctx, tel) else Tanitim.yaziGonder(ctx, tel)
                            if (!tamam) {
                                Toast.makeText(ctx, "WhatsApp açılamadı", Toast.LENGTH_SHORT).show()
                            } else {
                                tanitimAsama = if (!ikinci && dosyaVar) 1 else 0
                            }
                        }
                    }
                }

                Adim.ILCE -> {
                    Text("Hangi ilçe?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = yaka == "avrupa", onClick = { yaka = "avrupa" }, label = { Text("Avrupa") })
                        FilterChip(selected = yaka == "anadolu", onClick = { yaka = "anadolu" }, label = { Text("Anadolu") })
                    }
                    val sayilar = ilceSayilari(isler)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Ilceler.liste(yaka).forEach { il ->
                            val n = sayilar[il] ?: 0
                            FilterChip(
                                selected = il == ilce,
                                onClick = {
                                    ilce = il
                                    depo.sonYaka = yaka
                                    adim = Adim.ISLER
                                },
                                label = {
                                    Text(
                                        if (n > 0) "$il  $n" else il,
                                        fontWeight = if (n > 0) FontWeight.Bold else FontWeight.Normal,
                                        color = if (n > 0) renk.onSurface else renk.onSurfaceVariant,
                                    )
                                },
                            )
                        }
                    }
                    if (isler.isEmpty()) {
                        Text("İş listesi boş", color = renk.error, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { adim = Adim.SECIM }) { Text("Geri") }
                }

                Adim.ISLER -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            ilce,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { adim = Adim.ILCE }) { Text("İlçe değiştir") }
                    }
                    val buradaki = ilceIsleri(isler, ilce)
                    if (buradaki.isEmpty()) {
                        Text("Bu ilçede kayıtlı iş yok", color = renk.onSurfaceVariant)
                    }
                    buradaki.forEach { i ->
                        IsSatiri(i, i.id in secili, ilceGoster = false) {
                            if (i.id in secili) secili.remove(i.id) else secili.add(i.id)
                        }
                    }
                    val diger = digerIsler(isler, ilce)
                    if (diger.isNotEmpty()) {
                        TextButton(onClick = { digerAcik = !digerAcik }) {
                            Text(if (digerAcik) "Diğer işleri gizle" else "Diğer işler (${diger.size})")
                        }
                    }
                    if (digerAcik) {
                        OutlinedTextField(
                            value = ara,
                            onValueChange = { ara = it },
                            label = { Text("Ara") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        araSuz(diger, ara).take(80).forEach { i ->
                            IsSatiri(i, i.id in secili, ilceGoster = true) {
                                if (i.id in secili) secili.remove(i.id) else secili.add(i.id)
                            }
                        }
                    }
                    HorizontalDivider()
                    OutlinedTextField(
                        value = ad,
                        onValueChange = { ad = it },
                        label = { Text("Adı") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = not,
                        onValueChange = { not = it },
                        label = { Text("Not") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(onClick = { kaydet("is") }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text(if (secili.isEmpty()) "Kaydet" else "Kaydet · ${secili.size} iş", fontWeight = FontWeight.Bold)
                    }
                }

                Adim.MUSTERI -> {
                    Text("Müşteri", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = ad,
                        onValueChange = { ad = it },
                        label = { Text("Müşteri adı") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = not,
                        onValueChange = { not = it },
                        label = { Text("Not") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { adim = Adim.SECIM }) { Text("Geri") }
                        Spacer(Modifier.weight(1f))
                        Button(onClick = { kaydet("musteri") }, modifier = Modifier.height(52.dp)) {
                            Text("Kaydet", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        }
    }
}

/** Küçük ×: dokununca kapatır, uzun basınca "bu numarada çıkmasın" sorusu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KapatDugmesi(onKapat: () -> Unit, onUzun: (() -> Unit)?) {
    val titresim = LocalHapticFeedback.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .combinedClickable(
                onClick = onKapat,
                onLongClick = onUzun?.let { u ->
                    {
                        titresim.performHapticFeedback(HapticFeedbackType.LongPress)
                        u()
                    }
                },
                onClickLabel = "Kapat",
                onLongClickLabel = "Bu numarada çıkmasın",
            ),
    ) {
        Icon(
            painterResource(R.drawable.s_kapat),
            contentDescription = "Kapat",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BuyukSecim(
    yazi: String,
    simge: Int,
    zemin: Color,
    yaziRengi: Color,
    modifier: Modifier,
    etkin: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = etkin,
        shape = RoundedCornerShape(16.dp),
        color = zemin,
        modifier = modifier.height(84.dp).alpha(if (etkin) 1f else 0.4f),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(painterResource(simge), contentDescription = null, tint = yaziRengi, modifier = Modifier.size(30.dp))
            Spacer(Modifier.height(8.dp))
            Text(yazi, color = yaziRengi, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun IsSatiri(i: Is, secili: Boolean, ilceGoster: Boolean, onDegis: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onDegis)
            .padding(end = 8.dp),
    ) {
        Checkbox(checked = secili, onCheckedChange = { onDegis() })
        Column(Modifier.weight(1f)) {
            Text(i.ad, style = MaterialTheme.typography.bodyLarge)
            if (ilceGoster) {
                Text(i.ilce.ifEmpty { "İlçe yok" }, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
