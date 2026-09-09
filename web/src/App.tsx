import AltCubuk from "./bilesenler/AltCubuk";
import Bugun from "./ekranlar/Bugun";
import Yakinda from "./ekranlar/Yakinda";
import { ADLAR, useSekme } from "./yol";

/**
 * Uygulamanın iskeleti: gövde ve alt çubuk.
 *
 * Bu aşamada yalnız Bugün ekranı dolu. Ötekiler ne olacaklarını
 * söyleyen boş ekranlar — boş bir kutu göstermektense ne geleceğini
 * yazmak, sonraki aşamada nereye bakılacağını da belli ediyor.
 */
export default function App() {
  const [sekme, git] = useSekme();

  return (
    <div className="uygulama">
      <main className="govde">
        {sekme === "bugun" ? (
          <Bugun />
        ) : (
          <Yakinda baslik={ADLAR[sekme]} aciklama={ACIKLAMALAR[sekme]} />
        )}
      </main>
      <AltCubuk acik={sekme} onGit={git} />
    </div>
  );
}

const ACIKLAMALAR: Record<string, string> = {
  kitaplik:
    "EPUB ve PDF okuyucu buraya gelecek. Kitaplar telefonunda duracak, " +
    "işaretlediğin kelimeler listene düşecek.",
  kelimeler:
    "Deste ve tekrar programı buraya gelecek. Telefondaki kelimelerin " +
    "aynısı, senkron üzerinden.",
  pocket:
    "Kaydedilmiş yazılar buraya gelecek. Okuma tarafı tarayıcıda çalışır; " +
    "kaydetme telefonda kalabilir.",
  daha: "Alışkanlıklar, görevler, senkron ve ayarlar buraya gelecek.",
};
