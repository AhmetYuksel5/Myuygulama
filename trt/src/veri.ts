import { useSyncExternalStore } from "react";
import type { Anahtar } from "./dil";

/**
 * Veri katmanı.
 *
 * İlk sürümde kayıtlar tarayıcıda (localStorage) duruyor ve örnek veriyle
 * açılıyor. Amaç ekranları oturtmak; çok kullanıcılı sunucu katmanı
 * geldiğinde yalnız bu dosyanın yükle/kaydet kısmı değişecek, ekranlar
 * `useVeri` ve eylemleri aynı şekilde çağırmaya devam edecek.
 */

export const ROLLER = [
  "planlama",
  "muhabir",
  "newsdesk",
  "output",
  "dil",
  "media",
  "program",
  "yonetim",
] as const;
export type Rol = (typeof ROLLER)[number];

export const ROL_ADI: Record<Rol, Anahtar> = {
  planlama: "rolPlanlama",
  muhabir: "rolMuhabir",
  newsdesk: "rolNewsdesk",
  output: "rolOutput",
  dil: "rolDil",
  media: "rolMedia",
  program: "rolProgram",
  yonetim: "rolYonetim",
};

/*
 * Next Day akışının adımları. Belgedeki 3-10. adımlar: akşam toplantısında
 * plana giren haber, Newsdesk'in devralmasından iNews'e yüklenmesine
 * kadar bu sırayı izliyor. Metin kontrolü belgede tek kutu ama üç elden
 * geçiyor (Newsdesk/Output, dil denetmeni, son script); üçü ayrı adım,
 * çünkü her birinin sahibi ayrı ve "kimde bekliyor" sorusu bunu soruyor.
 */
export const ADIMLAR = [
  "planda",
  "newsdesk",
  "metin",
  "kontrol",
  "dil",
  "script",
  "video",
  "media",
  "inews",
  "yayin",
] as const;
export type Adim = (typeof ADIMLAR)[number];

export const ADIM_ADI: Record<Adim, Anahtar> = {
  planda: "aPlanda",
  newsdesk: "aNewsdesk",
  metin: "aMetin",
  kontrol: "aKontrol",
  dil: "aDil",
  script: "aScript",
  video: "aVideo",
  media: "aMedia",
  inews: "aInews",
  yayin: "aYayin",
};

/* Hangi adımı hangi rol ileri taşır. "Senin sıran" kutusu buna bakıyor. */
export const ADIM_SAHIBI: Record<Adim, Rol> = {
  planda: "planlama",
  newsdesk: "newsdesk",
  metin: "muhabir",
  kontrol: "output",
  dil: "dil",
  script: "muhabir",
  video: "muhabir",
  media: "media",
  inews: "newsdesk",
  yayin: "planlama",
};

export const TURLER = ["haber", "feature", "ekonomi", "program", "diger"] as const;
export type Tur = (typeof TURLER)[number];
export const TUR_ADI: Record<Tur, Anahtar> = {
  haber: "tHaber",
  feature: "tFeature",
  ekonomi: "tEkonomi",
  program: "tProgram",
  diger: "tDiger",
};

export const FORMATLAR = ["paket", "canli", "vt", "studyo"] as const;
export type Format = (typeof FORMATLAR)[number];
export const FORMAT_ADI: Record<Format, Anahtar> = {
  paket: "fPaket",
  canli: "fCanli",
  vt: "fVt",
  studyo: "fStudyo",
};

export type OneriDurum = "bekliyor" | "kabul" | "ret";
export type PlanDurum = "taslak" | "toplantida" | "onayli" | "devralindi";

export interface Muhabir {
  id: string;
  ad: string;
  konum: string;
  uzmanlik: string[];
  diller: string[];
  telefon: string;
  eposta: string;
}

export interface Oneri {
  id: string;
  muhabirId: string;
  baslik: string;
  aciklama: string;
  tur: Tur;
  tarih: string;
  durum: OneriDurum;
}

export interface Paket {
  id: string;
  baslik: string;
  muhabirId: string;
  tur: Tur;
  format: Format;
  planTarihi: string;
  yayinSaati?: string;
  adim: Adim;
  gecikti?: boolean;
  klipKodu?: string;
  videoBaglanti?: string;
  metin?: string;
  guncelleme: string;
}

export interface Plan {
  tarih: string;
  durum: PlanDurum;
  paketIds: string[];
}

export interface Hareket {
  id: string;
  zaman: string;
  metin: string;
}

export interface Durum {
  muhabirler: Muhabir[];
  oneriler: Oneri[];
  paketler: Paket[];
  planlar: Plan[];
  hareketler: Hareket[];
}

/* --- Tarih yardımcıları --- */

