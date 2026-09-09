/** Henüz yazılmamış ekranlar için; ne geleceğini söylüyor. */
export default function Yakinda({
  baslik,
  aciklama,
}: {
  baslik: string;
  aciklama: string;
}) {
  return (
    <>
      <header className="tepe">
        <h1>{baslik}</h1>
      </header>
      <div className="bos">
        <h2>Henüz burada bir şey yok</h2>
        <p>{aciklama}</p>
      </div>
    </>
  );
}
