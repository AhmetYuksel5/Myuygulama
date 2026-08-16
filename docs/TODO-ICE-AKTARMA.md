# Microsoft To Do görevlerini içe aktarma

> Kısa cevap: **Microsoft To Do'nun dışa aktarma düğmesi yok** — aradığın için
> bulamadın, gerçekten yok. Ama hesap kaydı yapmadan, 5 dakikada verini çekebilirsin.

---

## Neden yok

To Do, görevleri Exchange Online'da saklar ve Microsoft yıllardır uygulamaya bir
"dışa aktar" düğmesi koymadı. Outlook masaüstü üzerinden `.pst` almak teorik olarak
mümkün ama zahmetli ve To Do'nun alt görev/tekrar bilgilerini düzgün taşımıyor.

Geriye iki pratik yol kalıyor. İkincisi 5 dakika sürer ve her şeyi getirir.

---

## Yol 1: Elle yapıştırma (en hızlı, az görev varsa)

Uygulamada **Görevler → sağ üst menü → İçe aktar** ve şu biçimde yapıştır:

```
# Alışveriş
- [ ] Süt al
- [x] Ekmek al
    - Tam buğday olsun
# İş
Rapor yaz
Faturayı öde
```

Kurallar:
- `#` ile başlayan satır **liste adı** olur.
- `- [x]` tamamlanmış, `- [ ]` tamamlanmamış demek. İşaretleri hiç yazmayabilirsin de —
  sade bir satır listesi de çalışır.
- **Girintili satır** bir önceki görevin alt görevi olur.

Uygulama yazmadan önce "3 liste, 27 görev" gibi bir özet gösterir; yanlış
yapıştırma sessizce içeri girmez.

---

## Yol 2: Graph Explorer (her şeyi getirir, hesap kaydı gerektirmez)

Microsoft'un kendi test aracını kullanıyoruz. Bizim tarafta hiçbir kurulum,
uygulama kaydı veya API anahtarı gerekmiyor — sadece kendi hesabınla giriş yapıp
çıktıyı kopyalıyorsun.

### 1. Araca gir ve giriş yap
<https://developer.microsoft.com/graph/graph-explorer>
Sağ üstten **Sign in** ile To Do'yu kullandığın Microsoft hesabınla gir.

### 2. `Tasks.Read` iznini elle onayla (bu adım atlanamaz)
Graph Explorer izinleri kendiliğinden istemez. Onaylamadan sorgu çalıştırırsan
**boş mesajlı `401 Unauthorized`** alırsın — yetkisiz olduğun için değil,
token'da o kapsam olmadığı için.

1. Adres kutusunun altındaki **Modify Permissions** sekmesine tıkla
2. Arama kutusuna `Tasks` yaz
3. **`Tasks.Read`** satırının sağındaki **Consent** düğmesine bas
4. Microsoft'un onay penceresinde **Accept** de

`Tasks.ReadWrite` gerekmiyor; yalnızca okuyoruz.

> **Consent düğmesi görünmüyorsa** sağ üstten çıkış yapıp tekrar gir;
> Graph Explorer izin listesini oturum açarken kuruyor ve bazen eksik kalıyor.
>
> **Hâlâ 401 alıyorsan** giriş yaptığın hesabın To Do listelerinin bulunduğu
> hesap olduğundan emin ol. **Access token** sekmesindeki token'ı
> [jwt.ms](https://jwt.ms) üzerine yapıştırıp `scp` alanında `Tasks.Read`
> var mı diye bakabilirsin.

### 3. Listeleri çek
Adres kutusuna şunu yaz ve **Run query**:

```
GET  https://graph.microsoft.com/v1.0/me/todo/lists
```

Gelen JSON'un tamamını kopyala → uygulamada **Görevler → menü → İçe aktar** →
yapıştır → **İçe aktar**. Bu adım **listeleri** oluşturur.

Çıktıda her listenin bir `id` değeri var, şuna benzer:

```json
{
  "value": [
    { "id": "AAMkAD...AAA=", "displayName": "Alışveriş" },
    { "id": "AAMkAD...BBB=", "displayName": "İş" }
  ]
}
```

Bu `id`'leri bir sonraki adımda kullanacaksın.

### 4. Tüm listelerin görevlerini **tek istekte** çek

Adım 3'ün çıktısı yalnızca liste başlıklarıdır — görevler orada değildir.
Her listeyi ayrı ayrı çekmek yerine Graph'ın **toplu istek** (`$batch`)
özelliğini kullanıyoruz: 20 sorguya kadar tek seferde çalıştırıyor.

1. Graph Explorer'da yöntemi **GET → POST** olarak değiştir
2. Adresi şu yap:
   ```
   https://graph.microsoft.com/v1.0/$batch
   ```
3. **Request Body** sekmesine şu yapıdaki gövdeyi yapıştır:
   ```json
   {
     "requests": [
       { "id": "Alışveriş", "method": "GET", "url": "/me/todo/lists/LISTE_ID_1/tasks?$top=200" },
       { "id": "İş",        "method": "GET", "url": "/me/todo/lists/LISTE_ID_2/tasks?$top=200" }
     ]
   }
   ```
4. **Run query** → gelen JSON'un tamamını uygulamaya yapıştır

**`id` alanına liste adını yazmak önemli.** Toplu yanıt hangi görevin hangi
listeye ait olduğunu başka türlü söylemiyor; ayrıştırıcı liste adını buradan
okuyor. Adres yerine ad yazarsan görevler doğru listelere düşer.

Hazır gövde: [`todo-batch-istegi.json`](todo-batch-istegi.json) — bu depodaki
dosya senin 17 listenle önceden doldurulmuş durumda, olduğu gibi kopyalayabilirsin.
(Sistem listesi olan "Flagged Emails" bilerek dışarıda bırakıldı.)

Bu adımda başlık, tamamlanma durumu, tarih, önem derecesi, notlar ve
**alt görevler** (checklist) birlikte gelir.

> **20'den fazla listen varsa** gövdeyi ikiye böl, iki kez çalıştır.
>
> **Bir listede 200'den fazla görev varsa** o listenin yanıtında bir
> `@odata.nextLink` adresi olur; onu ayrıca çalıştırıp gelen çıktıyı da yapıştır.
>
> **Aynı adlı liste ikinci kez içe aktarılırsa** yeni liste açılmaz, görevler
> mevcut listeye eklenir. Yani adımları peş peşe yapman sorun değil.

#### Tek listeyi çekmek istersen
```
GET  https://graph.microsoft.com/v1.0/me/todo/lists/{LISTE_ID}/tasks?$top=200
```
Bu biçim de tanınıyor; sadece görevler hangi listeye ait bilinmediği için
hepsi "İçe aktarılan" adlı tek listeye düşer.

### 5. Tamamlanmışları da istiyor musun?
Varsayılan olarak To Do tamamlanmış görevleri de döndürür. İstemiyorsan sorguya
şunu ekleyebilirsin:

```
GET  .../tasks?$filter=status ne 'completed'&$top=200
```

---

## İçe aktarmadan sonra

Görevler artık **bizim veritabanımızda** yaşıyor. Microsoft To Do ile sürekli
senkron kurmuyoruz (bu kararı birlikte verdik — Azure uygulama kaydı, MSAL ve
delta senkronu tam bir fazı yiyordu). To Do'yu artık silebilirsin.

İki telefon arasındaki senkron ayrı bir konu ve bizim kendi mekanizmamızla
çözülüyor: [`SENKRON.md`](SENKRON.md).
