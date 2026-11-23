plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.securetrack"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.securetrack"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "1.8.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            // Production keystore configuration via environment variables or local properties
            val customStoreFile = project.findProperty("SECURETRACK_KEYSTORE_PATH") as String?
                ?: System.getenv("SECURETRACK_KEYSTORE_PATH")
            if (customStoreFile != null && file(customStoreFile).exists()) {
                storeFile = file(customStoreFile)
                storePassword = (project.findProperty("SECURETRACK_STORE_PASSWORD") as String?)
                    ?: System.getenv("SECURETRACK_STORE_PASSWORD") ?: ""
                keyAlias = (project.findProperty("SECURETRACK_KEY_ALIAS") as String?)
                    ?: System.getenv("SECURETRACK_KEY_ALIAS") ?: ""
                keyPassword = (project.findProperty("SECURETRACK_KEY_PASSWORD") as String?)
                    ?: System.getenv("SECURETRACK_KEY_PASSWORD") ?: ""
            } else {
                // Compatibility fallback: preserves existing installation signing key on physical test fleet
                val defaultKeystore = file(System.getProperty("user.home") + "/.android/debug.keystore")
                if (defaultKeystore.exists()) {
                    storeFile = defaultKeystore
                    storePassword = "android"
                    keyAlias = "androiddebugkey"
                    keyPassword = "android"
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity.ktx)
    implementation(libs.constraintlayout)
    implementation(libs.swiperefreshlayout)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // Database & Async Tasks
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)
    implementation(libs.work.runtime)

    // Location & Maps
    implementation(libs.play.services.location)
    implementation(libs.osmdroid.android)

    // Google Sign-In & Credential Manager
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Firebase BoM, Authentication, Cloud Firestore, App Check & Functions
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-functions")
    implementation("com.google.firebase:firebase-appcheck")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    implementation("com.google.firebase:firebase-appcheck-debug")

    // QR Code Scanning and Generation
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // Biometric Authentication
    implementation("androidx.biometric:biometric:1.1.0")

    // Lifecycle
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    implementation(libs.lifecycle.service)

    // Testing
    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.12.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation("androidx.room:room-testing:2.6.1")
}
