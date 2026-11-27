"""Genel tanıtım kataloğu (1. sayfa telefon oranında) ve yalnız referanslar dosyası
(A4, 2 sayfa, 60 detaylı kart). Kullanıcı 2026-11-27: "sadece referanslar dosyası olsun,
bir de daha genel çerçevede tanıtım kataloğu olsun". Kaynak: yesiltemizlik.com.
Kullanım: python3 uret_genel.py <cikti_klasoru> <site_verisi_klasoru>
Çıktı: tanitim_1.html (telefon sayfası), tanitim_2.html (A4 referans sayfası), referans.html (A4, 2 sayfa)."""
import html, json, os, sys, base64, io
from PIL import Image
K, S = sys.argv[1], sys.argv[2]
e = html.escape
refs = [r for r in json.load(open(S + "/ref.json", encoding="utf-8")) if r["logo"]]

def b64(path, maxw=360, maxh=140):
    im = Image.open(path).convert("RGBA"); im.thumbnail((maxw, maxh))
    z = Image.new("RGBA", im.size, (255, 255, 255, 255)); z.alpha_composite(im)
    b = io.BytesIO(); z.convert("RGB").save(b, "PNG")
    return "data:image/png;base64," + base64.b64encode(b.getvalue()).decode()

def foto(ad, w=700, h=500):
    yol = next(os.path.join(S, "foto", ad + x) for x in (".webp", ".jpg") if os.path.exists(os.path.join(S, "foto", ad + x)))
    im = Image.open(yol).convert("RGB"); im.thumbnail((w, h))
    b = io.BytesIO(); im.save(b, "JPEG", quality=82)
    return "data:image/jpeg;base64," + base64.b64encode(b.getvalue()).decode()

LOGO = b64(S + "/site-logo.png", 900, 140)
def logo_yolu(r): return os.path.join(S, "logo", r["logo"].strip("/").replace("/", "_"))
def kisa(h, adUzun=0):
    parca = [x.strip() for x in h.split(",")]
    if adUzun >= 22: return parca[0]
    if len(h) <= 55: return h
    return ", ".join(parca[:2])

# ---- A4 referans sayfası (öteki kataloglarla aynı kart düzeni) ----
CSS_A4 = """
@page{size:A4;margin:0}
*{box-sizing:border-box}
body{margin:0;font-family:Arial,'Liberation Sans',Helvetica,sans-serif;color:#222}
.sayfa{width:210mm;height:297mm;position:relative;overflow:hidden;page-break-after:always;padding:10mm 11mm 0}
.ust img{height:15mm;display:block;margin-left:2mm}
.serit{margin-top:6mm;background:#92D050;color:#fff;font-weight:700;font-size:15pt;padding:2.6mm 4mm}
.altb{font-style:italic;color:#555;font-size:9pt;margin:1.6mm 0 4mm}
.sayilar{display:grid;grid-template-columns:repeat(4,1fr);gap:2.5mm;margin:0 0 4mm;text-align:center}
.sayilar div{background:#F3F8EE;border-radius:2mm;padding:2mm 1mm;font-size:8pt;color:#555}
.sayilar b{display:block;font-size:14pt;color:#1F4E2C}
.kartlar{display:grid;grid-template-columns:repeat(3,1fr);gap:2.2mm}
.kart{border:.3mm solid #dcdcdc;border-radius:2mm;display:grid;grid-template-columns:17mm 1fr;gap:2.4mm;align-items:center;padding:1.8mm 2.2mm;height:21.1mm;overflow:hidden}
.kart .lg{height:15mm;display:flex;align-items:center;justify-content:center;border:.25mm solid #eee;border-radius:1.4mm;padding:1mm;background:#fff}
.kart .lg img{max-width:100%;max-height:100%;object-fit:contain}
.kart b{display:block;font-size:8.6pt;color:#1F4E2C;line-height:1.2}
.kart .hz{font-size:7.4pt;color:#333;line-height:1.25;margin-top:.5mm}
.kart .tr{font-size:7.2pt;color:#777;margin-top:.4mm}
.alt{position:absolute;left:11mm;right:11mm;bottom:8mm;border-top:.35mm solid #92D050;padding-top:1.6mm;font-size:8pt;color:#2F5597}
"""
SAYILAR = '<div class="sayilar"><div><b>484+</b>kurumsal referans</div><div><b>8</b>ülke konsolosluğu</div><div><b>4.7★</b>Google (89 yorum)</div><div><b>2012</b>\'den beri</div></div>'
def a4_sayfa(secim, alt_baslik, sayilar=False):
    kart = "".join(f'<div class="kart"><div class="lg"><img src="{b64(logo_yolu(r))}"></div><div><b>{e(r["ad"])}</b><div class="hz">{e(kisa(r["hizmet"], len(r["ad"])))}</div><div class="tr">{e(" · ".join(x for x in (r["tarih"], r["semt"]) if x))}</div></div></div>' for r in secim)
    return (f'<div class="sayfa"><div class="ust"><img src="{LOGO}"></div><div class="serit">REFERANSLARIMIZ</div>'
            f'<div class="altb">{e(alt_baslik)}</div>{SAYILAR if sayilar else ""}<div class="kartlar">{kart}</div>'
            '<div class="alt">YEŞİL TEMİZLİK | 0212 369 90 75 | www.yesiltemizlik.com</div></div>')
