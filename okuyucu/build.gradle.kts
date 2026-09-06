plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

/**
 * Sürüm numarası CI çalışma numarasından geliyor; Merkez ile aynı sayacı
 * paylaşıyorlar ki aynı sürümde çıkan iki APK aynı numarayı taşısın.
 */
val buildNumber = (System.getenv("BUILD_NUMBER") ?: "0").toInt()

android {
    namespace = "com.ahmety.arapca"
    compileSdk = 35

    defaultConfig {
        // Merkez'den ayrı bir kimlik: ikisi telefonda yan yana durabilsin,
        // biri kurulunca diğerinin üstüne yazmasın.
        applicationId = "com.ahmety.arapca"
        minSdk = 29
        targetSdk = 35
        versionCode = 1 + buildNumber
        versionName = "0.$buildNumber"
    }

    // Merkez ile aynı anahtar: iki APK da aynı imzayla çıksın, güncellemeler
    // her ikisinde de sorunsuz kurulsun.
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
            /*
             * Küçültme kapalı. Uygulama tek ekran, kazanılacak yer yok; buna
             * karşılık köprüdeki yöntemin adı karıştırılırsa sayfadan gelen
             * çağrı sessizce boşa düşer ve yedek indirme kırılır.
             */
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

    kotlinOptions {
        jvmTarget = "17"
    }

    // Merkez ile aynı sebep: AGP 8.7'nin lint motoru sürüm derlemesini
    // durduruyor, çıktısına da bakmıyoruz.
    lint {
        checkReleaseBuilds = false
    }
}

/*
 * Bilerek bağımlılıksız: Activity ve WebView Android'in kendi sınıfları.
 * Compose, Hilt, AppCompat girmediği için derleme saniyeler sürüyor ve
 * kütüphane sürümü çakışması diye bir dert olmuyor.
 */
dependencies {
}
