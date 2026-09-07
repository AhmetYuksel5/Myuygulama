/*
 * Arapça Kitap Okuyucu — tarayıcı sürümü.
 *
 * Android uygulamasının okuma ve kelime çalışma kısmının karşılığı.
 * Tek kişilik: veri telefondan çıkmıyor, hesap yok, sunucu yok. Sayfa
 * GitHub'dan iniyor, geri kalan her şey cihazda.
 *
 * Çerçeve kullanılmıyor. Sebebi tembellik değil: bir derleme adımı
 * olmayınca dosyalar olduğu gibi yayınlanıyor, uygulama yıllar sonra da
 * açılıyor ve iPhone'da kurulum "bağlantıyı aç, ana ekrana ekle"den
 * ibaret kalıyor.
 */

import { depo, kaliciIste, disaAktar, iceAktar } from "./depo.js";
import { epubOku, zipAc } from "./epub.js";
import { pdfAc, sayfaCiz } from "./pdf.js";
import { cevir, cumle, ekOrnekler, kart as kartIste, soru } from "./yapayzeka.js";
import { yeniKelime, bekleyenler, karar, bugun } from "./tekrar.js";

const ekran = document.getElementById("ekran");
const cubuk = document.getElementById("cubuk");
const KALEMLER = ["YELLOW", "BLUE", "GREEN", "RED"];

let ayarlar = { anahtar: "", model: "gpt-4o-mini" };

/**
 * Okuma tercihleri.
 *
 * İki hazır zemin: açık ve koyu. Eskiden üç vardı; en koyusu (neredeyse
 * siyah) kaldırıldı, uzun okumada sert geliyordu. Koyu artık ikisinin
 * arasındaki gri — metni de bilerek sönük, siyah üstüne beyaz yoruyor.
 *
 * Bu ikisi yetmezse "Özelleştir": zemin tonu kademesiz seçiliyor.
 */
const ZEMINLER = {
  kagit: { ad: "Açık", zemin: "#faf7f0", yazi: "#22201c", cizgi: "#ddd5c7" },
  koyu: { ad: "Koyu", zemin: "#3a3d43", yazi: "#d9d5cd", cizgi: "#565a62" },
};

let okumaTercihi = {
  punto: 19, zemin: "kagit", kenar: 16, satir: 1.9, ton: 22, harekesiz: false,
};

/*
 * Harekeler: harfin üstüne ve altına konan seslendirme işaretleri, şedde
 * ve sükûn dâhil; tatvîl de aynı yere giriyor.
 *
 * Gizleme yalnız gösterimde. Kelimenin kendisi harekeleriyle saklanıyor,
 * yoksa işaretlenmiş kelime bulunamaz ve dokunulan kelimenin kartı başka
 * bir kelimeninki olurdu.
 */
const HAREKE = /[ً-ْٰـ]/g;
const gorunen = sade => (okumaTercihi.harekesiz ? sade.replace(HAREKE, "") : sade);

/** Karşılaştırma gövdesi: yalnız harekeler atılmış hâli. */
const sadeKok = s => s.replace(HAREKE, "").trim();

/**
 * İki yazılış aynı kelime mi?
 *
 * Arapçada kelimenin başına "ve", "el", "bi", "li" gibi harfler
 * yapışıyor, sonuna da hâl eki geliyor. Harf harf karşılaştırınca
 * "والمثالية" ile "المثالية" tutmuyor ve öbekteki asıl kelime silik
 * yazılmıyordu. Kısa olan uzunun içinde geçiyorsa aynı kelime sayılıyor;
 * üç harften kısa gövdelerde bu ölçüt gevşek kaçtığı için aranmıyor.
 */
function ayniKelime(a, b) {
  if (!a || !b) return false;
  const [kisa, uzun] = a.length <= b.length ? [a, b] : [b, a];
  return kisa.length >= 3 && uzun.includes(kisa);
}

/**
 * Özel ton: 0 en koyu, 100 en açık.
 *
 * Yazı rengi tondan kendiliğinden çıkıyor — açık zeminde koyu mürekkep,
 * koyu zeminde sönük açık yazı. En açık yazı bile tam beyaz değil:
 * siyaha yakın zeminde tam beyaz göz kamaştırıyor.
 */
function tondanZemin(ton) {
  const l = Math.max(0, Math.min(100, Number(ton) || 0));
  /*
   * Doygunluk açık uçta yüksek, koyu uçta düşük. Sabit tutulunca çubuk
   * boyunca hep aynı gri geliyordu; oysa açık taraf krem ve hafif sarımsı
   * tonlardan geçmeli, kâğıt rengi orada. Koyuda ise renk artarsa çamur
   * gibi duruyor.
   */
  const s = Math.round(5 + (l / 100) * 25);
  const acik = l > 55;
  return {
    ad: "Özel",
    zemin: `hsl(40 ${s}% ${l}%)`,
    yazi: acik ? "hsl(40 12% 12%)" : "hsl(40 8% 82%)",
    cizgi: `hsl(40 ${s}% ${acik ? Math.max(0, l - 12) : Math.min(100, l + 14)}%)`,
  };
}

function seciliZemin() {
  if (okumaTercihi.zemin === "ozel") return tondanZemin(okumaTercihi.ton);
  return ZEMINLER[okumaTercihi.zemin] || ZEMINLER.kagit;
}

function tercihleriUygula() {
  const z = seciliZemin();
  const govde = document.getElementById("okuma");
  if (govde) {
    govde.style.fontSize = `${okumaTercihi.punto}px`;
    // Satır aralığı değişkenden gidiyor: Arapça paragrafın kendi kuralı
    // buradaki değeri eziyordu ve ayar hiç işlemiyor görünüyordu.
    govde.style.setProperty("--satir", String(okumaTercihi.satir));
    govde.style.padding = `0 ${okumaTercihi.kenar}px`;
  }
  // Okuma zemini sayfanın tamamını kaplıyor: metnin çevresinde başka
  // renkte bir şerit kalması okumayı bozuyor. Alt çubuk ve görünüm
  // kutusu da aynı zemini değişkenlerden alıyor.
  document.body.style.background = z.zemin;
  document.body.style.color = z.yazi;
  document.body.style.setProperty("--okuma-zemin", z.zemin);
  document.body.style.setProperty("--okuma-yazi", z.yazi);
  document.body.style.setProperty("--okuma-cizgi", z.cizgi);
}

function tercihleriBirak() {
  document.body.style.background = "";
  document.body.style.color = "";
  // Seçim kutusu bu değişkenlerden besleniyor: kitaptan çıkınca
  // bırakılmazsa liste açık zeminliyken kutu koyu kalıyor.
  ["--okuma-zemin", "--okuma-yazi", "--okuma-cizgi"]
    .forEach(ad => document.body.style.removeProperty(ad));
}

// --- Yönlendirme -----------------------------------------------------

// Anahtarlar alt çubuktaki data-git değerleriyle birebir aynı olmak
// zorunda; "ayarlarEkrani" yazılıydı ve o sekme hiç açılmıyordu.
const sayfalar = { kitaplik, deste, ayarlar: ayarlarEkrani };
let acikKitap = null;
let acikEkran = "kitaplik";

/*
 * Telefonun geri tuşu.
 *
 * Sayfa tek ekran olduğu için geri tuşu, hiçbir şey yapılmazsa doğrudan
 * uygulamadan çıkıyordu — kart açıkken bile. Yöntem şu: geri alınacak
 * bir şey varken tarayıcı geçmişine bir "yedek adım" bırakılıyor. Geri
 * tuşu o adımı tüketiyor, biz uygulamada bir kademe geri gidip yeni bir
 * yedek koyuyoruz. Geri alınacak bir şey kalmayınca yedek konmuyor ve
 * geri tuşu sayfadan çıkıyor.
 */
let derinlik = 0;

/**
 * Bir kademe derinleşildi; geçmişe kendi adımını bırakıyor.
 *
 * Önce tek bir "yedek adım" tutuluyor, geri tuşu onu tükettikçe yenisi
 * konuyordu. Kırılgandı: kitap, kutu ve üste binen kartlar tek adımı
 * paylaşınca geri tuşu kademe atlıyor, kartın üstünden doğrudan
 * kitaplığa düşüyordu. Artık her kademenin kendi adımı var ve geçmişin
 * derinliği uygulamanınkiyle birebir.
 */
function derinles() {
  derinlik += 1;
  history.pushState({ merkez: derinlik }, "");
}

/** Kart yığınında bir kademe geri; karttaki geri düğmesi bunu çağırıyor. */
function tarayiciGeri() {
  history.back();
}

/**
 * Bir kademe geri al. Geri alacak bir şey bulduysa true döner.
 *
 * Sıra kullanıcının beklediği sıra: önce üste binmiş kart, sonra kutu,
 * sonra hangi ekranda olursa olsun kitaplığa dönüş.
 */
function birKademeGeri() {
  if (!perde.hidden && kartYigini.length > 1) { kartGeri(); return true; }
  if (!perde.hidden) { kutuyuKapat(); return true; }
  if (acikKitap || acikEkran !== "kitaplik") { git("kitaplik"); return true; }
  return false;
}

/*
 * Aşağı çekince sayfa yenileniyor.
 *
 * Sayfa GitHub'dan iniyor ve servis çalışanı önce ağa bakıyor; yenilemek
 * yeni sürümü getirmenin en kısa yolu. Kitap okurken kapalı: orada aşağı
 * çekmek sayfayı kaydırmak demek. Kutu açıkken de kapalı.
 */
const CEKME_ESIGI = 90;
let cekmeBasi = 0;
let cekmeIzi = null;

function iziGoster(mesafe) {
  if (!cekmeIzi) {
    cekmeIzi = yap("div", "↻", "yenile-izi");
    document.body.append(cekmeIzi);
  }
  // Parmak kadar değil, yarısı kadar iniyor: çekmenin bir direnci olsun.
  const yol = Math.min(mesafe * 0.5, CEKME_ESIGI);
  const hazir = mesafe >= CEKME_ESIGI;
  cekmeIzi.style.transform =
    `translate(-50%, ${yol}px) rotate(${Math.min(mesafe * 2, 360)}deg)`;
  cekmeIzi.classList.toggle("hazir", hazir);
  cekmeIzi.hidden = false;
  return hazir;
}

function iziKaldir() {
  if (cekmeIzi) cekmeIzi.hidden = true;
}

document.addEventListener("touchstart", olay => {
  const uygun = !acikKitap && perde.hidden && window.scrollY <= 0
    && olay.touches.length === 1;
  cekmeBasi = uygun ? olay.touches[0].clientY : 0;
}, { passive: true });

// Pasif değil: çekerken sayfanın kendi esnemesi araya girmesin.
document.addEventListener("touchmove", olay => {
  if (!cekmeBasi) return;
  const mesafe = olay.touches[0].clientY - cekmeBasi;
  // Yukarı gidiyorsa bu bir kaydırma; çekmeyi hiç başlatma.
  if (mesafe <= 0) {
    cekmeBasi = 0;
    iziKaldir();
    return;
  }
  olay.preventDefault();
  iziGoster(mesafe);
}, { passive: false });

document.addEventListener("touchend", olay => {
  if (!cekmeBasi) return;
  const bitis = olay.changedTouches[0]?.clientY ?? cekmeBasi;
  const yeter = bitis - cekmeBasi >= CEKME_ESIGI;
  cekmeBasi = 0;
  if (!yeter) { iziKaldir(); return; }
  // Bırakınca iz yerinde kalıyor ve dönüyor: yenilendiği görülsün.
  if (cekmeIzi) cekmeIzi.classList.add("donuyor");
  location.reload();
}, { passive: true });

window.addEventListener("popstate", olay => {
  // Geçmişteki adımın derinliği hedefimiz; oraya inene kadar kademe
  // kademe geri alıyoruz. Genellikle tek kademe, ama tarayıcı birkaç
  // adımı birden geri alırsa da doğru yere düşüyoruz.
  const hedef = olay.state?.merkez || 0;
  while (derinlik > hedef && birKademeGeri()) derinlik -= 1;
  derinlik = hedef;
});

async function git(ad) {
  // Yalnız kitaplıktan ayrılırken adım bırakılıyor. Liste ile ayarlar
  // arasında gidip gelmek yeni kademe sayılmıyor; geri tuşu her ikisinden
  // de doğrudan kitaplığa dönüyor.
  if (ad !== "kitaplik" && acikEkran === "kitaplik") derinles();
  acikEkran = ad;
  acikKitap = null;
  if (pdfTemizle) pdfTemizle();
  if (okumaTemizle) okumaTemizle();
  adresleriBirak();
  tercihleriBirak();
  cubuk.querySelectorAll("button").forEach(d =>
    d.classList.toggle("secili", d.dataset.git === ad));
  cubuk.hidden = false;
  ekran.innerHTML = "";
  await sayfalar[ad]();
}

cubuk.addEventListener("click", e => {
  const dugme = e.target.closest("button");
  if (dugme) git(dugme.dataset.git);
});

