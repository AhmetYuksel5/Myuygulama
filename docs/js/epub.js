/*
 * EPUB ayrıştırıcı.
 *
 * EPUB, içinde XHTML dosyaları bulunan bir ZIP. Hazır bir kitaplık
 * kullanmıyoruz: ZIP'in merkezî dizinini okumak yüz satır, açmayı ise
 * tarayıcının kendi DecompressionStream'i yapıyor. Böylece uygulamanın
 * hiç dış bağımlılığı yok — internetsiz de, yıllar sonra da çalışır.
 *
 * Android'deki ayrıştırıcıyla aynı işi yapıyor ve aynı biçimi üretiyor:
 * bölümler ve paragraflar.
 */

/** ZIP'i okur: ad -> baytlar. */
export async function zipAc(tampon) {
  const veri = new DataView(tampon);
  const bayt = new Uint8Array(tampon);

  // Merkezî dizinin sonu ("end of central directory") dosyanın sonunda,
  // değişken uzunlukta bir yorumdan önce. Sondan geriye doğru arıyoruz.
  let son = -1;
  for (let i = veri.byteLength - 22; i >= 0 && i > veri.byteLength - 65558; i--) {
    if (veri.getUint32(i, true) === 0x06054b50) { son = i; break; }
  }
  if (son < 0) throw new Error("ZIP değil");

  const adet = veri.getUint16(son + 10, true);
  let yer = veri.getUint32(son + 16, true);

  const girisler = new Map();
  for (let i = 0; i < adet; i++) {
    if (veri.getUint32(yer, true) !== 0x02014b50) break;
    const yontem = veri.getUint16(yer + 10, true);
    // Merkezî dizinde 20. bayt sıkıştırılmış, 24. bayt açılmış boyut.
    // Yanlışlıkla açılmış boyut okunuyordu: dosyadan gereğinden fazla
    // bayt kesiliyor ve çözücü "sıkıştırılmış verinin ardında çöp var"
    // diyerek her kitabı reddediyordu.
    const boyut = veri.getUint32(yer + 20, true);
    const adUzunluk = veri.getUint16(yer + 28, true);
    const ekUzunluk = veri.getUint16(yer + 30, true);
    const yorumUzunluk = veri.getUint16(yer + 32, true);
    const yerelYer = veri.getUint32(yer + 42, true);
    const ad = new TextDecoder().decode(bayt.subarray(yer + 46, yer + 46 + adUzunluk));

    girisler.set(ad, { yontem, boyut, yerelYer });
    yer += 46 + adUzunluk + ekUzunluk + yorumUzunluk;
  }

  return {
    adlar: () => [...girisler.keys()],
    async oku(ad) {
      const giris = girisler.get(ad);
      if (!giris) return null;
      // Yerel başlığın uzunluğu merkezî dizinde yazmıyor; oradan okunuyor.
      const adUzunluk = veri.getUint16(giris.yerelYer + 26, true);
      const ekUzunluk = veri.getUint16(giris.yerelYer + 28, true);
      const bas = giris.yerelYer + 30 + adUzunluk + ekUzunluk;
      const ham = bayt.subarray(bas, bas + giris.boyut);
      if (giris.yontem === 0) return ham;
      const akis = new Blob([ham]).stream()
        .pipeThrough(new DecompressionStream("deflate-raw"));
      return new Uint8Array(await new Response(akis).arrayBuffer());
    },
  };
}

const metneCevir = bayt => new TextDecoder("utf-8").decode(bayt);

/**
 * Yerel adı verilen bütün etiketler; ön ek olsun olmasın.
 *
 * EPUB dosyalarında aynı etiket kimi kitapta "title", kiminde "dc:title"
 * diye geçiyor. Ad alanına göre aramak ikisini de yakalıyor, isim
 * eşleştirmek yalnız birini.
 */
function etiket(belge, ad) {
  const bulunan = [];
  // Ağaç elle dolaşılıyor. getElementsByTagName("*") ad alanlı belgelerde
  // her ayrıştırıcıda aynı davranmıyor; çocuk listesinde böyle bir
  // belirsizlik yok.
  (function dolas(oge) {
    for (const cocuk of oge.children || []) {
      // Ön ek her durumda atılıyor: bazı ayrıştırıcılar localName'e
      // "dc:title" gibi ön ekli adı koyuyor ve o zaman ad tutmuyor.
      const yerel = (cocuk.localName || cocuk.nodeName || "").split(":").pop();
      if (yerel === ad) bulunan.push(cocuk);
      dolas(cocuk);
    }
  })(belge.documentElement || belge);
  return bulunan;
}