export const iso = (t: Date) => {
  const y = t.getFullYear();
  const a = String(t.getMonth() + 1).padStart(2, "0");
  const g = String(t.getDate()).padStart(2, "0");
  return `${y}-${a}-${g}`;
};
export const bugun = () => iso(new Date());
export const gunEkle = (tarih: string, n: number) => {
  const t = new Date(tarih + "T12:00:00");
  t.setDate(t.getDate() + n);
  return iso(t);
};
const saat = (h: number, m: number) => {
  const t = new Date();
  t.setHours(h, m, 0, 0);
  return t.toISOString();
};
export const saatYaz = (isoZaman: string) => {
  const t = new Date(isoZaman);
  return `${String(t.getHours()).padStart(2, "0")}:${String(t.getMinutes()).padStart(2, "0")}`;
};

/* --- Örnek veri. Tarihler bugüne göre hesaplanıyor ki gösterim eskimesin. --- */

const ORNEK = (): Durum => {
  const yarin = gunEkle(bugun(), 1);
  const dun = gunEkle(bugun(), -1);
  const muhabirler: Muhabir[] = [
    { id: "m1", ad: "Ahmed Kaya", konum: "Ankara", uzmanlik: ["Siyaset", "Dış politika"], diller: ["Arapça", "Türkçe"], telefon: "+90 532 000 00 01", eposta: "ahmed.kaya@trt.net.tr" },
    { id: "m2", ad: "Leyla Sayed", konum: "İstanbul stüdyo", uzmanlik: ["Ekonomi"], diller: ["Arapça", "İngilizce"], telefon: "+90 532 000 00 02", eposta: "leyla.sayed@trt.net.tr" },
    { id: "m3", ad: "Omar Haddad", konum: "Gazze", uzmanlik: ["Saha", "Canlı bağlantı"], diller: ["Arapça"], telefon: "+970 59 000 00 03", eposta: "omar.haddad@trt.net.tr" },
    { id: "m4", ad: "Rania Mansour", konum: "Kahire", uzmanlik: ["Kültür", "Feature"], diller: ["Arapça", "İngilizce"], telefon: "+20 10 000 00 04", eposta: "rania.mansour@trt.net.tr" },
    { id: "m5", ad: "Yusuf Demir", konum: "İstanbul", uzmanlik: ["Spor", "Toplum"], diller: ["Arapça", "Türkçe"], telefon: "+90 532 000 00 05", eposta: "yusuf.demir@trt.net.tr" },
    { id: "m6", ad: "Nour Al-Ali", konum: "Beyrut", uzmanlik: ["Siyaset", "Saha"], diller: ["Arapça", "Fransızca"], telefon: "+961 3 000 006", eposta: "nour.alali@trt.net.tr" },
  ];
  const paketler: Paket[] = [
    { id: "p1", baslik: "Dışişleri bakanının Körfez turu", muhabirId: "m1", tur: "haber", format: "paket", planTarihi: yarin, yayinSaati: "19:00", adim: "dil", guncelleme: saat(13, 40) },
    { id: "p2", baslik: "Merkez Bankası faiz kararı ve piyasalar", muhabirId: "m2", tur: "ekonomi", format: "studyo", planTarihi: yarin, yayinSaati: "14:00", adim: "kontrol", gecikti: true, guncelleme: saat(11, 5) },
    { id: "p3", baslik: "Gazze'de insani koridor: sahadan canlı", muhabirId: "m3", tur: "haber", format: "canli", planTarihi: yarin, yayinSaati: "19:00", adim: "newsdesk", guncelleme: saat(9, 30) },
    { id: "p4", baslik: "Kahire kitap fuarında Türk yayıncılar", muhabirId: "m4", tur: "feature", format: "paket", planTarihi: yarin, adim: "video", guncelleme: saat(12, 15) },
    { id: "p5", baslik: "Süper Lig derbisi öncesi taraftar nabzı", muhabirId: "m5", tur: "haber", format: "vt", planTarihi: yarin, yayinSaati: "21:00", adim: "metin", guncelleme: saat(10, 50) },
    { id: "p6", baslik: "Beyrut limanı davasında yeni duruşma", muhabirId: "m6", tur: "haber", format: "paket", planTarihi: yarin, yayinSaati: "12:00", adim: "media", klipKodu: "ARB-2610-0412", guncelleme: saat(14, 2) },
    { id: "p7", baslik: "Zeytin hasadı ve ihracat rakamları", muhabirId: "m2", tur: "ekonomi", format: "paket", planTarihi: yarin, adim: "planda", guncelleme: saat(8, 45) },
    { id: "p8", baslik: "Ankara'da Filistin konferansı", muhabirId: "m1", tur: "haber", format: "paket", planTarihi: dun, yayinSaati: "19:00", adim: "yayin", klipKodu: "ARB-2609-0388", guncelleme: saat(20, 10) },
    { id: "p9", baslik: "İstanbul'da Arap öğrenciler: kampüs hayatı", muhabirId: "m5", tur: "feature", format: "paket", planTarihi: dun, adim: "yayin", klipKodu: "ARB-2609-0390", guncelleme: saat(18, 30) },
  ];
  const oneriler: Oneri[] = [
    { id: "o1", muhabirId: "m3", baslik: "Refah sınır kapısında bekleyen yardım tırları", aciklama: "Sabah saatlerinde sınırdan canlı; BM yetkilisiyle kısa röportaj mümkün.", tur: "haber", tarih: bugun(), durum: "bekliyor" },
    { id: "o2", muhabirId: "m4", baslik: "Nil kıyısında geleneksel tekne yapımı", aciklama: "Stok feature; iki günlük çekim, zamana bağlı değil.", tur: "feature", tarih: bugun(), durum: "bekliyor" },
    { id: "o3", muhabirId: "m2", baslik: "Türkiye-Körfez ticaret hacmi: yıl sonu beklentisi", aciklama: "DEİK verisi yarın açıklanacak; stüdyo konuğu ayarlanabilir.", tur: "ekonomi", tarih: bugun(), durum: "bekliyor" },
    { id: "o4", muhabirId: "m1", baslik: "Meclis'te yeni yasama yılı: Arap dünyasını ilgilendiren başlıklar", aciklama: "Açılış konuşması sonrası paket.", tur: "haber", tarih: bugun(), durum: "bekliyor" },
    { id: "o5", muhabirId: "m6", baslik: "Lübnan'da elektrik krizi: mahalle jeneratörleri", aciklama: "Feature paketi, bir hafta içinde teslim.", tur: "feature", tarih: bugun(), durum: "bekliyor" },
    { id: "o6", muhabirId: "m5", baslik: "Arapça konuşan esnaf: Fatih'te bir gün", aciklama: "Program önerisi; 12 dakikalık bölüm.", tur: "program", tarih: bugun(), durum: "bekliyor" },
    { id: "o7", muhabirId: "m2", baslik: "Altın fiyatlarında rekor: küçük yatırımcı ne yapıyor", aciklama: "Kapalıçarşı'dan kısa röportajlar.", tur: "ekonomi", tarih: bugun(), durum: "bekliyor" },
    { id: "o8", muhabirId: "m1", baslik: "Dışişleri bakanının Körfez turu", aciklama: "", tur: "haber", tarih: dun, durum: "kabul" },
    { id: "o9", muhabirId: "m4", baslik: "Mısır'da Ramazan dizileri sektörü", aciklama: "", tur: "feature", tarih: dun, durum: "ret" },
    { id: "o10", muhabirId: "m6", baslik: "Beyrut limanı davasında yeni duruşma", aciklama: "", tur: "haber", tarih: dun, durum: "kabul" },
  ];
  const planlar: Plan[] = [
    { tarih: yarin, durum: "toplantida", paketIds: ["p1", "p2", "p3", "p4", "p5", "p6", "p7"] },
    { tarih: dun, durum: "devralindi", paketIds: ["p8", "p9"] },
  ];
  const hareketler: Hareket[] = [
    { id: "h1", zaman: saat(14, 2), metin: "Nour Al-Ali video bağlantısını Newsdesk'e iletti: Beyrut limanı davası" },
    { id: "h2", zaman: saat(13, 40), metin: "Dil denetimi başladı: Dışişleri bakanının Körfez turu" },
    { id: "h3", zaman: saat(12, 15), metin: "Rania Mansour son script'i aldı, çekime geçti: Kahire kitap fuarı" },
    { id: "h4", zaman: saat(11, 5), metin: "Output kontrolünde gecikme: Merkez Bankası faiz kararı" },
    { id: "h5", zaman: saat(9, 30), metin: "Newsdesk yarının planını devraldı: 7 haber" },
    { id: "h6", zaman: saat(8, 45), metin: "Planlama muhabirlere öneri çağrısı gönderdi" },
  ];
  return { muhabirler, oneriler, paketler, planlar, hareketler };
};

