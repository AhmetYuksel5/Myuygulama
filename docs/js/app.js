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
import { cevir, cumle, kart, soru } from "./yapayzeka.js";
import { yeniKelime, bekleyenler, karar, bugun } from "./tekrar.js";

const ekran = document.getElementById("ekran");
const cubuk = document.getElementById("cubuk");
const KALEMLER = ["YELLOW", "BLUE", "GREEN", "RED"];

let ayarlar = { anahtar: "", model: "gpt-4o-mini" };

/**
 * Okuma tercihleri.
 *
 * Üç zemin: açık, orta, koyu. Koyu zeminin metni bilerek arayüzünkinden
 * sönük: siyah üstüne beyaz uzun okumada yoruyor. Orta, ikisinin
 * arasında gri — gece odada açık zemin göz alıyor, koyu zemin bazı
 * gözlere fazla sert geliyor.
 */
const ZEMINLER = {
  kagit: { ad: "Açık", zemin: "#faf7f0", yazi: "#22201c", cizgi: "#ddd5c7" },
  orta: { ad: "Orta", zemin: "#3a3d43", yazi: "#d9d5cd", cizgi: "#565a62" },
  gece: { ad: "Koyu", zemin: "#0f1013", yazi: "#bfbbb3", cizgi: "#2a2c31" },
};

let okumaTercihi = { punto: 19, zemin: "kagit", kenar: 16, satir: 1.7 };