// --- Kitaplık --------------------------------------------------------

async function kitaplik() {
  const kitaplar = await depo.kitaplar();

  const baslik = yap("h1", "Kitaplık");
  const yukle = yap("button", "Kitap yükle", "dolu");
  const secici = Object.assign(document.createElement("input"), {
    type: "file",
    accept: ".epub,.pdf,application/epub+zip,application/pdf",
  });
  secici.hidden = true;
  yukle.onclick = () => secici.click();
  secici.onchange = () => secici.files[0] && kitapEkle(secici.files[0]);

  ekran.append(baslik, yukle, secici);

  if (!kitaplar.length) {
    ekran.append(yap("div",
      "Raf boş. Bir EPUB yükle: okurken kelimelere dokunup işaretlediklerin listene düşer.",
      "bos"));
    return;
  }

  const liste = yap("div", "");
  liste.style.marginTop = "16px";
  kitaplar.sort((a, b) => (b.acildi || 0) - (a.acildi || 0)).forEach(k => {
    const kart = yap("button", "", "kart");
    const sirt = yap("div", (k.ad[0] || "?").toUpperCase(), "sirt");
    sirt.style.background = renkTohumu(k.ad);
    // Kapak varsa harfin yerini alıyor; yoksa adından türeyen renkli
    // sırt kalıyor, o da kitabı rafta tanıtmaya yetiyor.
    if (k.kapak) kapagiKoy(k, sirt);
    const bilgi = yap("div", "", "bilgi");
    bilgi.append(yap("b", k.ad));
    if (k.yazar) bilgi.append(yap("span", k.yazar));
    const yuzde = ilerleme(k);
    if (yuzde > 0) {
      const cizik = yap("div", "", "cizik");
      const dolu = document.createElement("i");
      dolu.style.width = `${yuzde}%`;
      cizik.append(dolu);
      bilgi.append(cizik);
    }
    kart.append(sirt, bilgi);
    // Uzun basmak (masaüstünde sağ tuş) kitabın menüsünü açıyor. Parmak
    // kalkınca tarayıcı bir de tıklama gönderiyor; menü açıldıysa o
    // tıklama kitabı açmamalı.
    let zaman;
    let menuAcildi = false;
    kart.onclick = () => {
      if (menuAcildi) { menuAcildi = false; return; }
      oku(k.id);
    };
    kart.addEventListener("touchstart", () => {
      zaman = setTimeout(() => { menuAcildi = true; kitapMenusu(k); }, 600);
    }, { passive: true });
    ["touchend", "touchmove", "touchcancel"].forEach(o =>
      kart.addEventListener(o, () => clearTimeout(zaman), { passive: true }));
    kart.addEventListener("contextmenu", e => {
      e.preventDefault();
      clearTimeout(zaman);
      kitapMenusu(k);
    });
    liste.append(kart);
  });
  ekran.append(liste);
}

async function kitapSil(kitap) {
  if (!confirm(`"${kitap.ad}" silinsin mi? İşaretlediğin kelimeler kalır.`)) return;
  await depo.kitapSil(kitap.id);
  await depo.dosyaSil(kitap.id);
  await depo.dosyaSil(`${kitap.id}-kapak`);
  git("kitaplik");
}

// --- Kitap menüsü ----------------------------------------------------

/**
 * Tek seferlik alt pencere: içeriği verilen kutu, dışına dokununca
 * kapanıyor ve belgeden siliniyor. Seçim kutusundan ayrı, çünkü o
 * kutunun bölmeleri sabit ve seçime bağlı.
 */
function pencereAc(icerik) {
  const perde = yap("div", "", "perde");
  const kutu = yap("div", "", "kutu");
  kutu.append(...icerik);
  perde.append(kutu);
  perde.onclick = e => { if (e.target === perde) perde.remove(); };
  document.body.append(perde);
  return perde;
}

function kitapMenusu(kitap) {
  // İki yol aynı anda açmasın: Android'de uzun basma hem bizim sayacı
  // hem tarayıcının sağ tuş olayını tetikliyor.
  if (document.querySelector(".perde")) return;
  const madde = (yazi, islem, sinif = "") => {
    const dugme = yap("button", yazi, `menu-madde ${sinif}`.trim());
    dugme.onclick = () => { perde.remove(); islem(); };
    return dugme;
  };
  const perde = pencereAc([
    yap("div", kitap.ad, "menu-baslik"),
    madde("Yeniden adlandır", () => adlandir(kitap)),
    madde("Kapağı değiştir", () => kapakEkrani(kitap)),
    madde("Sil", () => kitapSil(kitap), "tehlike"),
  ]);
}

function alan(etiket, deger) {
  const sarma = yap("label", etiket);
  const giris = Object.assign(document.createElement("input"), { value: deger });
  sarma.append(giris);
  return { sarma, giris };
}

async function adlandir(kitap) {
  const ad = alan("Kitabın adı", kitap.ad);
  const yazar = alan("Yazar", kitap.yazar || "");
  const kaydet = yap("button", "Kaydet", "dolu");
  kaydet.onclick = async () => {
    const yeniAd = ad.giris.value.trim();
    if (!yeniAd) { ad.giris.focus(); return; }
    const guncel = await depo.kitap(kitap.id);
    await depo.kitapYaz({ ...guncel, ad: yeniAd, yazar: yazar.giris.value.trim() });
    // Listedeki kelimeler kitabın adını kendi üstünde taşıyor; eski adla
    // kalmasınlar.
    for (const k of await depo.kelimeler()) {
      if (k.kitap === kitap.id) await depo.kelimeYaz({ ...k, eser: yeniAd });
    }
    perde.remove();
    git("kitaplik");
  };
  const perde = pencereAc([
    yap("div", "Yeniden adlandır", "menu-baslik"),
    ad.sarma, yazar.sarma, kaydet,
  ]);
  ad.giris.focus();
  ad.giris.select();
}

/**
 * Kapak ekranı: şimdiki kapak, telefondan görsel seçme, kaldırma.
 *
 * Seçilen görsel küçültülerek saklanıyor. Telefon fotoğrafı on
 * megabayt; kitaplıkta 44 piksellik bir sırt için depoya o kadar yük
 * bindirmenin anlamı yok.
 */
async function kapakEkrani(kitap) {
  const onizleme = yap("div", (kitap.ad[0] || "?").toUpperCase(), "kapak-onizleme");
  onizleme.style.background = renkTohumu(kitap.ad);
  if (kitap.kapak) kapagiKoy(kitap, onizleme);

  const secici = Object.assign(document.createElement("input"), {
    type: "file", accept: "image/*",
  });
  secici.hidden = true;

  // null: dokunulmadı; "" : kaldırıldı; nesne: yeni görsel.
  let yeni = null;
  const sec = yap("button", "Telefondan görsel seç", "tonlu");
  sec.onclick = () => secici.click();
  secici.onchange = async () => {
    const dosya = secici.files[0];
    if (!dosya) return;
    sec.disabled = true;
    try {
      yeni = await kapagiKucult(dosya);
      const adres = URL.createObjectURL(new Blob([yeni.veri], { type: yeni.tur }));
      acikAdresler.push(adres);
      onizleme.textContent = "";
      onizleme.style.background = `center/cover no-repeat url("${adres}")`;
    } catch (e) {
      uyari.textContent = `Görsel okunamadı: ${e?.message || e}`;
    }
    sec.disabled = false;
  };

  const kaldir = yap("button", "Kapağı kaldır", "cizgili");
  kaldir.hidden = !kitap.kapak;
  kaldir.onclick = () => {
    yeni = "";
    onizleme.textContent = (kitap.ad[0] || "?").toUpperCase();
    onizleme.style.background = renkTohumu(kitap.ad);
    kaldir.hidden = true;
  };

  const uyari = yap("div", "", "kucuk uyari");
  const kaydet = yap("button", "Kaydet", "dolu");
  kaydet.onclick = async () => {
    const guncel = await depo.kitap(kitap.id);
    if (yeni === "") {
      await depo.dosyaSil(`${kitap.id}-kapak`);
      await depo.kitapYaz({ ...guncel, kapak: "" });
    } else if (yeni) {
      await depo.dosyaYaz(`${kitap.id}-kapak`, yeni.veri);
      await depo.kitapYaz({ ...guncel, kapak: yeni.tur });
    }
    perde.remove();
    git("kitaplik");
  };

  const satir = yap("div", "", "satir");
  satir.append(sec, kaldir);
  const perde = pencereAc([
    yap("div", "Kapak", "menu-baslik"),
    onizleme, satir, secici, uyari, kaydet,
  ]);
}

/** Görseli en çok 480×720 JPEG'e indirir; okunamazsa olduğu gibi bırakır. */
async function kapagiKucult(dosya) {
  const adres = URL.createObjectURL(dosya);
  try {
    const resim = await new Promise((tamam, hata) => {
      const r = new Image();
      r.onload = () => tamam(r);
      r.onerror = () => hata(new Error("tarayıcı bu biçimi açamadı"));
      r.src = adres;
    });
    const oran = Math.min(1, 480 / resim.naturalWidth, 720 / resim.naturalHeight);
    const tuval = document.createElement("canvas");
    tuval.width = Math.round(resim.naturalWidth * oran);
    tuval.height = Math.round(resim.naturalHeight * oran);
    tuval.getContext("2d").drawImage(resim, 0, 0, tuval.width, tuval.height);
    const blob = await new Promise(t => tuval.toBlob(t, "image/jpeg", 0.85));
    if (!blob) throw new Error("küçültülemedi");
    return { veri: await blob.arrayBuffer(), tur: "image/jpeg" };
  } finally {
    URL.revokeObjectURL(adres);
  }
}

async function kitapEkle(dosya) {
  const bekle = yap("p", "Kitap okunuyor…", "sonuk");
  ekran.append(bekle);
  try {
    const tampon = await dosya.arrayBuffer();
    if (!tampon || tampon.byteLength === 0) {
      throw new Error("Dosya boş geldi; seçiciden bir daha dene.");
    }
    const id = `k${Date.now()}`;

    // Uzantıya değil dosyanın kendisine bakılıyor: PDF'ler "%PDF-" ile
    // başlıyor ve uzantı yanlış olsa da doğru okuyucu seçiliyor.
    if (pdfMi(tampon)) {
      const belge = await pdfAc(tampon);
      await depo.dosyaYaz(id, tampon);
      await depo.kitapYaz({
        id,
        tur: "pdf",
        ad: dosya.name.replace(/\.pdf$/i, ""),
        yazar: "",
        sayfaSayisi: belge.numPages,
        sayfa: 1,
        acildi: Date.now(),
      });
    } else {
      const kitap = await epubOku(tampon);
      await depo.dosyaYaz(id, tampon);

      // Kapak bir kez çıkarılıp ayrı saklanıyor: kitaplığı çizerken
      // koca EPUB'ı açmak için sebep kalmasın.
      let kapakVar = false;
      if (kitap.kapak) {
        const zip = await zipAc(tampon);
        const bayt = await zip.oku(kitap.kapak);
        if (bayt) {
          await depo.dosyaYaz(`${id}-kapak`, bayt.buffer.slice(
            bayt.byteOffset, bayt.byteOffset + bayt.byteLength,
          ));
          kapakVar = true;
        }
      }

      await depo.kitapYaz({
        id,
        tur: "epub",
        ad: kitap.ad,
        yazar: kitap.yazar,
        kapak: kapakVar ? turBul(kitap.kapak) : "",
        bolumler: kitap.bolumler,
        bolum: 0,
        acildi: Date.now(),
      });
    }
    await kaliciIste();
    git("kitaplik");
  } catch (e) {
    // Hatanın kendisi de yazılıyor.
    //
    // Önce yalnız "okunamadı" deniyordu ve elde tek bilgi o cümleydi;
    // dosyanın biçimi mi bozuk, depo mu dolu, ayrıştırıcı mı takıldı,
    // ayırt etmenin yolu yoktu.
    bekle.innerHTML = "";
    bekle.className = "uyari";
    bekle.append(yap("div", "Bu dosya okunamadı."));
    bekle.append(yap("div", `${e?.name || "Hata"}: ${e?.message || e}`, "kucuk"));
  }
}

function pdfMi(tampon) {
  const bas = new Uint8Array(tampon.slice(0, 5));
  return String.fromCharCode(...bas) === "%PDF-";
}

const ilerleme = k => {
  if (k.tur === "pdf") {
    const toplam = k.sayfaSayisi || 0;
    return toplam ? Math.round((k.sayfa || 1) * 100 / toplam) : 0;
  }
  const toplam = k.bolumler?.length || 0;
  if (!toplam) return 0;
  return Math.round(((k.bolum || 0) + 1) * 100 / toplam);
};

// --- Okuyucu ---------------------------------------------------------

