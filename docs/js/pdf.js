/*
 * PDF okuyucu.
 *
 * Mozilla'nın pdf.js'i kullanılıyor; tarayıcıda PDF çizmenin başka
 * gerçekçi yolu yok. Dosyalar depoda duruyor (CDN'den çekilmiyor):
 * uygulamanın internetsiz de açılması ve yıllar sonra bir adresin
 * kapanmasıyla bozulmaması gerekiyor.
 *
 * Motor ancak bir PDF açılınca iniyor — EPUB okuyan biri bir buçuk
 * megabaytı boşuna indirmesin.
 */

let motor = null;

async function motoruYukle() {
  if (motor) return motor;
  motor = await import("../lib/pdf.min.mjs");
  // İşçi ayrı bir iş parçacığında çalışıyor; olmadan çizim arayüzü
  // kilitliyor.
  motor.GlobalWorkerOptions.workerSrc = new URL("../lib/pdf.worker.min.mjs", import.meta.url).href;
  return motor;
}

export async function pdfAc(tampon) {
  const pdf = await motoruYukle();
  // Kopya veriliyor: pdf.js aldığı tamponu kendine devralıyor ve aynı
  // baytları ikinci kez açmak isteyince elimizde boş bir tampon kalıyor.
  const belge = await pdf.getDocument({ data: new Uint8Array(tampon.slice(0)) }).promise;
  return belge;
}

/**
 * Bir sayfayı çizer ve üstüne metin katmanını serer.
 *
 * Metin katmanı gözle görünmüyor ama gerçek metin: seçme işini tarayıcının
 * kendisi yapıyor. iPhone'da bu, sistemin kendi seçim tutamaklarını ve
 * büyütecini bedavaya getiriyor — Android tarafında bunların hepsini elle
 * yazmıştık.
 */
export async function sayfaCiz(belge, numara, kap, genislik) {
  const pdf = await motoruYukle();
  const sayfa = await belge.getPage(numara);

  const ham = sayfa.getViewport({ scale: 1 });
  const olcek = genislik / ham.width;
  const gorunum = sayfa.getViewport({ scale: olcek });

  // Ekranın gerçek piksel yoğunluğunda çiziyoruz; yoksa yazı bulanık.
  const yogunluk = Math.min(window.devicePixelRatio || 1, 2);
  const tuval = document.createElement("canvas");
  tuval.width = Math.floor(gorunum.width * yogunluk);
  tuval.height = Math.floor(gorunum.height * yogunluk);
  tuval.style.width = `${Math.floor(gorunum.width)}px`;
  tuval.style.height = `${Math.floor(gorunum.height)}px`;

  kap.innerHTML = "";
  kap.style.width = `${Math.floor(gorunum.width)}px`;
  kap.style.height = `${Math.floor(gorunum.height)}px`;
  kap.append(tuval);

  await sayfa.render({
    canvasContext: tuval.getContext("2d"),
    viewport: gorunum,
    transform: yogunluk === 1 ? null : [yogunluk, 0, 0, yogunluk, 0, 0],
  }).promise;

  const katman = document.createElement("div");
  katman.className = "metin-katmani";
  kap.append(katman);

  const icerik = await sayfa.getTextContent();
  const metinKatmani = new pdf.TextLayer({
    textContentSource: icerik,
    container: katman,
    viewport: gorunum,
  });
  await metinKatmani.render();

  return { sayfa: numara, toplam: belge.numPages, metin: icerik.items.map(x => x.str).join(" ") };
}
