# Kişisel Süper Uygulama — Yol Haritası

> Tek APK, tek cihaz, tek kullanıcı (sen). Play Store yok, kısıtlama yok, veri senin cihazında.

---

## 0. En önemli karar: bu "12 uygulama" değil, "1 çekirdek + 12 yüz"

İstediğin şeyleri saydığımızda 12 ayrı modül çıkıyor: alışkanlık, yapılacaklar, takvim,
kelime, oku-sonra, not, kenar hareketleri, haber, PDF, bilgi arşivi, dosya kasası, finans.

Bunları 12 ayrı ekran gibi yazarsak sonuç, tek APK içine tıkıştırılmış 12 yarım uygulama olur —
yani şu an telefonunda olan durumun aynısı, sadece tek ikonla. Kazanç sıfır.

Bunun yerine mimarinin merkezine **tek bir "Kayıt" (Entry) çekirdeği** koyuyoruz:

```
Entry(id, tip, başlık, içerik, kaynak, tarih, etiketler[], bağlantılar[])
   ├─ tip = NOT          → Keep yerine
   ├─ tip = MAKALE       → Pocket yerine (readability ile süzülmüş)
   ├─ tip = PDF          → doküman + işaretlemeler
   ├─ tip = ALINTI       → makale/PDF içinden seçtiğin pasaj
   ├─ tip = KELIME       → İngilizce kelime + örnek cümleler
   ├─ tip = GOREV        → yapılacak
   └─ tip = HABER        → RSS öğesi
```

Bunun getirdikleri — ve "entelektüel birikim" isteğinin gerçek cevabı bu:

- **Tek arama kutusu.** Notlarında, kaydettiğin makalelerde, PDF işaretlemelerinde,
  kelimelerinde, görevlerinde aynı anda arama (SQLite FTS tam metin indeksi).
- **Tek etiket sistemi.** `#stoacılık` etiketi hem bir nota, hem bir makaleye,
  hem bir PDF alıntısına, hem bir kelimeye yapışır. Etikete tıklayınca hepsi birden gelir.
- **Bağlantılar (backlink).** Bir notun içinden bir makaleye atıf verirsin; makaleyi
  açtığında "bu kaynağa atıf veren 3 notun var" görürsün. Zettelkasten mantığı.
- **Tek yedek, tek dışa aktarım.** Her şey aynı veritabanında → tek dosyayla yedek.
- **Zincirleme akış.** Makale oku → pasajı işaretle → alıntı Entry olur → içindeki
  bilmediğin kelimeyi seç → kelime Entry'si olur → ertesi gün tekrar kartında karşına çıkar.
  Bu zincir ancak ortak çekirdek varsa kurulabilir; 12 silo ile kurulamaz.

**Özet ilke:** Modüller veriyi *sahiplenmez*, sadece çekirdeğin üzerine *görünüm* koyar.

---

## 1. Teknoloji kararları (ve neden)

| Konu | Karar | Gerekçe |
|---|---|---|
| Dil / UI | Kotlin 2.x + Jetpack Compose (Material 3, dinamik renk) | Tek dil, tek UI toolkit; widget'lar da aynı ekosistemde (Glance) |
| Mimari | Tek Activity + Compose Navigation, MVVM, çok modüllü Gradle | 12 modülü tek modülde tutarsak derleme süresi ve karmaşa patlar |
| Veritabanı | Room (SQLite) + **FTS4 tam metin arama** | Tek veritabanı, tek migration hattı, modüller arası sorgu kolay |
| Şifreleme | SQLCipher (finans + kasa geldiğinde), anahtar Android Keystore'da | Telefon kaybolursa finans ve kasa verisi okunamasın |
| DI | Hilt | Standart, KSP ile hızlı |
| Arka plan | WorkManager (senkron, RSS çekme, yedek) | Doze/pil kısıtlarına uyumlu |
| Ayarlar | DataStore | SharedPreferences'ın modern hâli |
| Widget | **Glance** (+ karmaşık görseller için Canvas → Bitmap) | Aşağıda "yıllık widget" notuna bak |
| Ağ | OkHttp + Retrofit + kotlinx.serialization | — |
| Makale süzme | Jsoup + Readability4J | Mozilla Readability'nin JVM portu = Pocket'ın yaptığı iş |
| PDF | Görüntüleme: `PdfRenderer` (sistemde var) · İşaretleme: PDFBox-Android | Aşağıdaki PDF notuna bak |
| Takvim | **CalendarContract** (cihazın takvim sağlayıcısı) | Aşağıdaki takvim notuna bak — OAuth'a hiç girmiyoruz |
| Microsoft To Do | MSAL + Microsoft Graph (`/me/todo/lists`, delta sorgu) | Tek resmî yol, API'si var |
| Yapay zekâ | Claude API, anahtar cihazda şifreli; arayüz soyut (`AiProvider`) | Kelime cümlesi, makale özeti, haber derlemesi |
| Derleme | Gradle Kotlin DSL + version catalog, KSP (kapt yok) | Hız |
| Dağıtım | GitHub Actions → imzalı APK → telefona indir | Bölüm 6 |