function tercihleriUygula() {
  const z = ZEMINLER[okumaTercihi.zemin] || ZEMINLER.kagit;
  const govde = document.getElementById("okuma");
  if (govde) {
    govde.style.fontSize = `${okumaTercihi.punto}px`;
    govde.style.lineHeight = String(okumaTercihi.satir);
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
}

// --- Yönlendirme -----------------------------------------------------

// Anahtarlar alt çubuktaki data-git değerleriyle birebir aynı olmak
// zorunda; "ayarlarEkrani" yazılıydı ve o sekme hiç açılmıyordu.
const sayfalar = { kitaplik, deste, ayarlar: ayarlarEkrani };
let acikKitap = null;

async function git(ad) {
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

  const geri = yap("button", "‹ Kitaplık");
  geri.onclick = () => git("kitaplik");
  const liste = yap("button", "Kelimeler");
  liste.onclick = () => git("deste");
  const ayar = yap("button", "⚙");
  ayar.setAttribute("aria-label", "Görünüm");
  ayar.onclick = gorunumKutusu;
  const yazi = yap("span", bilgi || "", "bilgi");
  okumaAlt.append(geri, liste, yazi, ayar);
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
  const camiGoster = (x, y) => {
    let cam = document.getElementById("cam");
    if (!cam) {
      cam = yap("div", "");
      cam.id = "cam";
      document.body.append(cam);
    }
    const [a, b] = [Math.min(bas, son), Math.max(bas, son)];
    cam.textContent = kelimeler.slice(a, b + 1).map(k => k.textContent).join(" ");
    cam.hidden = false;

    const yukari = 110;
    const ustte = y - yukari;
    cam.style.top = `${ustte > 60 ? ustte : y + yukari}px`;
    // Yatayda ekranın dışına taşmasın; genişliği ölçtükten sonra
    // ortalanıyor.
    const yari = cam.offsetWidth / 2;
    cam.style.left = `${Math.min(Math.max(x, yari + 8), window.innerWidth - yari - 8)}px`;
  };

  const camiKapat = () => {
    const cam = document.getElementById("cam");
    if (cam) cam.hidden = true;
  };

  govde.addEventListener("touchstart", olay => {
    const kelime = olay.target.closest?.("span.k");
    if (!kelime) return;
    bas = son = Number(kelime.dataset.sira);
    const nokta = olay.touches[0];
    zamanlayici = setTimeout(() => {
      seciyor = true;
      boya();
      camiGoster(nokta.clientX, nokta.clientY);
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
    camiGoster(nokta.clientX, nokta.clientY);
  }, { passive: false });

  const bitir = () => {
    clearTimeout(zamanlayici);
    if (!seciyor) return;
    seciyor = false;
    const [a, b] = [Math.min(bas, son), Math.max(bas, son)];
    const secim = kelimeler.slice(a, b + 1).map(k => k.textContent).join(" ");
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
  Object.entries(ZEMINLER).forEach(([anahtar, z]) => {
    const dugme = yap("button", z.ad, anahtar === okumaTercihi.zemin ? "dolu" : "tonlu");
    dugme.onclick = () => {
      okumaTercihi.zemin = anahtar;
      yaz();
      kutu.remove();
      gorunumKutusu();
    };
    zeminler.append(dugme);
  });

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
      .map(p => (p.trim() ? `<span class="k">${kacir(p)}</span>` : p))
      .join("");
    cikti.push(kacir(on) +
      `<mark class="${bulundu.kalem}" data-tam="${kacir(ic).replace(/"/g, "&quot;")}">${govde}</mark>` +
      kacir(arka));
    i = bulundu.son + 1;
  }
  return cikti.join("");
}

/** İşaretsiz tek kelime: noktalaması dışarıda kalacak şekilde sarılıyor. */
function kelimeyiSar(parca) {
  const sade = parca.replace(/^[^\p{L}\p{N}\p{M}]+|[^\p{L}\p{N}\p{M}]+$/gu, "");
  if (!sade) return kacir(parca);
  const [on, arka] = parca.split(sade);
  return kacir(on || "") + `<span class="k">${kacir(sade)}</span>` + kacir(arka || "");
}

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
const kutuSor = document.getElementById("kutu-sor");
const kutuSoruAlani = document.getElementById("kutu-soru-alani");
const kutuSoruMetin = document.getElementById("kutu-soru-metin");
const kutuSoruGonder = document.getElementById("kutu-soru-gonder");
const kutuCevap = document.getElementById("kutu-cevap");

const BEKLEME = "Anlamına bakılıyor…";

let secili = null;

perde.addEventListener("click", e => {
  // Dışarı dokunmak kapatıyor; kutunun içi kapatmıyor.
  if (e.target === perde) kutuyuKapat();
});

function kutuyuKapat() {
  perde.hidden = true;
  secili = null;
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
  const metin = isaret ? isaret.dataset.tam : oge.textContent;
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
  kutuNotlar.innerHTML = "";
  kutuNot.innerHTML = "";
  kutuNot.className = "sonuc";
  kutuSoruAlani.hidden = true;
  kutuSoruMetin.value = "";
  kutuCevap.textContent = "";
  kutuAyrinti.disabled = false;
  kutuAyrinti.textContent = "Ayrıntı";
  perde.hidden = false;

  const [sozluk, kelimeler] = await Promise.all([depo.sozluk(anahtar), depo.kelimeler()]);
  if (secili !== istek) return;
  const varOlan = kayit || kelimeler.find(k => k.anahtar === anahtar);
  kalemleriCiz(varOlan?.kalem);

  istek.ceviri = sozluk?.ceviri || gecerliCeviri(varOlan);
  istek.notlar = sozluk?.notlar || varOlan?.notlar || [];
  istek.kart = sozluk?.kart || varOlan?.kart || null;

  if (istek.kart) kutuNot.append(kartiCiz(istek.kart));

  if (istek.ceviri) {
    ceviriyiGoster(istek);
    return;
  }

  kutuCeviri.textContent = BEKLEME;
  kutuCeviri.className = "sonuc sonuk";
  const eser = istek.kaynak?.ad || "";
  // Tek kelimede karşılık; öbekte tam çeviri ve zor ifadeler.
  const sonuc = kelime.trim().includes(" ")
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
  if (!secili) return;
  const istek = secili;
  kutuAyrinti.disabled = true;
  kutuAyrinti.textContent = "Getiriliyor…";
  const sonuc = await kart(ayarlar, istek.kelime, istek.baglam, istek.kaynak?.ad || "");
  if (sonuc.kart) {
    istek.kart = sonuc.kart;
    await sozlugeYaz(istek.anahtar, { kart: sonuc.kart });
  }
  if (secili !== istek) return;
  kutuAyrinti.disabled = false;
  kutuAyrinti.textContent = "Ayrıntı";
  kutuNot.innerHTML = "";
  kutuNot.className = "sonuc";
  if (sonuc.kart) {
    kutuNot.append(kartiCiz(sonuc.kart));
  } else {
    kutuNot.className = "sonuc uyari";
    kutuNot.textContent = sonuc.hata;
  }
};

// "Soru sor" en altta, hafif: her seçimde gerekmiyor, göze batmasın.
kutuSor.onclick = () => {
  kutuSoruAlani.hidden = !kutuSoruAlani.hidden;
  if (!kutuSoruAlani.hidden) kutuSoruMetin.focus();
};
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
  const sonuc = await soru(ayarlar, istek.kelime, istek.baglam,
    istek.kaynak?.ad || "", metin, istek.kart);
  if (secili !== istek) return;
  kutuSoruGonder.disabled = false;
  kutuCevap.textContent = sonuc.metin || sonuc.hata;
  kutuCevap.className = sonuc.metin ? "sonuc" : "sonuc uyari";
};

/**
 * Kelime kartı.
 *
 * Düz metin olarak tek blokta yazılıyordu ve okunmuyordu: karşılık,
 * örnekler ve kök aynı gri yığının içinde kayboluyordu. Android'deki
 * kartın bölümleri burada da ayrı ayrı duruyor.
 */
function kartiCiz(k) {
  const kart = yap("div", "", "kelime-kart");

  if (k.karsilik) kart.append(yap("p", k.karsilik, "karsilik"));
  if (k.tanim) kart.append(yap("p", k.tanim, "tanim"));

  if (k.ornekler?.length) {
    const liste = yap("ol", "", "ornekler");
    k.ornekler.forEach(o => {
      const madde = document.createElement("li");
      // Örnek iki satır: İngilizce cümle ve altında Türkçesi. Model
      // bazen düz metin döndürüyor, o zaman tek satır kalıyor.
      if (typeof o === "string") {
        madde.textContent = o;
        madde.dir = "auto";
      } else {
        // Alan "asil": örnek cümle kelimenin kendi dilinde. Eski kartlarda
        // adı "en" idi, onlar da okunsun.
        const asil = yap("div", o.asil || o.en || "");
        asil.dir = "auto";
        madde.append(asil);
        if (o.tr) madde.append(yap("div", o.tr, "kucuk sonuk"));
      }
      liste.append(madde);
    });
    kart.append(liste);
  }

  const bolum = (etiket, deger) => {
    if (!deger || (Array.isArray(deger) && !deger.length)) return;
    const satir = yap("div", "", "kart-bolum");
    satir.append(yap("span", etiket, "etiket"));
    // Arapça bir kelimenin eş anlamlıları sağdan sola yazılmalı; yönü
    // metnin ilk harfinden tarayıcı buluyor.
    const deger_ = yap("span", Array.isArray(deger) ? deger.join(" · ") : deger);
    deger_.dir = "auto";
    satir.append(deger_);
    kart.append(satir);
  };

  bolum("Kök", k.kok);
  bolum("Aile", k.aile);
  bolum("Eş", k.esanlam);
  bolum("Karşıt", k.karsit);
  bolum("Birlikte", k.birliktelik);
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
  ekran.append(calis);

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
  const gosterilen = eserler.find(e => e.id === seciliEser).kelimeler;
  gosterilen.forEach(k => {
    const satir = yap("button", "", "kelime");
    const nokta = document.createElement("span");
    nokta.style.cssText = `width:10px;height:10px;border-radius:50%;flex:none;background:var(--${
      { YELLOW: "sari", BLUE: "mavi", GREEN: "yesil", RED: "kirmizi" }[k.kalem]})`;
    const kelime = yap("b", k.kelime);
    kelime.dir = "auto";
    const ceviri = yap("span", gecerliCeviri(k));
    satirlar.set(k.anahtar, ceviri);
    satir.append(nokta, kelime, ceviri);
    satir.onclick = () => kelimeKutusu(k);
    liste.append(satir);
  });
  ekran.append(liste);

  eksikleriDoldur(gosterilen.filter(k => !gecerliCeviri(k)), satirlar);
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
        const sonuc = k.kelime.trim().includes(" ")
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
  if (secili && !secili.kart) kutuAyrinti.onclick();
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
    } catch { /* bozuk kayıt varsayılanı bozmasın */ }
    // Eski kayıtlarda dört zemin vardı; kalkanlar açığa düşsün.
    if (!ZEMINLER[okumaTercihi.zemin]) okumaTercihi.zemin = "kagit";
    await git("kitaplik");
  } catch (e) {
    hataGoster(e);
  }
  if ("serviceWorker" in navigator) {
    navigator.serviceWorker.register("sw.js").catch(() => {});
  }
})();
