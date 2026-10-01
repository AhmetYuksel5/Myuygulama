import { useDil, type Anahtar } from "../dil";

/**
 * Beş ana başlık. Her biri hem o türdeki planların listesine açılıyor
 * hem de altındaki "+ oluştur" ile yeni plan başlatıyor.
 */
export const BASLIKLAR: { yol: string; ad: Anahtar }[] = [
  { yol: "nextday", ad: "nextday" },
  { yol: "haftalik", ad: "haftalik" },
  { yol: "aylik", ad: "aylik" },
  { yol: "ozel", ad: "ozel" },
  { yol: "yurtdisi", ad: "yurtdisi" },
];

export default function AnaBasliklar({ acik }: { acik: string }) {
  const [, t] = useDil();
  return (
    <nav className="basliklar">
      {BASLIKLAR.map((b) => (
        <div key={b.yol} className={`baslik baslik-${b.yol} ${acik === b.yol ? "acik" : ""}`}>
          <a href={`#/${b.yol}`}>
            <strong>{t(b.ad)}</strong>
          </a>
          <a className="olustur" href={`#/${b.yol}/yeni`}>
            {t("olustur")}
          </a>
        </div>
      ))}
    </nav>
  );
}