**Sürüm hedefi:** `minSdk 29` (Android 10), `targetSdk` en güncel.
Play Store'da olmadığımız için normalde reddedilecek her şey bize serbest:
`MANAGE_EXTERNAL_STORAGE` (tüm dosyalara erişim), `AccessibilityService`,
`SYSTEM_ALERT_WINDOW` (üste çizim), `QUERY_ALL_PACKAGES`, `SCHEDULE_EXACT_ALARM`,
pil optimizasyonundan muafiyet. Hepsini isteyeceğiz, ilk açılışta tek tek yönlendireceğiz.

### Modül yapısı

```
:app                      → giriş, navigasyon, izin sihirbazı
:core:model               → Entry, Tag, Link ve ortak veri tipleri
:core:database            → Room, DAO'lar, FTS, migration'lar
:core:designsystem        → tema, renk, tipografi, ortak bileşenler
:core:datastore :core:network :core:common :core:sync :core:ai
:feature:home             → "Bugün" ekranı (her şeyin buluştuğu yer)
:feature:habits :feature:tasks :feature:calendar :feature:notes
:feature:reader :feature:vocab :feature:news :feature:docs
:feature:vault :feature:finance :feature:gestures :feature:search :feature:settings
:widget                   → Glance widget'ları
```

---

## 2. Sorduğun soru: Fluid NG nedir, neden Play Store'da yok?

**O uygulama Fluid NG** (eski adıyla *Fluid Navigation Gestures*), XDA çevresinden çıkmış,
Play Store dışından APK olarak dağıtılan bir gezinme uygulaması. Ekranın kenarında ince bir
şerit çizer; o şeritten yukarı kaydırınca son uygulamalar, aşağı kaydırınca bildirim paneli
gelir — tam senin tarif ettiğin davranış.

**Neden Play Store'da yayınlanamıyor:** Google'ın politikası, `AccessibilityService` API'sinin
yalnızca *erişilebilirlik amacıyla* (görme/işitme/motor engel desteği) kullanılmasına izin
veriyor. Gezinme jesti bu tanıma girmiyor. Ama teknik olarak "son uygulamaları aç" veya
"bildirim panelini indir" komutlarını verebilmenin **tek** yolu erişilebilirlik servisidir
(`performGlobalAction(GLOBAL_ACTION_RECENTS / GLOBAL_ACTION_NOTIFICATIONS)`).
Yani uygulama, işini yapabilmek için politikayı ihlal etmek zorunda → mağaza dışı kalıyor.

**Senin güvenlik endişen doğru ve yerinde.** Ama risk sandığın yerde değil:

- Asıl risk "üstte gözükmesi" değil. Asıl risk şu: bir erişilebilirlik servisi, ekranındaki
  **tüm metni okuyabilir** — banka uygulamandaki bakiyeyi, yazdığın şifreyi, gelen SMS'i —
  ve **senin adına dokunma/yazma yapabilir.** Kapalı kaynak, mağaza dışı bir APK'ya bu yetkiyi
  vermek, telefonunu o geliştiriciye emanet etmek demektir.
- Ayrıca APK'yı mağaza dışından aldığın için güncelleme kanalı da denetimsiz.

**Çözüm, tam da bu projenin varlık sebebi:** Aynı özelliği kendi uygulamamıza koyuyoruz.
Kodunu sen görüyorsun, imzasını sen atıyorsun, dışarıya tek bayt gitmiyor. Ek olarak:

- Katmanı `TYPE_ACCESSIBILITY_OVERLAY` ile çiziyoruz → "diğer uygulamaların üstünde göster"
  iznine gerek kalmadan, tam ekran uygulamaların üstünde de çalışır ve sistem tarafından
  daha iyi yönetilir.
