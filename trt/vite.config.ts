import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

/*
 * Kaynak burada (`trt/`), çıktı `docs/trt/` altına yazılıyor.
 *
 * Merkez'in web sürümüyle aynı sebep: GitHub bu daldaki `docs/` klasörünü
 * olduğu gibi sunuyor, derleme yapmıyor. Çıktıyı depoya koyunca yayınlamak
 * "gönder ve bitti" kalıyor. Site kurum içine taşındığında aynı çıktı bir
 * sunucunun kök klasörüne de konabilir; o zaman yalnız `base` değişir.
 */
export default defineConfig({
  plugins: [react()],
  base: "/Myuygulama/trt/",
  build: {
    outDir: "../docs/trt",
    emptyOutDir: true,
  },
});