async function oku(id) {
  const kitap = await depo.kitap(id);
  if (!kitap) return git("kitaplik");
  // Kitap açıkken geri tuşu kitaplığa dönsün. Bölüm değiştirmek ya da
  // harekeleri açıp kapatmak aynı kitabı yeniden çiziyor; o yeni kademe
  // değil.
  if (acikKitap?.id !== kitap.id) derinles();
  acikKitap = kitap;
  kitap.acildi = Date.now();
  await depo.kitapYaz(kitap);

  cubuk.hidden = true;
  ekran.innerHTML = "";

  if (kitap.tur === "pdf") return pdfOku(kitap);

  const bolum = kitap.bolumler[kitap.bolum] || kitap.bolumler[0];

  okumaKabugu(`${(kitap.bolum || 0) + 1}/${kitap.bolumler.length}`);

  const govde = yap("div", "");
  govde.id = "okuma";
  const isaretler = await isaretHaritasi(kitap.id);
  const resimler = [];
  bolum.paragraflar.forEach(p => {
    if (p.resim) {
      const resim = document.createElement("img");
      resim.alt = "";
      // Kaynağı sonra doldruluyor: ZIP'i açmak için okumanın başlamasını
      // beklemek gerekmiyor, metin hemen görünsün.
      resimler.push({ oge: resim, yol: p.resim });
      govde.append(resim);
      return;
    }
    const oge = document.createElement(p.baslik ? "h3" : "p");
    oge.innerHTML = kelimele(p.yazi, isaretler);
    // Arapça paragraf sağdan sola. Kitap başına değil paragraf başına
    // bakılıyor: Arapça kitaplarda İngilizce alıntılar, İngilizce
    // kitaplarda Arapça alıntılar oluyor ve ikisi de kendi yönünde
    // durmalı.
    if (arapcaMi(p.yazi)) oge.dir = "rtl";
    govde.append(oge);
  });
  ekran.append(govde);

  if (resimler.length) resimleriDoldur(kitap, resimler);

  const alt = yap("div", "", "satir");
  alt.style.marginTop = "24px";
  const onceki = yap("button", "‹ Önceki", "tonlu");
  const sonraki = yap("button", "Sonraki ›", "tonlu");
  onceki.disabled = (kitap.bolum || 0) <= 0;
  sonraki.disabled = (kitap.bolum || 0) >= kitap.bolumler.length - 1;
  onceki.onclick = () => bolumeGit(kitap, -1);
  sonraki.onclick = () => bolumeGit(kitap, 1);
  alt.append(onceki, sonraki);
  ekran.append(alt);

  govde.addEventListener("click", kelimeyeDokun);
  secimiKur(govde);
  tercihleriUygula();

  /*
   * Kaldığın **satır**.
   *
   * Bölüm ya da paragraf numarası yetmiyordu: uzun bir paragrafın
   * ortasındayken kitabı kapatıp açınca başa dönülüyordu. Sayfanın
   * kaydırma konumu olduğu gibi saklanıyor; ekranın tepesindeki satır
   * neyse onunla devam ediyor.
   *
   * Yerleşim oturmadan geri dönmek işe yaramıyor, o yüzden bir kare
   * bekleniyor.
   */
  requestAnimationFrame(() => {
    window.scrollTo(0, kitap.kaydirma || 0);
  });

  let bekleyen;
  const konumuYaz = () => {
    clearTimeout(bekleyen);
    bekleyen = setTimeout(async () => {
      if (acikKitap?.id !== kitap.id) return;
      kitap.kaydirma = Math.round(window.scrollY);
      await depo.kitapYaz(kitap);
    }, 400);
  };
  window.addEventListener("scroll", konumuYaz, { passive: true });
  okumaTemizle = () => {
    window.removeEventListener("scroll", konumuYaz);
    clearTimeout(bekleyen);
    kabuguKaldir();
    okumaTemizle = null;
  };
}

let okumaTemizle = null;

// --- Okuma kabuğu: alt çubuk ve görünüm kutusu -------------------------

let okumaAlt = null;

/**
 * Okurken ekranda çubuk yok; boş bir yere dokununca alttan çıkıyor,
 * bir daha dokununca gidiyor. Soldan sağa: kitaplığa dön, kelime
 * listesi, görünüm ayarları. Kelimeye dokunmak yine kutuyu açıyor,
 * çubuğu değil.
 */
function okumaKabugu(bilgi) {
  kabuguKaldir();
  okumaAlt = yap("div", "");
  okumaAlt.id = "okuma-alt";
  okumaAlt.hidden = true;

  const geri = yap("button", "Kitaplık ›");
  geri.onclick = () => git("kitaplik");
  const liste = yap("button", "Kelimeler");
  liste.onclick = () => git("deste");
  const ayar = yap("button", "⚙", "disli");
  ayar.setAttribute("aria-label", "Görünüm");
  ayar.onclick = gorunumKutusu;
  const yazi = yap("span", bilgi || "", "bilgi");

  /*
   * Harekeleri gizleyip geri getiren düğme. Harekeli metin okumayı
   * öğretiyor ama bir yere gelince köstek oluyor: harekesiz yazıyı
   * sökmek ayrı bir alışkanlık ve kitap onu çalıştırmıyordu.
   */
  const hareke = yap("button", "◌َ", okumaTercihi.harekesiz ? "hareke kapali" : "hareke");
  hareke.setAttribute("aria-label", "Harekeler");
  hareke.onclick = async () => {
    okumaTercihi.harekesiz = !okumaTercihi.harekesiz;
    await depo.ayarYaz("okuma", JSON.stringify(okumaTercihi));
    if (acikKitap) oku(acikKitap.id);
  };

  okumaAlt.append(geri, liste, yazi, hareke, ayar);
  document.body.append(okumaAlt);

  ekran.addEventListener("click", kabugaDokunma);
  return yazi;
}

function kabugaDokunma(e) {
  // Kelime, düğme, görsel: hepsinin kendi işi var. Seçim bitince gelen
  // tıklama da sayılmıyor.
  if (e.target.closest("span.k, mark, button, a, input, select, img")) return;
  if (Date.now() - secimBitti < 500) return;
  if (!okumaAlt) return;
  okumaAlt.hidden = !okumaAlt.hidden;
  if (okumaAlt.hidden) document.getElementById("gorunum-kutusu")?.remove();
}

function kabuguKaldir() {
  ekran.removeEventListener("click", kabugaDokunma);
  document.getElementById("gorunum-kutusu")?.remove();
  okumaAlt?.remove();
  okumaAlt = null;
}

/** Rafta kitabın kendi kapağı. */
async function kapagiKoy(kitap, sirt) {
  const dosya = await depo.dosya(`${kitap.id}-kapak`);
  if (!dosya) return;
  const adres = URL.createObjectURL(new Blob([dosya.veri], { type: kitap.kapak }));
  acikAdresler.push(adres);
  sirt.textContent = "";
  sirt.style.background = `center/cover no-repeat url("${adres}")`;
}

/**
 * Kendi seçim aracımız.
 *
 * iPhone'un kendi seçimi devrede olduğunda basılı tutunca sistemin
 * büyüteci ve ardından sistemin menüsü çıkıyor; o menüye kendi
 * maddemizi ekleyemiyoruz ve kullanıcı seçtiği şeyi bize
 * ulaştıramıyor. Bu yüzden metinde sistem seçimi kapalı (CSS'te
 * user-select yok) ve seçimi burada kendimiz yapıyoruz.
 *
 * Metin zaten kelime kelime kutulanmış olduğu için iş, parmağın altındaki
 * kelimeyi bulup aradakileri boyamaktan ibaret.
 *
 * PDF tarafında bunun tersini yaptık: orada sayfa bir resim, sistemin
 * seçimi hem büyüteci hem tutamakları bedavaya getiriyor ve kaybedecek
 * bir şey yok.
 */
function secimiKur(govde) {
  const kelimeler = [...govde.querySelectorAll("span.k")];
  kelimeler.forEach((k, i) => (k.dataset.sira = i));

  let zamanlayici = null;
  let bas = -1;
  let son = -1;
  let seciyor = false;
  // Seçimin başladığı yükseklik; cam oraya demirleniyor.
  let camDemir = 0;

  const kelimeBul = (x, y) => {
    const oge = document.elementFromPoint(x, y);
    return oge && oge.closest ? oge.closest("span.k") : null;
  };

  const boya = () => {
    const [a, b] = [Math.min(bas, son), Math.max(bas, son)];
    kelimeler.forEach((k, i) => k.classList.toggle("secim", i >= a && i <= b));
  };

  const temizle = () => {
    kelimeler.forEach(k => k.classList.remove("secim"));
    camiKapat();
  };

  /*
   * Büyüteç.
   *
   * Android'deki camın karşılığı. Orada sayfa bir resim olduğu için
   * resmin bir parçası büyütülüyordu; burada metin canlı, o yüzden
   * seçilen kelimeler büyük puntoyla gösteriliyor. İkisinin de derdi
   * aynı: parmağın altında kalanı görebilmek.
   *
   * Parmağın bir parmak boyu yukarısında duruyor; yukarıda yer
   * kalmadığında altına geçiyor, yoksa sayfanın ilk satırlarında ekranın
   * dışında kalıyor.
   */
  const camiGoster = () => {
    let cam = document.getElementById("cam");
    if (!cam) {
      cam = yap("div", "");
      cam.id = "cam";
      document.body.append(cam);
    }
    const [a, b] = [Math.min(bas, son), Math.max(bas, son)];
    const metin = kelimeler.slice(a, b + 1).map(sozcuk).join(" ");
    cam.textContent = metin;
    cam.hidden = false;

    /*
     * Seçim uzadıkça yazı küçülüyor. Önce tek satıra sığdırılıp fazlası
     * kırpılıyordu; parmak ilerledikçe cümlenin başı kayboluyor ve
     * büyüteç ne seçtiğini göstermez oluyordu.
     */
    const u = metin.length;
    cam.style.fontSize = `${u > 110 ? 15 : u > 70 ? 17 : u > 38 ? 19 : 22}px`;

    /*
     * Cam parmağı takip etmiyor: seçimin başladığı yerin biraz
     * yukarısında duruyor ve alt kenarı orada sabit kalıyor. Uzadıkça
     * yukarı doğru büyüyor, böylece okunan satır yerinde kalıyor.
     */
    const yukseklik = cam.offsetHeight;
    cam.style.top = `${Math.max(yukseklik + 10, camDemir - 30)}px`;
  };

  const camiKapat = () => {
    const cam = document.getElementById("cam");
    if (cam) cam.hidden = true;
  };

  govde.addEventListener("touchstart", olay => {
    const kelime = olay.target.closest?.("span.k");
    if (!kelime) return;
    bas = son = Number(kelime.dataset.sira);
    camDemir = olay.touches[0].clientY;
    zamanlayici = setTimeout(() => {
      seciyor = true;
      boya();
      camiGoster();
    }, 350);
  }, { passive: true });

  // Bu dinleyici pasif değil: seçim sürerken sayfanın kaymaması için
  // hareketi durdurmak gerekiyor.
  govde.addEventListener("touchmove", olay => {
    if (!seciyor) {
      // Parmak kaydıysa bu bir kaydırma; seçimi hiç başlatma.
      clearTimeout(zamanlayici);
      return;
    }
    olay.preventDefault();
    const nokta = olay.touches[0];
    const kelime = kelimeBul(nokta.clientX, nokta.clientY);
    if (!kelime) return;
    son = Number(kelime.dataset.sira);
    boya();
    camiGoster();
  }, { passive: false });

  const bitir = () => {
    clearTimeout(zamanlayici);
    if (!seciyor) return;
    seciyor = false;
    const [a, b] = [Math.min(bas, son), Math.max(bas, son)];
    const secim = kelimeler.slice(a, b + 1).map(sozcuk).join(" ");
    temizle();
    camiKapat();
    // Tıklama olayı bunun ardından da geliyor; tek kelime kutusunu
    // ikinci kez açmasın diye işaretliyoruz.
    secimBitti = Date.now();
    if (secim.trim()) {
      const paragraf = kelimeler[a].closest("p, h3")?.textContent || "";
      kutuyuAc(secim, pencere(paragraf, secim), acikKitap);
    }
  };

  govde.addEventListener("touchend", bitir, { passive: true });
  govde.addEventListener("touchcancel", () => {
    clearTimeout(zamanlayici);
    seciyor = false;
    temizle();
  }, { passive: true });
}

let secimBitti = 0;

/** Sayfada duran görsellerin ömrü; bölüm değişince serbest bırakılıyor. */
let acikAdresler = [];

function adresleriBirak() {
  acikAdresler.forEach(URL.revokeObjectURL);
  acikAdresler = [];
}

/**
 * Bölümün görsellerini kitabın kendi dosyasından çıkarır.
 *
 * Görseller ayrıca saklanmıyor: EPUB zaten olduğu gibi duruyor, aynı
 * baytları ikinci kez yazmak yerini iki katına çıkarırdı. Bölüm açılınca
 * ZIP'ten okunup geçici bir adrese bağlanıyorlar.
 */
