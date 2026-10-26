import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
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
        versionCode = 58
        versionName = "v2.4.2"
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
    // 16 KB page size（Android 16+）要求 native 库按 16 KB 对齐。真机 Android 17 实测会弹
    // 「此应用不符合 16 KB 对齐要求」（libandroidx.graphics.path.so 未对齐）。
    // useLegacyPackaging=false → .so 以未压缩方式存储，交给 zipalign 处理对齐。
    // 注：AGP 需 ≥ 8.5.1 才会自动使用 `zipalign -P 16`；本工程是 8.2.2，
    //     这一步只把打包方式摆正，彻底消除告警还需升级 AGP。
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
        jniLibs { useLegacyPackaging = false }
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    testOptions {
        unitTests {
            // Robolectric 需要 android 资源与 manifest，否则测试里 Context/Resources 为空
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
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
    implementation(libs.haze)

    // LSPosed / Xposed API (compileOnly, never pack into dex)
    compileOnly("de.robv.android.xposed:api:82")

    // ---- 单元测试 ----
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
