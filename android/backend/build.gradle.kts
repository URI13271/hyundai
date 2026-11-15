import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.13.0")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

// 기기 없이 백엔드를 HTTP로 띄운다. 러스트 API와 같은 주소, 같은 환경변수다.
//   gradlew :backend:devServer
//   DRIVE_TALK_API=http://127.0.0.1:8080 ruby bin/drive_talk
tasks.register<JavaExec>("devServer") {
    group = "application"
    description = "코틀린 백엔드를 http://127.0.0.1:8080 에 띄운다."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("drivetalk.backend.DevServerKt")
    workingDir = rootDir.parentFile.resolve("backend")
}
