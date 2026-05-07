plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.billiards.sdk"
    compileSdk = 34

    defaultConfig {
        minSdk = 33
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    externalNativeBuild {
        cmake {
            cppFlags += "-std=c++17"
            arguments += listOf(
                "-DANDROID_STL=c++_shared",
                "-DANDROID_TOOLCHAIN=clang"
            )
        }
    }

    ndkVersion = "25.2.9519653"
}

dependencies {
    implementation("com.google.android.apps.common:content-capture:1.0.0-alpha8")
}
