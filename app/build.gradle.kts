plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.kotlin.kapt)
}

android {
    namespace = "com.xuan.fitai"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.xuan.fitai"
        minSdk = 31
        targetSdk = 36
        versionCode = 12
        versionName = "1.3.8"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
      jniLibs {
        useLegacyPackaging = false
      }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation("dev.chrisbanes.haze:haze:1.7.2")
  implementation("androidx.compose.material:material-icons-core")
  implementation("androidx.compose.material:material-icons-extended")
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  implementation("androidx.window:window:1.5.0")
  implementation("androidx.work:work-runtime-ktx:2.12.0")
  androidTestImplementation("androidx.work:work-testing:2.12.0")

  // Navigation Compose
  implementation(libs.androidx.navigation.compose)

  // Room Database
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  kapt(libs.androidx.room.compiler)

  // DataStore Preferences
  implementation(libs.androidx.datastore.preferences)

  // CameraX
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)

  // MediaPipe
  implementation(libs.mediapipe.tasks.genai)

  // Health Connect
  implementation("androidx.health.connect:connect-client:1.1.0-alpha11")

  // 16 KB Page Size Compatibility overrides
  implementation("androidx.graphics:graphics-path:1.1.0")
  implementation("com.google.ai.edge.litert:litert:1.4.2")
  implementation("com.google.ai.edge.litert:litert-gpu:1.4.2")
  implementation("com.google.ai.edge.litert:litert-gpu-api:1.4.2")
  implementation("com.google.ai.edge.litert:litert-support-api:1.4.2") {
      exclude(group = "com.google.android.odml", module = "image")
  }

  // Guava for ListenableFuture used by CameraX
  implementation("com.google.guava:guava:33.2.1-android")
}

