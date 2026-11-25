# Merkez

Tek kişilik bir Android uygulaması: kitap ve film altyazısı okurken
bilinmeyen kelimeleri işaretleyip tekrar programıyla çalışmak.

- Paket: `com.ahmety.uygulama`
- Geliştirme dalı: `claude/android-personal-management-app-1icryt`
- Dağıtım: dala her gönderim GitHub Actions'ı çalıştırır; imzalı APK
  GitHub Releases'e düşer (`merkez-<sürüm>.apk`). Sürüm numarası çalıştırma
  numarasından gelir.
- Yerel derleme: 2026-11-24'te `dl.google.com` açıktı. SDK komut satırı
  araçları geçici klasöre kurulup `ANDROID_HOME` ile `./gradlew
  :ofis:assembleRelease` yerelde geçti. Kapalıysa doğrulama yalnızca CI'da.

## Değişikliği biriktirme

Bazı değişiklikler bir sürüm çıkarmaya değmeyecek kadar küçük: bir yazının
metnini düzeltmek, bir düğmenin adını değiştirmek, gereksiz bir satırı
kaldırmak. Bunlar için ayrı bir derleme başlatmak hem beş dakika hem de
telefona kurulacak yeni bir APK demek.

**Anahtar kelime: `biriktir`**

Kullanıcının iletisinde `biriktir` geçiyorsa (eş anlamlıları: `acelesi yok`,
`sonraki sürüme`) o değişiklik için sürüm çıkarılmaz:

1. Değişiklik normal şekilde yapılır.
2. Commit iletisinin **son satırına** `[skip ci]` eklenir. GitHub bu işareti
  gören gönderimde iş akışını hiç başlatmaz.
3. Gönderim yine de yapılır — iş kaybolmasın ve dal geride kalmasın diye.
4. Kullanıcıya "biriktirildi, bir sonraki sürümde gelecek" denir. Bağlantı
  verilmez, derleme beklenmez.

**Biriktirilenleri çıkarmak:** kullanıcı `yayınla` derse ya da anahtar kelime
taşımayan bir istek gelirse, o gönderim `[skip ci]` taşımaz ve tek bir
derleme o ana kadar biriken her şeyi birden yayınlar.

Önemli ayrıntı: GitHub yalnızca **en son commit'in** iletisine bakar. Bu
yüzden biriken commit'lerin hepsinde işaretin bulunması gerekmez; yeter ki
biriktirme sırasındaki son commit taşısın, yayınlama sırasındaki taşımasın.

**Tuzak:** GitHub işareti iletinin *herhangi bir yerinde* arıyor, yalnızca
son satırında değil. Bu yüzden işaretten söz eden bir cümle bile derlemeyi
iptal ediyor — bir kez oldu, "künye satırları (… ) nota ait değil" diye
yazan bir madde yüzünden sürüm çıkmadı. İşaretten bahsedilecekse adı
yazılmadan bahsedilmeli.

## Commit iletisi = sürüm notu

Sürüm notu doğrudan commit iletisinden geliyor ve uygulamanın içindeki
güncelleme penceresinde okunuyor. Bu yüzden ileti gövdesi **maddeli**
yazılıyor:

```
Kısa başlık

- Ne değişti, tek cümle.
- Bir şey neden öyle yapıldı.
```

Pencere tek satır sonlarını geri açıyor (sarma sayıyor), boş satırı
paragraf ayırıcı sayıyor ve `- ` ile başlayan satırı madde yapıyor.
Künye satırları (yardımcı yazar, oturum bağlantısı, `[skip ci]`)
gösterilmiyor.

## Çalışma biçimi

- Bir şey yapmadan önce ne yapacağını söyle; kullanıcı bunu açıkça istedi.
- Bir varsayımın yanlış çıkarsa üstünü örtme, düzelt ve söyle.
- Yorumlar Türkçe ve "neden" anlatır, "ne" değil.

## Tarayıcı sürümü (`docs/`)

