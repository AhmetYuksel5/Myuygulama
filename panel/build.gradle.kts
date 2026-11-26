plugins {
    alias(libs.plugins.android.application)
}

/**
 * Yeşil Temizlik Panel — panel.yesiltemizlik.com'u adres çubuğu olmadan,
 * uygulama gibi açan kabuk (Trusted Web Activity).
 *
 * Neden burada: Merkez ile aynı imza anahtarı ve aynı yayın hattı (GitHub
 * Releases, herkese açık). Panelin kendi deposu gizli olduğundan oradan
 * herkese açık indirme bağlantısı verilemiyordu; eski el yapımı APK'nın
 * anahtarı da bu ortamda yoktu.
 *
 * Neden WebView değil de TWA: panel Google ile giriş ve Takvim/Drive
 * yetkilendirmesi kullanıyor; Google bunları gömülü WebView'da engelliyor.
 * TWA sayfayı telefonun Chrome'unda (çerezler, bildirim, dosya seçme dahil)
 * tam ekran açar. Telefonda TWA destekleyen tarayıcı yoksa kütüphane
 * kendi WebView penceresine düşer — o zaman da adres çubuğu görünmez.
 */
val buildNumber = (System.getenv("BUILD_NUMBER") ?: "0").toInt()

android {
    namespace = "com.yesiltemizlik.panel"
    compileSdk = 35

    defaultConfig {
        // Eski el yapımı APK com.yesiltemizlik.panel.twa idi ve başka anahtarla
        // imzalıydı; aynı kimlik verilseydi telefon "paket çakışıyor" deyip
        // kurmazdı. Yeni kimlik: yanına kurulur, eskisi silinebilir.
        applicationId = "com.yesiltemizlik.panel"
        minSdk = 24
        targetSdk = 35
        versionCode = 1 + buildNumber
        versionName = "1.$buildNumber"
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("SIGNING_KEYSTORE_PATH")
            if (!keystorePath.isNullOrBlank()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "merkez"
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
                    ?: System.getenv("SIGNING_STORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Kendi kodu yok, kütüphane sınıfları manifestte adıyla anılıyor;
            // küçültme adları karıştırırsa uygulama açılmaz.
            isMinifyEnabled = false
            signingConfig = if (System.getenv("SIGNING_KEYSTORE_PATH").isNullOrBlank()) {
                signingConfigs.getByName("debug")
            } else {
                signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        checkReleaseBuilds = false
    }
}

dependencies {
    // Google'ın resmî TWA kütüphanesi (Bubblewrap/PWABuilder APK'ları da bunu
    // kullanır): tam ekran destekleyen tarayıcıyı seçer, açılış görselini
    // tarayıcıya aktarır, bildirimleri uygulamaya devreder.
    implementation("com.google.androidbrowserhelper:androidbrowserhelper:2.5.0")
}
