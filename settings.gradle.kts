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
include(":feature:habits")
include(":feature:tasks")
include(":feature:calendar")
include(":feature:widget")
include(":feature:gestures")
include(":feature:library")
include(":feature:reader")
