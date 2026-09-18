import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

val currentBuildTime: String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

/**
 * GitHub Actions 发布构建使用的固定签名密钥。
 *
 * 不设置该环境变量时（本地开发），release 回退到 AGP 的 debug 签名，行为与以往一致。
 * CI 上则统一用同一份密钥，保证历代发布的 APK 签名身份相同、可相互覆盖安装。
 * 路径由 workflow 从 `FAKEGPS_KEYSTORE` 注入，口令另见三个同名环境变量。
 */
val ciKeystoreFile: File? = System.getenv("FAKEGPS_KEYSTORE")
    ?.let { File(it) }
    ?.takeIf { it.exists() }

android {
    namespace = "com.mockrun.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mockrun.app"
        minSdk = 29
        targetSdk = 34
        versionCode = 21
        versionName = "v1.4.4"
        buildConfigField("String", "BUILD_TIME", "\"$currentBuildTime\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        // 仅在 CI 注入了密钥时注册；本地构建不声明，避免干扰 debug 签名。
        if (ciKeystoreFile != null) {
            create("ci") {
                storeFile = ciKeystoreFile
                storePassword = System.getenv("FAKEGPS_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("FAKEGPS_KEY_ALIAS")
                keyPassword = System.getenv("FAKEGPS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (ciKeystoreFile != null) {
                signingConfigs.getByName("ci")
            } else {
                signingConfigs.getByName("debug")
            }
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
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // OSMDroid (open source map, no API key required)
    implementation(libs.osmdroid.android)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Gson
    implementation(libs.gson)

    // Chris Banes Haze (Glassmorphism / Frosted Glass)
    implementation("dev.chrisbanes.haze:haze:0.7.3")

    // LSPosed / Xposed API (compileOnly, never pack into dex)
    compileOnly("de.robv.android.xposed:api:82")
}