Abisinin iPhone'u için aynı uygulamanın tarayıcı sürümü. GitHub Pages
bu daldaki `docs/` klasöründen yayınlıyor; her gönderimde kendiliğinden
güncelleniyor, APK ile ilgisi yok. Yalnız `docs/` değişen bir gönderim
için Android derlemesi gereksiz — o gönderimler `[skip ci]` taşımalı.

- Çerçeve yok, derleme adımı yok: düz ES modülleri, IndexedDB, elle
  yazılmış ZIP okuyucu (`js/epub.js`), pdf.js `lib/` altında.
- Dosyalar: `js/app.js` (ekranlar, okuyucu, seçim kutusu, liste),
  `js/depo.js` (IndexedDB: kitap, dosya, kelime, ayar, sozluk),
  `js/yapayzeka.js` (OpenAI yönergeleri), `js/tekrar.js` (tekrar
  merdiveni), `app.css`, `index.html`, `sw.js`.
- `sw.js` içindeki `SURUM` her değişiklikte artırılmalı; yoksa telefon
  eski dosyaları önbellekten okumaya devam ediyor.
- Doğrulama: `node --input-type=module -e 'import("./docs/js/app.js")'`
  sözdizimini kontrol ediyor ("document is not defined" hatası normal,
  modül yüklenmiş demek). Saf fonksiyonlar Node'da ayrıca deneniyor.
- Kararlar: sistem seçimi kapalı, seçimi kendimiz yapıyoruz (iPhone'un
  menüsüne madde eklenemiyor); işaret anahtarı noktalamasız; yapay zekâ
  cevabında yalnız iki dil (Türkçe + metnin kendi dili); bir kez alınan
  çeviri/kart `sozluk` deposunda kalıyor.

## Merkez'in web sürümü (`web/` → `docs/merkez/`)

Merkez'in tarayıcı sürümü. Kaynak `web/` altında, derlenmiş çıktı
`docs/merkez/` altına yazılıyor ve depoya konuyor.

- Kaynak: React + TypeScript + Vite. `cd web && npm install` bir kez;
  sonra `npm run build` çıktıyı `docs/merkez/` içine üretiyor.
- Adres: `https://ahmetyuksel5.github.io/Myuygulama/merkez/`. Kökteki
  sayfa Arapça okuyucunun, Merkez onun altında ayrı klasörde.
- Çıktı neden depoda: GitHub sayfayı `docs/` klasöründen olduğu gibi
  sunuyor, derleme yapmıyor. Yayınlamayı iş akışına taşısaydık,
  biriktirme işareti taşıyan bir gönderim siteyi de yayınlamaz olurdu.
- **Derlemeden gönderme.** `docs/merkez/` elle düzenlenmez; kaynağı
  değiştirip yeniden derlemek gerekiyor, yoksa sayfa eski kalır.
- Tasarım dili tek dosyada: `web/src/tasarim.css`. Renk, punto, köşe
  yarıçapı ve aralık oradan geliyor; ekranlarda çıplak değer yok.
- Görünüm yönü "kartlı": her bölüm kendi kartı ve kendi rengi, alt
  çubukta açık sekmenin simgesi renkli bir hapın içinde.
- Yalnız `web/` ve `docs/` değişen gönderimlerde Android derlemesi
  gereksiz; o gönderimler iş akışını atlatan işareti taşımalı.
- Servis işçisi henüz yok. Sebebi bilinçli: kökteki okuyucunun servis
  işçisi etkinleşirken kendi sürümü dışındaki bütün önbellekleri
  siliyor ve aynı alan adında olduğumuz için Merkez'inkini de silerdi.
  Çevrimdışı desteği, o dosya kendi önbelleklerini ayırt eder hâle
  geldiğinde eklenecek.

## My Asistan (`ofis/`)

Yeşil Temizlik'in ofis asistanı; ilk kullanıcı ofis telefonu (Esra Hanım,
Redmi Note 12S / MIUI 14). İleride başka iş özellikleri de buraya eklenecek.
Ad yalnız görünen addır; paket `com.yesiltemizlik.ofis`, kişisel modüllerin hiçbirini
taşımaz, değiştirilmez (kurulu sürüm güncellenemez olur). CI onu
`ofis-v0.N` etiketiyle, yalnız `ofis/` değişince yayınlar;
yalnız `ofis/` değişen gönderim Merkez sürümü çıkarmaz.

