import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.toclaude"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.toclaude"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"
    }

    // 发布签名：和 EasyClash 用同一把固定密钥（D:\EasyClash\signing）。
    // 依次找：环境变量 TOCLAUDE_KEYSTORE_PROPS（CI 用）、仓库根目录的 keystore.properties、D:/EasyClash/signing/keystore.properties；
    // 都没有时退回 debug 签名，只能自己测试，不能当正式版发布（无法覆盖升级）。
    val releaseProps = listOfNotNull(
        System.getenv("TOCLAUDE_KEYSTORE_PROPS")?.let { File(it) },
        rootProject.file("keystore.properties"),
        File("D:/EasyClash/signing/keystore.properties"),
    ).firstOrNull { it.isFile }?.let { f -> Properties().apply { f.inputStream().use { load(it) } } }

    signingConfigs {
        if (releaseProps != null) {
            create("release") {
                storeFile = File(releaseProps.getProperty("storeFile"))
                storePassword = releaseProps.getProperty("storePassword")
                keyAlias = releaseProps.getProperty("keyAlias")
                keyPassword = releaseProps.getProperty("keyPassword")
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }

    packaging { resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1") }

    sourceSets["main"].java.srcDirs("src/main/kotlin")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
