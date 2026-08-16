# Veri nerede duracak? (ve Firebase neden değil)

> Kısa cevap: **veri telefonda, SQLite'ta duracak.** Bulut, verinin *yaşadığı* yer değil,
> cihazlar arasında *taşındığı* ve *yedeklendiği* yer olacak.

---

## Neden Firebase değil

Firebase, personel takip gibi işlerde doğru araç: çok kullanıcı var, herkes farklı
cihazdan aynı veriye bakıyor, yetkilendirme gerekiyor, sunucu yazmak istemiyorsun.
Bizim durumumuzda bunların yalnızca biri var: iki cihaz. Tek kullanıcı, yetkilendirme
yok, paylaşım yok, sunucuda çalışması gereken mantık yok. İki cihazı senkronlamak için
veriyi buluta taşımak gerekmiyor — bunu Bölüm "İkinci cihaz"da anlatılan değişiklik
günlüğüyle çözüyoruz. Firebase'i eleyen somut sebepler:

**1. Yapmak istediğimiz sorgular Firestore'un yapamadığı sorgular.**
Planın merkezinde şunlar var: tek arama kutusundan tüm kayıtlarda tam metin arama,
etiket kesişimleri, "bu makaleye atıf veren notlar" (backlink), tarih aralığı +
tip + etiket birleşik filtreleri. Firestore bir doküman veritabanı: JOIN yok,
tam metin arama yok (bunun için ayrıca Algolia gibi bir servis bağlaman gerekir),
karmaşık filtreler için elle bileşik indeks tanımlarsın. SQLite'ta bunların hepsi
tek satır SQL. **Veri modelimiz ilişkisel; ilişkisel veritabanı kullanacağız.**

**2. Çevrimdışı çalışma bizde istisna değil, kural.**
Metroda not alacaksın, uçakta PDF işaretleyeceksin, kapsama olmayan yerde alışkanlık
işaretleyeceksin. Yerel veritabanında bu zaten varsayılan davranış. Firestore'un
çevrimdışı önbelleği var ama sınırlı ve senkron çakışmaları senin problemin oluyor.

**3. Hız.** Widget'lar, "Bugün" ekranı, arama — hepsi milisaniyelerle ölçülen
yerel sorgular. Ağ gecikmesi olan bir mimaride widget'ın anlık güncellenmesi
tatsız bir mühendislik problemine dönüşür.

**4. Gizlilik.** Bu uygulamada finans kayıtların, kişisel notların, "kasa"daki
önemli dosyaların olacak. Bunları başkasının sunucusuna koymak için bir sebep yok —
çünkü ortada paylaşılacak kimse yok. Veri hiç çıkmıyorsa sızmıyor da.

