/*
 * Tarayıcının kendi deposu (IndexedDB).
 *
 * Her şey burada duruyor: kitapların baytları, çıkarılmış metinleri,
 * işaretlenen kelimeler, tekrar programı ve ayarlar. Hiçbiri sunucuya
 * gitmiyor — telefonun dışına çıkan tek şey, kullanıcı "çevir" dediğinde
 * seçtiği metin oluyor.
 *
 * iOS'ta bunun bir kırılganlığı var: Safari uzun süre açılmayan sitelerin
 * verisini siliyor. Ana ekrana eklenmiş uygulamalar bu kuralın dışında,
 * ama yine de yedek alma yolu bırakıyoruz.
 */

const AD = "merkez";
const SURUM = 1;

let acik = null;

function ac() {
  if (acik) return acik;
  acik = new Promise((tamam, hata) => {
    const istek = indexedDB.open(AD, SURUM);
    istek.onupgradeneeded = () => {
      const db = istek.result;
      // Kitabın künyesi ile baytları ayrı duruyor: kitaplığı çizerken
      // otuz megabaytlık dosyayı belleğe almanın anlamı yok.
      if (!db.objectStoreNames.contains("kitap")) {
        db.createObjectStore("kitap", { keyPath: "id" });
      }
      if (!db.objectStoreNames.contains("dosya")) {
        db.createObjectStore("dosya", { keyPath: "id" });
      }
      if (!db.objectStoreNames.contains("kelime")) {
        db.createObjectStore("kelime", { keyPath: "anahtar" });
      }
      if (!db.objectStoreNames.contains("ayar")) {
        db.createObjectStore("ayar", { keyPath: "ad" });
      }
    };
    istek.onsuccess = () => tamam(istek.result);
    istek.onerror = () => hata(istek.error);
  });
  return acik;
}

function is(magaza, kip, islem) {
  return ac().then(db => new Promise((tamam, hata) => {
    const t = db.transaction(magaza, kip);
    const sonuc = islem(t.objectStore(magaza));
    // Kayıt bulunamadığında result undefined oluyor; "undefined ise
    // isteğin kendisini döndür" demek, çağırana bir istek nesnesi
    // veriyordu ve o nesne her koşulda doğru sayılıyordu.
    t.oncomplete = () => tamam(sonuc instanceof IDBRequest ? sonuc.result : sonuc);
    t.onerror = () => hata(t.error);
  }));
}

export const depo = {
  kitaplar: () => is("kitap", "readonly", m => m.getAll()),
  kitap: id => is("kitap", "readonly", m => m.get(id)),
  kitapYaz: k => is("kitap", "readwrite", m => m.put(k)),
  kitapSil: id => is("kitap", "readwrite", m => m.delete(id)),

  dosya: id => is("dosya", "readonly", m => m.get(id)),
  dosyaYaz: (id, veri) => is("dosya", "readwrite", m => m.put({ id, veri })),
  dosyaSil: id => is("dosya", "readwrite", m => m.delete(id)),

  kelimeler: () => is("kelime", "readonly", m => m.getAll()),
  kelimeYaz: k => is("kelime", "readwrite", m => m.put(k)),
  kelimeSil: anahtar => is("kelime", "readwrite", m => m.delete(anahtar)),

  async ayar(ad, varsayilan = "") {
    const satir = await is("ayar", "readonly", m => m.get(ad));
    return satir ? satir.deger : varsayilan;
  },
  ayarYaz: (ad, deger) => is("ayar", "readwrite", m => m.put({ ad, deger })),
};

/**
 * Verinin silinmemesini rica ediyoruz.
 *
 * Tarayıcı her zaman kabul etmiyor; reddederse de uygulama çalışıyor,
 * yalnız yedek almak daha önemli hâle geliyor.
 */
export async function kaliciIste() {
  if (!navigator.storage || !navigator.storage.persist) return false;
  if (await navigator.storage.persisted()) return true;
  return navigator.storage.persist();
}

/** Her şeyi tek bir nesneye çıkarır; yedek dosyası bundan yazılıyor. */
export async function disaAktar() {
  const kitaplar = await depo.kitaplar();
  const dosyalar = [];
  for (const k of kitaplar) {
    const d = await depo.dosya(k.id);
    if (!d) continue;
    dosyalar.push({ id: k.id, veri: bayttanMetne(d.veri) });
  }
  return {
    surum: 1,
    tarih: new Date().toISOString(),
    kitaplar,
    dosyalar,
    kelimeler: await depo.kelimeler(),
  };
}

export async function iceAktar(paket) {
  for (const k of paket.kitaplar || []) await depo.kitapYaz(k);
  for (const d of paket.dosyalar || []) await depo.dosyaYaz(d.id, metindenBayta(d.veri));
  for (const k of paket.kelimeler || []) await depo.kelimeYaz(k);
}

// Yedek dosyası JSON; baytlar base64 olarak taşınıyor.
function bayttanMetne(tampon) {
  const bayt = new Uint8Array(tampon);
  let metin = "";
  // Tek seferde geçirmek büyük dosyada yığını taşırıyor.
  for (let i = 0; i < bayt.length; i += 8192) {
    metin += String.fromCharCode.apply(null, bayt.subarray(i, i + 8192));
  }
  return btoa(metin);
}

function metindenBayta(metin) {
  const ham = atob(metin);
  const bayt = new Uint8Array(ham.length);
  for (let i = 0; i < ham.length; i++) bayt[i] = ham.charCodeAt(i);
  return bayt.buffer;
}
