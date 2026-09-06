/*
 * Tekrar programı.
 *
 * Android sürümündeki merdivenin aynısı: her kelimenin bir kademesi var,
 * bildikçe kademe yükseliyor ve bir sonraki tekrar uzağa gidiyor,
 * bilemedikçe başa dönüyor. Aralıkların gün cinsinden karşılığı aşağıda;
 * son kademeye gelen kelime programdan çıkıyor.
 *
 * Gün başlangıcını yerel saatle alıyoruz: gece yarısını geçtiğinde
 * "bugünün tekrarı" değişmeli, saat dilimine göre değil.
 */

const ARALIK = [0, 1, 3, 7, 16, 35];

export const bugun = () => {
  const t = new Date();
  return new Date(t.getFullYear(), t.getMonth(), t.getDate()).getTime();
};

const gun = 86400000;

/** Yeni işaretlenen kelimenin başlangıç durumu. */
export function yeniKelime(alanlar) {
  return {
    kademe: 0,
    siradaki: bugun(),
    tekrar: 0,
    eklendi: Date.now(),
    ...alanlar,
  };
}

/** Bugün çalışılacaklar; en eskiden başlayarak. */
export function bekleyenler(kelimeler) {
  const sinir = bugun();
  return kelimeler
    .filter(k => k.siradaki !== null && k.siradaki <= sinir)
    .sort((a, b) => a.siradaki - b.siradaki);
}

/**
 * Karar sonrası kelimenin yeni hâli.
 *
 * "Biliyorum" bir kademe yukarı, "bilmiyorum" başa. Başa döndürmek sert
 * ama doğru: yarı yolda bırakılan bir kelime birkaç gün sonra yine
 * bilinmiyor ve merdivende yukarıda durması onu görünmez yapıyor.
 */
export function karar(kelime, biliyorum) {
  const kademe = biliyorum ? Math.min(kelime.kademe + 1, ARALIK.length) : 0;
  const bitti = kademe >= ARALIK.length;
  return {
    ...kelime,
    kademe,
    tekrar: (kelime.tekrar || 0) + 1,
    // Programdan çıkan kelime listede kalıyor, yalnız tekrara gelmiyor.
    siradaki: bitti ? null : bugun() + ARALIK[kademe] * gun,
    sonTekrar: Date.now(),
  };
}
