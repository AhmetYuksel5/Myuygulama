import type { ReactNode } from "react";
import { useDil } from "../dil";
import { ADIMLAR, ADIM_ADI, TUR_ADI, type Adim, type Paket, type Tur } from "../veri";

/** Beyaz kart; başlık küçük harfli etiket gibi, sağ ucuna bağlantı konabiliyor. */
export function Kart({ baslik, sagUc, children, aciklama }: { baslik?: string; sagUc?: ReactNode; aciklama?: string; children: ReactNode }) {
  return (
    <section className="kart">
      {baslik && (
        <h2>
          {baslik}
          {sagUc && <span className="sag-uc">{sagUc}</span>}
        </h2>
      )}
      {aciklama && <p className="aciklama">{aciklama}</p>}
      {children}
    </section>
  );
}

export function TurRozeti({ tur }: { tur: Tur }) {
  const [, t] = useDil();
  return <span className={`rozet rozet-${tur}`}>{t(TUR_ADI[tur])}</span>;
}

export function Rozet({ ton = "", children }: { ton?: "iyi" | "uyari" | "kotu" | "vurgu" | ""; children: ReactNode }) {
  return <span className={`rozet ${ton ? `rozet-${ton}` : ""}`}>{children}</span>;
}

/**
 * On noktalı adım şeridi. Dolu nokta geçildi, renkli nokta şu an bekleyen;
 * gecikmişse kırmızı, son adımdaysa yeşil. Üstüne gelince adımın adı çıkıyor.
 */
export function AdimSeridi({ paket }: { paket: Paket }) {
  const [, t] = useDil();
  const sira = ADIMLAR.indexOf(paket.adim);
  const bitti = sira === ADIMLAR.length - 1;
  return (
    <span className="adimlar" title={t(ADIM_ADI[paket.adim])}>
      {ADIMLAR.map((a, i) => {
        let sinif = "";
        if (i < sira) sinif = "gecti";
        else if (i === sira) sinif = bitti ? "bitti" : paket.gecikti ? "gecikti" : "simdi";
        return <i key={a} className={sinif} />;
      })}
    </span>
  );
}

/** Adımların açık yazılı hâli; paket detayında kullanılıyor. */
export function AdimListesi({ adim, gecikti }: { adim: Adim; gecikti?: boolean }) {
  const [, t] = useDil();
  const sira = ADIMLAR.indexOf(adim);
  return (
    <div className="adim-liste">
      {ADIMLAR.map((a, i) => {
        const sinif = i < sira ? "gecti" : i === sira ? (gecikti ? "gecikti" : "simdi") : "";
        return (
          <div key={a} className={sinif}>
            <b>{i + 1}</b>
            {t(ADIM_ADI[a])}
          </div>
        );
      })}
    </div>
  );
}

export function Bos({ metin }: { metin: string }) {
  return <p className="bos">{metin}</p>;
}
