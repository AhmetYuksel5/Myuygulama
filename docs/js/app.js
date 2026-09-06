/*
 * Merkez — tarayıcı sürümü.
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
import { epubOku } from "./epub.js";
import { cevir, bilgi, kart } from "./yapayzeka.js";
import { yeniKelime, bekleyenler, karar, bugun } from "./tekrar.js";

const ekran = document.getElementById("ekran");
const cubuk = document.getElementById("cubuk");
const KALEMLER = ["YELLOW", "BLUE", "GREEN", "RED"];

let ayarlar = { anahtar: "", model: "gpt-4o-mini" };

// --- Yönlendirme -----------------------------------------------------

// Anahtarlar alt çubuktaki data-git değerleriyle birebir aynı olmak
// zorunda; "ayarlarEkrani" yazılıydı ve o sekme hiç açılmıyordu.
const sayfalar = { kitaplik, deste, ayarlar: ayarlarEkrani };
let acikKitap = null;

async function git(ad) {
  acikKitap = null;
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
    accept: ".epub,application/epub+zip",
  });
  secici.hidden = true;
  yukle.onclick = () => secici.click();
  secici.onchange = () => secici.files[0] && kitapEkle(secici.files[0]);

  ekran.append(baslik, yukle, secici);

  if (!kitaplar.length) {
    ekran.append(yap("div",
      "Raf boş. Bir EPUB yükle: okurken kelimelere dokunup işaretlediklerin destene düşer.",
      "bos"));
    return;
  }

  const liste = yap("div", "");
  liste.style.marginTop = "16px";
  kitaplar.sort((a, b) => (b.acildi || 0) - (a.acildi || 0)).forEach(k => {
    const kart = yap("button", "", "kart");
    const sirt = yap("div", (k.ad[0] || "?").toUpperCase(), "sirt");
    sirt.style.background = renkTohumu(k.ad);
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
    kart.onclick = () => oku(k.id);
    // Uzun basmak silme soruyor; kitaplıkta ayrı bir menü kurmaya değmez.
    let zaman;
    kart.addEventListener("touchstart", () => {
      zaman = setTimeout(() => kitapSil(k), 600);
    }, { passive: true });
    ["touchend", "touchmove", "touchcancel"].forEach(o =>
      kart.addEventListener(o, () => clearTimeout(zaman), { passive: true }));
    liste.append(kart);
  });
  ekran.append(liste);
}

async function kitapSil(kitap) {
  if (!confirm(`"${kitap.ad}" silinsin mi? İşaretlediğin kelimeler kalır.`)) return;
  await depo.kitapSil(kitap.id);
  await depo.dosyaSil(kitap.id);
  git("kitaplik");
}

async function kitapEkle(dosya) {
  const bekle = yap("p", "Kitap okunuyor…", "sonuk");
  ekran.append(bekle);
  try {
    const tampon = await dosya.arrayBuffer();
    const kitap = await epubOku(tampon);
    const id = `k${Date.now()}`;
    await depo.dosyaYaz(id, tampon);
    await depo.kitapYaz({
      id,
      ad: kitap.ad,
      yazar: kitap.yazar,
      bolumler: kitap.bolumler,
      bolum: 0,
      paragraf: 0,
      acildi: Date.now(),
    });
    await kaliciIste();
    git("kitaplik");
  } catch (e) {
    bekle.textContent = "Bu dosya okunamadı. EPUB olduğundan emin misin?";
    bekle.className = "uyari";
  }
}

const ilerleme = k => {
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

  const bolum = kitap.bolumler[kitap.bolum] || kitap.bolumler[0];

  const ust = yap("div", "", "okuma-cubuk");
  const geri = yap("button", "‹ Kitaplık", "cizgili");
  geri.onclick = () => git("kitaplik");
  ust.append(geri, yap("div",
    `${kitap.ad} · ${(kitap.bolum || 0) + 1}/${kitap.bolumler.length}`, "baslik"));
  ekran.append(ust);

  const govde = yap("div", "");
  govde.id = "okuma";
  const isaretler = await isaretHaritasi(kitap.id);
  bolum.paragraflar.forEach(p => {
    const oge = document.createElement(p.baslik ? "h3" : "p");
    oge.innerHTML = kelimele(p.yazi, isaretler);
    govde.append(oge);
  });
  ekran.append(govde);

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

  window.scrollTo(0, 0);
  govde.addEventListener("click", kelimeyeDokun);
}

async function bolumeGit(kitap, yon) {
  kitap.bolum = Math.max(0, Math.min(kitap.bolumler.length - 1, (kitap.bolum || 0) + yon));
  await depo.kitapYaz(kitap);
  oku(kitap.id);
}

/**
 * Paragrafı kelimelere böler.
 *
 * Her kelime ayrı bir eleman: dokunulanı bulmak için metin içinde konum
 * hesaplamak gerekmiyor ve işaretli olanları boyamak tek sınıf ekleme
 * meselesi oluyor.
 */
function kelimele(yazi, isaretler) {
  return yazi.split(/(\s+)/).map(parca => {
    if (!parca.trim()) return parca;
    const sade = parca.replace(/^[^\p{L}\p{N}]+|[^\p{L}\p{N}]+$/gu, "");
    if (!sade) return kacir(parca);
    const kalem = isaretler.get(sade.toLowerCase());
    const govde = `<span class="k">${kacir(sade)}</span>`;
    const boyali = kalem ? `<mark class="${kalem}">${govde}</mark>` : govde;
    const [on, arka] = parca.split(sade);
    return kacir(on || "") + boyali + kacir(arka || "");
  }).join("");
}