- Servisin `accessibility_service_config.xml` dosyasında **pencere içeriği okuma bayrağını
  hiç açmıyoruz** (`canRetrieveWindowContent="false"`). Yani uygulama, ekranındaki hiçbir
  metni okuyamaz — sadece jesti alır ve global komutu tetikler. Fluid NG'nin veremediği garanti bu.
- Ekstra: uygulama bazlı devre dışı bırakma, kenar kalınlığı/uzunluğu/konumu, titreşim,
  ve senin ek isteklerin (ör. sağdan sola → geri, uzun basış → belirlediğin uygulama).

Efor açısından bu modül küçük (birkaç yüz satır) ama **güvenlik kazancı anında**.
Bu yüzden fazlar arasında erkene aldım.

---

## 3. Zor/riskli konularda dürüst değerlendirme

### Takvim — Google senkronu için OAuth'a hiç girmiyoruz
Telefonunda Google hesabın zaten ekli ve Google'ın kendi senkron mekanizması takvimi
cihazın **CalendarContract** sağlayıcısına yazıyor. Biz Google Calendar API'sine gitmek
yerine doğrudan bu sağlayıcıyı okuyup yazacağız:

- OAuth ekranı yok, API kotası yok, token yenileme derdi yok, internet gerekmiyor.
- Biz yazdığımızda Google'ın senkron adaptörü bulut tarafına kendisi taşır → iki yönlü senkron bedava.
- Çoklu takvim (kişisel / iş / doğum günleri) doğrudan destekli.

Bu, projenin en büyük "az işle çok kazanç" kararı. İhtiyaç: `READ_CALENDAR` + `WRITE_CALENDAR`.

### Microsoft To Do — burada kestirme yol yok
Cihazda içerik sağlayıcısı yok, tek yol Microsoft Graph API. Gerekenler:
Azure'da ücretsiz bir "uygulama kaydı" (senin yapman gereken ~10 dakikalık bir işlem,
adım adım yazacağım), MSAL ile giriş, `delta` sorgularıyla artımlı senkron, çakışma çözümü.
**Orta-büyük efor.** Bu yüzden Faz 1'de görevleri önce yerel yazıyoruz, Graph senkronunu
Faz 3'te üstüne takıyoruz — sen Faz 1'den itibaren uygulamayı kullanabilir hâlde oluyorsun.
(Alternatif: To Do'yu tamamen terk edip görevleri buraya taşımak. Sana soruyorum.)

### PDF ve depolama alanı endişen — haklısın, ama çözümü var
"PDF'ler okuma/yazma alanımı yer mi?" diye sordun. Ölçüler şöyle:
tipik bir kitap PDF'i 2–20 MB, makale PDF'i 0.3–3 MB. 500 doküman ≈ 3–5 GB.
Bugünün telefonunda yönetilebilir ama başıboş bırakılırsa şişer. Aldığımız önlemler:

- **İşaretlemeler PDF'in içine gömülmez.** Vurgular/notlar ayrı bir tabloda
  (sayfa no + koordinat + renk + not metni) tutulur → birkaç kilobayt, geri alınabilir,
  aranabilir, ve orijinal dosya hiç bozulmaz. "Düz PDF olarak dışa aktar" ayrı bir komut.
- **Alıntılar Entry olur** → PDF'i silsen bile bilgi birikimin kalır.
- **Depolama paneli:** hangi modül ne kadar yer kaplıyor, tek ekranda; büyük dosyaları
  "buluta taşı, yerelden sil, gerekince indir" seçeneği.

### Haberler — Twitter/Instagram yok, RSS var
İstediğin "iyi kaynaklardan derleme" için doğru araç RSS/Atom. Kaynak listesi senin
kontrolünde (OPML içe aktarma da olacak), ilgi alanına göre klasörler, anahtar kelime
filtreleri, ve isteğe bağlı **günlük yapay zekâ derlemesi**: "bugünkü 60 başlıktan
seni ilgilendiren 7'si ve neden" özeti. Beslemesi olmayan siteler için de tek tek
sayfa çekip readability ile süzebiliriz.

### Yapay zekâ özellikleri
Kelime için örnek cümle üretimi, makale özeti, haber derlemesi, notlarda "bunu bana
açıkla" — hepsi Claude API ile. Bunun için kendi API anahtarın gerekiyor (cihazda
şifreli saklanır, tek kullanıcı olduğu için maliyeti çok düşük).
İnternet yokken veya anahtar yokken uygulama çalışmaya devam eder, sadece bu özellikler
susar — kod bunu baştan varsayacak şekilde yazılacak.