- **Sürüm numarası Merkez'den bağımsız** (kullanıcı 2026-11-24: "1.0'dan
  başlasın"): görünen ad 1.0, 1.1… = 258. derlemeden sonraki `ofis-v0.*`
  etiketi sayısı (CI `OFIS_SURUM`; sürüm başlığı "My Asistan 1.x", dosya
  `my-asistan-1.x.apk`). Etiketteki N ve gizli sürüm kodu derleme numarası
  olarak kalıyor: güncelleme karşılaştırması onunla, eski kurulumlar da
  güncellemeyi buluyor. Uygulama görünen adı sürüm başlığından okur.

- Görüşme bitince kart: `AramaAlicisi` (PHONE_STATE, OFFHOOK → IDLE) →
  `KartAcici` (pencere + yedek bildirim) → `KartActivity`. Numara/süre/yön
  arama kaydından okunur.
- Kart: üç düğme, aynı çizgi simge dili (`res/drawable/s_*.xml`, 24 birim,
  2 kalınlık). İş arayan → yaka → ilçe → o ilçedeki işler (+ diğer işler,
  aynı yaka önce) → ad, not → Kaydet. Müşteri → ad, not → Kaydet. WhatsApp →
  numarayla sohbet (önce Business, sonra WhatsApp). Pencerenin dışına
  dokununca kapanır (kullanıcı 2026-11-24). Sağ üstte küçük ×: dokununca
  kapatır, uzun basınca "bu numarada kart çıkmasın mı" (kullanıcı
  2026-11-25). Liste `Depo.engelliler` (son 10 hane); Ayarlar'da numaraya dokununca
  "yeniden çıksın mı" penceresi (satırda düğme yok).
  Alıcı kartı açmadan önce arama kaydına en çok 3 sn bakar (`goAsync`);
  listedeki numarada kart da bildirim de çıkmaz.
- Ana ekran = son aramalar (arama kaydından); dokununca o aramanın kartı
  açılır, kartı doldurulan arama etiketlenir (`Depo.siniflar`, anahtar arama
  başlangıcı). Eşleşme, iş listesi, izinler, kartı dene Ayarlar'da; ana
  ekranda yalnız "Kurulum eksik" satırı.
- İşler panelden: PersonelTakip deposundaki `Ofis.gs`. Anahtar yok; panelde
  Log › Cihazlar › My Asistan › Eşleştirme kodu (6 hane, 10 dk) bir kez
  girilir, cihaz jetonu alınır. Kayıt panelde `aday_gorusme`e düşer.
- Kayıt önce telefona (`Depo.kuyruk`) yazılır, sonra gönderilir; kimlik
  telefonda üretildiği için tekrar gönderim ikinci kayıt açmaz.
- MIUI: otomatik başlatma, "arka planda açılır pencere", kilit ekranı
  izinleri izin ekranında; durumları AppOps 10008/10021/10020'den okunur.
  Erişilebilirlik servisi bilerek boş: açık durması MIUI'nin süreci
  öldürmesini engelliyor.
- Güncelleme uygulama içinde (`Guncelleme.kt`, `GuncellemeArayuz.kt`):
  açılışta yeni sürüm varsa "Yeni güncelleme var" penceresi (Güncelle/Sonra),
  görüşme kartının üstünde şerit (saatlik önbellek, GitHub'ın girişsiz
  60/saat sınırı), Ayarlar'da Denetle/Güncelle satırı. Kurulum
  PackageInstaller oturumuyla: uygulama kendi kurucusu olunca Android 12+
  sonraki güncellemeleri sormadan kurar (`USER_ACTION_NOT_REQUIRED`). İlk
  güncelleme (dosya yöneticisinden kurulmuş) bir kez onay ister. Sessiz
  güncelleme kart kapanınca / uygulamadan çıkınca tetiklenir, görüşme
  sırasında değil (kurulum süreci öldürür). Onay gerekirse `KurulumAlicisi`
  bildirim düşer. Oturum açılamazsa eski yol: FileProvider + kurulum ekranı.
