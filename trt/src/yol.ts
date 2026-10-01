import { useEffect, useState } from "react";

/**
 * Adres çubuğundaki `#/paketler/p3` ile açık sayfayı eşler.
 *
 * Kütüphane yerine adresin kendisi: GitHub Pages alt klasörden sunuyor,
 * `#` sonrası sunucuya gitmediği için her sayfa tazelenince yerinde
 * açılıyor; tarayıcının geri tuşu da bedavaya çalışıyor.
 */
export interface Yol {
  sayfa: string;
  id?: string;
}

const cozumle = (): Yol => {
  const [sayfa = "", id] = location.hash.replace(/^#\/?/, "").split("/");
  return { sayfa: sayfa || "ana", id: id ? decodeURIComponent(id) : undefined };
};

export function useYol(): Yol {
  const [yol, ayarla] = useState<Yol>(cozumle);
  useEffect(() => {
    const dinle = () => ayarla(cozumle());
    window.addEventListener("hashchange", dinle);
    return () => window.removeEventListener("hashchange", dinle);
  }, []);
  return yol;
}

export const git = (yol: string) => {
  location.hash = `#/${yol}`;
};

export const baglanti = (yol: string) => `#/${yol}`;