### Widget'lar — "yıllık" widget'ın teknik notu
Widget'lar `RemoteViews` üzerinde çalışır; eleman sayısı ve iç içe geçme sınırlıdır.
Günlük ve aylık widget'lar Glance ile doğrudan çizilebilir. Ama **365 kutuluk yıllık ısı
haritası** widget'ı Glance bileşenleriyle çizilirse sınırı zorlar → onu `Canvas` ile
Bitmap olarak çizip widget'a tek görsel olarak basacağız. Sonuç daha hızlı ve daha güzel.

Planlanan widget seti: Bugün (alışkanlık + görev + ajanda) · Alışkanlık haftalık ·
Alışkanlık yıllık ısı haritası · Ay takvimi · Hızlı not · Günün kelimesi · Bütçe özeti.

### Yedekleme
Her gece: tek şifreli arşiv (veritabanı + dosyalar) → cihazda bir klasör, istersen
Drive klasörüne kopya. Ayrıca her şey açık formatta dışa aktarılabilir (Markdown, CSV,
JSON) — bu uygulamaya mahkûm kalmayasın diye.

---

## 4. Faz planı

Her fazın sonunda **telefonuna kurulabilir bir APK** çıkar. Sıra, "en erken günlük
kullanıma girsin" ilkesine göre dizildi.

### Faz 0 — Temel (S)
Gradle iskeleti, modüller, tema/tasarım sistemi, Room + FTS çekirdeği, Entry/Tag/Link
modeli, navigasyon, ilk açılış izin sihirbazı (tüm izinleri tek tek isteyen ekran),
GitHub Actions ile imzalı APK üretimi.
→ **Çıktı:** kurulan, açılan, boş ama iskeleti sağlam uygulama.

### Faz 1 — Günlük çekirdek (L)
- **Alışkanlıklar:** günlük/haftalık/x-kere hedefler, seri (streak) takibi, hatırlatıcı,
  günlük–aylık–yıllık görünümler.
- **Görevler:** yerel liste, alt görev, tarih, öncelik, tekrar.
- **Takvim:** CalendarContract ile okuma/yazma, gün ve ay görünümü.
- **Bugün ekranı:** alışkanlıklar + bugünün görevleri + ajanda tek ekranda.
- **Widget'lar:** Bugün, alışkanlık haftalık, alışkanlık yıllık, ay takvimi.
→ **Çıktı:** uygulamayı her gün açmaya başlarsın. Projenin kaderi bu fazda belli olur.

### Faz 2 — Yakala ve sakla (M) + Kenar hareketleri (S)
- **Notlar:** Markdown editör, etiket, sabitleme, hatırlatıcı, kontrol listesi, arşiv.
  Keep'ten Google Takeout JSON'u ile toplu içe aktarma.
