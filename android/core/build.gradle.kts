plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    // Toolchain so'ralmaydi: Android Studio'ning o'z JDK (21) si bilan Java 17 bayt-kodi yig'iladi.
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    systemProperty("testdata.dir", rootProject.projectDir.resolve("../testdata/tracks").absolutePath)
}
