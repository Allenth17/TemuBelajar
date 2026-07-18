plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

android {
    namespace = "com.hiralen.temubelajar"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hiralen.temubelajar"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = project.findProperty("app.version") as? String ?: "1.0.0"

        // Phase 0.12 — inject BASE_URL / BASE_WS_URL from Gradle properties so
        // release builds ship the production HTTPS endpoint and never the dev
        // cleartext LAN IP. Defaults remain HTTP for `assembleDebug` runs.
        buildConfigField(
            "String",
            "BASE_URL",
            "\"${project.findProperty("api.url") as? String ?: "http://192.168.1.4:4000"}\""
        )
        buildConfigField(
            "String",
            "BASE_WS_URL",
            "\"${project.findProperty("api.wsUrl") as? String ?: "ws://192.168.1.4:4000/socket/websocket?vsn=2.0.0"}\""
        )
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
    }
    buildTypes {
        release {
            // Phase 8.6 — release must be obfuscated & shrunk so the bundled
            // BASE_URL / BuildConfig values aren't trivially recoverable from
            // the APK. Keep rules below in proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(project(":composeApp"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.decompose)
    implementation(libs.decompose.extensions.compose)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
}