**5. Maliyet ve bağımlılık.** PDF'ler ve kasadaki dosyalar gigabaytlara çıkabilir;
Cloud Storage bunu ücretlendirir. Ayrıca ileride servis politikası değişirse
(Pocket'ın kapanması gibi) verin başkasının kararına bağlı olur.

**Tek gerçek dezavantaj:** telefon kaybolursa/bozulursa veri gider.
Bunu yedekleme katmanıyla çözüyoruz — ve o katman, Firebase'in getirdiği tüm
karmaşıklığın yanında çok daha basit.

---

## Kurduğumuz üç katman

### 1. Veritabanı — telefon içi, uygulamaya özel alan
`SQLite` (Room ile), uygulamanın özel dizininde: `/data/data/com.ahmety.uygulama/`.
Buraya başka hiçbir uygulama erişemez. Notlar, görevler, alışkanlıklar, kelimeler,
etiketler, PDF işaretlemeleri, finans kayıtları — yani **metin olan her şey** burada.
Bin sayfa not bile birkaç megabayt tutar.

Finans ve kasa modülleri geldiğinde bu veritabanı **SQLCipher** ile şifrelenecek;
anahtar Android Keystore'da (donanım destekli) duracak. Telefon rootlansa bile
veritabanı dosyası tek başına okunamaz.

### 2. Büyük dosyalar — telefonda görünür bir klasör
PDF'ler, kasadaki dosyalar, makale görselleri veritabanına **konmaz**; dosya sisteminde
`/storage/emulated/0/Merkez/` altında durur, veritabanında sadece yolları tutulur.
Neden uygulamaya özel alan değil de görünür klasör:

- Uygulamayı silsen/yeniden kursan dosyalar durur.
- Bir dosya yöneticisiyle doğrudan erişebilirsin — uygulamaya mahkûm değilsin.
- **Drive, Syncthing veya herhangi bir yedekleme uygulaması bu klasörü doğrudan
  senkronlayabilir.** Yani "bulut yedeği"ni biz yazmadan bile alabilirsin.

Kasadaki hassas dosyalar bu klasörde de şifreli durur (dosya bazında AES),
anahtar yine Keystore'da; biyometrik kilidin arkasında.

### 3. Yedek — senin kontrolünde, tek dosya
Her gece WorkManager tek bir şifreli arşiv üretir: veritabanı + dosyalar + sürüm bilgisi.
Bu arşiv `Merkez/yedek/` klasörüne yazılır ve istersen Drive'a kopyalanır
(Android'in kendi dosya seçicisiyle — API anahtarı, OAuth ekranı yok).
Son N yedek tutulur, eskiler silinir. Geri yükleme tek dosya seçmekle biter.

Ayrıca her modül **açık formatta dışa aktarılabilir** olacak: notlar Markdown,
finans CSV, kelimeler CSV, tüm kayıtlar JSON. Bu uygulama yarın kaybolsa bile
verin okunabilir kalsın diye.

---

## İkinci cihaz — artık gerçek bir gereksinim

İki telefon kullanıyorsun ve hangisinden girersen gir verinin aynı olmasını istiyorsun.
Bu, "ileride bakarız" maddesi olmaktan çıkıp **Faz 2** oldu.

Faz 0'da attığımız temel tam da bunun içindi: her kaydın otomatik artan `id`'sinin
yanında cihazdan bağımsız bir **`uuid`**'si, bir **`updatedAt`** zaman damgası ve
silindiğinde satırı yok etmek yerine işaretleyen bir **`deletedAt` mezar taşı** var.
Bu üçü, herhangi bir senkron algoritmasının ihtiyaç duyduğu asgari zemin.

Önemli olan şu: **veritabanı dosyasını Drive'a atıp indirmek işe yaramaz** — iki
cihazda da yazma yapıldığı anda birinin o günkü girdileri sessizce kaybolur.
Bunun yerine her cihaz kendi **değişiklik günlüğünü** yazacak, karşı taraf onu
okuyup uygulayacak. Böylece "A'da not yazdım, B'de görev tamamladım" durumunda
ikisi de yaşar.

Taşıyıcı olarak **Google Drive**'ı uygulama içinden kullanacağız (`drive.file`
kapsamı — Drive'ının geri kalanını görmez), yedek yol olarak da herhangi bir
klasör + Syncthing/FolderSync. Paylaşılan alana giden her şey cihazda şifrelenir.

Tasarımın tamamı: [`SENKRON.md`](SENKRON.md)

---

## Not: depo şu anda herkese açık

`AhmetYuksel5/Myuygulama` public bir GitHub deposu. Kod açık olmasında sakınca yok
(hatta güvenlik açısından iyi), ama şuna dikkat: **depoya hiçbir kişisel veri,
API anahtarı veya imzalama anahtarı girmeyecek.** Anahtarlar GitHub Secrets'ta
şifreli duracak, kişisel veriler zaten sadece telefonda olacak.
İstersen depoyu private yapabilirsin; Actions dakikaları public depolarda ücretsiz
olduğu için tek farkı bu olur.