/* --- Saklama ve abonelik --- */

const SAKLA = "trt-planlama-v1";

const yukle = (): Durum => {
  try {
    const ham = localStorage.getItem(SAKLA);
    if (ham) return JSON.parse(ham) as Durum;
  } catch {
    /* bozuk kayıt: örnekten başla */
  }
  return ORNEK();
};

let durum: Durum = yukle();
const dinleyiciler = new Set<() => void>();

const kaydet = (yeni: Durum) => {
  durum = yeni;
  try {
    localStorage.setItem(SAKLA, JSON.stringify(yeni));
  } catch {
    /* saklanamazsa bellekte kalır */
  }
  dinleyiciler.forEach((d) => d());
};

export function useVeri(): Durum {
  return useSyncExternalStore(
    (d) => {
      dinleyiciler.add(d);
      return () => dinleyiciler.delete(d);
    },
    () => durum,
  );
}

const kimlik = (on: string) => `${on}${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`;

const hareketEkle = (d: Durum, metin: string): Durum => ({
  ...d,
  hareketler: [{ id: kimlik("h"), zaman: new Date().toISOString(), metin }, ...d.hareketler].slice(0, 50),
});

/* --- Eylemler --- */

export const sifirla = () => kaydet(ORNEK());

export const muhabirAdi = (d: Durum, id: string) => d.muhabirler.find((m) => m.id === id)?.ad ?? "?";

