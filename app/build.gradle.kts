import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// 릴리즈 서명 키 정보. keystore.properties/*.jks 는 커밋되지 않으므로 로컬에 없으면
// 릴리즈 빌드만 서명 없이 실패한다(디버그 빌드는 영향 없음).
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        load(FileInputStream(keystorePropertiesFile))
    }
}

// 서울시 버스 Open API 서비스키. local.properties 의 BUS_API_KEY=... 로 넣는다(커밋되지 않음).
// 설정 화면에서 입력받지 않고 빌드에 구워 넣는다 — 키는 개발자 소유이고 사용자가 바꿀 일이 없다.
val busApiKey: String = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        load(FileInputStream(file))
    }
}.getProperty("BUS_API_KEY", "")

// CARTO 베이스맵 API 키. local.properties 의 CARTO_API_KEY=... 로 넣는다(커밋되지 않음).
val cartoApiKey: String = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        load(FileInputStream(file))
    }
}.getProperty("CARTO_API_KEY", "")

android {
    namespace = "com.example.busnowbar"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.busnowbar"
        minSdk = 36
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "BUS_API_KEY", "\"${busApiKey.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        buildConfigField("String", "CARTO_API_KEY", "\"${cartoApiKey.replace("\\", "\\\\").replace("\"", "\\\"")}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = true
                // kotlin.** 을 범위에 넣으면 R8이 kotlin.collections 내부 클래스를 잘못
                // 리패키징해 IllegalAccessError로 크래시하는 문제가 있어 뺐다
                // (androidx.startup.InitializationProvider 초기화 중 실제로 재현됨).
                packageScope = setOf("androidx.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.osmdroid.android)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
