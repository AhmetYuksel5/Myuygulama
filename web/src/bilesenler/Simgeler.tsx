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
 * Renk `currentColor`: simge bulunduğu yerin rengini alıyor, her
 * kullanım yerinde ayrıca renk verilmesi gerekmiyor.
 */

type Ozellik = { className?: string };

const kutu = {
  viewBox: "0 0 24 24",
  fill: "currentColor",
  xmlns: "http://www.w3.org/2000/svg",
  "aria-hidden": true,
} as const;

/** Bugün: güneş. Öteki sekmeler nesne, bu bir zaman; ayrı dursun. */
export const Gunes = ({ className }: Ozellik) => (
  <svg {...kutu} className={className}>
    <circle cx="12" cy="12" r="4.4" />
    <path d="M12 2.6a1 1 0 0 1 1 1v1.6a1 1 0 1 1-2 0V3.6a1 1 0 0 1 1-1Zm0 15.2a1 1 0 0 1 1 1v1.6a1 1 0 1 1-2 0v-1.6a1 1 0 0 1 1-1ZM2.6 12a1 1 0 0 1 1-1h1.6a1 1 0 1 1 0 2H3.6a1 1 0 0 1-1-1Zm15.2 0a1 1 0 0 1 1-1h1.6a1 1 0 1 1 0 2h-1.6a1 1 0 0 1-1-1ZM5.3 5.3a1 1 0 0 1 1.4 0l1.2 1.2a1 1 0 0 1-1.4 1.4L5.3 6.7a1 1 0 0 1 0-1.4Zm10.8 10.8a1 1 0 0 1 1.4 0l1.2 1.2a1 1 0 0 1-1.4 1.4l-1.2-1.2a1 1 0 0 1 0-1.4Zm2.6-10.8a1 1 0 0 1 0 1.4l-1.2 1.2a1 1 0 0 1-1.4-1.4l1.2-1.2a1 1 0 0 1 1.4 0ZM7.9 16.1a1 1 0 0 1 0 1.4l-1.2 1.2a1 1 0 0 1-1.4-1.4l1.2-1.2a1 1 0 0 1 1.4 0Z" />
  </svg>
);

/** Kitaplık: açık kitap. */
export const Kitap = ({ className }: Ozellik) => (
  <svg {...kutu} className={className}>
    <path d="M11.1 6.9C9.4 5.5 7 4.9 3.6 5.2v12.3c3.4-.3 5.8.3 7.5 1.6Z" />
    <path d="M12.9 6.9c1.7-1.4 4.1-2 7.5-1.7v12.3c-3.4-.3-5.8.3-7.5 1.6Z" />
  </svg>
);

/** Kelimeler: satırlı bir kart, destedeki kelime kartı. */
export const Kart = ({ className }: Ozellik) => (
  <svg {...kutu} className={className}>
    <path
      fillRule="evenodd"
      d="M6.2 5h11.6A3.2 3.2 0 0 1 21 8.2v7.6a3.2 3.2 0 0 1-3.2 3.2H6.2A3.2 3.2 0 0 1 3 15.8V8.2A3.2 3.2 0 0 1 6.2 5Zm1.3 4.4a1 1 0 0 0 0 2h7a1 1 0 0 0 0-2Zm0 3.6a1 1 0 0 0 0 2H12a1 1 0 0 0 0-2Z"
    />
  </svg>
);

/** Pocket: cep ve içindeki ok; kaydedilmiş yazının kendi işareti. */
export const Cep = ({ className }: Ozellik) => (
  <svg {...kutu} className={className}>
    <path
      fillRule="evenodd"
      d="M6.1 4h11.8a1.6 1.6 0 0 1 1.6 1.6V11a7.5 7.5 0 0 1-15 0V5.6A1.6 1.6 0 0 1 6.1 4Zm2.5 5.5L7.2 10.9l4.8 4.8 4.8-4.8-1.4-1.4L12 12.9Z"
    />
  </svg>
);

/** Daha: üç nokta. */
export const UcNokta = ({ className }: Ozellik) => (
  <svg {...kutu} className={className}>
    <circle cx="5.2" cy="12" r="2.1" />
    <circle cx="12" cy="12" r="2.1" />
    <circle cx="18.8" cy="12" r="2.1" />
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
