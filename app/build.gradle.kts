plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * Sürüm numarası CI çalışma numarasından geliyor; her yayın bir öncekinden
 * büyük olmak zorunda, yoksa Android güncellemeyi reddeder.
 */
val buildNumber = (System.getenv("BUILD_NUMBER") ?: "0").toInt()

android {
    namespace = "com.ahmety.uygulama"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ahmety.uygulama"
        minSdk = 29
        targetSdk = 35
        versionCode = 1 + buildNumber
        versionName = "0.$buildNumber"
    }

    /**
     * Sabit imzalama anahtarı olmadan her derleme farklı imzalanır ve Android
     * yeni APK'yı güncelleme olarak kabul etmez — kullanıcı her seferinde
     * uygulamayı silip izinleri yeniden vermek zorunda kalır. Anahtar
     * GitHub Secrets'tan geliyor; yerel derlemelerde debug imzasına düşüyoruz.
     */
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
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":core:designsystem"))
    implementation(project(":feature:habits"))
    implementation(project(":feature:tasks"))
    implementation(project(":feature:calendar"))
    implementation(project(":feature:widget"))
    implementation(project(":feature:gestures"))
    implementation(project(":feature:library"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.kotlinx.datetime)

    testImplementation(libs.junit)
}
