import { SEKMELER, ADLAR, type Sekme } from "../yol";
import { Gunes, Kitap, Kart, Cep, UcNokta } from "./Simgeler";

const SIMGELER: Record<Sekme, (o: { className?: string }) => React.ReactElement> = {
  bugun: Gunes,
  kitaplik: Kitap,
  kelimeler: Kart,
  pocket: Cep,
  daha: UcNokta,
};

/**
 * Alt çubuk.
 *
 * Açık sekmenin simgesi renkli bir hapın içinde duruyor; seçilen simge
 * dili bu. Yazı hapın dışında kalıyor, yoksa hap sekme genişliğini
 * yiyor ve beş sekme sığmıyor.
 */
export default function AltCubuk({
  acik,
  onGit,
}: {
  acik: Sekme;
  onGit: (s: Sekme) => void;
}) {
  return (
    <nav className="alt-cubuk" aria-label="Bölümler">
      {SEKMELER.map((sekme) => {
        const Simge = SIMGELER[sekme];
        const secili = sekme === acik;
        return (
          <button
            key={sekme}
            type="button"
            onClick={() => onGit(sekme)}
            aria-current={secili ? "page" : undefined}
          >
            <span className="kapsul">
              <Simge />
            </span>
            <span className="ad">{ADLAR[sekme]}</span>
          </button>
        );
      })}
    </nav>
  );
}
