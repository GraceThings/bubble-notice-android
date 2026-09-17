plugins {
    alias(libs.plugins.android.application)
    
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.gracethings.bubblenotice"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.gracethings.bubblenotice"
        minSdk = 30
        targetSdk = 37
        versionCode = 17
        versionName = "1.0.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
        }
    }
    packaging {
        resources.excludes.add("META-INF/version-control-info.textproto")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        freeCompilerArgs.add("-Xencoding=utf-8")
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
    buildFeatures {
        compose = true
        buildConfig = true
    }

    // 用于 F-Droid 打包 / For F-Droid packaging
    dependenciesInfo {
        // 构建 APK 时禁用依赖元数据 / Disable dependency metadata when building APKs.
        includeInApk = false
        // 构建 Android App Bundle 时禁用依赖元数据 / Disable dependency metadata when building Android App Bundles.
        includeInBundle = false
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.activity)

    implementation(platform(libs.androidx.compose.bom.v20260301))
    implementation(libs.androidx.compose.ui.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)

    implementation(libs.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)


}
