plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * My Asistan: Yeşil Temizlik'in ofis asistanı uygulaması. Merkez'den ayrı bir uygulama:
 * ofisteki telefona kurulur, kişisel modüllerin hiçbirini taşımaz.
 * Sürüm numarası Merkez ile aynı sayaçtan gelir.
 */
val buildNumber = (System.getenv("BUILD_NUMBER") ?: "0").toInt()

android {
    namespace = "com.yesiltemizlik.ofis"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.yesiltemizlik.ofis"
        minSdk = 29
        targetSdk = 35
        versionCode = 1 + buildNumber
        versionName = "0.$buildNumber"
    }

    // Merkez ile aynı anahtar: güncellemeler sorunsuz üstüne kurulsun.
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
            // Küçültme açık: Compose kütüphaneleri küçültmesiz APK'yı ~22 MB yapıyor.
            // Manifest'te adı geçen alıcı, servis ve etkinlikleri R8 kendiliğinden koruyor.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
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

    buildFeatures {
        compose = true
    }

    // Merkez ile aynı sebep: AGP 8.7'nin lint motoru sürüm derlemesini durduruyor.
    lint {
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit)
}
