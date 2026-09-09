import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

/*
 * Kaynak burada (`web/`), çıktı `docs/merkez/` altına yazılıyor.
 *
 * Sebebi GitHub'ın sayfayı nasıl yayınladığı: bu daldaki `docs/` klasörünü
 * olduğu gibi sunuyor, derleme yapmıyor. Derlemeyi burada yapıp çıktıyı
 * depoya koyunca sayfa yayınlamak yine "gönder ve bitti" kalıyor;
 * yayınlamayı iş akışına taşısaydık, biriktirme işareti taşıyan bir
 * gönderim siteyi de yayınlamaz hâle gelirdi.
 *
 * `docs/` kökü Arapça okuyucunun; Merkez onun altında ayrı bir klasörde
 * yaşıyor ve adresi bu yüzden alt yol içeriyor.
 */
export default defineConfig({
  plugins: [react()],
  base: "/Myuygulama/merkez/",
  build: {
    outDir: "../docs/merkez",
    emptyOutDir: true,
  },
});
