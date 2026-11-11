plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.trekdeck.remote"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.trekdeck.remote"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Сеть
    implementation("com.squareup.okhttp3:okhttp:4.12.0")            // WebSocket
    implementation("com.squareup.retrofit2:retrofit:2.11.0")        // REST
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")  // JSON
}
