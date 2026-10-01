import { useState } from "react";
import { tarihYaz, useDil } from "../dil";
import { Bos, Kart, Rozet, TurRozeti } from "../bilesenler/Parcalar";
import { TURLER, TUR_ADI, muhabirAdi, oneriEkle, oneriKarar, useVeri, type Tur } from "../veri";

/**
 * Öneri havuzu. Belgede muhabirler önerilerini e-postayla iletiyor; burada
 * form aynı işi görüyor ve öneri doğrudan planlamanın kuyruğuna düşüyor.
 */
export default function Oneriler({ yeni }: { yeni?: boolean }) {
  const [dil, t] = useDil();
  const v = useVeri();
  const bekleyen = v.oneriler.filter((o) => o.durum === "bekliyor");
  const kararli = v.oneriler.filter((o) => o.durum !== "bekliyor");
  return (
    <>
      <div className="sayfa-basi">
        <h1>{t("oneriler")}</h1>
        {!yeni && <a className="dugme sag-uc" href="#/oneriler/yeni">{t("yeniOneri")}</a>}
      </div>
      {yeni && <YeniOneri />}
      <Kart baslik={`${t("bekleyenler")} · ${bekleyen.length}`} aciklama={t("oneriKabulBilgi")}>
        {bekleyen.length === 0 ? (
          <Bos metin={t("onerilerBitti")} />
        ) : (
          <ul className="liste">
            {bekleyen.map((o) => (
              <li key={o.id}>
                <span className="ad">
                  {o.baslik}
                  <small>
                    {muhabirAdi(v, o.muhabirId)} · {tarihYaz(o.tarih, dil)}{o.aciklama ? ` · ${o.aciklama}` : ""}
                  </small>
                </span>
                <TurRozeti tur={o.tur} />
                <button className="dugme dugme-iyi dugme-kucuk" onClick={() => oneriKarar(o.id, "kabul")}>{t("kabulEt")}</button>
                <button className="dugme dugme-kotu dugme-kucuk" onClick={() => oneriKarar(o.id, "ret")}>{t("reddet")}</button>
              </li>
            ))}
          </ul>
        )}
      </Kart>
      <Kart baslik={t("kararVerilenler")}>
        {kararli.length === 0 ? (
          <Bos metin={t("bos")} />
        ) : (
          <ul className="liste">
            {kararli.map((o) => (
              <li key={o.id}>
                <span className="ad">
                  {o.baslik}
                  <small>{muhabirAdi(v, o.muhabirId)} · {tarihYaz(o.tarih, dil)}</small>
                </span>
                <TurRozeti tur={o.tur} />
                <Rozet ton={o.durum === "kabul" ? "iyi" : "kotu"}>{t(o.durum === "kabul" ? "dKabul" : "dRet")}</Rozet>
              </li>
            ))}
          </ul>
        )}
      </Kart>
    </>
  );
}

function YeniOneri() {
  const [, t] = useDil();
  const v = useVeri();
  const [muhabirId, setMuhabir] = useState(v.muhabirler[0]?.id ?? "");
  const [baslik, setBaslik] = useState("");
  const [aciklama, setAciklama] = useState("");
  const [tur, setTur] = useState<Tur>("haber");
  return (
    <Kart baslik={t("yeniOneri")}>
      <form
        className="form"
        onSubmit={(e) => {
          e.preventDefault();
          if (!baslik.trim()) return;
          oneriEkle({ muhabirId, baslik: baslik.trim(), aciklama: aciklama.trim(), tur });
          location.hash = "#/oneriler";
        }}
      >
        <div className="satir">
          <label>
            {t("muhabir")}
            <select value={muhabirId} onChange={(e) => setMuhabir(e.target.value)}>
              {v.muhabirler.map((m) => (
                <option key={m.id} value={m.id}>{m.ad}</option>
              ))}
            </select>
          </label>
          <label>
            {t("tur")}
            <select value={tur} onChange={(e) => setTur(e.target.value as Tur)}>
              {TURLER.map((x) => (
                <option key={x} value={x}>{t(TUR_ADI[x])}</option>
              ))}
            </select>
          </label>
        </div>
        <label>
          {t("baslik")}
          <input value={baslik} onChange={(e) => setBaslik(e.target.value)} required />
        </label>
        <label>
          {t("aciklama")}
          <textarea value={aciklama} onChange={(e) => setAciklama(e.target.value)} />
        </label>
        <div className="dugmeler">
          <button className="dugme" type="submit">{t("gonder")}</button>
          <a className="dugme dugme-ikincil" href="#/oneriler">{t("geri")}</a>
        </div>
      </form>
    </Kart>
  );
}
