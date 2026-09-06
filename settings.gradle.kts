pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Merkez"

include(":app")
// Tarayıcı sürümünü kendi penceresinde açan ayrı uygulama; Merkez'in
// hiçbir modülüne bağlı değil.
include(":okuyucu")
include(":core:model")
include(":core:database")
include(":core:designsystem")
include(":core:ai")
include(":feature:habits")
include(":feature:gestures")
include(":feature:library")
include(":feature:reader")
include(":feature:vocab")
include(":feature:ebook")
include(":feature:subtitles")
