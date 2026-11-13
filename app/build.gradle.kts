plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.contestproject.app"
    compileSdk = 35
    // CarPropertyManager(기어·바깥 온도·시동 상태)용 AAOS 라이브러리. 차량에 기본 탑재돼 있다.
    useLibrary("android.car")

    defaultConfig {
        // TODO(제출 전): applicationId 확정. Pleos 배포 후에는 변경 불가.
        applicationId = "com.contestproject.app"
        minSdk = 28      // Pleos 요구사항: API 28 이상
        targetSdk = 34   // Pleos Connect Emulator = API 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            // R8로 코드 축소·최적화(앱 시작 속도와 APK 크기 개선). Pleos SDK는 proguard-rules.pro에서 보존한다.
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // 릴리스와 같은 최적화로 에뮬레이터에서 성능을 재기 위한 빌드(디버그 키로 서명). 제출용 아님.
        create("staging") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
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
        buildConfig = true
    }
    testOptions {
        // JVM 단위 테스트에서 android.util.Log 같은 프레임워크 호출이 예외 대신 기본값을 돌려주게 한다.
        unitTests.isReturnDefaultValues = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.tts)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
