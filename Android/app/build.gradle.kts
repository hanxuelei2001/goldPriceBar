import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// ── 版本号 ──────────────────────────────────────────────────────────────────
// 唯一来源是 Android/version.properties；构建脚本与 CI 也读这一份来决定产物文件名。
// 注意不能写 java.util.Properties：脚本里 `java` 会先解析成 Gradle 的 java 扩展。
val versionProps = Properties()
val versionFile = rootProject.file("version.properties")
if (versionFile.exists()) {
    versionFile.inputStream().use { versionProps.load(it) }
}
val appVersionName: String = versionProps.getProperty("versionName", "1.0.0")
val appVersionCode: Int = versionProps.getProperty("versionCode", "1").trim().toInt()

// ── 签名 ────────────────────────────────────────────────────────────────────
// 优先级：CI Secrets 解出的路径（ANDROID_KEYSTORE_PATH）→ 工程内 keystore/goldpricebar.jks
//        → 退回 debug 签名。
// 退回 debug 签名时产物仍可安装，但因为每个环境的调试密钥不同，无法覆盖安装历史版本，
// 所以 CI 上没配 Secrets 会打一条 warning。
val releaseKeystore: File = System.getenv("ANDROID_KEYSTORE_PATH")
    ?.takeIf { it.isNotBlank() }
    ?.let { file(it) }
    ?: file("../keystore/goldpricebar.jks")
val hasReleaseKeystore = releaseKeystore.exists()

fun secret(envName: String, fallback: String): String =
    System.getenv(envName)?.takeIf { it.isNotBlank() } ?: fallback

val releaseStorePassword = secret("ANDROID_KEYSTORE_PASSWORD", "goldpricebar")
val releaseKeyAlias = secret("ANDROID_KEY_ALIAS", "goldpricebar")
val releaseKeyPassword = secret("ANDROID_KEY_PASSWORD", "goldpricebar")

android {
    namespace = "com.goldpricebar.monitor"
    compileSdk = 35

    // 显式固定，否则 AGP 8.7 会去找默认的 34.0.0 并尝试联网下载。
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.goldpricebar.monitor"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 有自签名密钥就用它，否则退回 debug 签名，保证产物始终可安装。
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.material)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.json)
    testImplementation(libs.kotlinx.coroutines.test)
}
