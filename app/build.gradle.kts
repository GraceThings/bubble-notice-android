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
        versionCode = 10
        versionName = "1.0.0"

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
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) } }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    // for f-droid packaging
    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)

    implementation(platform(libs.androidx.compose.bom.v20260301))
    implementation(libs.androidx.compose.ui.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)

    // Material 3 (榛樿鍖呭惈 Expressive 璁捐鍏冪礌)
    implementation(libs.material3)
    // Compose 鏍稿績鍥炬爣搴?
    implementation(libs.androidx.material.icons.core)
    // Compose 鎵╁睍鍥炬爣搴?(鍖呭惈浜?Settings, Info 绛夌粷澶у鏁板浘鏍?
    implementation(libs.androidx.material.icons.extended)


    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