/**
 * Akşam toplantısı kararı. Kabul edilen öneri yarının planına paket olarak
 * girer; plan yoksa açılır. Belgede bu adımın sonunda muhabirlere e-posta
 * gidiyor; burada hareket akışına düşüyor, e-posta sunucu katmanıyla gelecek.
 */
export const oneriKarar = (id: string, karar: "kabul" | "ret") => {
  const oneri = durum.oneriler.find((o) => o.id === id);
  if (!oneri || oneri.durum !== "bekliyor") return;
  let d: Durum = {
    ...durum,
    oneriler: durum.oneriler.map((o) => (o.id === id ? { ...o, durum: karar } : o)),
  };
  if (karar === "kabul") {
    const yarin = gunEkle(bugun(), 1);
    const paket: Paket = {
      id: kimlik("p"),
      baslik: oneri.baslik,
      muhabirId: oneri.muhabirId,
      tur: oneri.tur,
      format: "paket",
      planTarihi: yarin,
      adim: "planda",
      guncelleme: new Date().toISOString(),
    };
    const plan = d.planlar.find((p) => p.tarih === yarin);
    d = {
      ...d,
      paketler: [paket, ...d.paketler],
      planlar: plan
        ? d.planlar.map((p) => (p.tarih === yarin ? { ...p, paketIds: [...p.paketIds, paket.id] } : p))
        : [{ tarih: yarin, durum: "taslak", paketIds: [paket.id] }, ...d.planlar],
    };
  }
  kaydet(
    hareketEkle(
      d,
      `${karar === "kabul" ? "Kabul" : "Ret"}: ${oneri.baslik} (${muhabirAdi(d, oneri.muhabirId)})`,
    ),
  );
};

export const oneriEkle = (girdi: { muhabirId: string; baslik: string; aciklama: string; tur: Tur }) => {
  const oneri: Oneri = { id: kimlik("o"), tarih: bugun(), durum: "bekliyor", ...girdi };
  kaydet(
    hareketEkle(
      { ...durum, oneriler: [oneri, ...durum.oneriler] },
      `Yeni öneri: ${oneri.baslik} (${muhabirAdi(durum, oneri.muhabirId)})`,
    ),
  );
};

export const paketIlerle = (id: string) => {
  const paket = durum.paketler.find((p) => p.id === id);
  if (!paket) return;
  const sira = ADIMLAR.indexOf(paket.adim);
  if (sira >= ADIMLAR.length - 1) return;
  const yeni = ADIMLAR[sira + 1];
  kaydet(
    hareketEkle(
      {
        ...durum,
        paketler: durum.paketler.map((p) =>
          p.id === id ? { ...p, adim: yeni, gecikti: false, guncelleme: new Date().toISOString() } : p,
        ),
      },
      `${paket.baslik}: ${yeni} adımına geçti`,
    ),
  );
};

export const planDurumAyarla = (tarih: string, yeni: PlanDurum) => {
  const plan = durum.planlar.find((p) => p.tarih === tarih);
  if (!plan) return;
  let paketler = durum.paketler;
  // Newsdesk devralınca plandaki her paket "Newsdesk devraldı" adımına geçer.
  if (yeni === "devralindi") {
    paketler = paketler.map((p) =>
      plan.paketIds.includes(p.id) && p.adim === "planda" ? { ...p, adim: "newsdesk", guncelleme: new Date().toISOString() } : p,
    );
  }
  kaydet(
    hareketEkle(
      { ...durum, paketler, planlar: durum.planlar.map((p) => (p.tarih === tarih ? { ...p, durum: yeni } : p)) },
      `Plan ${tarih}: ${yeni}`,
    ),
  );
};

export const planOlustur = (tarih: string) => {
  if (durum.planlar.some((p) => p.tarih === tarih)) return;
  kaydet(
    hareketEkle(
      { ...durum, planlar: [{ tarih, durum: "taslak", paketIds: [] }, ...durum.planlar] },
      `Yeni Next Day planı açıldı: ${tarih}`,
    ),
  );
};
