import { useState } from "react";
import { tarihYaz, useDil } from "../dil";
import { AdimListesi, AdimSeridi, Bos, Kart, Rozet, TurRozeti } from "../bilesenler/Parcalar";
import { ADIMLAR, ADIM_ADI, ADIM_SAHIBI, FORMAT_ADI, ROL_ADI, TURLER, TUR_ADI, muhabirAdi, paketIlerle, saatYaz, useVeri, type Tur } from "../veri";

/** Bütün haber paketleri; türe göre süzülüyor, yeniden eskiye. */
export default function Paketler() {
  const [dil, t] = useDil();
  const v = useVeri();
  const [tur, setTur] = useState<Tur | "">("");
  const paketler = v.paketler
    .filter((p) => !tur || p.tur === tur)
    .sort((a, b) => (a.guncelleme < b.guncelleme ? 1 : -1));
  return (
    <>
      <div className="sayfa-basi">
        <h1>{t("paketler")}</h1>
      </div>
      <Kart>
        <div className="sekmeler">
          <button className={tur === "" ? "acik" : ""} onClick={() => setTur("")}>{t("hepsi")}</button>
          {TURLER.map((x) => (
            <button key={x} className={tur === x ? "acik" : ""} onClick={() => setTur(x)}>{t(TUR_ADI[x])}</button>
          ))}
        </div>
        {paketler.length === 0 ? (
          <Bos metin={t("bos")} />
        ) : (
          <div className="tablo-sar">
            <table className="tablo">
              <thead>
                <tr>
                  <th>{t("tarih")}</th>
                  <th>{t("baslik")}</th>
                  <th>{t("muhabir")}</th>
                  <th>{t("tur")}</th>
                  <th>{t("adim")}</th>
                  <th>{t("klipKodu")}</th>
                </tr>
              </thead>
              <tbody>
                {paketler.map((p) => (
                  <tr key={p.id}>
                    <td className="sonuk">{tarihYaz(p.planTarihi, dil)}</td>
                    <td>
                      <a href={`#/paketler/${p.id}`}>{p.baslik}</a>
                      {p.gecikti && <> <Rozet ton="kotu">{t("gecikti")}</Rozet></>}
                    </td>
                    <td><a href={`#/muhabirler/${p.muhabirId}`}>{muhabirAdi(v, p.muhabirId)}</a></td>
                    <td><TurRozeti tur={p.tur} /></td>
                    <td><AdimSeridi paket={p} /> <span className="sonuk">{t(ADIM_ADI[p.adim])}</span></td>
                    <td className="sonuk">{p.klipKodu ?? "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Kart>
    </>
  );
}

/** Paket detayı: on adımın açık hâli, metin, video, klip kodu. */
export function PaketDetay({ id }: { id: string }) {
  const [dil, t] = useDil();
  const v = useVeri();
  const p = v.paketler.find((x) => x.id === id);
  if (!p) return <Bos metin={t("bos")} />;
  const sira = ADIMLAR.indexOf(p.adim);
  const sonraki = ADIMLAR[sira + 1];
  return (
    <>
      <div className="sayfa-basi">
        <a className="geri" href="#/paketler">← {t("geri")}</a>
        <h1>{p.baslik}</h1>
        <TurRozeti tur={p.tur} />
        {p.gecikti && <Rozet ton="kotu">{t("gecikti")}</Rozet>}
      </div>
      <Kart baslik={t("adim")}>
        <AdimListesi adim={p.adim} gecikti={p.gecikti} />
        <p className="aciklama" style={{ marginTop: "var(--ara-3)" }}>
          {t("adimSahibi")}: <b>{t(ROL_ADI[ADIM_SAHIBI[p.adim]])}</b> · {t("sonGuncelleme")}: {saatYaz(p.guncelleme)}
        </p>
        <div className="dugmeler">
          {sonraki ? (
            <button className="dugme" onClick={() => paketIlerle(p.id)}>
              {t("ileriTasi")} → {t(ADIM_ADI[sonraki])}
            </button>
          ) : (
            <Rozet ton="iyi">{t("tamamlandi")}</Rozet>
          )}
        </div>
      </Kart>
      <Kart>
        <div className="alanlar">
          <div className="alan"><b>{t("muhabir")}</b><a href={`#/muhabirler/${p.muhabirId}`}>{muhabirAdi(v, p.muhabirId)}</a></div>
          <div className="alan"><b>{t("tarih")}</b>{tarihYaz(p.planTarihi, dil)}</div>
          <div className="alan"><b>{t("yayinSaati")}</b>{p.yayinSaati ?? "—"}</div>
          <div className="alan"><b>{t("format")}</b>{t(FORMAT_ADI[p.format])}</div>
          <div className="alan"><b>{t("klipKodu")}</b>{p.klipKodu ?? "—"}</div>
          <div className="alan"><b>{t("videoBaglanti")}</b>{p.videoBaglanti ?? "—"}</div>
        </div>
      </Kart>
      <Kart baslik={t("metin")}>
        {p.metin ? <p>{p.metin}</p> : <Bos metin={t("metinYok")} />}
      </Kart>
    </>
  );
}
