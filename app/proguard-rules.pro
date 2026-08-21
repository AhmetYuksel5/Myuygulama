# R8 kuralları.
#
# Amaç yalnızca küçültme: kullanılmayan sınıf ve yöntemler atılıyor.
# Adlar karıştırılmıyor (-dontobfuscate). İki sebebi var: uygulama bir hata
# aldığında sınıfın adını ekranda gösteriyoruz ("Beklenmedik hata: …") ve
# karıştırılmış bir ad orada hiçbir işe yaramıyor; ayrıca ada göre çalışan
# her şey (enum valueOf, serileştirme, Room) karıştırma olmadan kendiliğinden
# güvende. Boyut kazancının büyük kısmı zaten atılan koddan geliyor,
# adların kısalmasından değil.
-dontobfuscate

# Kaynak satır numaraları hata ayıklamada işe yarıyor, yer kaplamıyorlar.
-keepattributes SourceFile,LineNumberTable

# kotlinx.serialization: üretilen serileştiriciler koda ada göre bağlanıyor.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp ve Okio: yalnızca derleme zamanı uyarıları, sınıflar çalışma
# zamanında aranmıyor.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# jsoup: EPUB ayrıştırma. Kendi kuralları yok.
-dontwarn org.jsoup.**

# Compose ve Hilt kendi kurallarını kütüphaneleriyle birlikte getiriyor;
# burada tekrar etmeye gerek yok.
