# Merkez

Tek kişilik bir Android uygulaması: kitap ve film altyazısı okurken
bilinmeyen kelimeleri işaretleyip tekrar programıyla çalışmak.

- Paket: `com.ahmety.uygulama`
- Geliştirme dalı: `claude/android-personal-management-app-1icryt`
- Dağıtım: dala her gönderim GitHub Actions'ı çalıştırır; imzalı APK
  GitHub Releases'e düşer (`merkez-<sürüm>.apk`). Sürüm numarası çalıştırma
  numarasından gelir.
- Yerel derleme yok: bu ortamdan `dl.google.com` kapalı, AGP çözülemiyor.
  Doğrulama yalnızca CI'da.

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
