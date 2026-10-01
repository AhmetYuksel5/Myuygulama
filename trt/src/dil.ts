import { useSyncExternalStore } from "react";

/**
 * Dil tablosu.
 *
 * Arayüz üç dilde olacak: Türkçe, Arapça, İngilizce. Şimdilik yalnız
 * Türkçe dolu; boş bırakılan dil Türkçeye düşüyor ki eksik bir çeviri
 * ekranı kırmasın. Her metin tek anahtar altında üç dili yan yana
 * taşıyor: çeviri yapılırken dosyada dolaşmak değil, satırı tamamlamak
 * yetiyor.
 */
export const DILLER = ["tr", "ar", "en"] as const;
export type Dil = (typeof DILLER)[number];

interface Metin {
  tr: string;
  ar?: string;
  en?: string;
}

const M = {
  uygulama: { tr: "TRT Arabi · Planlama" },
  ara: { tr: "Ara…" },
  bildirimler: { tr: "Bildirimler" },
  rol: { tr: "Rol" },
  dil: { tr: "Dil" },

  // Roller
  rolPlanlama: { tr: "Planlama" },
  rolMuhabir: { tr: "Muhabir" },
  rolNewsdesk: { tr: "Newsdesk" },
  rolOutput: { tr: "Output" },
  rolDil: { tr: "Dil denetmeni" },
  rolMedia: { tr: "Media Manager" },
  rolProgram: { tr: "Program birimi" },
  rolYonetim: { tr: "Yönetim" },

  // Üst başlıklar
  nextday: { tr: "Next Day planı" },
  haftalik: { tr: "Haftalık plan" },
  aylik: { tr: "Aylık plan" },
  ozel: { tr: "Özel yayın planı" },
  yurtdisi: { tr: "Yurt dışı görevlendirme" },
  olustur: { tr: "+ oluştur" },

  // Sol menü
  anasayfa: { tr: "Ana sayfa" },
  muhabirler: { tr: "Muhabirler" },
  paketler: { tr: "Haber paketleri" },
  oneriler: { tr: "Öneriler" },
  toplantilar: { tr: "Toplantılar" },
  ekipler: { tr: "Ekipler ve kaynaklar" },
  programBirimi: { tr: "Program birimi" },
  feature: { tr: "Feature / ekonomi" },
  arsiv: { tr: "Arşiv" },
  raporlar: { tr: "Raporlar" },
  ayarlar: { tr: "Ayarlar" },

  // Ana sayfa
  bugun: { tr: "Bugün" },
  yarininPlani: { tr: "Yarının planı" },
  haber: { tr: "haber" },
  tamamlanan: { tr: "tamamlanan" },
  bekleyenOneri: { tr: "Bekleyen öneri" },
  aksamToplantisi: { tr: "Akşam haber toplantısı" },
  gundemHazir: { tr: "gündem hazır" },
  geciken: { tr: "Geciken" },
  haftaninYeri: { tr: "Haftanın yeri" },
  muhabirlerBugun: { tr: "Muhabirler yarın" },
  sonHareketler: { tr: "Son hareketler" },
  hizliIslem: { tr: "Hızlı işlem" },
  oneriGonder: { tr: "Öneri gönder" },
  paketYukle: { tr: "Paket yükle" },
  gorevAta: { tr: "Görev ata" },
  seninSiran: { tr: "Senin sıran" },
  seninSiranBos: { tr: "Bu rolde bekleyen iş yok." },
  planYok: { tr: "Yarın için plan açılmamış." },
  planAc: { tr: "Planı aç" },

  // Hafta döngüsü
  hOneri: { tr: "öneri toplama" },
  hPlan: { tr: "plan hazırlama" },
  hToplanti: { tr: "haftalık toplantı" },
  hGeri: { tr: "geri dönüş" },

  // Paket adımları (Next Day akışının 3-10. adımları)
  aPlanda: { tr: "Planda" },
  aNewsdesk: { tr: "Newsdesk devraldı" },
  aMetin: { tr: "Metin yazılıyor" },
  aKontrol: { tr: "Newsdesk / Output kontrolü" },
  aDil: { tr: "Dil denetimi" },
  aScript: { tr: "Son script muhabirde" },
  aVideo: { tr: "Video üretimi" },
  aMedia: { tr: "Media Manager" },
  aInews: { tr: "iNews montaj" },
  aYayin: { tr: "Sisteme yüklendi" },

  // Öneri
  tHaber: { tr: "Haber / güncel" },
  tFeature: { tr: "Feature / stok" },
  tEkonomi: { tr: "Ekonomi" },
  tProgram: { tr: "Program" },
  tDiger: { tr: "Diğer dosya" },
  dBekliyor: { tr: "Bekliyor" },
  dKabul: { tr: "Kabul" },
  dRet: { tr: "Ret" },
  kabulEt: { tr: "Kabul et" },
  reddet: { tr: "Reddet" },
  yeniOneri: { tr: "Yeni öneri" },
  baslik: { tr: "Başlık" },
  aciklama: { tr: "Açıklama" },
  tur: { tr: "Tür" },
  gonder: { tr: "Gönder" },
  muhabir: { tr: "Muhabir" },
  tarih: { tr: "Tarih" },
  durum: { tr: "Durum" },
  adim: { tr: "Adım" },
  bekleyenler: { tr: "Bekleyenler" },
  kararVerilenler: { tr: "Karar verilenler" },
  oneriKabulBilgi: { tr: "Kabul edilen öneri yarının planına paket olarak düşer; muhabire bildirim gider." },

  // Paket
  format: { tr: "Format" },
  fPaket: { tr: "Paket" },
  fCanli: { tr: "Canlı bağlantı" },
  fVt: { tr: "VT" },
  fStudyo: { tr: "Stüdyo" },
  yayinSaati: { tr: "Yayın saati" },
  klipKodu: { tr: "Klip kodu" },
  videoBaglanti: { tr: "Video bağlantısı" },
  metin: { tr: "Metin" },
  metinYok: { tr: "Metin henüz yazılmadı." },
  ileriTasi: { tr: "Bir adım ileri taşı" },
  tamamlandi: { tr: "Tamamlandı" },
  sonrakiAdim: { tr: "Sonraki adım" },
  gecikti: { tr: "gecikti" },
  sonGuncelleme: { tr: "Son güncelleme" },
  adimSahibi: { tr: "Bu adımın sahibi" },

  // Muhabir
  iletisim: { tr: "İletişim" },
  uzmanlik: { tr: "Uzmanlık" },
  diller: { tr: "Diller" },
  konum: { tr: "Konum" },
  bugunkuGorev: { tr: "Yarınki görev" },
  gorevYok: { tr: "Görev yok" },
  paketleri: { tr: "Paketleri" },
  oneriGecmisi: { tr: "Öneri geçmişi" },
  kabulOrani: { tr: "Kabul oranı" },

  // Plan
  planlar: { tr: "Planlar" },
  planOlustur: { tr: "Yeni Next Day planı" },
  pTaslak: { tr: "Taslak" },
  pToplantida: { tr: "Toplantıda" },
  pOnayli: { tr: "Onaylı" },
  pDevralindi: { tr: "Newsdesk devraldı" },
  toplantiyaGotur: { tr: "Toplantıya götür" },
  planiOnayla: { tr: "Planı onayla" },
  devral: { tr: "Newsdesk olarak devral" },
  planAciklama: {
    tr: "Her satır bir haber paketi. Noktalar Next Day akışının on adımı; dolu olan geçildi, renkli olan şu an bekleyen.",
  },
  toplantiAciklama: {
    tr: "Akşam toplantısı: bekleyen öneriler burada karara bağlanır, kabul edilenler plana düşer.",
  },
  planYokGun: { tr: "Bu gün için plan yok." },
  onerilerBitti: { tr: "Bekleyen öneri kalmadı." },

  // Genel
  hepsi: { tr: "Hepsi" },
  bos: { tr: "Kayıt yok." },
  geri: { tr: "Geri" },
  yakinda: { tr: "Yakında" },
  yakindaAciklama: {
    tr: "Bu sayfa planda var, henüz yapılmadı. Ana sayfa ve Next Day akışı oturunca sıra buraya gelecek.",
  },
  ornekVeri: { tr: "Örnek veri" },
  ornekVeriAciklama: { tr: "Ekranlardaki kayıtlar örnektir ve bu tarayıcıda saklanır." },
  sifirla: { tr: "Örnek veriye dön" },
  gunKisa: { tr: "Paz,Pzt,Sal,Çar,Per,Cum,Cmt" },
  aylar: { tr: "Ocak,Şubat,Mart,Nisan,Mayıs,Haziran,Temmuz,Ağustos,Eylül,Ekim,Kasım,Aralık" },
} satisfies Record<string, Metin>;

