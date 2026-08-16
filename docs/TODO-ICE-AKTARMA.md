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

### 4. Her listenin görevlerini çek
Her liste için sırayla (yukarıdaki `id`'yi yapıştırarak):

```
GET  https://graph.microsoft.com/v1.0/me/todo/lists/{LISTE_ID}/tasks?$top=200
```

Gelen JSON'u yine kopyala ve içe aktar. Bu adımda başlık, tamamlanma durumu,
tarih, önem derecesi, notlar ve **alt görevler** (checklist) birlikte gelir.

> **Not:** Aynı adlı liste ikinci kez içe aktarılırsa yeni liste açılmaz,
> görevler mevcut listeye eklenir. Yani adım 3 ve 4'ü peş peşe yapman sorun değil.

> **200'den fazla görev varsa:** çıktının sonunda bir `@odata.nextLink` adresi
> olur; onu adres kutusuna yapıştırıp çalıştırınca kalanı gelir.

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
