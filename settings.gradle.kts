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
include(":core:model")
include(":core:database")
include(":core:designsystem")
include(":core:ai")
include(":feature:habits")
include(":feature:calendar")
include(":feature:vocab")
include(":feature:ebook")
include(":feature:subtitles")
