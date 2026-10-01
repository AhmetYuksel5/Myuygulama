import { useDil, type Anahtar } from "../dil";

/** Sol sütun: kayıt defterleri. Üstteki başlıklar "plan", buradakiler "kaynak". */
const MADDELER: { yol: string; ad: Anahtar; ayrac?: boolean }[] = [
  { yol: "ana", ad: "anasayfa" },
  { yol: "muhabirler", ad: "muhabirler", ayrac: true },
  { yol: "paketler", ad: "paketler" },
  { yol: "oneriler", ad: "oneriler" },
  { yol: "toplantilar", ad: "toplantilar" },
  { yol: "ekipler", ad: "ekipler" },
  { yol: "program", ad: "programBirimi", ayrac: true },
  { yol: "feature", ad: "feature" },
  { yol: "arsiv", ad: "arsiv", ayrac: true },
  { yol: "raporlar", ad: "raporlar" },
  { yol: "ayarlar", ad: "ayarlar" },
];

export default function SolMenu({ acik }: { acik: string }) {
  const [, t] = useDil();
  return (
    <nav className="sol">
      {MADDELER.map((m) => (
        <div key={m.yol}>
          {m.ayrac && <div className="ayrac" />}
          <a href={m.yol === "ana" ? "#/" : `#/${m.yol}`} className={acik === m.yol ? "acik" : ""}>
            <i />
            {t(m.ad)}
          </a>
        </div>
      ))}
    </nav>
  );
}
