plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

val useWhisperJni = project.findProperty("useWhisperJni")?.toString()?.toBoolean() ?: false

android {
    namespace = "com.example.offlinehqasr"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.offlinehqasr"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "0.6.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        buildConfigField("boolean", "USE_WHISPER", if (useWhisperJni) "true" else "false")

        if (useWhisperJni) {
            externalNativeBuild {
                cmake {
                    arguments += listOf("-DWHISPER_STUB=ON")
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            // You can toggle Whisper JNI by -PuseWhisperJni=true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    if (useWhisperJni) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
            }
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Vosk
    implementation("org.vosk:android:0.3.38")

    // JSON
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("com.squareup.okio:okio:3.9.0")

    // Optional Whisper JNI (placeholder, compileOnly so project compiles without it)
    if (useWhisperJni) {
        implementation("ai.picovoice:porcupine-android:3.0.2") // dummy dep to show optionality
    }

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
