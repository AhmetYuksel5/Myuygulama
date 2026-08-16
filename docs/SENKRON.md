# İki cihaz arasında senkronizasyon

> Karar: **SQLite dosyasını senkronlamıyoruz.** Bunun yerine her cihaz kendi
> **değişiklik günlüğünü** yazıyor, karşı taraf onu okuyup uyguluyor.
> Taşıyıcı (Drive, Syncthing, herhangi bir klasör) bu tasarımın üstünde
> değiştirilebilir bir parça.

---

## 1. Neden veritabanı dosyasını kopyalamak işe yaramaz

Akla ilk gelen çözüm şu: `merkez.db` dosyasını Drive'a at, öbür telefon indirsin.
Bu, iki cihazda da yazma yapıldığı anda çöker:

- İki telefon da dosyayı değiştirdiyse, senkron aracı ikisinden **birini seçmek**
  zorundadır. Seçilmeyen tarafın o gün girdiği her şey sessizce silinir.
- SQLite dosyası WAL/journal dosyalarıyla birlikte tutarlıdır; yarısı kopyalanmış
  bir veritabanı bozuk veritabanıdır.
- Çakışma "dosya" düzeyinde çözülür, oysa gerçek çakışma "kayıt" düzeyindedir:
  A telefonunda bir not yazdın, B telefonunda başka bir görevi tamamladın —
  bunlar çakışmıyor bile, ikisi de yaşamalı.

Bu yüzden senkronun birimi **dosya değil, değişiklik** olacak.

---

## 2. Tasarım: cihaz başına ekleme-yapılır (append-only) değişiklik günlüğü

Her cihazın kalıcı bir `deviceId`'si var. Uygulamadaki her yazma işlemi
(ekle / güncelle / sil) veritabanına yazılırken **aynı işlem içinde** bir de
değişiklik satırı bırakır:

```
change(
  opId,          // bu değişikliğin kimliği
  deviceId,      // hangi cihazda üretildi
  seq,           // o cihazdaki sıra numarası (monoton artan)
  entityType,    // entry / habit / habit_check / task / transaction ...
  entityUuid,    // hangi kayıt
  operation,     // UPSERT | DELETE
  payload,       // kaydın yeni hâli (JSON)
  updatedAt      // kayıt zamanı
)
```

Senkron sırasında cihaz, günlüğünün **henüz dışa aktarılmamış** kısmını
paylaşılan alana yazar:

```
sync/
  A1B2C3/                 ← A telefonunun deviceId'si
    000001-000500.jsonl
    000501-001000.jsonl
  D4E5F6/                 ← B telefonunun deviceId'si
    000001-000320.jsonl
  anlik/
    2026-08-16.snapshot   ← periyodik özet (aşağıda)
```

**Kritik nokta: her cihaz yalnızca kendi klasörüne yazar.** İki cihaz asla aynı
dosyaya dokunmaz, dosyalar hiç değişmez (sadece yenisi eklenir). Böylece taşıyıcı
ne olursa olsun **dosya düzeyinde çakışma matematiksel olarak imkânsız** hâle gelir.
Senkron aracının "çakışan kopya" üretmesi diye bir durum kalmaz.

Karşı taraf da basitçe: "bu cihazın klasöründe en son hangi `seq`'e kadar okumuştum,
sonrasını oku ve uygula."

---

## 3. Çakışmalar nasıl çözülüyor

Aynı kaydı iki cihazda da düzenlersen:

- **Varsayılan: son yazan kazanır** (`updatedAt` karşılaştırması; saniyesi saniyesine
  eşitse `deviceId` ile deterministik bir sıra — iki cihaz da aynı sonuca varsın diye).
- **Doğal olarak çakışmayan veriler:** alışkanlık işaretleri `(alışkanlıkUuid, gün)`
  anahtarlı olduğu için idempotent; hangi telefondan işaretlediğin fark etmez,
  sonuç aynıdır. Görev tamamlama da böyle.
- **Silme:** kayıt gerçekten silinmez, `deletedAt` mezar taşı bırakılır. Aksi hâlde
  A'da sildiğin kayıt, B'nin eski günlüğü uygulanınca geri dirilirdi.
- **Notlar gibi uzun metinler:** ileride istersen alan bazlı birleştirme
  (üç yönlü diff) eklenebilir; ama tek kullanıcı iki cihaz senaryosunda aynı notu
  aynı anda iki telefondan düzenleme ihtimali düşük olduğu için başlangıçta
  son-yazan-kazanır + **çakışan sürümü ayrı bir kayıt olarak saklama** yapacağız.
  Yani hiçbir durumda yazdığın bir şey sessizce kaybolmaz.

Bu üçlü (`uuid` + `updatedAt` + `deletedAt`) Faz 0'da tüm tablolara zaten kondu.

---

## 4. Günlük şişmesin: periyodik özet (snapshot)