def a4_html(sayfalar): return f'<!doctype html><html lang="tr"><head><meta charset="utf-8"><style>{CSS_A4}</style></head><body>{"".join(sayfalar)}</body></html>'

# Sitedeki sırayla (öne çıkanlar başta); referans dosyası 27 + 30 = 57 kart (1. sayfada sayılar var).
open(os.path.join(K, "tanitim_2.html"), "w", encoding="utf-8").write(a4_html([a4_sayfa(refs[:30], "Kurumsal referanslarımızdan seçmeler")]))
open(os.path.join(K, "referans.html"), "w", encoding="utf-8").write(a4_html([
    a4_sayfa(refs[:27], "2012'den beri çalıştığımız kurumlardan seçmeler", sayilar=True),
    a4_sayfa(refs[27:57], "Kurumsal referanslarımızdan seçmeler (devam)")]))

# ---- Tanıtım kataloğu 1. sayfa: telefon oranında ----
HIZMET = [("bos-daire-genel-temizlik", "Boş Daire"), ("sahadan-ofis-masa-silme-ekibi-01", "Ofis Temizliği"),
          ("insaat-sonrasi-daire-temizligi", "İnşaat Sonrası"), ("ofis-halifleks-taski-kuru-kopuk", "Halıfleks Yıkama"),
          ("sahadan-tuik-binasi-dis-cephe-cam-temizligi-01", "Dış Cephe Cam"), ("zemin-temizligi", "Zemin Temizliği"),
          ("villa-teras-temizligi-malzemeli", "Villa Temizliği"), ("g10", "Fabrika ve Depo")]
CSS_M = """
@page{size:120mm 213mm;margin:0}
*{box-sizing:border-box}
body{margin:0;font-family:Arial,'Liberation Sans',Helvetica,sans-serif;color:#1d2a22}
.sayfa{width:120mm;height:213mm;position:relative;overflow:hidden;padding:7mm 7mm 0}
.ust img{height:10mm;display:block}
.serit{margin-top:4mm;background:#92D050;color:#fff;font-weight:700;font-size:17pt;padding:2.4mm 3.5mm;border-radius:1.5mm}
.altb{font-style:italic;color:#555;font-size:9.5pt;margin:1.6mm 0 3mm}
.giris{font-size:10.4pt;line-height:1.45;margin:0 0 3mm;color:#26332b}
.sayilar{display:grid;grid-template-columns:repeat(4,1fr);gap:1.5mm;margin:0 0 3mm;text-align:center}
.sayilar div{background:#F3F8EE;border-radius:2mm;padding:1.6mm .6mm;font-size:6.8pt;color:#555;line-height:1.2}
.sayilar b{display:block;font-size:12pt;color:#1F4E2C}
h3{margin:0 0 1.8mm;font-size:11pt;color:#1F4E2C;border-left:1.1mm solid #92D050;padding-left:2mm}
.hizmet{display:grid;grid-template-columns:1fr 1fr;gap:2mm}
.hz{position:relative;height:19.5mm;border-radius:2mm;overflow:hidden}
.hz img{width:100%;height:100%;object-fit:cover;display:block}
.hz span{position:absolute;left:0;right:0;bottom:0;background:linear-gradient(transparent,rgba(0,0,0,.72));color:#fff;font-weight:700;font-size:9pt;padding:3mm 2mm 1.4mm}
.not{margin-top:2.5mm;text-align:center;font-size:9pt;color:#2F5D1E;font-weight:700}
.cagri{position:absolute;left:7mm;right:7mm;bottom:12mm;background:#1F6B3A;color:#fff;border-radius:2.5mm;padding:3mm 3.5mm;font-size:10.5pt;font-weight:700;line-height:1.35}
.cagri small{display:block;font-weight:400;opacity:.9;font-size:9pt;margin-top:.8mm}
.alt{position:absolute;left:7mm;right:7mm;bottom:4.5mm;font-size:7.8pt;color:#2F5597;text-align:center}
"""
hz = "".join(f'<div class="hz"><img src="{foto(a)}"><span>{e(t)}</span></div>' for a, t in HIZMET)
t1 = f"""<!doctype html><html lang="tr"><head><meta charset="utf-8"><style>{CSS_M}</style></head><body>
<div class="sayfa"><div class="ust"><img src="{LOGO}"></div><div class="serit">Yeşil Temizlik</div>
<div class="altb">2012'den beri İstanbul'un iki yakasında profesyonel temizlik</div>
<p class="giris">Kurumsal firmalara, devlet kurumlarına, konsolosluklara ve bireysel müşterilere hizmet veriyoruz. Yaklaşımımız işi zamanında, eksiksiz ve sorunsuz teslim etmek.</p>
<div class="sayilar"><div><b>484+</b>kurumsal referans</div><div><b>8</b>konsolosluk</div><div><b>4.7★</b>Google puanı</div><div><b>2012</b>'den beri</div></div>
<h3>Hizmetlerimiz</h3><div class="hizmet">{hz}</div>
<div class="not">Malzeme dahil · Keşif ücretsiz</div>
<div class="cagri">Fiyat için kısa bir video gönderin<small>Bu sohbete mekânın kısa bir videosunu atın, fiyatı hemen iletelim.</small></div>
<div class="alt">YEŞİL TEMİZLİK · 0212 369 90 75 · www.yesiltemizlik.com</div></div></body></html>"""
open(os.path.join(K, "tanitim_1.html"), "w", encoding="utf-8").write(t1)
print("tamam")
