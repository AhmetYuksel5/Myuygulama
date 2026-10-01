import { useState } from "react";
import { tarihYaz, useDil, type Anahtar } from "../dil";
import { AdimSeridi, Bos, Kart, Rozet, TurRozeti } from "../bilesenler/Parcalar";
import {
  ADIM_ADI,
  FORMAT_ADI,
  TUR_ADI,
  bugun,
  gunEkle,
  muhabirAdi,
  oneriKarar,
  paketIlerle,
  planDurumAyarla,
  planOlustur,
  useVeri,
  type PlanDurum,
} from "../veri";

const PLAN_DURUM: Record<PlanDurum, { ad: Anahtar; ton: "" | "uyari" | "iyi" | "vurgu" }> = {
  taslak: { ad: "pTaslak", ton: "" },
  toplantida: { ad: "pToplantida", ton: "uyari" },
  onayli: { ad: "pOnayli", ton: "iyi" },
  devralindi: { ad: "pDevralindi", ton: "vurgu" },
};

/** Next Day planlarının listesi ve yeni plan açma. */
export default function NextDay({ yeni }: { yeni?: boolean }) {
  const [dil, t] = useDil();
  const v = useVeri();
  const [tarih, setTarih] = useState(gunEkle(bugun(), 1));
  const planlar = [...v.planlar].sort((a, b) => (a.tarih < b.tarih ? 1 : -1));

  return (
    <>
      <div className="sayfa-basi">
        <h1>{t("nextday")}</h1>
      </div>
      {yeni && (
        <Kart baslik={t("planOlustur")}>
          <form
            className="form"
            onSubmit={(e) => {
              e.preventDefault();
              planOlustur(tarih);
              location.hash = `#/nextday/${tarih}`;
            }}
          >
            <div className="satir">
              <label>
                {t("tarih")}
                <input type="date" value={tarih} onChange={(e) => setTarih(e.target.value)} required />
              </label>
            </div>
            <div className="dugmeler">
              <button className="dugme" type="submit">{t("planOlustur")}</button>
            </div>
          </form>
        </Kart>
      )}
      <Kart baslik={t("planlar")} sagUc={!yeni && <a href="#/nextday/yeni">{t("olustur")}</a>}>
        {planlar.length === 0 ? (
          <Bos metin={t("bos")} />
        ) : (
          <ul className="liste">
            {planlar.map((p) => {
              const d = PLAN_DURUM[p.durum];
              return (
                <li key={p.tarih}>
                  <a className="ad" href={`#/nextday/${p.tarih}`}>
                    {tarihYaz(p.tarih, dil)}
                    <small>{p.paketIds.length} {t("haber")}</small>
                  </a>
                  <Rozet ton={d.ton}>{t(d.ad)}</Rozet>
                </li>
              );
            })}
          </ul>
        )}
      </Kart>
    </>
  );
}

/**
 * Bir günün planı: haber listesi ve akşam toplantısı.
 *
 * Belgedeki 2-4. adımlar bu ekranda: plan hazırlanır, toplantıda öneriler
 * karara bağlanır, onaylanan plan Newsdesk'e devredilir. Plan durumu
 * ilerledikçe düğmeler değişiyor; aynı ekran sabah Newsdesk'in de ekranı.
 */