const kacir = m => m.replace(/[&<>]/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;" }[c]));

async function isaretHaritasi(kitapId) {
  const hepsi = await depo.kelimeler();
  const harita = new Map();
  hepsi.filter(k => k.kitap === kitapId).forEach(k => harita.set(k.kelime.toLowerCase(), k.kalem));
  return harita;
}

// --- Seçim kutusu ----------------------------------------------------

const perde = document.getElementById("perde");
const kutuSecim = document.getElementById("kutu-secim");
const kutuCeviri = document.getElementById("kutu-ceviri");
const kutuKalemler = document.getElementById("kutu-kalemler");
const kutuNot = document.getElementById("kutu-not");
const kutuBilgi = document.getElementById("kutu-bilgi");
const kutuAyrinti = document.getElementById("kutu-ayrinti");

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

  const kelime = oge.textContent;
  const paragraf = oge.closest("p, h3")?.textContent || "";
  secili = { kelime, baglam: pencere(paragraf, kelime) };

  kutuSecim.textContent = kelime;
  kutuNot.textContent = "";
  kutuCeviri.textContent = "Anlamına bakılıyor…";
  kutuCeviri.className = "sonuc sonuk";
  perde.hidden = false;

  const varOlan = (await depo.kelimeler())
    .find(k => k.kelime.toLowerCase() === kelime.toLowerCase());
  kalemleriCiz(varOlan?.kalem);

  const sonuc = await cevir(ayarlar, kelime, secili.baglam, acikKitap.ad);
  if (!secili) return;
  kutuCeviri.textContent = sonuc.metin || sonuc.hata;
  kutuCeviri.className = sonuc.metin ? "sonuc" : "sonuc uyari";
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
  if (!secili || !acikKitap) return;
  const anahtar = secili.kelime.toLowerCase();
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
      kitap: acikKitap.id,
      eser: acikKitap.ad,
      ceviri: kutuCeviri.classList.contains("uyari") ? "" : kutuCeviri.textContent,
    }));
  }
  kutuyuKapat();
  oku(acikKitap.id);
}

kutuBilgi.onclick = async () => {
  if (!secili) return;
  kutuBilgi.disabled = true;
  kutuBilgi.textContent = "Bakılıyor…";
  const sonuc = await bilgi(ayarlar, secili.kelime, secili.baglam, acikKitap?.ad || "");
  kutuBilgi.disabled = false;
  kutuBilgi.textContent = "Bilgi al";
  if (!secili) return;
  kutuNot.textContent = sonuc.metin || sonuc.hata;
  kutuNot.className = sonuc.metin ? "sonuc" : "sonuc uyari";
};

kutuAyrinti.onclick = async () => {
  if (!secili) return;
  kutuAyrinti.disabled = true;
  kutuAyrinti.textContent = "Getiriliyor…";
  const sonuc = await kart(ayarlar, secili.kelime, secili.baglam, acikKitap?.ad || "");
  kutuAyrinti.disabled = false;
  kutuAyrinti.textContent = "Ayrıntı";
  if (!secili) return;
  kutuNot.className = "sonuc";
  kutuNot.textContent = sonuc.kart ? kartMetni(sonuc.kart) : sonuc.hata;
  if (!sonuc.kart) kutuNot.className = "sonuc uyari";
};

function kartMetni(k) {
  const satirlar = [];
  if (k.karsilik) satirlar.push(k.karsilik);
  if (k.tanim) satirlar.push(k.tanim);
  (k.ornekler || []).forEach((o, i) => satirlar.push(`${i + 1}. ${o}`));
  if (k.kok) satirlar.push(`Kök: ${k.kok}`);
  if (k.aile?.length) satirlar.push(`Aile: ${k.aile.join(" · ")}`);
  if (k.esanlam?.length) satirlar.push(`Eş: ${k.esanlam.join(" · ")}`);
  if (k.karsit?.length) satirlar.push(`Karşıt: ${k.karsit.join(" · ")}`);
  if (k.birliktelik?.length) satirlar.push(`Birlikte: ${k.birliktelik.join(" · ")}`);
  return satirlar.join("\n");
}

// --- Deste -----------------------------------------------------------

async function deste() {
  const kelimeler = await depo.kelimeler();
  ekran.append(yap("h1", "Deste"));

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

  ekran.append(yap("h2", `Bütün kelimeler (${kelimeler.length})`));
  const liste = yap("div", "");
  kelimeler.sort((a, b) => (b.eklendi || 0) - (a.eklendi || 0)).forEach(k => {
    const satir = yap("button", "", "kelime");
    const nokta = document.createElement("span");
    nokta.style.cssText = `width:10px;height:10px;border-radius:50%;flex:none;background:var(--${
      { YELLOW: "sari", BLUE: "mavi", GREEN: "yesil", RED: "kirmizi" }[k.kalem]})`;
    satir.append(nokta, yap("b", k.kelime), yap("span", k.ceviri || ""));
    satir.onclick = () => kelimeKutusu(k);
    liste.append(satir);
  });
  ekran.append(liste);
}

async function kelimeKutusu(k) {
  const metin = [k.kelime, k.ceviri, "", k.baglam, "", k.eser].filter(Boolean).join("\n");
  if (confirm(`${metin}\n\nBu kelimeyi desteden silmek için Tamam'a bas.`)) {
    await depo.kelimeSil(k.anahtar);
    git("deste");
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
      const don = yap("button", "Desteye dön", "dolu");
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
      if (kelime.ceviri) arka.append(yap("p", kelime.ceviri));
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
    bag.download = `merkez-yedek-${new Date().toISOString().slice(0, 10)}.json`;
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
    await git("kitaplik");
  } catch (e) {
    hataGoster(e);
  }
  if ("serviceWorker" in navigator) {
    navigator.serviceWorker.register("sw.js").catch(() => {});
  }
})();
