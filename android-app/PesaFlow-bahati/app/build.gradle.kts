plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
}

import java.time.LocalDate

android {
    namespace = "com.pesaflow.app"
    compileSdk = 34

    defaultConfig {
        // TEST COPY: distinct id so test + production install side by side.
        applicationId = "com.pesaflow.app.bahati"
        minSdk = 26
        targetSdk = 34
        // Date-based: every build is a newer version, never a forgotten bump.
        // 20260921 < 2100000000, always inside Play's int range.
        val today = LocalDate.now()
        versionCode = today.year * 10000 + today.monthValue * 100 + today.dayOfMonth
        versionName = "1.3.0-bahati"
    }

    buildTypes {
        release {
            // Smaller APK + Play-ready: Room/WorkManager/Compose all ship
            // their own keep rules, debug builds stay untouched.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // java.time + friends backported to minSdk 26 (kills the
        // SimpleDateFormat/Calendar thicket over time).
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // Room schema export location (exportSchema = true in AppDatabase).
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    testOptions {
        unitTests {
            // JVM tests may touch android.jar stubs (proxied prefs in tests).
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core / lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.activity.ktx)

    // Jetpack Compose (via BOM so versions stay in sync)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons.core)
    implementation(libs.androidx.compose.icons.extended)

    // Room (KSP: ~2x faster than kapt annotation processing)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Desugared java.time core libs (see isCoreLibraryDesugaringEnabled above).
    coreLibraryDesugaring(libs.desugar.libs)

    // Typed, observable settings (coach flags, method memory, approval prefs).
    implementation(libs.androidx.datastore)

    // Real branded launch (replaces the windowBackground color hack).
    implementation(libs.androidx.splashscreen)

    // Rationale-aware permission flows (SMS priming with explanation first).
    implementation(libs.accompanist.permissions)

    // Shimmer placeholders while lists load.
    implementation(libs.accompanist.placeholder)

    // Celebration animations (save success, onboarding finish).
    implementation(libs.lottie.compose)

    // Home-screen widget (safe-to-spend at a glance).
    implementation(libs.glance.appwidget)

    // Type-safe backup JSON (v2 payload — export/import parity by construction).
    // 1.6.3 = newest runtime that still supports Kotlin 1.9.x (1.7.x needs K2).
    implementation(libs.kotlinx.serialization.json)

    // Background reminders (daily/weekly summaries, budget watch)
    implementation(libs.androidx.work)

    // On-device text recognition (timetable photo/PDF import, offline-capable
    // via Play Services; every call site falls back to manual grid)
    implementation(libs.mlkit.text)

    // Password-protected M-Pesa statement PDFs (Safaricom ships them
    // encrypted — the ID used at download unlocks them in-app).
    implementation(libs.pdfbox.android)

    // Unit tests
    testImplementation(libs.junit)

    // Debug tooling
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
