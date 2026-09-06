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
import { epubOku, zipAc } from "./epub.js";
import { pdfAc, sayfaCiz } from "./pdf.js";
import { cevir, bilgi, kart } from "./yapayzeka.js";
import { yeniKelime, bekleyenler, karar, bugun } from "./tekrar.js";

const ekran = document.getElementById("ekran");
const cubuk = document.getElementById("cubuk");
const KALEMLER = ["YELLOW", "BLUE", "GREEN", "RED"];

let ayarlar = { anahtar: "", model: "gpt-4o-mini" };

/**
 * Okuma tercihleri.
 *
 * Zemin renkleri Android'dekilerin aynısı. Gece zemininin metni bilerek
 * arayüzünkinden sönük: siyah üstüne beyaz uzun okumada yoruyor.
 */
const ZEMINLER = {
  kagit: { ad: "Kâğıt", zemin: "#faf7f0", yazi: "#22201c" },
  krem: { ad: "Krem", zemin: "#f3eada", yazi: "#2b2620" },
  gece: { ad: "Gece", zemin: "#14161a", yazi: "#c6c2bb" },
  murekkep: { ad: "Mürekkep", zemin: "#000000", yazi: "#b9b5ae" },
};

let okumaTercihi = { punto: 19, zemin: "kagit", kenar: 16 };

function tercihleriUygula() {
  const z = ZEMINLER[okumaTercihi.zemin] || ZEMINLER.kagit;
  const govde = document.getElementById("okuma");
  if (!govde) return;
  govde.style.fontSize = `${okumaTercihi.punto}px`;
  govde.style.padding = `0 ${okumaTercihi.kenar}px`;
  // Okuma zemini sayfanın tamamını kaplıyor: metnin çevresinde başka
  // renkte bir şerit kalması okumayı bozuyor.
  document.body.style.background = z.zemin;
  document.body.style.color = z.yazi;
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
  await depo.dosyaSil(`${kitap.id}-kapak`);
  git("kitaplik");
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

  const ust = yap("div", "", "okuma-cubuk");
  const geri = yap("button", "‹", "cizgili");
  geri.onclick = () => git("kitaplik");
  const gorunum = yap("button", "Aa", "cizgili");
  gorunum.onclick = gorunumKutusu;
  ust.append(geri, yap("div",
    `${kitap.ad} · ${(kitap.bolum || 0) + 1}/${kitap.bolumler.length}`, "baslik"), gorunum);
  ekran.append(ust);

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
    okumaTemizle = null;
  };
}

let okumaTemizle = null;

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
      kutuyuAc(secim, pencere(paragraf, secim));
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
function gorunumKutusu() {
  const eski = document.getElementById("gorunum-kutusu");
  if (eski) return eski.remove();

  const kutu = yap("div", "");
  kutu.id = "gorunum-kutusu";

  const kademe = (ad, deger, eksi, arti) => {
    const satir = yap("div", "", "olcu");
    satir.append(yap("span", ad));
    const az = yap("button", "−", "cizgili");
    const cok = yap("button", "+", "cizgili");
    const sayi = yap("b", String(deger));
    az.onclick = () => { eksi(); sayi.textContent = okumaTercihi[ad === "Punto" ? "punto" : "kenar"]; };
    cok.onclick = () => { arti(); sayi.textContent = okumaTercihi[ad === "Punto" ? "punto" : "kenar"]; };
    satir.append(az, sayi, cok);
    return satir;
  };

  const yaz = async () => {
    tercihleriUygula();
    await depo.ayarYaz("okuma", JSON.stringify(okumaTercihi));
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

  kutu.append(
    kademe("Punto", okumaTercihi.punto,
      () => { okumaTercihi.punto = Math.max(14, okumaTercihi.punto - 1); yaz(); },
      () => { okumaTercihi.punto = Math.min(30, okumaTercihi.punto + 1); yaz(); }),
    kademe("Kenar", okumaTercihi.kenar,
      () => { okumaTercihi.kenar = Math.max(0, okumaTercihi.kenar - 4); yaz(); },
      () => { okumaTercihi.kenar = Math.min(48, okumaTercihi.kenar + 4); yaz(); }),
    zeminler,
  );
  ekran.querySelector(".okuma-cubuk").after(kutu);
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

  const ust = yap("div", "", "okuma-cubuk");
  const geri = yap("button", "‹ Kitaplık", "cizgili");
  geri.onclick = () => git("kitaplik");
  const baslik = yap("div", kitap.ad, "baslik");
  ust.append(geri, baslik);
  ekran.append(ust);

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
    baslik.textContent = `${kitap.ad} · ${sayfa}/${belge.numPages}`;
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
    pdfDugmesi = null;
    pdfTemizle = null;
  };

  dugme.onclick = () => {
    const secim = (window.getSelection()?.toString() || "").replace(/\s+/g, " ").trim();
    if (!secim) return;
    kutuyuAc(secim, pencere(sayfaMetni, secim));
  };
}

let pdfDugmesi = null;
let pdfTemizle = null;

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
  // Uzun basıp bıraktıktan hemen sonra tarayıcı bir de tıklama
  // gönderiyor; kutu iki kez açılmasın.
  if (Date.now() - secimBitti < 500) return;
  const paragraf = oge.closest("p, h3")?.textContent || "";
  kutuyuAc(oge.textContent, pencere(paragraf, oge.textContent));
}

/** Seçim kutusunu açar ve çeviriyi ister. İki okuyucu da buradan geçiyor. */
async function kutuyuAc(kelime, baglam) {
  secili = { kelime, baglam };

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
  kutuNot.innerHTML = "";
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
  kutuNot.innerHTML = "";
  kutuNot.className = "sonuc";
  if (sonuc.kart) {
    kutuNot.append(kartiCiz(sonuc.kart));
  } else {
    kutuNot.className = "sonuc uyari";
    kutuNot.textContent = sonuc.hata;
  }
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
      } else {
        madde.append(yap("div", o.en || ""));
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
    satir.append(yap("span", Array.isArray(deger) ? deger.join(" · ") : deger));
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
    await git("kitaplik");
  } catch (e) {
    hataGoster(e);
  }
  if ("serviceWorker" in navigator) {
    navigator.serviceWorker.register("sw.js").catch(() => {});
  }
})();
