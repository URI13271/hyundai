plugins {
    id("com.android.application")
}

android {
    namespace = "app.drivetalk"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.drivetalk"
        minSdk = 29
        targetSdk = 35
        versionCode = 2
        versionName = "2.0.0"
    }

    buildFeatures {
        buildConfig = true
        resValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // standalone: 화면과 코틀린 백엔드가 앱 안에 있다. 서버가 없어도 돈다.
    // client: 화면 서버(ruby bin/drive_talk_all)의 주소를 받아 그 화면을 띄운다.
    flavorDimensions += "backend"
    productFlavors {
        create("standalone") {
            dimension = "backend"
            buildConfigField("boolean", "STANDALONE", "true")
            resValue("string", "app_name", "드라이브 토크")
        }
        create("client") {
            dimension = "backend"
            applicationIdSuffix = ".remote"
            buildConfigField("boolean", "STANDALONE", "false")
            resValue("string", "app_name", "드라이브 토크 서버")
        }
    }

}

dependencies {
    "standaloneImplementation"(project(":backend"))
    // Pleos Connect 차량 SDK. 차량 서비스가 없는 기기에서는 초기화가 실패하고 앱은 그대로 돈다.
    implementation("ai.pleos.playground:Vehicle:2.3.2")
}

// 화면의 css와 글꼴, 덱은 저장소의 것을 그대로 가져온다. 여기에 사본을 두지 않는다.
abstract class WebAssets : DefaultTask() {
    @get:InputFiles
    abstract val styles: ConfigurableFileCollection

    @get:InputFiles
    abstract val copies: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val out: DirectoryProperty

    @TaskAction
    fun run() {
        val dir = out.get().asFile
        dir.deleteRecursively()
        dir.mkdirs()
        // 앱 안에서는 글꼴이 css 옆에 있다.
        styles.forEach { file ->
            dir.resolve(file.name).writeText(file.readText().replace("url(\"/assets/fonts/", "url(\""))
        }
        copies.forEach { file -> file.copyTo(dir.resolve(file.name)) }
    }
}

val repo = rootDir.parentFile
val webAssets = tasks.register<WebAssets>("webAssets") {
    styles.from(listOf("app.css", "adaptive.css", "webapp.css").map { repo.resolve("public/$it") })
    copies.from(repo.resolve("public/fonts/PretendardVariable.woff2"), repo.resolve("backend/deck.json"))
}

androidComponents {
    onVariants(selector().withFlavor("backend" to "standalone")) { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(webAssets, WebAssets::out)
    }
}
