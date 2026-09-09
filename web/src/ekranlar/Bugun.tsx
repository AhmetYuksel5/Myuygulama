import { useState } from "react";
import { Cep, Halka, Kart as KartSimgesi, Tik } from "../bilesenler/Simgeler";

/*
 * Bugün ekranı.
 *
 * Bu aşamada veri katmanı yok; gösterilen içerik örnek ve ekranın
 * tepesinde öyle olduğu yazıyor. Boş bir iskelet göstermek tasarımın
 * çalışıp çalışmadığını anlatmıyor — kart aralıkları, renk dengesi ve
 * satır yükseklikleri ancak dolu ekranda görünüyor.
 *
 * Kutucuklara basmak çalışıyor ama hiçbir yere yazılmıyor: sayfayı
 * yenileyince örnekler başa dönüyor.
 */

type Satir = { ad: string; sag?: string; bitti: boolean };

const ILK_ALISKANLIKLAR: Satir[] = [
  { ad: "Sabah yürüyüşü", sag: "12 gün", bitti: true },
  { ad: "Kitap, 40 dakika", sag: "5 gün", bitti: true },
  { ad: "Su", sag: "3 gün", bitti: false },
];

const ILK_GOREVLER: Satir[] = [
  { ad: "Bina faturalarını kes", sag: "bugün", bitti: false },
  { ad: "Ehliyet randevusu", bitti: false },
  { ad: "Kitaplığı düzenle", bitti: true },
];

const KELIMELER = [
  { kelime: "resilient", karsilik: "dirençli", kalem: "var(--kalem-kirmizi)" },
  { kelime: "on the house", karsilik: "müessesenin ikramı", kalem: "var(--kalem-mavi)" },
  { kelime: "hack off", karsilik: "koparmak", kalem: "var(--kalem-sari)" },
];

export default function Bugun() {
  const [aliskanliklar, setAliskanliklar] = useState(ILK_ALISKANLIKLAR);
  const [gorevler, setGorevler] = useState(ILK_GOREVLER);

  const cevir = (
    liste: Satir[],
    yaz: (y: Satir[]) => void,
    sira: number,
  ) => yaz(liste.map((s, i) => (i === sira ? { ...s, bitti: !s.bitti } : s)));

  const yapilanAliskanlik = aliskanliklar.filter((a) => a.bitti).length;
  const acikGorev = gorevler.filter((g) => !g.bitti).length;

  return (
    <>
      <header className="tepe">
        <div className="gun">{bugunYazisi()}</div>
        <h1>Bugün</h1>
      </header>

      <Kart
        baslik="Alışkanlıklar"
        renk="var(--aliskanlik)"
        sis="var(--aliskanlik-sis)"
        sayac={`${yapilanAliskanlik}/${aliskanliklar.length}`}
        simge={<Halka />}
      >
        {aliskanliklar.map((s, i) => (
          <SatirDugmesi
            key={s.ad}
            satir={s}
            onBas={() => cevir(aliskanliklar, setAliskanliklar, i)}
            renk="var(--aliskanlik)"
          />
        ))}
      </Kart>

      <Kart
        baslik="Görevler"
        renk="var(--gorev)"
        sis="var(--gorev-sis)"
        sayac={`${acikGorev} açık`}
        simge={<Tik />}
      >
        {gorevler.map((s, i) => (
          <SatirDugmesi
            key={s.ad}
            satir={s}
            onBas={() => cevir(gorevler, setGorevler, i)}
            renk="var(--gorev)"
          />
        ))}
      </Kart>

      <Kart
        baslik="Kelimeler"
        renk="var(--kelime)"
        sis="var(--kelime-sis)"
        sayac="7 bekliyor"
        simge={<KartSimgesi />}
      >
        <div className="cizgi" aria-hidden>
          <i style={{ width: "34%" }} />
        </div>
        <div style={{ height: "var(--ara-1)" }} />
        {KELIMELER.map((k) => (
          <div className="satir" key={k.kelime}>
            <span className="nokta" style={{ background: k.kalem }} />
            <span className="metin">{k.kelime}</span>
            <span className="karsilik">{k.karsilik}</span>
          </div>
        ))}
      </Kart>

      <Kart
        baslik="Pocket"
        renk="var(--pocket)"
        sis="var(--pocket-sis)"
        sayac="4"
        simge={<Cep />}
      >
        <div className="satir">
          <span className="metin">Sessizliğin ekonomisi</span>
          <span className="sag">8 dk</span>
        </div>
      </Kart>

      <p className="ornek-not">
        Buradaki içerik örnek. Veri katmanı ve telefonla senkron bir sonraki
        aşamada bağlanacak.
      </p>
    </>
  );
}

/* --- Parçalar ---------------------------------------------------- */

function Kart({
  baslik,
  renk,
  sis,
  sayac,
  simge,
  children,
}: {
  baslik: string;
  renk: string;
  sis: string;
  sayac: string;
  simge: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <section className="kart">
      <h2 className="kart-baslik">
        <span className="rozet" style={{ background: sis, color: renk }}>
          {simge}
        </span>
        {baslik}
        <span className="sayac" style={{ background: sis, color: renk }}>
          {sayac}
        </span>
      </h2>
      {children}
    </section>
  );
}

function SatirDugmesi({
  satir,
  onBas,
  renk,
}: {
  satir: Satir;
  onBas: () => void;
  renk: string;
}) {
  return (
    <button
      type="button"
      className={satir.bitti ? "satir bitti" : "satir"}
      onClick={onBas}
      aria-pressed={satir.bitti}
    >
      <span
        className={satir.bitti ? "kutucuk dolu" : "kutucuk"}
        style={satir.bitti ? { background: renk, borderColor: renk } : undefined}
      >
        <Tik />
      </span>
      <span className="metin">{satir.ad}</span>
      {satir.sag && <span className="sag">{satir.sag}</span>}
    </button>
  );
}

/** "9 Eylül, Salı" — cihazın kendi diline değil, uygulamanınkine göre. */
function bugunYazisi(): string {
  return new Intl.DateTimeFormat("tr-TR", {
    day: "numeric",
    month: "long",
    weekday: "long",
  }).format(new Date());
}
