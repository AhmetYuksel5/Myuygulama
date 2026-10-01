import { tarihYaz, useDil } from "../dil";
import { AdimSeridi, Bos, Kart, Rozet, TurRozeti } from "../bilesenler/Parcalar";
import { ADIM_ADI, bugun, gunEkle, useVeri } from "../veri";

/** Muhabir listesi: kim nerede, yarın ne yapıyor. */
export default function Muhabirler() {
  const [, t] = useDil();
  const v = useVeri();
  const yarin = gunEkle(bugun(), 1);
  return (
    <>
      <div className="sayfa-basi">
        <h1>{t("muhabirler")}</h1>
      </div>
      <Kart>
        <div className="tablo-sar">
          <table className="tablo">
            <thead>
              <tr>
                <th>{t("muhabir")}</th>
                <th>{t("konum")}</th>
                <th>{t("uzmanlik")}</th>
                <th>{t("bugunkuGorev")}</th>
                <th>{t("adim")}</th>
              </tr>
            </thead>
            <tbody>
              {v.muhabirler.map((m) => {
                const is = v.paketler.find((p) => p.muhabirId === m.id && p.planTarihi === yarin);
                return (
                  <tr key={m.id}>
                    <td><a href={`#/muhabirler/${m.id}`}>{m.ad}</a></td>
                    <td className="sonuk">{m.konum}</td>
                    <td className="sonuk">{m.uzmanlik.join(", ")}</td>
                    <td>{is ? <a href={`#/paketler/${is.id}`}>{is.baslik}</a> : <span className="sonuk">{t("gorevYok")}</span>}</td>
                    <td>{is && <AdimSeridi paket={is} />}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </Kart>
    </>
  );
}

/** Muhabir profili: iletişim, görev, paketler, öneri geçmişi ve kabul oranı. */
export function MuhabirDetay({ id }: { id: string }) {
  const [dil, t] = useDil();
  const v = useVeri();
  const m = v.muhabirler.find((x) => x.id === id);
  if (!m) return <Bos metin={t("bos")} />;
  const paketler = v.paketler.filter((p) => p.muhabirId === id).sort((a, b) => (a.planTarihi < b.planTarihi ? 1 : -1));
  const oneriler = v.oneriler.filter((o) => o.muhabirId === id);
  const kararli = oneriler.filter((o) => o.durum !== "bekliyor");
  const kabul = kararli.filter((o) => o.durum === "kabul").length;
  const oran = kararli.length ? Math.round((kabul / kararli.length) * 100) : null;

  return (
    <>
      <div className="sayfa-basi">
        <a className="geri" href="#/muhabirler">← {t("geri")}</a>
        <h1>{m.ad}</h1>
        <span className="rozet">{m.konum}</span>
      </div>
      <Kart>
        <div className="alanlar">
          <div className="alan"><b>{t("iletisim")}</b>{m.telefon}<br />{m.eposta}</div>
          <div className="alan"><b>{t("uzmanlik")}</b>{m.uzmanlik.join(", ")}</div>
          <div className="alan"><b>{t("diller")}</b>{m.diller.join(", ")}</div>
          <div className="alan"><b>{t("kabulOrani")}</b>{oran === null ? "—" : `%${oran}`} <span className="sonuk">({kabul}/{kararli.length})</span></div>
        </div>
      </Kart>
      <Kart baslik={t("paketleri")}>
        {paketler.length === 0 ? (
          <Bos metin={t("bos")} />
        ) : (
          <ul className="liste">
            {paketler.map((p) => (
              <li key={p.id}>
                <a className="ad" href={`#/paketler/${p.id}`}>
                  {p.baslik}
                  <small>{tarihYaz(p.planTarihi, dil)} · {t(ADIM_ADI[p.adim])}</small>
                </a>
                <TurRozeti tur={p.tur} />
                <AdimSeridi paket={p} />
              </li>
            ))}
          </ul>
        )}
      </Kart>
      <Kart baslik={t("oneriGecmisi")}>
        {oneriler.length === 0 ? (
          <Bos metin={t("bos")} />
        ) : (
          <ul className="liste">
            {oneriler.map((o) => (
              <li key={o.id}>
                <span className="ad">
                  {o.baslik}
                  <small>{tarihYaz(o.tarih, dil)}</small>
                </span>
                <TurRozeti tur={o.tur} />
                <Rozet ton={o.durum === "kabul" ? "iyi" : o.durum === "ret" ? "kotu" : "uyari"}>
                  {t(o.durum === "kabul" ? "dKabul" : o.durum === "ret" ? "dRet" : "dBekliyor")}
                </Rozet>
              </li>
            ))}
          </ul>
        )}
      </Kart>
    </>
  );
}
