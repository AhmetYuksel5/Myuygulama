# Uygulama

Kişisel, tek kullanıcılık Android "süper uygulaması". Alışkanlık takibi, görevler,
takvim, notlar, oku-sonra, İngilizce kelime çalışması, haber derleme, PDF okuma ve
işaretleme, dosya kasası, finans takibi ve kenar hareketleri — tek APK içinde,
ortak bir bilgi çekirdeği üzerinde.

Play Store'a çıkmaz; cihaza APK olarak kurulur. Veri cihazda kalır; iki telefon
arasında değişiklik günlüğüyle senkronlanır.

## Durum

**Faz 0 — Temel** ✔ proje iskeleti, ortak kayıt çekirdeği (Entry/Tag/Link + FTS),
izin sihirbazı, GitHub Actions ile imzasız APK.

**Faz 1 — Günlük çekirdek** ✔ alışkanlıklar (seri takibi, günlük/haftalık/haftada-N),
görevler (liste, alt görev, tarih, öncelik, tekrar, To Do'dan içe aktarma),
takvim (CalendarContract üzerinden ajanda), "Bugün" ekranı, ana ekran widget'ı.

**Faz 2 — İki cihaz senkronu** ✔ değişiklik günlüğü, paylaşılan klasör taşıyıcısı,
AES-GCM şifreleme, kayıt düzeyinde çakışma çözümü.
Google Drive taşıyıcısı henüz eklenmedi (OAuth kurulumu gerekiyor).

**Faz 3 — Yakala ve sakla** ◐ kenar hareketleri (Fluid NG yerine) yazıldı.
Notlar, oku-sonra ve genel arama sırada.

## Kurulum ve güncelleme

APK, her push'ta GitHub Actions tarafından **sabit bir anahtarla imzalanıp**
[Releases](../../releases) altında yayınlanır.

- **İlk kurulum:** Releases sayfasındaki en son `.apk` dosyasını telefondan indir ve kur.
- **Sonraki güncellemeler:** uygulama içinden — **Ayarlar → Güncellemeler → Güncelleme ara**.
  Yeni sürüm varsa indirip kurulum ekranını açar; silip yeniden kurmaya,
  izinleri baştan vermeye gerek kalmaz.

Sabit imza şart: imzası farklı bir APK'yı Android güncelleme saymaz, uygulamayı
silmeni ister. İmzalama anahtarı GitHub Secrets'ta durur, depoda değildir.

- Yol haritası: [`docs/YOL-HARITASI.md`](docs/YOL-HARITASI.md)
- Veri saklama kararları: [`docs/VERI-SAKLAMA.md`](docs/VERI-SAKLAMA.md)
- İki cihaz senkronizasyonu: [`docs/SENKRON.md`](docs/SENKRON.md)
- To Do görevlerini içe aktarma: [`docs/TODO-ICE-AKTARMA.md`](docs/TODO-ICE-AKTARMA.md)
