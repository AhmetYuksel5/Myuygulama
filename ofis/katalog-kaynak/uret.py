"""My Asistan katalogları — telefonda okunacak biçim (kullanıcı 2026-11-26:
"mobil görünüme uygun olsun, çok bilgi yığıntısı var"). Sayfa 9:16, iki sayfa:
1) başlık + tek büyük fotoğraf + kısa giriş + öne çıkanlar + yapılan işler + çağrı,
2) güven sayıları + 15 logolu referans. Kaynak: yesiltemizlik.com (metin, foto, referans).
Kullanım: python3 uret.py <cikti_klasoru> <site_verisi_klasoru>"""
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

def foto(ad):
    im = Image.open(os.path.join(S, "foto", ad + ".webp")).convert("RGB"); im.thumbnail((1100, 800))
    b = io.BytesIO(); im.save(b, "JPEG", quality=84)
    return "data:image/jpeg;base64," + base64.b64encode(b.getvalue()).decode()

LOGO = b64(S + "/site-logo.png", 900, 140)
def logo_yolu(r): return os.path.join(S, "logo", r["logo"].strip("/").replace("/", "_"))

KAT = {
 "ofis": dict(baslik="Ofis Temizliği", alt="Düzenli ve tek seferlik ofis temizliği",
   giris="Temizliği çalışma saatlerinize göre gün içinde, mesai sonrasında ya da hafta sonu planlıyoruz.",
   foto="sahadan-ofis-masa-silme-ekibi-01",
   cipler=["Haftada 1, 3 gün ya da her gün", "Yedek personel güvencesi", "Tam zamanlı ya da part-time", "Gün içi, mesai sonrası, hafta sonu"],
   isler=["Masalar, sandalyeler ve sık dokunulan yüzeyler", "Mutfak, lavabo ve tuvaletler", "Halıfleks ve zeminler", "Planlı cam silme ve halıfleks yıkama"],
   anahtar=("ofis", "personel", "halıfleks")),
 "bos": dict(baslik="Boş Daire Temizliği", alt="Taşınma öncesi ve sonrası, aynı gün teslim",
   giris="Ekibimiz malzemelerle sabah gelir, daireyi baştan sona temizleyip akşamüzeri teslim eder.",
   foto="bos-daire-oncesi-sonrasi-mutfak",
   cipler=["09.00–16.00 aynı gün teslim", "En az 2 kişilik ekip", "Malzeme ve ekipman bizden", "Önce memnuniyet, sonra ödeme"],
   isler=["Camlar, çerçeveler ve oluklar", "Mutfak dolapları, fırın ve aspiratör", "Banyo, duşakabin ve derzler", "Duvarlar, kapılar, balkon ve zeminler"],
   anahtar=("boş", "taşınma", "lojman", "daire", "konut")),
 "insaat": dict(baslik="İnşaat Sonrası Temizlik", alt="Boya, silikon, harç ve ince toz temizliği",
   giris="Ustalardan kalan izleri her yüzeye uygun yöntemle temizliyor, daireyi genellikle aynı gün teslim ediyoruz.",
   foto="insaat-sonrasi-daire-temizligi",
   cipler=["En az 2 kişilik ekip", "Buhar ve ıslak vakum makinesi", "Malzeme, yol ve yemek dahil", "Ödeme kontrolden sonra"],
   isler=["Camlardaki boya, harç ve silikon", "Çerçeve, oluk ve petek tozları", "Mutfak, banyo ve derzler", "Seramik, parke ve laminat zeminler"],
   anahtar=("inşaat", "tadilat")),
}
TIK = '<svg viewBox="0 0 24 24" width="11" height="11"><path d="M5 12.5l4.2 4.2L19 7" fill="none" stroke="#2F7D1E" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/></svg>'
CSS = """
@page{size:120mm 213mm;margin:0}
*{box-sizing:border-box}
body{margin:0;font-family:Arial,'Liberation Sans',Helvetica,sans-serif;color:#1d2a22}
.sayfa{width:120mm;height:213mm;position:relative;overflow:hidden;page-break-after:always;padding:7mm 7mm 0}
.ust img{height:10mm;display:block}
.serit{margin-top:4mm;background:#92D050;color:#fff;font-weight:700;font-size:17pt;padding:2.4mm 3.5mm;border-radius:1.5mm}
.altb{font-style:italic;color:#555;font-size:9.5pt;margin:1.6mm 0 3.5mm}
.foto{width:100%;height:58mm;object-fit:cover;border-radius:2.5mm;display:block}
.giris{font-size:11pt;line-height:1.45;margin:3.5mm 0 3.5mm;color:#26332b}
.cipler{display:grid;grid-template-columns:1fr 1fr;gap:1.8mm;margin-bottom:4mm}
.cip{background:#EEF6E6;border:.3mm solid #CFE6B8;color:#2F5D1E;font-size:9pt;font-weight:700;border-radius:2mm;padding:2mm 2.4mm;line-height:1.25}
h3{margin:0 0 1.8mm;font-size:11pt;color:#1F4E2C;border-left:1.1mm solid #92D050;padding-left:2mm}
.isler{list-style:none;margin:0;padding:0}
.isler li{display:flex;gap:2mm;align-items:center;font-size:10.2pt;padding:1.25mm 0;border-bottom:.25mm solid #e6ede4}
.cagri{position:absolute;left:7mm;right:7mm;bottom:12mm;background:#1F6B3A;color:#fff;border-radius:2.5mm;padding:3mm 3.5mm;font-size:10.5pt;font-weight:700;line-height:1.35}
.cagri small{display:block;font-weight:400;opacity:.9;font-size:9pt;margin-top:.8mm}
.alt{position:absolute;left:7mm;right:7mm;bottom:4.5mm;font-size:7.8pt;color:#2F5597;text-align:center}
.sayilar{display:grid;grid-template-columns:repeat(4,1fr);gap:1.5mm;margin:0 0 4mm;text-align:center}
.sayilar div{background:#F3F8EE;border-radius:2mm;padding:2mm 1mm;font-size:7.4pt;color:#555;line-height:1.2}
.sayilar b{display:block;font-size:13pt;color:#1F4E2C}
.kartlar{display:grid;grid-template-columns:repeat(3,1fr);gap:2mm}
.kart{border:.3mm solid #dfe5dd;border-radius:2mm;padding:2mm 1.6mm 1.6mm;text-align:center;height:25mm;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:1.2mm;overflow:hidden}
.kart .lg{height:12mm;width:100%;display:flex;align-items:center;justify-content:center}
.kart .lg img{max-width:90%;max-height:100%;object-fit:contain}
.kart b{font-size:7.6pt;color:#1F4E2C;line-height:1.15}
.ve{text-align:center;font-size:9pt;color:#555;margin-top:3mm}
"""
def ust(): return f'<div class="ust"><img src="{LOGO}"></div>'
def alt(): return '<div class="alt">YEŞİL TEMİZLİK · 0212 369 90 75 · www.yesiltemizlik.com</div>'
for k, v in KAT.items():
    uygun = [r for r in refs if any(t in r["hizmet"].lower() for t in v["anahtar"])]
    gor, secim = set(), []
    for r in uygun + refs:
        if r["ad"] in gor: continue
        gor.add(r["ad"]); secim.append(r)
        if len(secim) == 15: break
    cip = "".join(f'<div class="cip">{e(c)}</div>' for c in v["cipler"])
    isl = "".join(f"<li>{TIK}<span>{e(x)}</span></li>" for x in v["isler"])
    kart = "".join(f'<div class="kart"><div class="lg"><img src="{b64(logo_yolu(r))}"></div><b>{e(r["ad"])}</b></div>' for r in secim)
    h = f"""<!doctype html><html lang="tr"><head><meta charset="utf-8"><style>{CSS}</style></head><body>
<div class="sayfa">{ust()}<div class="serit">{e(v['baslik'])}</div><div class="altb">{e(v['alt'])}</div>
<img class="foto" src="{foto(v['foto'])}"><p class="giris">{e(v['giris'])}</p>
<div class="cipler">{cip}</div><h3>Yapılan işler</h3><ul class="isler">{isl}</ul>
<div class="cagri">Fiyat için kısa bir video gönderin<small>Bu sohbete mekânın kısa bir videosunu atın, fiyatı hemen iletelim.</small></div>{alt()}</div>
<div class="sayfa">{ust()}<div class="serit">Referanslarımız</div><div class="altb">2012'den beri çalıştığımız kurumlardan bazıları</div>
<div class="sayilar"><div><b>484+</b>kurumsal referans</div><div><b>8</b>konsolosluk</div><div><b>4.7★</b>Google puanı</div><div><b>2012</b>'den beri</div></div>
<div class="kartlar">{kart}</div><div class="ve">ve daha fazlası: yesiltemizlik.com/referanslar</div>{alt()}</div>
</body></html>"""
    open(os.path.join(K, k + "4.html"), "w", encoding="utf-8").write(h)
    print(k, "uygun", len(uygun))