export type Anahtar = keyof typeof M;

const SAKLA = "trt-planlama-dil";

let dil: Dil = (() => {
  try {
    const k = localStorage.getItem(SAKLA);
    return DILLER.includes(k as Dil) ? (k as Dil) : "tr";
  } catch {
    return "tr";
  }
})();

const dinleyiciler = new Set<() => void>();

const uygula = () => {
  document.documentElement.lang = dil;
  // Arapça sağdan sola; tarayıcı yerleşimi kendisi çeviriyor.
  document.documentElement.dir = dil === "ar" ? "rtl" : "ltr";
};
uygula();

export const dilAyarla = (yeni: Dil) => {
  dil = yeni;
  try {
    localStorage.setItem(SAKLA, yeni);
  } catch {
    /* özel pencerede saklanamaz, sorun değil */
  }
  uygula();
  dinleyiciler.forEach((d) => d());
};

export const metin = (k: Anahtar, d: Dil = dil): string => {
  const m: Metin = M[k];
  return m[d] ?? m.tr;
};

export function useDil(): [Dil, (k: Anahtar) => string] {
  const simdiki = useSyncExternalStore(
    (d) => {
      dinleyiciler.add(d);
      return () => dinleyiciler.delete(d);
    },
    () => dil,
  );
  return [simdiki, (k) => metin(k, simdiki)];
}

/** ISO tarihi (2026-10-02) okunur hâle çevirir: "2 Ekim Cum". */
export const tarihYaz = (iso: string, d: Dil = dil): string => {
  const t = new Date(iso + "T12:00:00");
  const gunler = metin("gunKisa", d).split(",");
  const aylar = metin("aylar", d).split(",");
  return `${t.getDate()} ${aylar[t.getMonth()]} ${gunler[t.getDay()]}`;
};
