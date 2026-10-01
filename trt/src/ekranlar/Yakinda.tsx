import { useDil } from "../dil";
import { Kart } from "../bilesenler/Parcalar";

/** Planda olup henüz yapılmamış sayfa: boş kutu yerine ne geleceğini yazıyor. */
export default function Yakinda({ baslik, aciklama }: { baslik: string; aciklama?: string }) {
  const [, t] = useDil();
  return (
    <>
      <div className="sayfa-basi">
        <h1>{baslik}</h1>
        <span className="rozet rozet-uyari">{t("yakinda")}</span>
      </div>
      <Kart>
        <p>{aciklama ?? t("yakindaAciklama")}</p>
      </Kart>
    </>
  );
}