export function NextDayDetay({ tarih }: { tarih: string }) {
  const [dil, t] = useDil();
  const v = useVeri();
  const plan = v.planlar.find((p) => p.tarih === tarih);
  if (!plan) {
    return (
      <>
        <div className="sayfa-basi">
          <a className="geri" href="#/nextday">← {t("geri")}</a>
          <h1>{tarihYaz(tarih, dil)}</h1>
        </div>
        <Kart>
          <Bos metin={t("planYokGun")} />
          <div className="dugmeler">
            <button className="dugme" onClick={() => planOlustur(tarih)}>{t("planOlustur")}</button>
          </div>
        </Kart>
      </>
    );
  }
  const paketler = v.paketler
    .filter((p) => plan.paketIds.includes(p.id))
    .sort((a, b) => (a.yayinSaati ?? "99") < (b.yayinSaati ?? "99") ? -1 : 1);
  const bekleyen = v.oneriler.filter((o) => o.durum === "bekliyor");
  const d = PLAN_DURUM[plan.durum];

  return (
    <>
      <div className="sayfa-basi">
        <a className="geri" href="#/nextday">← {t("geri")}</a>
        <h1>{t("nextday")} · {tarihYaz(tarih, dil)}</h1>
        <Rozet ton={d.ton}>{t(d.ad)}</Rozet>
        <div className="dugmeler sag-uc">
          {plan.durum === "taslak" && (
            <button className="dugme" onClick={() => planDurumAyarla(tarih, "toplantida")}>{t("toplantiyaGotur")}</button>
          )}
          {plan.durum === "toplantida" && (
            <button className="dugme" onClick={() => planDurumAyarla(tarih, "onayli")}>{t("planiOnayla")}</button>
          )}
          {plan.durum === "onayli" && (
            <button className="dugme" onClick={() => planDurumAyarla(tarih, "devralindi")}>{t("devral")}</button>
          )}
        </div>
      </div>

      <Kart baslik={`${paketler.length} ${t("haber")}`} aciklama={t("planAciklama")}>
        {paketler.length === 0 ? (
          <Bos metin={t("bos")} />
        ) : (
          <div className="tablo-sar">
            <table className="tablo">
              <thead>
                <tr>
                  <th>{t("yayinSaati")}</th>
                  <th>{t("baslik")}</th>
                  <th>{t("muhabir")}</th>
                  <th>{t("tur")}</th>
                  <th>{t("format")}</th>
                  <th>{t("adim")}</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {paketler.map((p) => (
                  <tr key={p.id}>
                    <td className="sonuk">{p.yayinSaati ?? "—"}</td>
                    <td>
                      <a href={`#/paketler/${p.id}`}>{p.baslik}</a>
                      {p.gecikti && <> <Rozet ton="kotu">{t("gecikti")}</Rozet></>}
                    </td>
                    <td><a href={`#/muhabirler/${p.muhabirId}`}>{muhabirAdi(v, p.muhabirId)}</a></td>
                    <td><TurRozeti tur={p.tur} /></td>
                    <td className="sonuk">{t(FORMAT_ADI[p.format])}</td>
                    <td>
                      <AdimSeridi paket={p} /> <span className="sonuk">{t(ADIM_ADI[p.adim])}</span>
                    </td>
                    <td>
                      {p.adim !== "yayin" && (
                        <button className="dugme dugme-ikincil dugme-kucuk" onClick={() => paketIlerle(p.id)}>→</button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Kart>

      {(plan.durum === "taslak" || plan.durum === "toplantida") && (
        <Kart baslik={`${t("aksamToplantisi")} · ${bekleyen.length} ${t("bekleyenOneri").toLowerCase()}`} aciklama={t("toplantiAciklama")}>
          {bekleyen.length === 0 ? (
            <Bos metin={t("onerilerBitti")} />
          ) : (
            <ul className="liste">
              {bekleyen.map((o) => (
                <li key={o.id}>
                  <span className="ad">
                    {o.baslik}
                    <small>
                      {muhabirAdi(v, o.muhabirId)} · {t(TUR_ADI[o.tur])}{o.aciklama ? ` · ${o.aciklama}` : ""}
                    </small>
                  </span>
                  <button className="dugme dugme-iyi dugme-kucuk" onClick={() => oneriKarar(o.id, "kabul")}>{t("kabulEt")}</button>
                  <button className="dugme dugme-kotu dugme-kucuk" onClick={() => oneriKarar(o.id, "ret")}>{t("reddet")}</button>
                </li>
              ))}
            </ul>
          )}
        </Kart>
      )}
    </>
  );
}
