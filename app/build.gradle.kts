plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.galynte.tapblok"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.galynte.tapblok"
        minSdk = 24
        targetSdk = 37
        versionCode = 10304
        versionName = "1.3.4"

        // Automatically override from Git tag during CI on tagged releases
        if (System.getenv("GITHUB_REF")?.startsWith("refs/tags/") == true) {
            val tag = System.getenv("GITHUB_REF")!!
                .removePrefix("refs/tags/")
                // No longer need to strip "v" — tag is already clean like "0.1.2"

            versionName = tag

            // Optional: Auto-calculate versionCode from tag (e.g., 0.1.2 → 102)
            // Recommended for Google Play (must increase with every upload)
            versionCode = tag.split(".").let { parts ->
                parts[0].toInt() * 10000 +
                parts[1].toInt() * 100 +
                (parts.getOrNull(2)?.toInt() ?: 0)
            }
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        getByName("debug") { }  // This registers/acknowledges the debug config (empty is fine)

        // Only create a 'release' signingConfig (using the standard debug keystore) if the file exists.
        // This allows easy local `./gradlew bundleRelease` and `assembleRelease` on developer machines
        // (after the first debug build has created ~/.android/debug.keystore) using debug keys for release variant.
        // On GitHub Actions CI, release APK and AAB signing is done via Gradle property injection
        // (android.injected.signing.*) from secrets; the conditional release signingConfig is not created.
        val debugKeystore = file(System.getProperty("user.home") + "/.android/debug.keystore")
        if (debugKeystore.exists()) {
            create("release") {
                storeFile = debugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            // Enables code-related app optimization.
            isMinifyEnabled = true
            // Enables resource shrinking.
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // Only wire up the release signing config if it was created above (i.e. the debug keystore existed).
            // This is for local dev convenience only; CI uses injected signing properties for release builds.
            signingConfigs.findByName("release")?.let {
                signingConfig = it
            }

            // Include native debug symbols (from androidx.graphics-path etc.) inside the AAB.
            // This eliminates the Play Console warning about missing debug symbols for native code
            // and enables better crash/ANR symbolication in Play Console.
            // See: https://developer.android.com/build/include-native-symbols
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("debug")  // Sign with debug keys
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // Using new compilerOptions DSL required by Kotlin 2.4+ / recent KGP
    // (old kotlinOptions {} is removed)
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    // Robolectric on JDK 17+ needs the agent and these modules opened.
    jvmArgs(
        "-XX:+EnableDynamicAgentLoading",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED"
    )
}

// New Kotlin compiler options DSL (required for Kotlin 2.4+)
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.coil.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)
    // --- START OF CHANGES ---
    // Added dependencies for QR code functionality
    implementation(libs.zxing.core)
    implementation(libs.zxing.android.embedded)
    // --- END OF CHANGES ---
}

