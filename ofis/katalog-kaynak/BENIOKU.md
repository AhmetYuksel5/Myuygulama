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

## Genel tanıtım kataloğu ve referanslar dosyası (2026-11-27)

`uret_genel.py` üretir:

- `tanitim_1.html` — tanıtım kataloğunun 1. sayfası (telefon oranında): hakkımızda, güven sayıları,
  sekiz hizmet fotoğraflı kutucuk, "malzeme dahil · keşif ücretsiz", video çağrısı.
- `tanitim_2.html` — 2. sayfa: A4 detaylı referanslar (sitedeki sırayla ilk 30).
  İkisi `pdfunite` ile birleşir → `assets/katalog/tanitim.pdf`.
- `referans.html` — yalnız referanslar, A4, 2 sayfa (27 + 30 kart, 1. sayfada güven sayıları)
  → `assets/katalog/referans.pdf`.
