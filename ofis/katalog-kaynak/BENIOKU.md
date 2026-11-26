# Katalog PDF'leri

Her katalog iki sayfa:

1. **Hizmet sayfası** — telefon oranında (120×213 mm): `uret.py` üretir (`<anahtar>4.html`, 1. sayfası).
2. **Referanslar** — A4, 30 detaylı kart (logo, ad, yapılan iş, dönem · semt): `uret_referans_a4.py`
   üretir (`<anahtar>2.html`, 2. sayfası). Kullanıcı 2026-11-26: referans sayfası bu hâliyle kalsın,
   sadeleştirilmesin.

Her iki betik `python3 <betik> <çıktı klasörü> <site verisi klasörü>` ile çalışır; site verisi
yesiltemizlik.com'dan alınır (`ref.json`, `logo/`, `foto/`, `site-logo.png`). HTML'ler Chromium ile
PDF'e basılır (1. sayfa `pageRanges:"1"`, 2. sayfa A4 `pageRanges:"2"`), `pdfunite` ile birleştirilir
ve `ofis/src/main/assets/katalog/<anahtar>.pdf` olarak konur. PDF değişince `Tanitim.KATALOG_SURUM`
artırılır.
