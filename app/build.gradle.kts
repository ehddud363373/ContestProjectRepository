import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// release 서명 정보는 git에 올리지 않는 local.properties에서만 읽는다(키 파일도 저장소 밖에 둔다).
// 값이 없으면 release는 서명 없이 빌드되고(설치 불가), 디버그·테스트 빌드에는 영향이 없다.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val releaseKeystore = localProps.getProperty("SHIMPYO_KEYSTORE")

android {
    namespace = "com.shimpyo.app"
    compileSdk = 35
    // CarPropertyManager(기어·바깥 온도·시동 상태)용 AAOS 라이브러리. 차량에 기본 탑재돼 있다.
    useLibrary("android.car")

    defaultConfig {
        // Pleos 배포 후에는 바꿀 수 없다.
        applicationId = "com.shimpyo.app"
        minSdk = 28      // Pleos 요구사항: API 28 이상
        targetSdk = 34   // Pleos Connect Emulator = API 34
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = localProps.getProperty("SHIMPYO_STORE_PASSWORD")
                keyAlias = localProps.getProperty("SHIMPYO_KEY_ALIAS")
                keyPassword = localProps.getProperty("SHIMPYO_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
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
