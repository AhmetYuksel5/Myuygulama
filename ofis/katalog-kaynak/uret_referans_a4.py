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
def foto(ad):
    im = Image.open(os.path.join(S, "foto", ad + ".webp")).convert("RGB")
    im.thumbnail((900, 600))
    b = io.BytesIO(); im.save(b, "JPEG", quality=82)
    return "data:image/jpeg;base64," + base64.b64encode(b.getvalue()).decode()
def logo_yolu(r): return os.path.join(S, "logo", r["logo"].strip("/").replace("/", "_"))

KAT = {
 "ofis": dict(baslik="OFİS TEMİZLİĞİ", alt="İstanbul'un iki yakasında düzenli ve tek seferlik ofis temizliği",
   giris="2012'den bu yana İstanbul'un Avrupa ve Anadolu yakasındaki ofis ve iş yerlerine düzenli ve tek seferlik temizlik hizmeti veriyoruz. Temizlik programını çalışma saatlerinize göre gün içinde, mesai sonrasında ya da hafta sonu kuruyor; görevli personel gelemediğinde yerine yedek personel gönderiyoruz.",
   cipler=["Haftada bir, üç gün ya da her gün", "Gün içinde, mesai sonrası, hafta sonu", "Tam zamanlı ya da part-time personel", "Yedek personel güvencesi"],
   fotolar=[("sahadan-ofis-masa-silme-ekibi-01", "Açık ofiste çalışma masaları"), ("sahadan-ofis-koltuk-kolcak-silme-01", "Sandalye kolçak ve ayakları"), ("sahadan-ofis-supurge-temizligi-01", "Halıfleks alanların süpürülmesi")],
   bolumler=[("Çalışma alanları", ["Masalar silinir, sandalyelerin oturak, kolçak ve ayakları tek tek temizlenir.", "Yüzeylere yüzey dezenfeksiyonu uygulanır, çöpler toplanır."]),
             ("Sık dokunulan yüzeyler", ["Duvardaki paneller ve dokunulan yüzeyler silinir.", "Cam kapı ve bölmelerdeki el izleri her gelişte temizlenir."]),
             ("Mutfak, lavabo ve tuvaletler", ["Mutfak temizlenir, çöpleri toplanır.", "Lavabo ve tuvaletler temizlenir, ıslak hacim zeminleri silinir."]),
             ("Zeminler ve planlı işler", ["Halıfleks alanlar ve koridorlar süpürülür, zeminler en sonda silinir.", "Camlar ve çerçeveler anlaşmadaki aralıklarla silinir; halıfleks kuru köpükle yıkanır."])],
   cagri="Fiyat için ofisinizin kısa bir videosunu ve yaklaşık metrekaresini WhatsApp'tan gönderin, teklifimizi hemen iletelim.",
   anahtar=("ofis", "personel", "halıfleks")),
 "bos": dict(baslik="BOŞ DAİRE TEMİZLİĞİ", alt="Taşınma öncesi ve sonrası, aynı gün teslim",
   giris="2012'den bu yana taşınma öncesi temizlikte her gün ortalama 2-3 ekiple binlerce kişiye hizmet verdik. Ekibimiz malzemelerle birlikte sabah gelir, daireyi baştan sona temizleyip akşamüzeri teslim eder; siz başında beklemek zorunda kalmazsınız.",
   cipler=["Aynı gün teslim, 09.00–16.00", "En az 2 kişilik ekip", "Malzeme ve ekipman bizden", "Buharlı temizlik", "Önce memnuniyet, sonra ödeme"],
   fotolar=[("bos-daire-oncesi-sonrasi-mutfak", "Mutfak: öncesi ve sonrası"), ("bos-daire-mutfak-dolap-temizligi-v03", "Dolap içleri ve davlumbaz"), ("bos-daire-banyo-derz-temizligi", "Banyoda derz temizliği")],
   bolumler=[("Camlar ve çerçeveler", ["Etiketler sökülür, çerçeve olukları buharla temizlenir.", "Camdaki boya ve harç, cam kazıma jiletiyle çizilmeden alınır."]),
             ("Mutfak", ["Dolapların içi, kapakları ve üstleri temizlenir.", "Aspiratör, fırın ve çevresindeki yağlar yağ çözücü ve buharla alınır."]),
             ("Banyo ve tuvalet", ["Klozet, küvet ve duşakabin yıkanıp parlatılır.", "Fayanslar yıkanır, derz aralarındaki kir giderilir."]),
             ("Duvarlar, kapılar ve zeminler", ["Duvar tozu kuru mopla alınır, priz ve anahtar çevreleri silinir.", "Balkon temizlenir; parkeler en son, odalar tek tek kapatılarak silinir."])],
   cagri="Fiyat için dairenin kısa bir videosunu WhatsApp'tan gönderin, fiyatınızı hemen iletelim.",
   anahtar=("boş", "taşınma", "lojman", "daire", "konut")),
 "insaat": dict(baslik="İNŞAAT SONRASI TEMİZLİK", alt="Boya, silikon, harç ve ince toz; her yüzeye uygun yöntemle",
   giris="2012'den bu yana İstanbul'un iki yakasında inşaat sonrası temizlik yapıyoruz. Ustalardan kalan boya ve silikon izlerini, harcı ve ince tozu yüzeyine uygun yöntemle temizliyor, daireyi en az iki kişilik ekiple genellikle aynı gün teslim ediyoruz. Ödemeyi işi birlikte kontrol ettikten sonra yaparsınız.",
   cipler=["En az 2 kişilik ekip", "Genellikle aynı gün teslim", "Buhar ve ıslak vakum makinesi", "Malzeme, yol ve yemek dahil", "Ödeme kontrolden sonra"],
   fotolar=[("insaat-sonrasi-daire-temizligi", "Yeni teslim alınan daire"), ("bos-daire-pencere-olugu-temizligi", "Pencere oluklarındaki ince toz"), ("ekip-doblo-sabah", "Sabah işe başlayan ekibimiz")],
   bolumler=[("Çerçeveler ve petekler", ["Koruyucu folyo ve etiketler sökülür, yapışkanı temizlenir.", "Oluk ve peteklerdeki toz buharla açılıp vakumla alınır."]),
             ("Camlar", ["Boya, harç ve silikon cam köpürtüldükten sonra jiletle kazınır.", "Camlar peluş ve çekçekle, dış yüz içeriden teleskopik sapla silinir."]),
             ("Mutfak ve banyo", ["Dolap içlerine çöken ince toz alınır, çekmeceler temizlenir.", "Derzlerdeki harç artıkları temizlenir, armatürler parlatılır."]),
             ("Kapılar, duvarlar ve zeminler", ["Priz, anahtar ve lamba çevresine sıçrayan boya temizlenir.", "Zeminler en son: seramik köpüklü vakumla, parke kuru köpükle temizlenir."])],
   cagri="Ustaların işi bittiyse mekânın kısa bir videosunu WhatsApp'tan gönderin; fiyatı hemen iletelim, temizlik gününü birlikte belirleyelim.",
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
.giris{font-size:10pt;line-height:1.5;margin:0 0 4mm;color:#333}
.iki{display:grid;grid-template-columns:1fr 1fr;gap:2.5mm 9mm}
.bol h3{margin:0 0 1.8mm;font-size:11pt;color:#1F4E2C;border-left:1.2mm solid #92D050;padding-left:2.2mm}
.bol ul{margin:0 0 2mm;padding-left:6mm}
.bol li{font-size:9.2pt;line-height:1.42;margin-bottom:.6mm}
.firma{margin-top:4mm;display:grid;grid-template-columns:1fr 1fr;gap:6mm;font-size:8.9pt;line-height:1.5}
.sayilar{display:grid;grid-template-columns:repeat(4,1fr);gap:1.5mm;margin:0 0 2mm}.sayilar div{font-size:7.4pt;color:#555;line-height:1.2}.sayilar b{display:block;font-size:12pt;color:#1F4E2C}
.firma b.b{color:#4C7D2E;display:block;margin-bottom:1.2mm}
.firma .not li{margin-bottom:.6mm}
.fotolar{display:grid;grid-template-columns:repeat(3,1fr);gap:3mm;margin:0 0 5mm}
.foto img{width:100%;height:47mm;object-fit:cover;border-radius:2mm;display:block}
.foto span{display:block;font-size:7.8pt;color:#666;margin-top:1mm}
.cipler{display:flex;flex-wrap:wrap;gap:1.8mm;margin:0 0 5mm}
.cip{background:#EEF6E6;border:.3mm solid #CFE6B8;color:#2F5D1E;font-size:8.6pt;font-weight:700;border-radius:4mm;padding:1.2mm 3mm}
.cagri{margin-top:4mm;background:#F3F8EE;border-left:1.2mm solid #92D050;padding:2.4mm 3.5mm;font-size:9.4pt;color:#264d16;font-weight:600}
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
    fot = "".join(f'<div class="foto"><img src="{foto(a)}"><span>{e(c)}</span></div>' for a, c in v["fotolar"])
    cip = "".join(f'<span class="cip">{e(c)}</span>' for c in v["cipler"])
    kart = "".join(f'<div class="kart"><div class="lg"><img src="{b64(logo_yolu(r))}"></div><div><b>{e(r["ad"])}</b><div class="hz">{e(kisa(r["hizmet"], len(r["ad"])))}</div><div class="tr">{e(" · ".join(x for x in (r["tarih"], r["semt"]) if x))}</div></div></div>' for r in secim)
    h = f"""<!doctype html><html lang="tr"><head><meta charset="utf-8"><style>{CSS}</style></head><body>
<div class="sayfa">{ust()}<div class="serit">{e(v['baslik'])}</div><div class="altb">{e(v['alt'])}</div>
<p class="giris">{e(v['giris'])}</p><div class="fotolar">{fot}</div><div class="cipler">{cip}</div><div class="iki">{bol}</div><div class="cagri">{e(v['cagri'])}</div>
<div class="firma"><div><b class="b">YEŞİL TEMİZLİK</b><div class="sayilar"><div><b>484+</b>kurumsal referans</div><div><b>8</b>ülke konsolosluğu</div><div><b>4.7★</b>Google (89 yorum)</div><div><b>2012</b>'den beri</div></div>Firmamız 6331 Sayılı İş Sağlığı ve Güvenliği Kanunu'na uygun hizmet vermektedir.</div>
<div><b class="b">İLETİŞİM</b>Merkez: Halıcıoğlu Mh. Kapı Kahyası Sk. No:14/A Beyoğlu / İST<br>Şube: Merdivenköy İpekyolu Sk. No:36/2 Göztepe / İST<br>İrtibat: 0 (212) 369 90 75 - 0 (216) 329 90 75<br><br>Esra Alkılıç · 0530 680 90 75</div></div>
{alt()}</div>
<div class="sayfa">{ust()}<div class="serit">REFERANSLARIMIZ</div><div class="altb">Kurumsal referanslarımızdan seçmeler</div>
<div class="kartlar">{kart}</div>{alt()}</div>
</body></html>"""
    open(os.path.join(K, k + "2.html"), "w", encoding="utf-8").write(h)
    print(k, "uygun", len(uygun), "kart", len(secim))
