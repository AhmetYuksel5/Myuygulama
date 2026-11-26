import html, json, os, sys, base64, io
from PIL import Image
K, S = sys.argv[1], sys.argv[2]
e = html.escape
refs = [r for r in json.load(open(S + "/ref.json", encoding="utf-8")) if r["logo"]]

def b64(path, maxw=360, maxh=140):
    im = Image.open(path).convert("RGBA")
    im.thumbnail((maxw, maxh))
    z = Image.new("RGBA", im.size, (255, 255, 255, 255)); z.alpha_composite(im)
    b = io.BytesIO(); z.convert("RGB").save(b, "PNG")
    return "data:image/png;base64," + base64.b64encode(b.getvalue()).decode()

LOGO = b64(S + "/site-logo.png", 900, 140)
def kisa(h, adUzun=0):
    # Kart sabit yükseklikte: uzun hizmet metninde virgülle ayrılmış ilk iki iş,
    # ad iki satıra taşıyorsa yalnız ilki.
    parca = [x.strip() for x in h.split(",")]
    if adUzun > 24: return parca[0]
    if len(h) <= 55: return h
    return ", ".join(parca[:2])
def logo_yolu(r): return os.path.join(S, "logo", r["logo"].strip("/").replace("/", "_"))

KAT = {
 "ofis": dict(baslik="OFİS TEMİZLİĞİ", alt="Kurumsal ofis ve plazalar için düzenli ve periyodik temizlik",
   giris="Ofisinizin her gün aynı özenle temiz kalması için çalışma düzeninize uygun bir temizlik planı kuruyoruz. Sabit personel ya da belirli günlerde gelen ekiplerle, iş akışınızı aksatmadan hizmet veriyoruz.",
   bolumler=[("Çalışma alanları", ["Masalar, ortak alanlar ve toplantı odaları temizlenir.", "Çöpler toplanır, çöp kovaları temizlenir."]),
             ("Mutfak ve çay ocağı", ["Tezgâh, dolap önleri ve çay ocağı temizlenir, bulaşıklar yıkanır."]),
             ("Lavabo ve tuvaletler", ["Lavabo, tuvalet ve aynalar yıkanarak temizlenir.", "Tuvalet kağıdı, peçete ve sabun eksikleri tamamlanır."]),
             ("Zeminler ve halıfleks", ["Zeminler süpürülür, sert zeminler paspaslanır.", "Halıfleks kuru köpük yöntemiyle yıkanır; aynı gün kullanıma uygundur."]),
             ("Çalışma düzeni", ["Ofisinize mümkün olduğunca aynı personelimiz gelir.", "Personelimizin gelemediği günlerde yedek personel gönderilir.", "Personelimiz üniformalı ve sigortalı çalışır.", "Yapılacak işler ve öncelikler sizinle birlikte belirlenir."])],
   anahtar=("ofis", "personel", "halıfleks")),
 "bos": dict(baslik="BOŞ DAİRE TEMİZLİĞİ", alt="Taşınma öncesi ve sonrası, eşyasız dairelerin baştan sona temizliği",
   giris="Yeni evinize temiz bir başlangıç ya da teslim edeceğiniz daireyi eksiksiz bırakmak için boş daireyi tavandan zemine temizliyoruz. Ekip, malzeme ve ekipmanla gelip işi tek seferde teslim ediyoruz.",
   bolumler=[("Mutfak", ["Dolapların içi ve dışı silinir.", "Tezgâh, evye ve fayanslar temizlenir."]),
             ("Banyo ve tuvaletler", ["Lavabo, klozet, duşakabin ve fayanslardaki kireç ve lekeler temizlenir.", "Ayna ve armatürler parlatılır."]),
             ("Cam ve doğramalar", ["Camlar, doğramalar, pervaz ve kapılar silinir.", "Priz, anahtar ve kalorifer petekleri tozdan arındırılır."]),
             ("Zeminler ve balkon", ["Zeminler süpürülür ve yıkanır.", "Balkon temizlenir."]),
             ("Çalışma düzeni", ["Temizlik malzemesi, makine ve ekipman firmamızca getirilir.", "Personelimiz üniformalı ve sigortalı çalışır.", "Çalışma günü ve saati uygunluğunuza göre planlanır."])],
   anahtar=("boş", "taşınma", "lojman", "daire", "konut")),
 "insaat": dict(baslik="İNŞAAT SONRASI TEMİZLİK", alt="İnşaat ve tadilat sonrası alanların kullanıma hazır hâle getirilmesi",
   giris="İnşaat ve tadilat sonrasında kalan toz, boya ve malzeme artıklarını temizleyerek alanı kullanıma hazır teslim ediyoruz. Tek daireden toplu konut projelerine kadar hizmet veriyoruz.",
   bolumler=[("Toz ve kalıntılar", ["İnşaat tozu tüm yüzeylerden alınır.", "Boya, sıva, silikon ve etiket kalıntıları temizlenir."]),
             ("Cam ve doğramalar", ["Camlar ve doğramalar temizlenir, koruma bantları sökülür."]),
             ("Mutfak ve ıslak hacimler", ["Mutfak, banyo ve tuvaletler detaylı temizlenir."]),
             ("Zeminler", ["Zeminler süpürülür ve yıkanır."]),
             ("Çalışma düzeni", ["Toplu konut projelerinde blok blok teslim edilir.", "Temizlik malzemesi, makine ve ekipman firmamızca getirilir.", "Personelimiz üniformalı ve sigortalı çalışır."])],
   anahtar=("inşaat", "tadilat")),
}
CSS = """
@page{size:A4;margin:0}
*{box-sizing:border-box}
body{margin:0;font-family:Arial,'Liberation Sans',Helvetica,sans-serif;color:#222}
.sayfa{width:210mm;height:297mm;position:relative;overflow:hidden;page-break-after:always;padding:10mm 11mm 0}
.ust img{height:15mm;display:block;margin-left:2mm}
.serit{margin-top:6mm;background:#92D050;color:#fff;font-weight:700;font-size:15pt;padding:2.6mm 4mm;letter-spacing:.2px}
.altb{font-style:italic;color:#555;font-size:9pt;margin:1.6mm 0 5mm}
.giris{font-size:10.5pt;line-height:1.55;margin:0 0 6mm;color:#333}
.iki{display:grid;grid-template-columns:1fr 1fr;gap:4mm 9mm}
.bol h3{margin:0 0 1.8mm;font-size:11pt;color:#1F4E2C;border-left:1.2mm solid #92D050;padding-left:2.2mm}
.bol ul{margin:0 0 2mm;padding-left:6mm}
.bol li{font-size:9.6pt;line-height:1.5;margin-bottom:1mm}
.firma{margin-top:7mm;display:grid;grid-template-columns:1fr 1fr;gap:6mm;font-size:9.4pt;line-height:1.55}
.firma b.b{color:#4C7D2E;display:block;margin-bottom:1.2mm}
.firma .not li{margin-bottom:.6mm}
.alt{position:absolute;left:11mm;right:11mm;bottom:8mm;border-top:.35mm solid #92D050;padding-top:1.6mm;font-size:8pt;color:#2F5597}
.kartlar{display:grid;grid-template-columns:repeat(3,1fr);gap:2.2mm}
.kart{border:.3mm solid #dcdcdc;border-radius:2mm;display:grid;grid-template-columns:17mm 1fr;gap:2.4mm;align-items:center;padding:1.8mm 2.2mm;height:21.1mm;overflow:hidden}
.kart .lg{height:15mm;display:flex;align-items:center;justify-content:center;border:.25mm solid #eee;border-radius:1.4mm;padding:1mm;background:#fff}
.kart .lg img{max-width:100%;max-height:100%;object-fit:contain}
.kart b{display:block;font-size:8.6pt;color:#1F4E2C;line-height:1.2}
.kart .hz{font-size:7.4pt;color:#333;line-height:1.25;margin-top:.5mm}
.kart .tr{font-size:7.2pt;color:#777;margin-top:.4mm}
"""
def alt(): return '<div class="alt">YEŞİL TEMİZLİK | 0212 369 90 75 | www.yesiltemizlik.com</div>'
def ust(): return f'<div class="ust"><img src="{LOGO}"></div>'
for k, v in KAT.items():
    uygun = [r for r in refs if any(t in r["hizmet"].lower() for t in v["anahtar"])]
    gor = set(); secim = []
    for r in uygun + refs:
        if r["ad"] in gor: continue
        gor.add(r["ad"]); secim.append(r)
        if len(secim) == 30: break
    bol = "".join(f'<div class="bol"><h3>{e(b)}</h3><ul>{"".join(f"<li>{e(x)}</li>" for x in m)}</ul></div>' for b, m in v["bolumler"])
    kart = "".join(f'<div class="kart"><div class="lg"><img src="{b64(logo_yolu(r))}"></div><div><b>{e(r["ad"])}</b><div class="hz">{e(kisa(r["hizmet"], len(r["ad"])))}</div><div class="tr">{e(" · ".join(x for x in (r["tarih"], r["semt"]) if x))}</div></div></div>' for r in secim)
    h = f"""<!doctype html><html lang="tr"><head><meta charset="utf-8"><style>{CSS}</style></head><body>
<div class="sayfa">{ust()}<div class="serit">{e(v['baslik'])}</div><div class="altb">{e(v['alt'])}</div>
<p class="giris">{e(v['giris'])}</p><div class="iki">{bol}</div>
<div class="firma"><div><b class="b">YEŞİL TEMİZLİK</b>2012'den beri kurumsal ofis, plaza, fabrika, konut ve villa temizliğinde hizmet veriyoruz. Referanslarımız arasında yerel ve uluslararası firmalar, konsolosluklar, vakıf ve dernekler yer alıyor.<br><br>Firmamız 6331 Sayılı İş Sağlığı ve Güvenliği Kanunu'na uygun hizmet vermektedir.</div>
<div><b class="b">İLETİŞİM</b>Merkez: Halıcıoğlu Mh. Kapı Kahyası Sk. No:14/A Beyoğlu / İST<br>Şube: Merdivenköy İpekyolu Sk. No:36/2 Göztepe / İST<br>İrtibat: 0 (212) 369 90 75 - 0 (216) 329 90 75<br><br>Esra Alkılıç · 0530 680 90 75</div></div>
{alt()}</div>
<div class="sayfa">{ust()}<div class="serit">REFERANSLARIMIZ</div><div class="altb">Kurumsal referanslarımızdan seçmeler</div>
<div class="kartlar">{kart}</div>{alt()}</div>
</body></html>"""
    open(os.path.join(K, k + "2.html"), "w", encoding="utf-8").write(h)
    print(k, "uygun", len(uygun), "kart", len(secim))
