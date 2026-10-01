import { tarihYaz, useDil, type Anahtar } from "../dil";
import { AdimSeridi, Bos, Kart, Rozet } from "../bilesenler/Parcalar";
import { ADIM_ADI, ADIM_SAHIBI, bugun, gunEkle, muhabirAdi, paketIlerle, useVeri, type Rol } from "../veri";

/**
 * Ana sayfa: "bugün ne var" sorusunun cevabı.
 *
 * Sayılar yarının planından, bekleyen önerilerden ve geciken paketlerden
 * çıkıyor; ayrı bir rapor tutulmuyor. Hafta şeridi haftalık akışın
 * (Cumartesi–Cuma) neresinde olduğumuzu gösteriyor.
 */
export default function AnaSayfa({ rol }: { rol: Rol }) {
  const [dil, t] = useDil();
  const v = useVeri();
  const yarin = gunEkle(bugun(), 1);
  const plan = v.planlar.find((p) => p.tarih === yarin);
  const planPaketleri = plan ? v.paketler.filter((p) => plan.paketIds.includes(p.id)) : [];
  const tamam = planPaketleri.filter((p) => p.adim === "yayin").length;
  const bekleyen = v.oneriler.filter((o) => o.durum === "bekliyor").length;
  const geciken = v.paketler.filter((p) => p.gecikti && p.adim !== "yayin");
  const siram = v.paketler.filter((p) => p.adim !== "yayin" && ADIM_SAHIBI[p.adim] === rol);

  return (
    <>
      <Kart baslik={t("bugun")}>
        <div className="sayilar">
          <a className="sayi" href={plan ? `#/nextday/${yarin}` : "#/nextday"}>
            <span>{t("yarininPlani")} · {tarihYaz(yarin, dil)}</span>
            <b>{planPaketleri.length}</b>
            <small>
              {plan ? `${t("haber")} · ${tamam} ${t("tamamlanan")}` : t("planYok")}
            </small>
          </a>
          <a className={`sayi ${bekleyen ? "uyari" : ""}`} href="#/oneriler">
            <span>{t("bekleyenOneri")}</span>
            <b>{bekleyen}</b>
            <small>{t("aksamToplantisi")} 17:00</small>
          </a>
          <a className={`sayi ${geciken.length ? "kotu" : "iyi"}`} href="#/paketler">
            <span>{t("geciken")}</span>
            <b>{geciken.length}</b>
            <small>{geciken[0] ? t(ADIM_ADI[geciken[0].adim]) : "—"}</small>
          </a>
        </div>
      </Kart>

      <Kart baslik={t("seninSiran")}>
        {siram.length === 0 ? (
          <Bos metin={t("seninSiranBos")} />
        ) : (
          <ul className="liste">
            {siram.map((p) => (
              <li key={p.id}>
                <a className="ad" href={`#/paketler/${p.id}`}>
                  {p.baslik}
                  <small>
                    {muhabirAdi(v, p.muhabirId)} · {t(ADIM_ADI[p.adim])}
                  </small>
                </a>
                {p.gecikti && <Rozet ton="kotu">{t("gecikti")}</Rozet>}
                <AdimSeridi paket={p} />
                <button className="dugme dugme-kucuk" onClick={() => paketIlerle(p.id)}>
                  {t("ileriTasi")}
                </button>
              </li>
            ))}
          </ul>
        )}
      </Kart>

      <HaftaSeridi />

      <Kart baslik={t("muhabirlerBugun")} sagUc={<a href="#/muhabirler">{t("hepsi")}</a>}>
        <ul className="liste">
          {v.muhabirler.map((m) => {
            const is = planPaketleri.find((p) => p.muhabirId === m.id);
            return (
              <li key={m.id}>
                <a className="ad" href={`#/muhabirler/${m.id}`}>
                  {m.ad}
                  <small>{m.konum}</small>
                </a>
                {is ? (
                  <>
                    <span className="not">
                      {is.yayinSaati ?? ""} {is.baslik}
                    </span>
                    <AdimSeridi paket={is} />
                  </>
                ) : (
                  <span className="not">{t("gorevYok")}</span>
                )}
              </li>
            );
          })}
        </ul>
      </Kart>
    </>
  );
}

/* Haftalık akış Cumartesi başlıyor: Cmt-Paz öneri, Pzt-Çar plan, Per toplantı, Cum geri dönüş. */
const EVRE: Record<number, Anahtar> = { 6: "hOneri", 0: "hOneri", 1: "hPlan", 2: "hPlan", 3: "hPlan", 4: "hToplanti", 5: "hGeri" };

function HaftaSeridi() {
  const [, t] = useDil();
  const gunler = t("gunKisa").split(",");
  const simdi = new Date().getDay();
  const sira = [6, 0, 1, 2, 3, 4, 5];
  return (
    <Kart baslik={t("haftaninYeri")} sagUc={<a href="#/haftalik">{t("haftalik")}</a>}>
      <div className="hafta">
        {sira.map((g) => (
          <div key={g} className={g === simdi ? "bugun" : ""}>
            <strong>{gunler[g]}</strong>
            {t(EVRE[g])}
          </div>
        ))}
      </div>
      <p className="hafta-evre">
        {t("hOneri")} → {t("hPlan")} → <b>{t("hToplanti")}</b> → {t("hGeri")}
      </p>
    </Kart>
  );
}
