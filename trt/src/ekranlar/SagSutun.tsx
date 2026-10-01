import { useDil } from "../dil";
import { Kart } from "../bilesenler/Parcalar";
import { saatYaz, sifirla, useVeri } from "../veri";

/** Sağ sütun: son hareketler, hızlı işlemler ve örnek veri notu. */
export default function SagSutun() {
  const [, t] = useDil();
  const v = useVeri();
  return (
    <>
      <Kart baslik={t("sonHareketler")}>
        <ul className="akis">
          {v.hareketler.slice(0, 8).map((h) => (
            <li key={h.id}>
              <time>{saatYaz(h.zaman)}</time>
              <span>{h.metin}</span>
            </li>
          ))}
        </ul>
      </Kart>
      <Kart baslik={t("hizliIslem")}>
        <div className="hizli">
          <a className="dugme" href="#/oneriler/yeni">{t("oneriGonder")}</a>
          <a className="dugme dugme-ikincil" href="#/paketler">{t("paketYukle")}</a>
          <a className="dugme dugme-ikincil" href="#/nextday">{t("gorevAta")}</a>
        </div>
      </Kart>
      <div className="not-kutu">
        <b>{t("ornekVeri")}.</b> {t("ornekVeriAciklama")}{" "}
        <button className="dugme dugme-ikincil dugme-kucuk" onClick={sifirla}>
          {t("sifirla")}
        </button>
      </div>
    </>
  );
}
