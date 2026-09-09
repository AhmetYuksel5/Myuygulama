/*
 * Simgeler.
 *
 * Çizilmiş şekiller, yazı tipi karakteri değil: karakterler yazı tipine
 * göre biçim değiştiriyor, hizaları kayıyor ve küçük boyda ne olduğu
 * anlaşılmıyordu.
 *
 * Hepsi 24×24 kutuda ve dolgulu. Seçilen simge dili "kapsül": açık olan
 * sekmenin simgesi renkli bir hapın içinde duruyor, o yüzden simgenin
 * kendisi konturlu değil dolu — hapın içinde kontur kayboluyor.
 *
 * Ortak yaşam alanı 24 birimlik kutuda yaklaşık 18×15. Genişlikler
 * bilerek birebir aynı değil: dolu bir dikdörtgen, aynı kutuyu kaplayan
 * açık bir kitaptan daha büyük görünüyor, o yüzden kart kitaptan biraz
 * dar. Ölçü kutu değil mürekkep miktarı — üç ağır simgenin alanı
 * birbirinden %12'den fazla ayrılmıyor.
 *
 * Renk `currentColor`: simge bulunduğu yerin rengini alıyor.
 */

type Ozellik = { className?: string };

const kutu = {
  viewBox: "0 0 24 24",
  fill: "currentColor",
  xmlns: "http://www.w3.org/2000/svg",
  "aria-hidden": true,
} as const;

/**
 * Bugün: güneş.
 *
 * Öteki sekmeler birer nesne, bu bir zaman; biçimi de onlardan ayrı.
 * Işınlar hapın içinde durabilsin diye 16,6 birimlik dar bir kutuya
 * sığdırıldı — yuvarlak bir şekil aynı kutuda köşeliden küçük göründüğü
 * için ötekilerden bir tık geniş.
 */
export const Gunes = ({ className }: Ozellik) => (
  <svg {...kutu} className={className}>
    <circle cx="12" cy="12" r="4.2" />
    {[0, 45, 90, 135, 180, 225, 270, 315].map((aci) => (
      <rect
        key={aci}
        x="17.9"
        y="11"
        width="2.4"
        height="2"
        rx="1"
        transform={`rotate(${aci} 12 12)`}
      />
    ))}
  </svg>
);

/** Kitaplık: açık kitap. Sırttaki boşluk bir çizgi kalınlığında. */
export const Kitap = ({ className }: Ozellik) => (
  <svg {...kutu} className={className}>
    <path d="M2.8 4.65C6.3 4.4 9.4 5 11.15 6.35V19.45C9.4 18.1 6.3 17.5 2.8 17.75Z" />
    <path d="M21.2 4.65C17.7 4.4 14.6 5 12.85 6.35V19.45C14.6 18.1 17.7 17.5 21.2 17.75Z" />
  </svg>
);

/** Kelimeler: satırlı bir kart, destedeki kelime kartı. */
export const Kart = ({ className }: Ozellik) => (
  <svg {...kutu} className={className}>
    <path
      fillRule="evenodd"
      d="M6.4 4.8H17.6A3.4 3.4 0 0 1 21 8.2v7.6a3.4 3.4 0 0 1-3.4 3.4H6.4A3.4 3.4 0 0 1 3 15.8V8.2A3.4 3.4 0 0 1 6.4 4.8ZM7.2 9.45h7.8a.85.85 0 0 1 0 1.7H7.2a.85.85 0 0 1 0-1.7ZM7.2 12.85h4.6a.85.85 0 0 1 0 1.7H7.2a.85.85 0 0 1 0-1.7Z"
    />
  </svg>
);

/** Pocket: cep ve içindeki ok; kaydedilmiş yazının kendi işareti. */
export const Cep = ({ className }: Ozellik) => (
  <svg {...kutu} className={className}>
    <path
      fillRule="evenodd"
      d="M6.2 4.7h11.6a2.5 2.5 0 0 1 2.5 2.5V11.4A8.3 8.3 0 0 1 3.7 11.4V7.2a2.5 2.5 0 0 1 2.5-2.5ZM9.5 10.1 12 12.6l2.5-2.5a.85.85 0 0 1 1.2 1.2L12.6 14.4a.85.85 0 0 1-1.2 0L8.3 11.3a.85.85 0 0 1 1.2-1.2Z"
    />
  </svg>
);

/**
 * Daha: üç nokta.
 *
 * Ötekilerin yanında hafif kalıyor ve bu geometriyle düzelmiyor;
 * üç noktalı bir işaretin doğasında var. Yapılabilecek kadarı yapıldı:
 * noktalar birbirine yaklaştırıldı, yarıçapları büyütüldü.
 */
export const UcNokta = ({ className }: Ozellik) => (
  <svg {...kutu} className={className}>
    <circle cx="6.2" cy="12" r="2" />
    <circle cx="12" cy="12" r="2" />
    <circle cx="17.8" cy="12" r="2" />
  </svg>
);

/** Alışkanlıklar rozetinde: halka. Doldurulmuş bir çember, ortası delik. */
export const Halka = ({ className }: Ozellik) => (
  <svg {...kutu} className={className}>
    <path
      fillRule="evenodd"
      d="M12 3.4a8.6 8.6 0 1 0 0 17.2 8.6 8.6 0 0 0 0-17.2Zm0 3.4a5.2 5.2 0 1 1 0 10.4 5.2 5.2 0 0 1 0-10.4Z"
    />
  </svg>
);

/** Kutucuğun içindeki onay; işaretlenmiş satırlarda görünüyor. */
export const Tik = ({ className }: Ozellik) => (
  <svg viewBox="0 0 24 24" fill="none" aria-hidden className={className}>
    <path
      d="m5 12.5 4.5 4.5L19 7"
      stroke="currentColor"
      strokeWidth="3"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
  </svg>
);
