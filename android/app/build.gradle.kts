import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// --- Egasining mahalliy sozlamalari (FR-AU.4, FR-AU.5). Repozitoriyga tushmaydi:
// %USERPROFILE%\.gradle\gradle.properties ichida saqlanadi.
fun prop(name: String): String = (project.findProperty(name) as String?)?.trim().orEmpty()
val ownerBuildTag = prop("OWNER_BUILD_TAG")
val expectedCertSha256 = prop("EXPECTED_CERT_SHA256").lowercase().replace(":", "")
val serverUrl = prop("HAMROH_SERVER_URL")
val ownerPubFile = file("src/main/assets/owner.pub")

android {
    namespace = "uz.hamroh.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "uz.hamroh.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "OWNER_BUILD_TAG", "\"$ownerBuildTag\"")
        buildConfigField("String", "EXPECTED_CERT_SHA256", "\"$expectedCertSha256\"")
        buildConfigField("String", "SERVER_URL", "\"$serverUrl\"")
    }

    signingConfigs {
        create("owner") {
            val ks = prop("HAMROH_KEYSTORE")
            if (ks.isNotEmpty()) {
                storeFile = file(ks)
                storePassword = prop("HAMROH_KEYSTORE_PASSWORD")
                keyAlias = prop("HAMROH_KEY_ALIAS")
                keyPassword = prop("HAMROH_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (prop("HAMROH_KEYSTORE").isNotEmpty()) signingConfig = signingConfigs.getByName("owner")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        noCompress += listOf("tflite", "task")
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(project(":core"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    val camerax = "1.4.0"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-video:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    implementation("com.google.mediapipe:tasks-vision:0.10.14")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    implementation("com.google.zxing:core:3.5.3")

    testImplementation("junit:junit:4.13.2")
}

// --- Modellarni yuklab olish (MediaPipe, Apache 2.0). Birinchi yig'ishda internet kerak.
val downloadModels by tasks.registering {
    val assets = file("src/main/assets")
    val models = mapOf(
        "efficientdet_lite0.tflite" to
            "https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/int8/latest/efficientdet_lite0.tflite",
        "face_landmarker.task" to
            "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task",
    )
    outputs.files(models.keys.map { File(assets, it) })
    doLast {
        assets.mkdirs()
        models.forEach { (name, url) ->
            val f = File(assets, name)
            if (!f.exists() || f.length() < 10_000) {
                logger.lifecycle("Yuklanmoqda: $name")
                URI(url).toURL().openStream().use { input -> f.outputStream().use { input.copyTo(it) } }
            }
        }
    }
}
tasks.named("preBuild") { dependsOn(downloadModels) }

// --- FR-AU.1/4/5: reliz faqat egasining kaliti va belgisi bilan yig'iladi.
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst {
        val problems = buildList {
            if (!ownerPubFile.exists() || ownerPubFile.length() != 32L) add("src/main/assets/owner.pub (32 bayt) yo'q")
            if (ownerBuildTag.isEmpty()) add("OWNER_BUILD_TAG sozlanmagan")
            if (expectedCertSha256.length != 64) add("EXPECTED_CERT_SHA256 (64 hex) sozlanmagan")
            if (prop("HAMROH_KEYSTORE").isEmpty()) add("HAMROH_KEYSTORE sozlanmagan")
        }
        if (problems.isNotEmpty()) throw GradleException("Reliz yig'ilmaydi: " + problems.joinToString("; ") +
            ". Batafsil: docs/AUTHORSHIP.md")
    }
}
