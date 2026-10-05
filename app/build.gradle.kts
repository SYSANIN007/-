plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "ru.sysanin.wearschedule"
    compileSdk = 34

    defaultConfig {
        applicationId = "ru.sysanin.wearschedule"
        minSdk = 26          // Wear OS 2.0+ (также работает на Wear OS 3/4/5)
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    val useBom = platform(libs.androidx.compose.bom)
    implementation(useBom)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.wear.compose.material)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.kotlinx.serialization.json)

    // Для парсинга HTML-страницы расписания из зеркала ЛК УлГТУ.
    implementation(libs.jsoup)
}
