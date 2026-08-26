import java.util.Properties

plugins {
    alias(libs.plugins.agp.app)
    alias(libs.plugins.kotlin)
    alias(libs.plugins.ksp)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "io.github.Rillwyn.maceditor"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
        targetSdk = 36
        versionCode = 11
        versionName = "0.1.0"
        applicationId = "io.github.Rillwyn.maceditor"
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    signingConfigs {
        if (localProps.containsKey("storeFile")) {
            create("release") {
                storeFile = file(localProps["storeFile"] as String)
                storePassword = localProps["storePassword"] as String
                keyAlias = localProps["keyAlias"] as String
                keyPassword = localProps["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles("proguard-rules.pro")
            signingConfigs.findByName("release")?.let {
                signingConfig = it
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        jvmToolchain(21)
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.viewpager2)

    // YukiHookAPI 核心库（1.3.2，基于 XposedBridge/LSPosed 兼容层）
    implementation(libs.yukihookapi.api)
    // KSP 处理器：自动生成 Xposed 入口（assets/xposed_init）与模块状态检测类
    ksp(libs.yukihookapi.ksp)
    // XposedBridge API（编译期提供 de.robv.android.xposed.*，运行时由 LSPosed 提供）
    compileOnly(files("libs/api-82.jar"))
}
