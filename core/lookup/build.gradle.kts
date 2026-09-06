plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.ahmety.uygulama.core.lookup"
    compileSdk = 35

    defaultConfig {
        minSdk = 29
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
}

// Seçim kutusu ve arkasındaki yapay zekâ işleri: karşılık, kart, soru.
// E-kitap, PDF ve Pocket üçü de buradan besleniyor; biri değişince
// ötekiler geride kalmasın diye tek modül.
dependencies {
    // Kullanan modüller OpenAiClient ve WorkBriefStore'u kurucuya veriyor,
    // kutuyu da designsystem'den alıyor; ikisi de api.
    api(project(":core:ai"))
    api(project(":core:designsystem"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.core)
    // OpenAiClient ve WorkBriefStore'un üstündeki @Inject işaretleri
    // çözülsün diye; burada Hilt kurulmuyor.
    implementation(libs.hilt.android)
}
