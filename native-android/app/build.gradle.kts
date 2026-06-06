plugins {
    id("com.android.application")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "top.xiaofeigun.tvliveplayer"
    compileSdk = 36

    defaultConfig {
        applicationId = "top.xiaofeigun.tvliveplayer"
        minSdk = 23
        targetSdk = 36
        versionCode = project.findProperty("versionCode")?.toString()?.toIntOrNull() ?: 1
        versionName = project.findProperty("versionName")?.toString() ?: "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-ktx:1.12.2")
    implementation("androidx.fragment:fragment-ktx:1.8.9")

    // Leanback (Android TV)
    implementation("androidx.leanback:leanback:1.2.0")
    implementation("androidx.leanback:leanback-preference:1.2.0")
    implementation("androidx.tvprovider:tvprovider:1.1.0")

    // Media3 ExoPlayer
    implementation("androidx.media3:media3-exoplayer:1.10.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.10.0")
    implementation("androidx.media3:media3-datasource-okhttp:1.10.0")
    implementation("androidx.media3:media3-ui:1.10.0")

    // Room Database
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // Hilt (DI)
    implementation("com.google.dagger:hilt-android:2.59.2")
    ksp("com.google.dagger:hilt-android-compiler:2.59.2")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")

    // Network
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON
    implementation("com.google.code.gson:gson:2.11.0")

    // Image Loading
    implementation("io.coil-kt.coil3:coil:3.4.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.4.0")

    // QR Code generation
    implementation("com.google.zxing:core:3.5.3")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // Desugaring (Java 8+ API for older Android)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}