async function resimleriDoldur(kitap, resimler) {
  try {
    const dosya = await depo.dosya(kitap.id);
    if (!dosya) return;
    const zip = await zipAc(dosya.veri);
    for (const { oge, yol } of resimler) {
      if (acikKitap?.id !== kitap.id) return;
      const bayt = await zip.oku(yol);
      if (!bayt) {
        // Bulunamayan görselin yerinde boş bir kutu kalmasın.
        oge.remove();
        continue;
      }
      const adres = URL.createObjectURL(new Blob([bayt], { type: turBul(yol) }));
      acikAdresler.push(adres);
      oge.src = adres;
    }
  } catch {
    resimler.forEach(({ oge }) => oge.remove());
  }
}

const turBul = yol => {
  const uzanti = yol.split(".").pop().toLowerCase();
  return {
    jpg: "image/jpeg", jpeg: "image/jpeg", png: "image/png",
    gif: "image/gif", svg: "image/svg+xml", webp: "image/webp",
  }[uzanti] || "application/octet-stream";
};

/** Görünüm kutusu: punto, zemin, kenar boşluğu. */
/**
 * Görünüm kutusu: alt çubuğun üstünde açılıyor.
 *
 * Punto, satır aralığı, kenar, zemin — ve en altta AI anahtarı. Anahtar
 * için Ayarlar sekmesine gitmek okumayı bölüyordu; burada bir satır,
 * dokununca açılıyor.
 */
function gorunumKutusu() {
  const eski = document.getElementById("gorunum-kutusu");
  if (eski) return eski.remove();

  const kutu = yap("div", "");
  kutu.id = "gorunum-kutusu";

  const yaz = async () => {
    tercihleriUygula();
    await depo.ayarYaz("okuma", JSON.stringify(okumaTercihi));
  };

  const kademe = (ad, alan, adim, enAz, enCok, goster = v => String(v)) => {
    const satir = yap("div", "", "olcu");
    satir.append(yap("span", ad));
    const az = yap("button", "−", "cizgili");
    const cok = yap("button", "+", "cizgili");
    const sayi = yap("b", goster(okumaTercihi[alan]));
    const kur = fark => {
      // Ondalık adımda kayan nokta artığı birikmesin.
      const yeni = Math.round((okumaTercihi[alan] + fark) * 100) / 100;
      okumaTercihi[alan] = Math.max(enAz, Math.min(enCok, yeni));
      sayi.textContent = goster(okumaTercihi[alan]);
      yaz();
    };
    az.onclick = () => kur(-adim);
    cok.onclick = () => kur(adim);
    satir.append(az, sayi, cok);
    return satir;
  };

  const zeminler = yap("div", "", "satir");
  const zeminSec = anahtar => {
    okumaTercihi.zemin = anahtar;
    yaz();
    kutu.remove();
    gorunumKutusu();
  };
  Object.entries(ZEMINLER).forEach(([anahtar, z]) => {
    const dugme = yap("button", z.ad, anahtar === okumaTercihi.zemin ? "dolu" : "tonlu");
    dugme.onclick = () => zeminSec(anahtar);
    zeminler.append(dugme);
  });
  const ozel = yap("button", "Özelleştir",
    okumaTercihi.zemin === "ozel" ? "dolu" : "tonlu");
  ozel.onclick = () => zeminSec("ozel");
  zeminler.append(ozel);

  /*
   * Ton çubuğu yalnız "Özelleştir" seçiliyken duruyor: iki hazır zemin
   * çoğu zaman yetiyor, çubuk hep açık dursa kutuyu kalabalıklaştırırdı.
   */
  const tonSatiri = yap("div", "", "olcu");
  if (okumaTercihi.zemin === "ozel") {
    tonSatiri.append(yap("span", "Ton"));
    const kaydirac = document.createElement("input");
    kaydirac.type = "range";
    // Uçlara kadar gitmiyor: tam siyah ve tam beyaz ikisi de yoruyor.
    kaydirac.min = "6";
    kaydirac.max = "97";
    kaydirac.value = String(okumaTercihi.ton);
    kaydirac.oninput = () => {
      okumaTercihi.ton = Number(kaydirac.value);
      yaz();
    };
    tonSatiri.append(kaydirac);
  }

  // AI anahtarı: kapalı bir satır, dokununca giriş alanı açılıyor.
  const anahtarSatiri = yap("button", ayarlar.anahtar ? "AI anahtarı · girili" : "AI anahtarı · girilmemiş", "menu-madde");
  const anahtarAlani = yap("div", "");
  anahtarAlani.hidden = true;
  const giris = document.createElement("input");
  giris.type = "password";
  giris.placeholder = "sk-…";
  giris.value = ayarlar.anahtar;
  const kaydet = yap("button", "Kaydet", "tonlu");
  kaydet.onclick = async () => {
    ayarlar.anahtar = giris.value.trim();
    await depo.ayarYaz("anahtar", ayarlar.anahtar);
    anahtarSatiri.textContent = ayarlar.anahtar ? "AI anahtarı · girili" : "AI anahtarı · girilmemiş";
    anahtarAlani.hidden = true;
  };
  const anahtarSatir = yap("div", "", "satir");
  anahtarSatir.append(giris, kaydet);
  anahtarAlani.append(anahtarSatir);
  anahtarSatiri.onclick = () => {
    anahtarAlani.hidden = !anahtarAlani.hidden;
    if (!anahtarAlani.hidden) giris.focus();
  };

  kutu.append(
    kademe("Punto", "punto", 1, 14, 30),
    kademe("Satır", "satir", 0.1, 1.2, 2.4, v => v.toFixed(1)),
    kademe("Kenar", "kenar", 4, 0, 48),
    zeminler,
    tonSatiri,
    anahtarSatiri, anahtarAlani,
  );
  document.body.append(kutu);
}

async function bolumeGit(kitap, yon) {
  adresleriBirak();
  kitap.bolum = Math.max(0, Math.min(kitap.bolumler.length - 1, (kitap.bolum || 0) + yon));
  await depo.kitapYaz(kitap);
  oku(kitap.id);
}

/**
 * PDF okuyucu.
 *
 * Sayfa sayfa gidiyoruz; kaydırmalı bir liste kurmak çok daha fazla kod
 * ve PDF zaten sayfalara bölünmüş bir şey.
 *
 * Metin seçmeyi tarayıcının kendisi yapıyor: sayfanın üstünde görünmez
 * ama gerçek bir metin katmanı duruyor. iPhone'da bu, sistemin kendi
 * seçim tutamaklarını ve büyütecini bedavaya getiriyor.
 */
async function pdfOku(kitap) {
  const dosya = await depo.dosya(kitap.id);
  if (!dosya) {
    ekran.append(yap("p", "Dosya bulunamadı.", "uyari"));
    return;
  }

  const baslik = okumaKabugu("");

  const kap = yap("div", "");
  kap.id = "pdf-kap";
  ekran.append(kap);

  const alt = yap("div", "", "satir");
  alt.style.marginTop = "16px";
  const onceki = yap("button", "‹ Önceki", "tonlu");
  const sonraki = yap("button", "Sonraki ›", "tonlu");
  alt.append(onceki, sonraki);
  ekran.append(alt);

  const belge = await pdfAc(dosya.veri);
  let sayfa = Math.min(kitap.sayfa || 1, belge.numPages);
  let sayfaMetni = "";

  const ciz = async () => {
    kap.innerHTML = "";
    baslik.textContent = `${sayfa}/${belge.numPages}`;
    onceki.disabled = sayfa <= 1;
    sonraki.disabled = sayfa >= belge.numPages;
    // Genişlik ekrana göre; kenar boşluğu okuma alanının dışında kalıyor.
    const genislik = Math.min(ekran.clientWidth, 720) - 4;
    const sonuc = await sayfaCiz(belge, sayfa, kap, genislik);
    sayfaMetni = sonuc.metin;
    kitap.sayfa = sayfa;
    await depo.kitapYaz(kitap);
    window.scrollTo(0, 0);
  };

  onceki.onclick = async () => { sayfa--; await ciz(); };
  sonraki.onclick = async () => { sayfa++; await ciz(); };
  await ciz();

  /*
   * Seçim yapılınca çıkan düğme.
   *
   * iPhone kendi menüsünü de gösteriyor ama oraya kendi maddemizi
   * ekleyemiyoruz; ekranın altında duran bir düğme hem görünür hem de
   * parmağın seçtiği yeri kapatmıyor.
   */
  const dugme = yap("button", "Seçileni çevir", "dolu");
  dugme.id = "secim-dugme";
  dugme.hidden = true;
  document.body.append(dugme);
  pdfDugmesi = dugme;

  const bak = () => {
    const secim = (window.getSelection()?.toString() || "").trim();
    dugme.hidden = secim.length < 1;
  };
  document.addEventListener("selectionchange", bak);
  pdfTemizle = () => {
    document.removeEventListener("selectionchange", bak);
    dugme.remove();
    kabuguKaldir();
    pdfDugmesi = null;
    pdfTemizle = null;
  };

  dugme.onclick = () => {
    const secim = (window.getSelection()?.toString() || "").replace(/\s+/g, " ").trim();
    if (!secim) return;
    kutuyuAc(secim, pencere(sayfaMetni, secim), acikKitap);
  };
}

let pdfDugmesi = null;
let pdfTemizle = null;

/**
 * Paragrafı kelimelere böler ve işaretlileri boyar.
 *
 * Her kelime ayrı bir eleman: dokunulanı bulmak için metin içinde konum
 * hesaplamak gerekmiyor.
 *
 * İşaret tek kelime olmak zorunda değil. Cümle işaretlendiğinde deste
 * doluyordu ama kitapta hiçbir şey boyanmıyordu: harita tek kelimeyle
 * aranıyordu, cümlenin anahtarı hiçbir kelimeye uymuyordu. Artık her
 * konumda önce en uzun ifade deneniyor, sonra kısalarak tek kelimeye
 * iniliyor — uzun olan kazansın diye.
 */
function kelimele(yazi, isaretler) {
  const parcalar = yazi.split(/(\s+)/);
  // İki fazlası: "went — home" gibi araya giren tire parçası kelime
  // sayılmıyor ama parça sayılıyor; pay bırakılmazsa o ifade bulunmaz.
  const enUzun = (isaretler.enUzun || 1) + 2;
  const cikti = [];

  let i = 0;
  while (i < parcalar.length) {
    if (!parcalar[i].trim()) { cikti.push(parcalar[i]); i++; continue; }

    let bulundu = null;
    const kalan = Math.ceil((parcalar.length - i) / 2);
    for (let uzunluk = Math.min(enUzun, kalan); uzunluk >= 1; uzunluk--) {
      const son = i + (uzunluk - 1) * 2;
      if (son >= parcalar.length) continue;
      const ham = parcalar.slice(i, son + 1).join("");
      const kalem = isaretler.get(anahtarla(ham));
      if (kalem) { bulundu = { son, ham, kalem }; break; }
    }

    if (!bulundu) {
      cikti.push(kelimeyiSar(parcalar[i]));
      i++;
      continue;
    }

    // İşaretin başındaki ve sonundaki noktalama boyanın dışında kalıyor;
    // içindeki kelimeler yine tek tek sarılıyor ki dokunma çalışsın.
    const ic = bulundu.ham.replace(/^[^\p{L}\p{N}\p{M}]+|[^\p{L}\p{N}\p{M}]+$/gu, "");
    const on = bulundu.ham.slice(0, bulundu.ham.indexOf(ic));
    const arka = bulundu.ham.slice(bulundu.ham.indexOf(ic) + ic.length);
    const govde = ic.split(/(\s+)/)
      .map(p => (p.trim() ? sozcukEtiketi(p) : p))
      .join("");
    cikti.push(kacir(on) +
      `<mark class="${bulundu.kalem}" data-tam="${kacir(ic).replace(/"/g, "&quot;")}">${govde}</mark>` +
      kacir(arka));
    i = bulundu.son + 1;
  }
  return cikti.join("");
}

/**
 * Bir sözcüğün etiketi.
 *
 * Görünen yazı harekesiz olabilir; aslı data-ham'da duruyor ve dokunma,
 * seçim ve büyüteç hep oradan okuyor.
 */
