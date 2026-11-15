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

rootProject.name = "drive-talk"

// app: 차량 화면에 뜨는 안드로이드 앱.
// backend: 판을 맡는 코틀린 백엔드. 안드로이드 없이 JVM에서도 돈다.
include(":app", ":backend")