- **Paylaş hedefi:** herhangi bir uygulamadan "Paylaş" → uygulamamıza not/link/kelime olarak düşer.
- **Oku-sonra:** URL kaydet → Readability ile süz → çevrimdışı sakla → okunabilir tipografiyle
  oku, vurgula, alıntıla. (Pocket'ın yaptığı iş.)
- **Genel arama:** her şeyi kapsayan tek arama + etiket gezgini + backlink'ler.
- **Kenar hareketleri:** Bölüm 2'deki güvenli Fluid NG yerine geçen modül.
→ **Çıktı:** Keep, Pocket ve Fluid NG telefondan silinir.

### Faz 3 — Senkronizasyon (M)
Microsoft To Do çift yönlü senkron (Azure kaydı + MSAL + delta), takvim ince ayar,
çakışma çözümü, senkron durumu ekranı.
→ **Çıktı:** To Do'daki her şey uygulamada, uygulamadaki her şey To Do'da.

### Faz 4 — İngilizce kelime (M)
Kelime listesi içe aktarma (CSV/TXT), yapay zekâ ile anlam + örnek cümle + eş anlamlı +
telaffuz, aralıklı tekrar (SM-2 algoritması), günlük tekrar kartları, "Günün kelimesi"
widget'ı, okuduğun makale/PDF içinden kelime madenciliği (bilmediğin kelimeyi seç → listeye ekle),
bildiklerin/öğrenmekte olduklarınla ilerleme istatistikleri.

### Faz 5 — Haberler (S-M)
RSS/Atom motoru, OPML içe aktarma, ilgi klasörleri, anahtar kelime filtresi,
günlük yapay zekâ derlemesi, tek dokunuşla "oku-sonra"ya at.

### Faz 6 — Dokümanlar ve kasa (L)
PDF görüntüleyici (kaydırma, yakınlaştırma, içindekiler, arama), vurgulama/not/çizim
katmanı, alıntı → Entry akışı, düz kopya dışa aktarma.
**Kasa:** önemli dosyalar için şifreli depo, kategori/etiket, hızlı önizleme,
biyometrik kilit, depolama paneli.

### Faz 7 — Finans (L)
Hesaplar ve varlıklar (nakit, banka, altın, döviz, yatırım), gelir/gider defteri,
kategoriler, tekrarlayan işlemler, aylık bütçe, Excel/CSV içe aktarma
(banka ekstresi eşleme sihirbazı ile), grafikler, ay sonu raporu,
opsiyonel döviz/altın kuru güncelleme.
→ **Not:** Bu faz açılmadan **önce** veritabanı şifrelemesi (SQLCipher) devreye alınır.

### Faz 8 — Cilalama (sürekli)
Performans, pil, animasyon, yedek/geri yükleme testi, arama iyileştirme,
kullandıkça çıkan istekler.

**Efor işaretleri:** S = küçük, M = orta, L = büyük. Faz 1, 6 ve 7 en ağır olanlar.

---

## 5. Senden gerekecekler (fazına gelince tek tek isteyeceğim)

| Ne zaman | Ne gerekiyor |
|---|---|
| Faz 0 | Uygulama adı ve paket adı; APK imzalama anahtarı (ben üretirim, sen saklarsın) |
| Faz 1 | Alışkanlık listen; widget'ları nasıl dizmek istediğin |
| Faz 2 | Keep yedeğin (Google Takeout) |
| Faz 3 | Azure'da ücretsiz uygulama kaydı (adımlarını yazacağım) |
| Faz 4 | Claude API anahtarı; varsa mevcut kelime listen |
| Faz 5 | Takip ettiğin siteler / ilgi alanların |
| Faz 7 | Örnek Excel dosyaların (kolon yapısını görmem için), hesap/kategori listen |

---

## 6. Derleme ve telefona ulaştırma (önemli teknik kısıt)

Bu oturumun çalıştığı sunucuda `dl.google.com` ağ politikası nedeniyle kapalı; Android SDK ve
AndroidX paketleri buraya inemiyor. Yani **kodu buradan derleyip test edemiyorum.** İki çözüm:

1. **GitHub Actions'ı derleyici olarak kullanmak (önerilen, hemen çalışır).**
   Her push'ta CI derler; hata varsa ben CI kayıtlarını okuyup düzeltirim.
   Her fazın sonunda imzalı APK bir GitHub Release olarak yayınlanır → telefonundan
   linke tıklayıp kurarsın. İmzalama anahtarı GitHub Secrets'ta şifreli durur.
   Bonus: güncellemeler için düzgün bir kanalın olur.
2. **Ortamın ağ politikasını genişletmek.** `dl.google.com` açılırsa burada da derleyip
   daha hızlı yineleyebilirim. (Bu, oturum ortamı ayarından yapılıyor.)

En iyisi ikisi birden: 1 zaten kalıcı dağıtım kanalı, 2 geliştirme hızını artırır.

---

## 7. Bu planın zayıf noktaları (bilerek kabul ettiklerimiz)

- **Kapsam büyük.** 12 modül tek seferde bitmez; fazlara bölünmesinin sebebi bu.
  Her faz sonunda kullanılabilir bir uygulama olması pazarlık dışı.
- **Tek kullanıcı varsayımı.** Çok cihaz/çok kullanıcı senaryosu için tasarlamıyoruz;
  bu, işin yarısını eliyor. İleride ikinci cihaz istersen yedek dosyası üzerinden çözeriz.
- **Kendi kendine güncelleme yok** (Play Store olmadığı için). GitHub Release + uygulama
  içinde "yeni sürüm var" bildirimi ile telafi ediyoruz.
- **Yapay zekâ özellikleri internet ve API anahtarı ister.** Çekirdek işlevlerin hiçbiri buna bağlı değil.
- **PDF işaretleme, uygulamanın en zor teknik parçası.** Faz 6'ya konmasının sebebi bu;
  gerekirse önce "oku + vurgula", sonra "serbest çizim" diye ikiye bölünür.
