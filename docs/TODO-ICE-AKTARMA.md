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

### 2. Listeleri çek
Adres kutusuna şunu yaz ve **Run query**:

```
GET  https://graph.microsoft.com/v1.0/me/todo/lists
```

İlk seferde izin isteyebilir (`Tasks.Read`); **Consent** deyip onayla.

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

### 3. Her listenin görevlerini çek
Her liste için sırayla (yukarıdaki `id`'yi yapıştırarak):

```
GET  https://graph.microsoft.com/v1.0/me/todo/lists/{LISTE_ID}/tasks?$top=200
```

Gelen JSON'u yine kopyala ve içe aktar. Bu adımda başlık, tamamlanma durumu,
tarih, önem derecesi, notlar ve **alt görevler** (checklist) birlikte gelir.

> **Not:** Aynı adlı liste ikinci kez içe aktarılırsa yeni liste açılmaz,
> görevler mevcut listeye eklenir. Yani adım 2 ve 3'ü peş peşe yapman sorun değil.

> **200'den fazla görev varsa:** çıktının sonunda bir `@odata.nextLink` adresi
> olur; onu adres kutusuna yapıştırıp çalıştırınca kalanı gelir.

### 4. Tamamlanmışları da istiyor musun?
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
