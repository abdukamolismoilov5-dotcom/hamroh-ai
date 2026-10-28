plugins {
    id("com.android.application") version "9.3.1" apply false
    id("org.jetbrains.kotlin.jvm") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}

subprojects {
    layout.buildDirectory.set(file("C:/Users/Abdukamol/AppData/Local/Temp/hamroh_android_build/${project.name}"))
}
