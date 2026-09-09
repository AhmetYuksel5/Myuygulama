import { useEffect, useState } from "react";

/**
 * Sekmeler.
 *
 * Android'deki dört sekmenin üstüne bir "Bugün" eklendi: telefonda ana
 * ekran widget'ı ve bildirimler o işi görüyor, tarayıcıda ise açılışta
 * insanın "bugün ne var" sorusuna cevap verecek bir yer lazım.
 */
export const SEKMELER = ["bugun", "kitaplik", "kelimeler", "pocket", "daha"] as const;

export type Sekme = (typeof SEKMELER)[number];

export const ADLAR: Record<Sekme, string> = {
  bugun: "Bugün",
  kitaplik: "Kitaplık",
  kelimeler: "Kelimeler",
  pocket: "Pocket",
  daha: "Daha",
};

const cozumle = (): Sekme => {
  const ad = location.hash.replace(/^#\/?/, "") as Sekme;
  return SEKMELER.includes(ad) ? ad : "bugun";
};

/**
 * Adres çubuğundaki `#/kelimeler` ile açık sekmeyi eşler.
 *
 * Kütüphane yerine adresin kendisi: telefonun geri tuşu ve ana ekrana
 * eklenen kısayol bedavaya doğru çalışıyor, uygulama da tazelendiğinde
 * kaldığı sekmede açılıyor.
 */
export function useSekme(): [Sekme, (s: Sekme) => void] {
  const [sekme, ayarla] = useState<Sekme>(cozumle);

  useEffect(() => {
    const dinle = () => ayarla(cozumle());
    window.addEventListener("hashchange", dinle);
    return () => window.removeEventListener("hashchange", dinle);
  }, []);

  const git = (yeni: Sekme) => {
    location.hash = `#/${yeni}`;
  };

  return [sekme, git];
}