function sozcukEtiketi(sade) {
  const ham = kacir(sade).replace(/"/g, "&quot;");
  return `<span class="k" data-ham="${ham}">${kacir(gorunen(sade))}</span>`;
}

/** İşaretsiz tek kelime: noktalaması dışarıda kalacak şekilde sarılıyor. */
function kelimeyiSar(parca) {
  const sade = parca.replace(/^[^\p{L}\p{N}\p{M}]+|[^\p{L}\p{N}\p{M}]+$/gu, "");
  if (!sade) return kacir(parca);
  const [on, arka] = parca.split(sade);
  return kacir(on || "") + sozcukEtiketi(sade) + kacir(arka || "");
}

/** Bir sözcük etiketinin asıl yazısı; harekesiz gösterimde bile tam. */
const sozcuk = oge => oge.dataset.ham || oge.textContent;

/**
 * İşaret anahtarı.
 *
 * Aynı metnin iki yazılışı aynı anahtara düşsün: küçük harf, tek boşluk,
 * harf ve rakam dışındaki her şey atılmış.
 *
 * Noktalama bütünüyle atılıyor, yalnız uçlardaki değil. Seçim aracı
 * kelimeleri boşlukla birleştiriyor ve kelime kutucukları noktalamayı
 * dışarıda bırakıyor: "He went home, quickly." seçilince elde "He went
 * home quickly" kalıyor. Paragrafın kendisinde virgül var; virgül
 * anahtarda kalsaydı cümle listeye düşer ama kitapta boyanmazdı — düştü,
 * boyanmadı.
 */
function anahtarla(metin) {
  // \p{M}: Arapça harekeler harfe yapışık işaret; atılırsa kelime dağılır.
  return metin.toLocaleLowerCase("tr")
    .replace(/[^\p{L}\p{N}\p{M}]+/gu, " ")
    .trim();
}

const kacir = m => m.replace(/[&<>]/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;" }[c]));

async function isaretHaritasi(kitapId) {
  const hepsi = await depo.kelimeler();
  const harita = new Map();
  let enUzun = 1;
  hepsi.filter(k => k.kitap === kitapId).forEach(k => {
    const anahtar = anahtarla(k.kelime);
    if (!anahtar) return;
    harita.set(anahtar, k.kalem);
    enUzun = Math.max(enUzun, anahtar.split(" ").length);
  });
  // Kaç kelimeye kadar geriye bakılacağını buradan öğreniyoruz; her
  // konumda paragrafın tamamını denemenin anlamı yok.
  harita.enUzun = enUzun;
  return harita;
}

// --- Seçim kutusu ----------------------------------------------------

const perde = document.getElementById("perde");
const kutuSecim = document.getElementById("kutu-secim");
const kutuCeviri = document.getElementById("kutu-ceviri");
const kutuNotlar = document.getElementById("kutu-notlar");
const kutuKalemler = document.getElementById("kutu-kalemler");
const kutuNot = document.getElementById("kutu-not");
const kutuAyrinti = document.getElementById("kutu-ayrinti");
/** Düğmenin yazısı; ok işaretini taşıyan parçaya dokunmuyor. */
const ayrintiYaz = metin => {
  kutuAyrinti.querySelector(".yazi").textContent = metin;
};
const kutuSor = document.getElementById("kutu-sor");
const kutuSorAlt = document.getElementById("kutu-sor-alt");
const kutuAlt = document.getElementById("kutu-alt");
const kutuSoruAlani = document.getElementById("kutu-soru-alani");
const kutuSoruMetin = document.getElementById("kutu-soru-metin");
const kutuSoruGonder = document.getElementById("kutu-soru-gonder");
const kutuCevap = document.getElementById("kutu-cevap");

const BEKLEME = "Anlamına bakılıyor…";

/*
 * Seçim öbek mi, cümle mi? Kutu buna göre davranıyor.
 *
 * Boşluk görür görmez cümle saymak yanlıştı: "سيارة إسعاف" iki kelime
 * ama tek bir şeyin adı — ambulans. Cümle yoluna girince model onu
 * çevirmek yerine içinde geçtiği cümleyi çeviriyordu, üstelik kelime
 * kartı düğmesi de kapanıyordu. İki kelimeye kadar olan her şey öbek:
 * karşılığı veriliyor ve kartı açılabiliyor.
 */
const OBEK_SINIRI = 2;
const kelimeSayisi = metin => metin.trim().split(/\s+/).filter(Boolean).length;
const cumleMi = metin => kelimeSayisi(metin) > OBEK_SINIRI;

let secili = null;
/* Kutunun renk seçicisi listede hiç çıkmıyor; durum burada saklanıyor
   ki iç kartlar arasında gidip gelirken kaybolmasın. */
let kalemlerGizli = false;

perde.addEventListener("click", e => {
  // Dışarı dokunmak kapatıyor; kutunun içi kapatmıyor.
  if (e.target === perde) kutuyuKapat();
});

function kutuyuKapat() {
  perde.hidden = true;
  secili = null;
  kartYigini = [];
}

async function kelimeyeDokun(e) {
  const oge = e.target.closest("span.k");
  if (!oge || !acikKitap) return;
  // Uzun basıp bıraktıktan hemen sonra tarayıcı bir de tıklama
  // gönderiyor; kutu iki kez açılmasın.
  if (Date.now() - secimBitti < 500) return;
  const paragraf = oge.closest("p, h3")?.textContent || "";
  // İşaretli bir cümlenin ortasına dokununca o tek kelime değil işaretin
  // tamamı açılıyor; kaldırmak isteyen kelime kelime uğraşmasın.
  const isaret = oge.closest("mark[data-tam]");
  const metin = isaret ? isaret.dataset.tam : sozcuk(oge);
  kutuyuAc(metin, pencere(paragraf, metin), acikKitap);
}

/**
 * Kayıttaki çeviri, geçerliyse.
 *
 * Bir dönem kutudaki yazı olduğu gibi kaydediliyordu; çeviri gelmeden
 * renge basılınca "bakılıyor" yazısı çeviri diye kalıyor ve listede
 * öyle görünüyordu. O kayıtlar hâlâ depoda olabilir.
 */
const gecerliCeviri = k => (k?.ceviri && k.ceviri !== BEKLEME ? k.ceviri : "");

/**
 * Seçim kutusunu açar; çeviriyi sözlükten alır, yoksa ister.
 *
 * Üç yerden çağrılıyor: e-kitap, PDF ve liste. Kaynak kitap parametre —
 * listede açık kitap yok, kelimenin kendi kitabı var.
 *
 * Bir kez alınan her şey sözlüğe yazılıyor: aynı seçime ikinci dokunuş
 * hiç istek atmıyor. Kutu kapansa bile gelen cevap sözlüğe ve varsa
 * kelime kaydına yazılıyor — çeviri gelmeden renge basanın kaydı boş
 * kalmasın.
 */
async function kutuyuAc(kelime, baglam, kaynak, kayit) {
  const anahtar = anahtarla(kelime);
  const istek = {
    kelime, baglam, anahtar,
    kaynak: kaynak || null,
    ceviri: "", notlar: [], kart: null,
  };
  secili = istek;

  kutuSecim.textContent = kelime;
  kutuSecim.dir = "auto";
  kutuSecim.hidden = false;
  // Tek kelime kutunun başlığı sayılıyor ve iri yazılıyor; cümle uzun,
  // iri puntoda kutuyu tek başına dolduruyor.
  kutuSecim.classList.toggle("tek", !cumleMi(kelime));
  kutuCeviri.hidden = false;
  kutuKalemler.hidden = kalemlerGizli;
  kutuNotlar.innerHTML = "";
  kutuNot.innerHTML = "";
  kutuNot.className = "sonuc";
  kutuSoruAlani.hidden = true;
  kutuSoruMetin.value = "";
  kutuCevap.textContent = "";
  kutuAyrinti.disabled = false;
  ayrintiYaz("Kelime kartı");
  /*
   * Cümlede "Ayrıntı" yok.
   *
   * Düğme bütün cümle için kelime kartı istiyordu: karşılık, kök, aile,
   * eş anlamlı… Cümlenin kökü olmuyor, çıkan şey saçmalıyordu. Cümlede
   * istenen zaten kutuda duruyor — anlamı, altında gerekiyorsa birkaç
   * not — ve onu tekrar eden bir düğme kalabalıktan başka bir şey değil.
   */
  kutuAyrinti.parentElement.hidden = false;
  kutuAyrinti.hidden = cumleMi(kelime);
  kutuAlt.hidden = true;
  // Listeden açılan kutuda renk seçici yok; iç kartta da gizleniyor,
  // durumu burada saklıyoruz.
  kalemlerGizli = Boolean(kayit);
  if (perde.hidden) derinles();
  perde.hidden = false;

  const [sozluk, kelimeler] = await Promise.all([depo.sozluk(anahtar), depo.kelimeler()]);
  if (secili !== istek) return;
  const varOlan = kayit || kelimeler.find(k => k.anahtar === anahtar);
  if (!kutuKalemler.hidden) kalemleriCiz(varOlan?.kalem);

  istek.ceviri = sozluk?.ceviri || gecerliCeviri(varOlan);
  istek.notlar = sozluk?.notlar || varOlan?.notlar || [];
  istek.kart = sozluk?.kart || varOlan?.kart || null;

  if (istek.kart) kartiGoster(istek.kart, istek.kelime);

  if (istek.ceviri) {
    ceviriyiGoster(istek);
    return;
  }

  kutuCeviri.textContent = BEKLEME;
  kutuCeviri.className = "sonuc sonuk";
  const eser = istek.kaynak?.ad || "";
  // Tek kelimede karşılık; öbekte tam çeviri ve zor ifadeler.
  const sonuc = cumleMi(kelime)
    ? await cumle(ayarlar, kelime, baglam, eser)
    : await cevir(ayarlar, kelime, baglam, eser);

  if (sonuc.hata) {
    if (secili === istek) {
      kutuCeviri.textContent = sonuc.hata;
      kutuCeviri.className = "sonuc uyari";
    }
    return;
  }
  istek.ceviri = sonuc.ceviri || sonuc.metin;
  istek.notlar = sonuc.zorlar || [];
  await sozlugeYaz(anahtar, { ceviri: istek.ceviri, notlar: istek.notlar });
  if (secili === istek) ceviriyiGoster(istek);
}

function ceviriyiGoster(istek) {
  kutuCeviri.textContent = istek.ceviri;
  kutuCeviri.className = "sonuc";
  kutuNotlar.innerHTML = "";
  // Zor ifadeler çevirinin hemen altında, küçük: ifade — anlamı.
  istek.notlar.forEach(n => {
    if (!n?.ifade) return;
    const satir = yap("div", "", "not");
    const ifade = yap("b", n.ifade);
    ifade.dir = "auto";
    satir.append(ifade, yap("span", n.anlam ? ` — ${n.anlam}` : ""));
    kutuNotlar.append(satir);
  });
}

/**
 * Sözlüğe yazar; kelime listedeyse kaydını da günceller.
 *
 * Kelime kaydı çeviriyi ve kartı kendi üstünde de taşıyor — liste
 * ekranı ve yedek dosyası oradan okuyor.
 */
async function sozlugeYaz(anahtar, alanlar) {
  await depo.sozlukYaz(anahtar, alanlar);
  const kayit = (await depo.kelimeler()).find(k => k.anahtar === anahtar);
  if (kayit) await depo.kelimeYaz({ ...kayit, ...alanlar });
}

/**
 * Kelimenin çevresinden on beş kelimelik pencere.
 *
 * Model bağlamı buradan alıyor; çevrilen şey yine yalnız seçim. Paragrafın
 * tamamını göndermek hem pahalı hem gereksiz.
 */
function pencere(paragraf, kelime) {
  const kelimeler = paragraf.split(/\s+/);
  const yer = kelimeler.findIndex(k => k.toLowerCase().includes(kelime.toLowerCase()));
  if (yer < 0) return paragraf.slice(0, 400);
  return kelimeler.slice(Math.max(0, yer - 15), yer + 16).join(" ");
}

function kalemleriCiz(seciliKalem) {
  kutuKalemler.innerHTML = "";
  KALEMLER.forEach(kalem => {
    const nokta = document.createElement("span");
    nokta.style.background = `var(--${{ YELLOW: "sari", BLUE: "mavi", GREEN: "yesil", RED: "kirmizi" }[kalem]})`;
    if (kalem === seciliKalem) nokta.className = "secili";
    // Seçili renge tekrar basmak işareti kaldırıyor.
    nokta.onclick = () => isaretle(kalem === seciliKalem ? null : kalem);
    kutuKalemler.append(nokta);
  });
}

async function isaretle(kalem) {
  if (!secili) return;
  const { anahtar } = secili;
  if (kalem === null) {
    await depo.kelimeSil(anahtar);
  } else {
    const hepsi = await depo.kelimeler();
    const eski = hepsi.find(k => k.anahtar === anahtar);
    await depo.kelimeYaz(yeniKelime({
      ...eski,
      anahtar,
      kelime: secili.kelime,
      kalem,
      baglam: secili.baglam,
      kitap: secili.kaynak?.id || eski?.kitap || "",
      eser: secili.kaynak?.ad || eski?.eser || "",
      // Çeviri henüz gelmediyse boş kalıyor; gelince `sozlugeYaz`
      // kaydı tamamlıyor.
      ceviri: secili.ceviri || gecerliCeviri(eski),
      notlar: secili.notlar.length ? secili.notlar : (eski?.notlar || []),
      kart: secili.kart || eski?.kart || null,
    }));
  }
  kutuyuKapat();
  if (acikKitap) oku(acikKitap.id); else git("deste");
}

kutuAyrinti.onclick = async () => {
  if (!secili || cumleMi(secili.kelime)) return;
  const istek = secili;
  kutuAyrinti.disabled = true;
  ayrintiYaz("Getiriliyor…");
  const sonuc = await kartIste(ayarlar, istek.kelime, istek.baglam, istek.kaynak?.ad || "");
  if (sonuc.kart) {
    istek.kart = sonuc.kart;
    await sozlugeYaz(istek.anahtar, { kart: sonuc.kart });
  }
  if (secili !== istek) return;
  kutuAyrinti.disabled = false;
  ayrintiYaz("Kelime kartı");
  if (sonuc.kart) {
    kartiGoster(sonuc.kart, istek.kelime);
  } else {
    kutuNot.innerHTML = "";
    kutuNot.className = "sonuc uyari";
    kutuNot.textContent = sonuc.hata;
  }
};

// Soru alanı düğmeye basınca açılıyor; her seçimde gerekmiyor. İki
// düğme aynı işi yapıyor: üstteki sırada duran ve kart açıkken dipte
// beliren.
const soruyuAc = () => {
  kutuSoruAlani.hidden = !kutuSoruAlani.hidden;
  if (!kutuSoruAlani.hidden) kutuSoruMetin.focus();
};
kutuSor.onclick = soruyuAc;
kutuSorAlt.onclick = soruyuAc;
kutuSoruMetin.addEventListener("keydown", e => {
  if (e.key === "Enter") kutuSoruGonder.click();
});
kutuSoruGonder.onclick = async () => {
  if (!secili) return;
  const metin = kutuSoruMetin.value.trim();
  if (!metin) { kutuSoruMetin.focus(); return; }
  const istek = secili;
  kutuSoruGonder.disabled = true;
  kutuCevap.textContent = "Bakılıyor…";
  kutuCevap.className = "sonuc sonuk";
  // Soru ekrandaki karta ait olmalı: iç karttayken dıştaki kelimeyi
  // sormak yanıltıcıydı.
  const ust = kartYigini[kartYigini.length - 1];
  const sonuc = await soru(ayarlar, ust?.kelime || istek.kelime, istek.baglam,
    istek.kaynak?.ad || "", metin, ust?.kart || istek.kart);
  if (secili !== istek) return;
  kutuSoruGonder.disabled = false;
  kutuCevap.textContent = sonuc.metin || sonuc.hata;
  kutuCevap.className = sonuc.metin ? "sonuc" : "sonuc uyari";
};

/** Kart puntosu; A+ / A− bunu değiştiriyor, ayarda saklanıyor. */
let kartPunto = 16;

async function puntoDegistir(yon) {
  kartPunto = Math.min(28, Math.max(12, kartPunto + yon * 2));
  // Açık duran kart yeniden çizilmeden büyüyor: her satır bu değişkenden
  // türüyor, tek yerde değiştirmek yetiyor.
  document.querySelectorAll(".kelime-kart").forEach(
    k => k.style.setProperty("--punto", `${kartPunto}px`));
  await depo.ayarYaz("kartPunto", String(kartPunto));
}

/**
 * Depodaki eski kartları yeni biçime uydurur.
 *
 * Bir dönem birliktelik düz dizi olarak tutuluyordu; okunuş, ilgili ve
 * karıştırma alanları hiç yoktu. O kartlar silinmiyor, eksik alanları
 * boş kalıyor.
 */
function kartiDuzle(k) {
  const b = k.birliktelik;
  if (Array.isArray(b) && b.length && typeof b[0] === "string") {
    return { ...k, birliktelik: [{ grup: "Birlikte", kelimeler: b }] };
  }
  return k;
}

/*
 * Kart yığını.
 *
 * Karttaki bir kelimeye dokunmak onun kartını üste bindiriyor; geri her
 * seferinde bir kademe iniyor. Kaç kademe olacağının sınırı yok — bir
 * kelimenin ailesinden başka bir kelimeye, oradan onun eş anlamlısına
 * gitmek okurken kurulan zincirin ta kendisi.
 */
let kartYigini = [];

/**
 * Kartı kutuya yerleştirir; yığını sıfırlar.
 *
 * Kart kendi başına tam bir madde: en üstünde kelimenin kendisi ve
 * karşılığı var. Bu yüzden kutunun kendi başlık ve çeviri satırı
 * gizleniyor — aynı şey iki kez yazılmasın.
 */
function kartiGoster(k, kelime) {
  kartYigini = [{ kart: k, kelime }];
  // Kart getirildi; üstteki sıranın işi bitti ve kartın önünde duruyordu.
  // Soru sormanın yolu kutunun dibindeki yapışık düğme.
  kutuAyrinti.parentElement.hidden = true;
  kutuAlt.hidden = false;
  yiginiCiz();
}

function yiginiCiz() {
  kutuSecim.hidden = true;
  kutuCeviri.hidden = true;
  kutuNot.innerHTML = "";
  kutuNot.className = "sonuc";
  /*
   * Üste binen kartta yalnız o kartın kendi şeyleri duruyor. Kutunun
   * renk seçicisi ilk seçilen kelimeye ait; iç kartın kendi seçicisi
   * varken ikisi yan yana durunca hangisinin hangi kelimeye ait olduğu
   * anlaşılmıyordu. Önceki kelimeye verilen cevap da siliniyor.
   */
  const icKart = kartYigini.length > 1;
  kutuKalemler.hidden = kalemlerGizli || icKart;
  kutuNotlar.hidden = icKart;
  kutuSoruAlani.hidden = true;
  kutuCevap.textContent = "";
  const ust = kartYigini[kartYigini.length - 1];
  if (!ust) return;
  kutuNot.append(kartiCiz(ust.kart, ust.kelime, kartYigini.length - 1));
}

/** Sözlükten eklenen kelimeler listede kendi başlığı altında toplanıyor. */
const SOZLUK_ESER = "Sözlükten";

/**
 * Üste binen kartın renk seçicisi.
 *
 * Kutunun kendi seçicisi ilk seçilen kelimeye ait; kartın içinden başka
 * bir kelimeye gidildiğinde ona yaramıyordu. Buradan işaretlenen kelime
 * bir kitaba değil sözlüğe bağlanıyor ve listede "Sözlükten" başlığı
 * altında duruyor.
 */
function sozlukKalemleri(kelime) {
  const sira = yap("div", "", "kart-kalemler");
  const anahtar = anahtarla(kelime);
  const ciz = seciliKalem => {
    sira.innerHTML = "";
    KALEMLER.forEach(kalem => {
      const nokta = yap("span", "", kalem === seciliKalem ? "secili" : "");
      nokta.style.background = `var(--${
        { YELLOW: "sari", BLUE: "mavi", GREEN: "yesil", RED: "kirmizi" }[kalem]})`;
      // Seçili renge tekrar basmak kelimeyi listeden çıkarıyor.
      nokta.onclick = async () => {
        const yeni = kalem === seciliKalem ? null : kalem;
        await sozluktenIsaretle(kelime, yeni);
        ciz(yeni);
      };
      sira.append(nokta);
    });
  };
  ciz(null);
  // Kayıt depodan gelince seçili renk yerine oturuyor.
  depo.kelimeler().then(hepsi => {
    const kayit = hepsi.find(k => k.anahtar === anahtar);
    if (kayit?.kalem) ciz(kayit.kalem);
  });
  return sira;
}

async function sozluktenIsaretle(kelime, kalem) {
  const anahtar = anahtarla(kelime);
  if (!kalem) {
    await depo.kelimeSil(anahtar);
    return;
  }
  const [hepsi, sozluk] = await Promise.all([depo.kelimeler(), depo.sozluk(anahtar)]);
  const eski = hepsi.find(k => k.anahtar === anahtar);
  await depo.kelimeYaz(yeniKelime({
    ...eski,
    anahtar,
    kelime,
    kalem,
    baglam: eski?.baglam || "",
    // Kitaptan gelmediyse sözlüğe bağlanıyor; kitaptan geldiyse kendi
    // kitabında kalıyor.
    kitap: eski?.kitap || "",
    eser: eski?.eser || SOZLUK_ESER,
    ceviri: sozluk?.ceviri || gecerliCeviri(eski),
    notlar: sozluk?.notlar || eski?.notlar || [],
    kart: sozluk?.kart || eski?.kart || null,
  }));
}

/** Bir kademe geri: üstteki kart kalkıyor, altındaki görünüyor. */
function kartGeri() {
  kartYigini.pop();
  yiginiCiz();
}

/**
 * Karttaki bir kelimenin kartını üste bindirir.
 *
 * Kart daha önce alınmışsa sözlükten geliyor, istek atılmıyor.
 */
async function kartaGir(kelime) {
  if (!secili) return;
  const temiz = kelime.trim();
  const anahtar = anahtarla(temiz);
  if (!anahtar) return;
  // Aynı kelimenin üstüne yine kendisi binmesin.
  const ust = kartYigini[kartYigini.length - 1];
  if (ust && anahtarla(ust.kelime || "") === anahtar) return;

  const istek = secili;
  const kademe = { kart: null, kelime: temiz };
  kartYigini.push(kademe);
  derinles();
  yiginiCiz();

  const sozluk = await depo.sozluk(anahtar);
  let veri = sozluk?.kart || null;
  if (!veri) {
    const sonuc = await kartIste(ayarlar, temiz, istek.baglam, istek.kaynak?.ad || "");
    if (sonuc.kart) {
      veri = sonuc.kart;
      await sozlugeYaz(anahtar, { kart: veri });
    } else {
      veri = { hata: sonuc.hata };
    }
  }
  // Kutu kapanmış ya da kademe geri alınmış olabilir.
  if (secili !== istek || !kartYigini.includes(kademe)) return;
  kademe.kart = veri;
  yiginiCiz();
}

/**
 * Kelime kartı — Android'deki kartın düzeni.
 *
 * Sıra oradaki gibi: büyük kelime, okunuş satırı, Türkçe karşılık, kendi
 * dilinde tanım, örnekler, kök ve aile, eş/karşıt baloncukları, ilgili
 * kelimeler, birliktelik grupları, karıştırılanlar.
 *
 * Arapça her yerde Türkçeden büyük yazılıyor: harekeli yazı küçükken
 * okunmuyor, harekeler birbirine giriyor. Türkçe olan her şey — karşılık,
 * örneklerin altı, "kelime — Türkçe" maddelerinin sağ yarısı — bir
 * kademe küçük.
 */
function kartiCiz(k, kelime = "", kademe = 0) {
  const arapca = k?.arapca ?? (kelime ? arapcaMi(kelime) : false);
  const kaynak = arapca ? "ar" : "tr";
  const kart = yap("div", "", "kelime-kart");
  kart.style.setProperty("--punto", `${kartPunto}px`);

  const yonlu = oge => { oge.dir = "auto"; return oge; };

  /*
   * Kaynak dilindeki metni kelime kelime dokunulur yapıyor: kartta
   * bilinmeyen bir kelime görülünce ona dokunmak kartını üste
   * bindiriyor.
   */
  const dokunulur = (metin, sinif, silikMi, izinli) => {
    const kap = yonlu(yap("span", "", sinif));
    metin.split(/(\s+)/).forEach(parca => {
      if (!parca.trim()) {
        kap.append(document.createTextNode(parca));
        return;
      }
      const sozcuk = yap("span", parca, silikMi?.(parca) ? "kk silik" : "kk");
      /*
       * Örnek cümlede iki kademe var: kapalıyken kelimeye dokunmak da
       * cümlenin Türkçesini açıyor, kart gelmiyor. Türkçesi açıldıktan
       * sonra kelimeye dokunmak kartını getiriyor. Kartın başka
       * yerlerinde kademe yok, ilk dokunuş kartı açıyor.
       */
      sozcuk.onclick = olay => {
        if (izinli && !izinli()) return;
        olay.stopPropagation();
        kartaGir(parca);
      };
      kap.append(sozcuk);
    });
    return kap;
  };

  // "kelime — Türkçe" maddesi: sol yarı kaynak dilinde ve büyük, sağ
  // yarı Türkçe ve küçük.
  const ikili = metin => {
    const kap = yap("span", "", "ikili-madde");
    const yer = metin.indexOf("—");
    if (yer < 0) {
      kap.append(dokunulur(metin, kaynak));
      return kap;
    }
    kap.append(dokunulur(metin.slice(0, yer).trim(), kaynak));
    kap.append(yap("span", ` — ${metin.slice(yer + 1).trim()}`, "tr"));
    return kap;
  };

  // Üst sıra: solda bir kademe geri (üste binmiş kartta), sağda punto.
  const ustSira = yap("div", "", "kart-punto");
  if (kademe > 0) {
    const geri = yap("button", "›", "punto-dugme geri");
    geri.onclick = () => tarayiciGeri();
    ustSira.append(geri);
  }
  const bosluk = yap("span", "", "esne");
  /*
   * Kartı yenileme. Depodaki kart bir kez alınıp saklanıyor; yönerge
   * sonradan değişince eski kartlar eski biçimleriyle açılmaya devam
   * ediyordu ve tazelemenin yolu yoktu.
   */
  if (kelime) {
    const yenile = yap("button", "↻", "punto-dugme");
    yenile.setAttribute("aria-label", "Kartı yenile");
    yenile.onclick = async () => {
      const istek = secili;
      if (!istek) return;
      yenile.disabled = true;
      const sonuc = await kartIste(
        ayarlar, kelime, istek.baglam, istek.kaynak?.ad || "");
      if (secili !== istek) return;
      yenile.disabled = false;
      if (!sonuc.kart) return;
      await sozlugeYaz(anahtarla(kelime), { kart: sonuc.kart });
      const ust = kartYigini[kartYigini.length - 1];
      if (ust) ust.kart = sonuc.kart;
      yiginiCiz();
    };
    ustSira.append(yenile);
  }
  const kucult = yap("button", "A−", "punto-dugme");
  const buyut = yap("button", "A+", "punto-dugme");
  kucult.onclick = () => puntoDegistir(-1);
  buyut.onclick = () => puntoDegistir(1);
  ustSira.append(bosluk, kucult, buyut);
  kart.append(ustSira);

  if (kelime) kart.append(yonlu(yap("div", kelime, `kart-baslik ${kaynak}`)));

  /*
   * Üste binen kartta kendi renk seçicisi var: sözlükte gezerken
   * rastlanan kelime de listeye alınabilsin. Kutunun kendi seçicisi ilk
   * seçilen kelimeye ait, buradakine yaramıyordu.
   */
  if (kademe > 0 && kelime) kart.append(sozlukKalemleri(kelime));

  // Kart daha gelmediyse ya da gelemediyse başlıkla birlikte durumu yaz.
  if (!k || k.hata) {
    kart.append(yap("p", k?.hata || BEKLEME, k?.hata ? "uyari" : "sonuk"));
    return kart;
  }

  /*
   * Okunuş satırı. İsimde harekeli yazım ve çoğul; fiilde mazi, muzari
   * ve mastar. Latin okunuş istenmiyor — okuyan yazıyı zaten okuyor,
   * araya giren çevriyazı işi zorlaştırıyor.
   */
  k = kartiDuzle(k);

  if (k.okunus) kart.append(dokunulur(k.okunus, `kart-okunus ${kaynak}`));
  if (k.karsilik) kart.append(yap("p", k.karsilik, "karsilik"));
  // Tanım kendi çerçevesinde: kartın en yoğun satırı, gövdeden ayrılınca
  // gözü yormuyor.
  if (k.tanim) {
    const kutu = yap("div", "", "tanim-kutu");
    kutu.append(dokunulur(k.tanim, `tanim ${kaynak}`));
    kart.append(kutu);
  }

  if (k.ornekler?.length) {
    const liste = yap("ol", "", "ornekler");
    // Arapçada bütün maddeler aynı yönde dizilsin; madde madde yön
    // sezdirilince kimi sağdan kimi soldan başlıyordu.
    if (arapca) liste.dir = "rtl";
    k.ornekler.forEach(o => {
      const madde = document.createElement("li");
      const asil = typeof o === "string" ? o : (o.asil || o.en || "");
      const ceviri = typeof o === "string" ? "" : o.tr;
      // Türkçesi kapalıyken kelimeye dokunmak kartı açmıyor; önce cümle
      // açılıyor.
      if (ceviri) madde.classList.add("acilir");
      madde.append(dokunulur(asil, kaynak, null,
        () => !madde.classList.contains("acilir")));
      if (ceviri) {
        /*
         * Türkçesi ilk dokunuşta açılıyor. Açık dururken göz cümleyi
         * anlamaya çalışmadan Türkçeye kayıyor.
         *
         * Yanında "Türkçesi" yazmıyor; cümlenin altındaki ince kesik
         * çizgi dokunulabilir olduğunu söylüyor, bağırmadan.
         */
        const alt = yap("div", ceviri, "tr");
        alt.hidden = true;
        madde.append(alt);
        madde.onclick = () => {
          alt.hidden = false;
          madde.classList.remove("acilir");
        };
      }
      liste.append(madde);
    });
    kart.append(liste);

    /*
     * Üç örnek çoğu kelimeyi oturtuyor; oturtmadığında tek yol kartı
     * baştan almaktı. Artı düğmesi üç tane daha getiriyor ve karta
     * yazıyor, bir daha istenmesin.
     */
    if (kelime) {
      const ekle = yap("button", "+", "ornek-ekle");
      ekle.setAttribute("aria-label", "Örnek ekle");
      ekle.onclick = async () => {
        if (!secili) return;
        ekle.disabled = true;
        ekle.textContent = "…";
        const sonuc = await ekOrnekler(
          ayarlar, kelime, secili.baglam, secili.kaynak?.ad || "", k.ornekler);
        ekle.disabled = false;
        ekle.textContent = "+";
        if (!sonuc.ornekler?.length) {
          if (sonuc.hata) ekle.title = sonuc.hata;
          return;
        }
        k.ornekler = [...k.ornekler, ...sonuc.ornekler];
        // Eski kayıtlar düzeltilirken kopyalanıyor; yığındaki kart da
        // yenilenmezse eklenen örnekler yeniden çizimde kayboluyor.
        const ust = kartYigini[kartYigini.length - 1];
        if (ust) ust.kart = k;
        await sozlugeYaz(anahtarla(kelime), { kart: k });
        yiginiCiz();
      };
      kart.append(ekle);
    }
  }

  /*
   * Bir bölüm satırı. "satirli" verilince maddeler yan yana değil alt
   * alta diziliyor: aile maddeleri "kelime — Türkçe" biçiminde uzun,
   * yan yana dizilince nerede bitip nerede başladıkları seçilmiyordu.
   */
  const bolum = (etiket, parcalar, satirli) => {
    if (!parcalar?.length) return;
    const satir = yap("div", "", "kart-bolum");
    satir.append(yap("span", etiket, "etiket"));
    const deger = yap("span", "", satirli ? "kart-deger satirli" : "kart-deger");
    parcalar.forEach((p, i) => {
      if (satirli) {
        const madde = document.createElement("div");
        madde.append(p);
        deger.append(madde);
        return;
      }
      if (i) deger.append(document.createTextNode(" · "));
      deger.append(p);
    });
    satir.append(deger);
    kart.append(satir);
  };

  /** "ب ش ش (gülümsemek)" — harfler kaynak dilinde, parantez Türkçe. */
  const kokYaz = metin => {
    const kap = yap("span", "", "ikili-madde");
    const yer = metin.indexOf("(");
    if (yer < 0) {
      kap.append(dokunulur(metin, kaynak));
      return kap;
    }
    kap.append(dokunulur(metin.slice(0, yer).trim(), kaynak));
    kap.append(yap("span", ` ${metin.slice(yer).trim()}`, "tr"));
    return kap;
  };

  if (k.kok) bolum("Kök", [kokYaz(k.kok)]);
  bolum("Aile", k.aile?.map(ikili), true);

  // Eş anlamlılar mavi, karşıtlar kırmızı, ilgili kelimeler gri —
  // Android'deki kartla aynı renkler.
  const baloncuklar = gruplar => {
    const dolu = gruplar.filter(g => g.liste?.length);
    if (!dolu.length) return;
    const sar = yap("div", "", "baloncuklar");
    dolu.forEach(g => g.liste.forEach(madde => {
      const balon = yap("span", "", `baloncuk ${g.sinif}`);
      balon.append(ikili(madde));
      sar.append(balon);
    }));
    kart.append(sar);
  };
  // Üçü ayrı satırda: aynı satırda akınca eş anlamlının nerede bitip
  // karşıtın nerede başladığı seçilmiyordu.
  baloncuklar([{ liste: k.esanlam, sinif: "es" }]);
  baloncuklar([{ liste: k.karsit, sinif: "karsit" }]);
  baloncuklar([{ liste: k.ilgili, sinif: "ilgili" }]);

  /*
   * Birliktelik: öbek modelden olduğu gibi geliyor.
   *
   * Sıralamayı grubun adından çıkarmaya çalışıyorduk ve yanlış diziyordu:
   * Arapçada sıfat mevsuftan sonra geliyor, "sıfat +" etiketi ise önce
   * geleceğini söylüyor. Etiket ortağın cinsini anlatıyor, yerini değil.
   * Ayrıca asıl kelimeyi öbeğin yanına biz eklediğimiz için tamlamada
   * kelime iki kez yazılıyordu. Artık tam öbeği model veriyor; biz
   * yalnız içindeki asıl kelimeyi silik gösteriyoruz — her satırda
   * tekrar ettiğinden.
   */
  const asilKok = kelime ? sadeKok(kelime) : "";
  const asilMi = parca => ayniKelime(sadeKok(parca), asilKok);
  k.birliktelik?.forEach(g => {
    if (!g?.grup || !g.kelimeler?.length) return;
    bolum(g.grup, g.kelimeler.map(obek => dokunulur(obek, kaynak, asilMi)), true);
  });

  if (k.karistirma?.length) {
    kart.append(yap("div", "Karıştırma", "kart-ayrac"));
    k.karistirma.forEach(madde => {
      const satir = yap("div", "", "karistirma");
      satir.append(ikili(madde));
      kart.append(satir);
    });
  }

  return kart;
}

// --- Deste -----------------------------------------------------------

async function deste() {
  const kelimeler = await depo.kelimeler();
  ekran.append(yap("h1", "Liste"));

  if (!kelimeler.length) {
    ekran.append(yap("div",
      "Henüz kelime yok. Okurken bir kelimeye dokunup renk seç.", "bos"));
    return;
  }

  const bekleyen = bekleyenler(kelimeler);
  const calis = yap("button",
    bekleyen.length ? `Tekrara başla (${bekleyen.length})` : "Bugünlük tekrar bitti",
    "dolu");
  calis.disabled = !bekleyen.length;
  calis.onclick = () => tekrarEkrani(bekleyen);
  ekran.append(calis, renkSuzgeciDugmesi());

  kelimeler.sort((a, b) => (b.eklendi || 0) - (a.eklendi || 0));

  // Kelimeler kitaba göre bölünüyor; her kitap bir sekme. "Bütün
  // kelimeler" başlığının ve her satırdaki kitap etiketinin yerine.
  const eserler = [];
  kelimeler.forEach(k => {
    const id = k.kitap || "";
    let eser = eserler.find(e => e.id === id);
    if (!eser) {
      eser = { id, ad: k.eser || "Diğer", kelimeler: [] };
      eserler.push(eser);
    }
    eser.kelimeler.push(k);
  });
  // Sözlükten eklenenler kitapların ardında: kitap sekmeleri asıl iş,
  // sözlük torbası en sonda.
  eserler.sort((a, b) =>
    Number(a.ad === SOZLUK_ESER) - Number(b.ad === SOZLUK_ESER));
  if (!eserler.some(e => e.id === seciliEser)) seciliEser = eserler[0].id;

  const sekmeler = yap("div", "", "sekmeler");
  eserler.forEach(e => {
    const sekme = yap("button", `${e.ad} (${e.kelimeler.length})`,
      e.id === seciliEser ? "sekme secili" : "sekme");
    sekme.onclick = () => { seciliEser = e.id; git("deste"); };
    sekmeler.append(sekme);
  });
  ekran.append(sekmeler);

  const liste = yap("div", "");
  const satirlar = new Map();
  const hepsi = eserler.find(e => e.id === seciliEser).kelimeler;
  const gosterilen = hepsi.filter(suzgectenGecer);
  gosterilen.forEach(k => {
    /*
     * Uzun kayıtlar iki sütuna sığmıyor: cümlenin Türkçesi dar sütunda
     * aşağı doğru uzayıp satırı devleştiriyordu. Cümlede Arapça kendi
     * satırında, Türkçesi altında baştan sona.
     */
    const uzun = cumleMi(k.kelime);
    const satir = yap("button", "", uzun ? "kelime uzun" : "kelime");
    const nokta = yap("span", "", "im");
    nokta.style.background = `var(--${
      { YELLOW: "sari", BLUE: "mavi", GREEN: "yesil", RED: "kirmizi" }[k.kalem]})`;
    const kelime = yap("b", k.kelime);
    kelime.dir = "auto";
    const ceviri = yap("span", gecerliCeviri(k), "karsiligi");
    satirlar.set(k.anahtar, ceviri);
    satir.append(nokta, kelime, ceviri);

    // Uzun basmak (masaüstünde sağ tuş) kelimenin menüsünü açıyor.
    // Parmak kalkınca tarayıcı bir de tıklama gönderiyor; menü açıldıysa
    // o tıklama kutuyu açmamalı.
    let zaman;
    let menuAcildi = false;
    satir.onclick = () => {
      if (menuAcildi) { menuAcildi = false; return; }
      kelimeKutusu(k);
    };
    satir.addEventListener("touchstart", () => {
      zaman = setTimeout(() => { menuAcildi = true; kelimeMenusu(k); }, 600);
    }, { passive: true });
    ["touchend", "touchmove", "touchcancel"].forEach(o =>
      satir.addEventListener(o, () => clearTimeout(zaman), { passive: true }));
    satir.addEventListener("contextmenu", olay => {
      olay.preventDefault();
      clearTimeout(zaman);
      kelimeMenusu(k);
    });
    liste.append(satir);
  });
  ekran.append(liste);

  eksikleriDoldur(gosterilen.filter(k => !gecerliCeviri(k)), satirlar);
}

/*
 * Renk süzgeci: kırmızı, mavi, ikisi birden.
 *
 * Dikey ikiye bölünmüş bir düğme; her basış sıradaki duruma geçiyor.
 * "İkisi" seçiliyken hiçbir şey elenmiyor — sarı ve yeşil işaretliler de
 * listede kalıyor, yoksa onlara ulaşacak yol kapanırdı.
 */
let renkSuzgeci = "hepsi";

const suzgectenGecer = k => {
  if (renkSuzgeci === "kirmizi") return k.kalem === "RED";
  if (renkSuzgeci === "mavi") return k.kalem === "BLUE";
  return true;
};

function renkSuzgeciDugmesi() {
  const sira = ["hepsi", "kirmizi", "mavi"];
  const dugme = yap("button", "", `suzgec ${renkSuzgeci}`);
  dugme.setAttribute("aria-label", "Renk süzgeci");
  dugme.append(yap("span", "", "yari kirmizi"), yap("span", "", "yari mavi"));
  dugme.onclick = () => {
    renkSuzgeci = sira[(sira.indexOf(renkSuzgeci) + 1) % sira.length];
    git("deste");
  };
  return dugme;
}

/** Listedeki kelimenin menüsü. Şimdilik tek iş: listeden çıkarmak. */
function kelimeMenusu(k) {
  // İki yol aynı anda açmasın: Android'de uzun basma hem bizim sayacı
  // hem tarayıcının sağ tuş olayını tetikliyor.
  if (document.querySelector(".perde")) return;
  const baslik = yap("div", k.kelime, "menu-baslik");
  baslik.dir = "auto";
  const sil = yap("button", "Listeden sil", "menu-madde tehlike");
  const pencere = pencereAc([baslik, sil]);
  sil.onclick = async () => {
    pencere.remove();
    await depo.kelimeSil(k.anahtar);
    git("deste");
  };
}

let doldurma = null;

/**
 * Çevirisi olmayan kelimeleri arka planda, tek tek tamamlar.
 *
 * Çeviri gelmeden renge basılan kelimeler boş kalıyordu; listede öyle
 * duruyor, dokunulunca alınıyordu. Şimdi liste açılınca sırayla
 * alınıyor ve satır yerinde doluyor. Sırayla: aynı anda yedi istek
 * atmanın anlamı yok. İlk hata sırayı durduruyor — anahtar yoksa hepsi
 * aynı hatayı verir.
 */
function eksikleriDoldur(kelimeler, satirlar) {
  if (doldurma || !kelimeler.length) return;
  doldurma = (async () => {
    for (const k of kelimeler) {
      const sozluk = await depo.sozluk(k.anahtar);
      let ceviri = sozluk?.ceviri || "";
      let notlar = sozluk?.notlar || [];
      if (!ceviri) {
        const eser = k.eser || "";
        const sonuc = cumleMi(k.kelime)
          ? await cumle(ayarlar, k.kelime, k.baglam || "", eser)
          : await cevir(ayarlar, k.kelime, k.baglam || "", eser);
        if (sonuc.hata) break;
        ceviri = sonuc.ceviri || sonuc.metin;
        notlar = sonuc.zorlar || [];
      }
      await sozlugeYaz(k.anahtar, { ceviri, notlar });
      const satir = satirlar.get(k.anahtar);
      if (satir) satir.textContent = ceviri;
    }
  })().finally(() => { doldurma = null; });
}

// Listede açık duran kitap; ekrandan çıkıp dönünce aynı sekme kalsın.
let seciliEser = "";

/**
 * Destede bir kelimeye dokunmak kartı açıyor.
 *
 * Önce silme sorusu soran bir uyarı kutusu çıkıyordu — hem kitabın adını
 * hem bağlamı olduğu gibi döküyordu, hem de kelimeye bakmak isteyene
 * "silelim mi" diye soruyordu. Artık okurken açılan kutunun aynısı
 * açılıyor: renkler, bilgi, ayrıntı. Seçili renge basmak kelimeyi
 * desteden çıkarıyor, okurkenki gibi.
 */
async function kelimeKutusu(k) {
  await kutuyuAc(k.kelime, k.baglam || "", { id: k.kitap, ad: k.eser }, k);
  // Kart daha önce alınmadıysa bir kez alınıp kelimeye yazılıyor.
  // Cümlede kart yok; düğme de gizli.
  if (secili && !secili.kart && !kutuAyrinti.hidden) {
    kutuAyrinti.onclick();
  }
}

function tekrarEkrani(kuyruk) {
  cubuk.hidden = true;
  let sira = 0;
  let acik = false;

  const ciz = () => {
    ekran.innerHTML = "";
    const kelime = kuyruk[sira];
    if (!kelime) {
      ekran.append(yap("h1", "Bitti"));
      ekran.append(yap("p", "Bugünlük tekrar tamamlandı.", "sonuk"));
      const don = yap("button", "Listeye dön", "dolu");
      don.onclick = () => git("deste");
      ekran.append(don);
      return;
    }

    ekran.append(yap("div", `${sira + 1} / ${kuyruk.length}`, "kucuk sonuk"));

    const kart = yap("div", "");
    kart.id = "tekrar-kart";
    kart.append(yap("div", kelime.kelime, "yuz"));
    if (acik) {
      const arka = yap("div", "", "arka");
      if (gecerliCeviri(kelime)) arka.append(yap("p", kelime.ceviri));
      if (kelime.baglam) arka.append(yap("p", kelime.baglam, "kucuk sonuk"));
      if (kelime.eser) arka.append(yap("p", kelime.eser, "kucuk sonuk"));
      kart.append(arka);
    }
    kart.onclick = () => { acik = true; ciz(); };
    ekran.append(kart);

    if (!acik) {
      const goster = yap("button", "Anlamını göster", "dolu");
      goster.onclick = () => { acik = true; ciz(); };
      ekran.append(goster);
    } else {
      const satir = yap("div", "", "satir");
      const bilmiyorum = yap("button", "Bilmiyorum", "tonlu");
      const biliyorum = yap("button", "Biliyorum", "dolu");
      const ilerle = async biliyor => {
        await depo.kelimeYaz(karar(kelime, biliyor));
        sira++; acik = false; ciz();
      };
      bilmiyorum.onclick = () => ilerle(false);
      biliyorum.onclick = () => ilerle(true);
      satir.append(bilmiyorum, biliyorum);
      ekran.append(satir);
    }
  };

  ciz();
}

// --- Ayarlar ---------------------------------------------------------

async function ayarlarEkrani() {
  ekran.append(yap("h1", "Ayarlar"));

  ekran.append(yap("h2", "OpenAI anahtarı"));
  ekran.append(yap("p",
    "Çeviri ve bilgi notu bu anahtarla çalışıyor. Anahtar yalnız bu telefonda duruyor.",
    "kucuk sonuk"));
  const anahtar = document.createElement("input");
  anahtar.type = "password";
  anahtar.value = ayarlar.anahtar;
  anahtar.placeholder = "sk-…";
  ekran.append(anahtar);

  ekran.append(yap("label", "Model"));
  const model = document.createElement("select");
  ["gpt-4o-mini", "gpt-4o", "gpt-4.1-mini", "gpt-4.1"].forEach(m => {
    const secenek = document.createElement("option");
    secenek.value = m; secenek.textContent = m;
    if (m === ayarlar.model) secenek.selected = true;
    model.append(secenek);
  });
  ekran.append(model);

  const kaydet = yap("button", "Kaydet", "dolu");
  kaydet.style.marginTop = "16px";
  kaydet.onclick = async () => {
    ayarlar = { anahtar: anahtar.value.trim(), model: model.value };
    await depo.ayarYaz("anahtar", ayarlar.anahtar);
    await depo.ayarYaz("model", ayarlar.model);
    kaydet.textContent = "Kaydedildi";
    setTimeout(() => (kaydet.textContent = "Kaydet"), 1500);
  };
  ekran.append(kaydet);

  ekran.append(yap("h2", "Yedek"));
  ekran.append(yap("p",
    "Her şey yalnız bu telefonda duruyor. Safari'nin verisi silinirse ya da " +
    "telefon değişirse yedek olmadan geri dönüşü yok.",
    "kucuk sonuk"));

  const satir = yap("div", "", "satir");
  const al = yap("button", "Yedek al", "tonlu");
  al.onclick = async () => {
    const paket = await disaAktar();
    const bag = document.createElement("a");
    bag.href = URL.createObjectURL(
      new Blob([JSON.stringify(paket)], { type: "application/json" }));
    bag.download = `arapca-kitap-yedek-${new Date().toISOString().slice(0, 10)}.json`;
    bag.click();
  };
  const geri = yap("button", "Yedeği geri yükle", "tonlu");
  const seciciYedek = Object.assign(document.createElement("input"),
    { type: "file", accept: "application/json,.json" });
  seciciYedek.hidden = true;
  geri.onclick = () => seciciYedek.click();
  seciciYedek.onchange = async () => {
    const dosya = seciciYedek.files[0];
    if (!dosya) return;
    await iceAktar(JSON.parse(await dosya.text()));
    git("kitaplik");
  };
  satir.append(al, geri);
  ekran.append(satir, seciciYedek);

  ekran.append(yap("h2", "Kurulum"));
  ekran.append(yap("p",
    "iPhone'da: Safari'de Paylaş → Ana Ekrana Ekle. Bundan sonra hep o " +
    "simgeden aç. Sekmeden kullanırsan Safari bir süre sonra verini silebilir.",
    "kucuk sonuk"));
}

// --- Yardımcılar -----------------------------------------------------

function yap(etiket, yazi, sinif) {
  const oge = document.createElement(etiket);
  if (yazi) oge.textContent = yazi;
  if (sinif) oge.className = sinif;
  return oge;
}

/**
 * Metin Arapça mı.
 *
 * Tek bir Arap harfi yetmiyor — İngilizce bir cümlenin içindeki tek
 * kelime bütün paragrafı ters çevirirdi. Harflerin belirgin bir kısmı
 * Arapçaysa yön değişiyor.
 */
function arapcaMi(yazi) {
  const arap = (yazi.match(/[\u0600-\u06FF]/g) || []).length;
  const harf = (yazi.match(/\p{L}/gu) || []).length;
  return harf > 0 && arap / harf > 0.4;
}

/** Kitabın adından türeyen sabit bir renk; kapağı olmayan kitap tanınsın. */
function renkTohumu(ad) {
  let toplam = 0;
  for (const harf of ad) toplam = (toplam * 31 + harf.charCodeAt(0)) % 360;
  return `hsl(${toplam} 45% 45%)`;
}

// --- Açılış ----------------------------------------------------------

/**
 * Hata ekranı.
 *
 * Bir şey patladığında sayfa sessizce boş kalıyordu ve elde "hiçbir şey
 * çıkmadı"dan başka bilgi olmuyordu. Artık hata ekrana yazılıyor: neyin
 * bozulduğunu görmeden düzeltmenin yolu yok.
 */
function hataGoster(sebep) {
  const kutu = yap("div", "", "bos");
  kutu.append(yap("p", "Bir şeyler ters gitti.", "uyari"));
  kutu.append(yap("p", String(sebep && sebep.message || sebep), "kucuk"));
  ekran.innerHTML = "";
  ekran.append(kutu);
}

window.addEventListener("error", e => hataGoster(e.error || e.message));
window.addEventListener("unhandledrejection", e => hataGoster(e.reason));

(async () => {
  try {
    ayarlar = {
      anahtar: await depo.ayar("anahtar", ""),
      model: await depo.ayar("model", "gpt-4o-mini"),
    };
    try {
      okumaTercihi = { ...okumaTercihi, ...JSON.parse(await depo.ayar("okuma", "{}")) };
      // Kart puntosu seçildiği gibi kalsın; her kart açılışında yeniden
      // büyütmek gerekmesin.
      kartPunto = Number(await depo.ayar("kartPunto", "16")) || 16;
    } catch { /* bozuk kayıt varsayılanı bozmasın */ }
    // Kalkan zeminler: "orta" ve "gece" artık tek bir "koyu"; tanınmayan
    // her şey açığa düşüyor. "ozel" hazır listede yok ama geçerli.
    if (okumaTercihi.zemin !== "ozel" && !ZEMINLER[okumaTercihi.zemin]) {
      const koyuydu = okumaTercihi.zemin === "orta" || okumaTercihi.zemin === "gece";
      okumaTercihi.zemin = koyuydu ? "koyu" : "kagit";
    }
    await git("kitaplik");
  } catch (e) {
    hataGoster(e);
  }
  if ("serviceWorker" in navigator) {
    navigator.serviceWorker.register("sw.js").catch(() => {});
  }
})();
