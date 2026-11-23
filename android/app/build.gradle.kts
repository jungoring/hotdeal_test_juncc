plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.jungoring.hotdeal"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jungoring.hotdeal"
        minSdk = 26
        targetSdk = 34
        versionCode = 5
        versionName = "1.0.4"
    }

    /**
     * 고정 디버그 서명 키 (2026-11-20 추가)
     * -------------------------------------------------
     * GitHub Actions 러너는 빌드마다 새 디버그 키를 만들기 때문에, 그 전에는 새 APK 를
     * 올리려면 앱을 지웠다가 다시 설치해야 했다.
     * 저장소에 함께 넣어 둔 이 키스토어로 항상 같은 서명을 쓰면, 다음 빌드부터는
     * 기존 앱을 지우지 않고 덮어쓰기 설치로 업데이트된다.
     * (디버그 전용 키이며 Play 스토어 업로드용 릴리스 키가 아니다)
     */
    signingConfigs {
        create("fixedDebug") {
            storeFile = file("debug-fixed.keystore")
            storeType = "PKCS12"
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixedDebug")
        }
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
        buildConfig = false
    }

    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
