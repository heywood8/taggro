import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

val apiId = localProps.getProperty("TELEGRAM_API_ID", "0")
val apiHash = localProps.getProperty("TELEGRAM_API_HASH", "")

android {
    namespace = "com.heywood8.telegramnews"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.heywood8.telegramnews"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("int", "TELEGRAM_API_ID", apiId)
        buildConfigField("String", "TELEGRAM_API_HASH", "\"$apiHash\"")
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        create("release") {
            val storeFilePath = localProps.getProperty("RELEASE_STORE_FILE", "")
            if (storeFilePath.isNotBlank()) {
                storeFile = file(storeFilePath)
                storePassword = localProps.getProperty("RELEASE_STORE_PASSWORD", "")
                keyAlias = localProps.getProperty("RELEASE_KEY_ALIAS", "")
                keyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD", "")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
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
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.adaptive.nav)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.tdktx)
    implementation(libs.work.runtime.ktx)
    implementation("io.coil-kt.coil3:coil-compose:3.1.0")
    implementation("io.coil-kt.coil3:coil-android:3.1.0")
    implementation(libs.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.compose.ui.test)
    androidTestImplementation(libs.turbine)
}