Değişiklik günlüğü sonsuza kadar büyüyemez. Belirli aralıklarla (ör. haftada bir
veya 5.000 değişiklikte bir) cihazlardan biri **anlık görüntü** üretir: o ana kadarki
tüm kayıtların son hâli tek bir sıkıştırılmış dosyada. Ondan eski günlük dosyaları
silinebilir.

Bunun ikinci faydası: **üçüncü bir cihaz** (veya telefonunu kaybedip yenisini aldığında)
sıfırdan kurulurken binlerce değişikliği tek tek uygulamak yerine son anlık görüntüyü
indirip üstüne kalan günlüğü uyguluyor.

---

## 5. Büyük dosyalar (PDF, kasa) nasıl senkronlanıyor

Bunlar günlüğe girmez — günlükte sadece "şu isimde, şu özetli (hash) dosya var" bilgisi
durur. Dosyaların kendisi `dosyalar/<hash>` altında **içerik adresli** tutulur:

- Aynı dosya iki kez eklenirse tek kopya durur.
- Dosyalar hiç değişmediği için üzerine yazma/çakışma olmaz.
- **İhtiyaç anında indirme:** 200 PDF'in hepsini ikinci telefona indirmek zorunda
  değilsin; kayıt listede görünür, dosya sen açmak isteyince iner.
  Depolama endişene doğrudan cevap veren kısım burası.

---

## 6. Taşıyıcı: veriyi iki telefon arasında ne taşıyacak?

Tasarımın taşıyıcıdan bağımsız olmasının sebebi bu: aşağıdakilerin hepsi aynı
klasör yapısıyla çalışır, birinden diğerine geçmek uygulamayı değiştirmez.

### Seçilen birincil yol: Google Drive (uygulama içinden)
Her iki telefonda Google hesabınla giriş yaparsın, uygulama senkronu kendi yapar,
başka hiçbir şey kurman gerekmez. İstediğin buydu.

Bilmen gereken teknik ayrıntılar:
- Kullanacağımız izin kapsamı **`drive.file`**: uygulamanın yalnızca *kendi
  oluşturduğu* dosyalara erişmesi. Drive'ının geri kalanını görmez.
  Bu kapsam Google'ın "hassas" listesinde değil; bu yüzden doğrulama sürecine
  girmeden kalıcı olarak kullanılabiliyor. (Tam erişim veya `appdata` kapsamı
  seçseydik doğrulama gerekirdi — o yüzden seçmiyoruz.)
- Senden bir kerelik ~15 dakikalık bir kurulum gerekecek: ücretsiz bir Google Cloud
  projesi, OAuth istemcisi ve uygulamanın imza parmak izinin girilmesi.
  Adım adım yazacağım. Bu, APK imzalama anahtarımızın da hazır olmasını gerektiriyor.
- Senkron WorkManager ile arka planda, şarj/wifi koşuluna bağlanabilir şekilde çalışır;
  ayrıca uygulamayı her açtığında bir tur atar.

### Yedek yol: klasör tabanlı (kurulum gerektirmez)
Uygulama, Android'in klasör seçicisiyle (`SAF`) gösterdiğin **herhangi bir klasöre**
de yazabilir. O klasörü neyle eşlediğin bizi ilgilendirmez:
- **Syncthing** — sunucusuz, uçtan uca şifreli, iki telefon arası doğrudan. Google hiç işin içinde olmaz.
- **FolderSync / Autosync** gibi bir araçla Drive, Nextcloud, WebDAV...

Drive kurulumu gözünü korkutursa ya da ileride Google'dan bağımsız olmak istersen,
tek yapman gereken ayarlardan taşıyıcıyı değiştirmek. Veri formatı aynı.

### Neden bir sunucu kurmuyoruz
Kendi sunucun (Supabase, Postgres, küçük bir VPS) en güçlü çözüm olurdu ama
bakım, maliyet ve finans verisinin cihaz dışına çıkması demek. İki cihaz için
gereğinden ağır.

---

## 7. Güvenlik

Paylaşılan alana yazılan her şey **cihazda şifrelenir**, öyle gönderilir.
Anahtar sende: ilk kurulumda bir kurtarma cümlesi (recovery phrase) üretilir,
ikinci telefona onu girersin. Google (veya hangi taşıyıcıysa) yalnızca
anlamsız baytlar görür. Kurtarma cümlesini kaybedersen bulutta duran veri
okunamaz — bu bilinçli bir tercih.

---

## 8. Ne zaman yapılacak

**Faz 2** — "Bugün" ekranı (alışkanlık, görev, takvim) bittikten hemen sonra.

Neden hemen sonra ve daha sonrası değil: senkron altyapısı bir kez kurulunca,
ondan sonra eklenen her modül (notlar, kelimeler, makaleler, finans) ona
**bedava biner**. Sona bırakırsak, o zamana kadar yazılmış her modüle geri dönüp
senkron desteği eklememiz gerekir.

Faz 1'de yazacağımız tablolar bu yüzden şimdiden `uuid` / `updatedAt` / `deletedAt`
taşıyacak ve tüm yazmalar değişiklik günlüğüne satır bırakan tek bir katmandan
geçecek — taşıyıcı henüz yokken bile.