/**
 * EPUB'ı bölümlere ayırır.
 *
 * Okuma sırası kitabın kendi "spine" listesinden geliyor; dosyaları
 * alfabetik sıraya koymak bölümleri karıştırıyor.
 */
export async function epubOku(tampon) {
  const zip = await zipAc(tampon);

  const kap = await zip.oku("META-INF/container.xml");
  if (!kap) throw new Error("EPUB değil");
  const kapBelge = new DOMParser().parseFromString(metneCevir(kap), "application/xml");
  // XML'de querySelector'a güvenilmiyor: bu belgelerin ad alanı var ve
  // "rootfile" gibi düz bir seçici motora göre tutuyor ya da tutmuyor.
  // getElementsByTagName yerel ada bakıyor, her yerde aynı çalışıyor.
  const opfYolu = etiket(kapBelge, "rootfile")[0]?.getAttribute("full-path");
  if (!opfYolu) throw new Error("EPUB okunamadı");

  const opf = new DOMParser().parseFromString(
    metneCevir(await zip.oku(opfYolu)), "application/xml",
  );
  const kok = opfYolu.includes("/") ? opfYolu.slice(0, opfYolu.lastIndexOf("/") + 1) : "";

  const ad = etiket(opf, "title")[0]?.textContent?.trim() || "Adsız kitap";
  const yazar = etiket(opf, "creator")[0]?.textContent?.trim() || "";

  const kaynaklar = new Map();
  etiket(opf, "item").forEach(x => {
    kaynaklar.set(x.getAttribute("id"), {
      yol: kok + (x.getAttribute("href") || ""),
      tur: x.getAttribute("media-type") || "",
    });
  });

  const bolumler = [];
  const sira = etiket(opf, "itemref");
  for (const oge of sira) {
    const kaynak = kaynaklar.get(oge.getAttribute("idref"));
    if (!kaynak || !kaynak.tur.includes("html")) continue;
    const ham = await zip.oku(kaynak.yol);
    if (!ham) continue;
    const bolum = bolumCikar(metneCevir(ham), kaynak.yol);
    if (bolum.paragraflar.length) bolumler.push(bolum);
  }

  return { ad, yazar, bolumler };
}

/**
 * Bir XHTML dosyasından başlık ve paragrafları çıkarır.
 *
 * Biçimlendirmenin tamamı atılıyor: okuyucu yazı tipini ve puntoyu kendi
 * veriyor, kitabın kendi stilini taşımak okumayı iyileştirmiyor.
 */
function bolumCikar(metin, dosyaYolu) {
  const belge = new DOMParser().parseFromString(metin, "text/html");
  belge.querySelectorAll("script, style").forEach(x => x.remove());

  const baslik = belge.querySelector("h1, h2, h3")?.textContent?.trim() || "";
  const paragraflar = [];

  // Resimler de metnin akışında: seçici sıraya göre dolaşıldığı için
  // görsel, kitapta hangi paragrafların arasındaysa orada duruyor.
  const secici = "p, h1, h2, h3, h4, li, blockquote, img, image";
  belge.querySelectorAll(secici).forEach(oge => {
    const ad = oge.tagName.toLowerCase();
    if (ad === "img" || ad === "image") {
      // SVG içine konmuş görseller "image" etiketiyle ve xlink ile
      // geliyor; kapaklar çoğu kitapta böyle.
      const kaynak = oge.getAttribute("src")
        || oge.getAttribute("xlink:href")
        || oge.getAttribute("href");
      if (kaynak) paragraflar.push({ resim: yoluCoz(dosyaYolu, kaynak) });
      return;
    }
    const yazi = oge.textContent.replace(/\s+/g, " ").trim();
    if (!yazi) return;
    paragraflar.push({ yazi, baslik: /^h[1-4]$/.test(ad) });
  });

  return { baslik, paragraflar };
}

/**
 * Görselin ZIP içindeki gerçek yolu.
 *
 * Bölüm dosyası "OEBPS/text/b1.xhtml" ise ve görsel "../gorsel/a.jpg"
 * diyorsa aranacak giriş "OEBPS/gorsel/a.jpg". Yolu çözmeden ZIP'te
 * karşılığı bulunamıyor.
 */
function yoluCoz(dosyaYolu, kaynak) {
  if (/^[a-z]+:/i.test(kaynak)) return kaynak;
  const parcalar = dosyaYolu.split("/").slice(0, -1);
  for (const parca of decodeURIComponent(kaynak).split("/")) {
    if (parca === "." || parca === "") continue;
    if (parca === "..") parcalar.pop();
    else parcalar.push(parca);
  }
  return parcalar.join("/");
}
