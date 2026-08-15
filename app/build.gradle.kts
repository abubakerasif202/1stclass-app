import java.io.File
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
}

val localProperties = Properties().apply {
  val file = rootProject.file("local.properties")
  if (file.exists()) file.inputStream().use { load(it) }
}

/**
 * Resolves a build-time setting from local.properties first, then the environment (CI secrets).
 * Nothing is defaulted to a real endpoint — an unset value leaves the app in local/offline mode
 * rather than pointing a build at somebody else's server.
 */
fun buildSetting(name: String): String =
  localProperties.getProperty(name) ?: System.getenv(name) ?: ""

/**
 * Production traffic must be HTTPS. A misconfigured release that would ship cleartext to a real
 * TMS fails the build instead of silently downgrading driver evidence to plain HTTP.
 */
fun requireHttps(url: String, buildType: String): String {
  if (url.isNotBlank() && !url.startsWith("https://")) {
    throw GradleException(
      "TMS_BASE_URL for the $buildType build must use https:// — refusing to build with '$url'."
    )
  }
  return url
}

val tmsBaseUrl = buildSetting("TMS_BASE_URL")
// Debug may point at a local dev server over http; release and staging use secure or configured endpoints.
val tmsBaseUrlDebug = buildSetting("TMS_BASE_URL_DEBUG").ifBlank { tmsBaseUrl }
val tmsBaseUrlStaging = buildSetting("TMS_BASE_URL_STAGING").ifBlank { "https://staging-api.1stclassexpress.com.au" }
val firebaseMessagingEnabled = buildSetting("FIREBASE_MESSAGING_ENABLED")
  .ifBlank { "true" }
  .toBooleanStrictOrNull() ?: true

val releaseSigningValues = mapOf(
  "KEYSTORE_PATH" to buildSetting("KEYSTORE_PATH"),
  "KEYSTORE_PASSWORD" to buildSetting("KEYSTORE_PASSWORD"),
  "KEY_ALIAS" to buildSetting("KEY_ALIAS"),
  "KEY_PASSWORD" to buildSetting("KEY_PASSWORD")
)

android {
  namespace = "au.com.firstclassexpress.driver"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "au.com.firstclassexpress.driver"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0.0"
    manifestPlaceholders["MAPS_API_KEY"] =
      localProperties.getProperty("MAPS_API_KEY") ?: System.getenv("MAPS_API_KEY") ?: ""
    manifestPlaceholders["FCM_ENABLED"] = firebaseMessagingEnabled

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // Development-only driver credentials. Release overrides these to empty strings so no test
    // account is provisioned in, or discoverable from, a production build.
    buildConfigField("String", "DEV_DRIVER_ID", "\"\"")
    buildConfigField("String", "DEV_DRIVER_PIN", "\"\"")
    buildConfigField("boolean", "SHOW_DEV_CREDENTIALS", "false")
    buildConfigField("boolean", "FCM_ENABLED", firebaseMessagingEnabled.toString())
    // Empty until the 1st Class Express TMS endpoint exists; the app falls back to local auth and
    // reports remote sync as unavailable rather than pretending queued work reached a server.
    buildConfigField("String", "TMS_BASE_URL", "\"\"")
    buildConfigField("String", "ENVIRONMENT_NAME", "\"production\"")
  }

  signingConfigs {
    create("release") {
      if (releaseSigningValues.values.all { it.isNotBlank() }) {
        storeFile = file(releaseSigningValues.getValue("KEYSTORE_PATH"))
        storePassword = releaseSigningValues.getValue("KEYSTORE_PASSWORD")
        keyAlias = releaseSigningValues.getValue("KEY_ALIAS")
        keyPassword = releaseSigningValues.getValue("KEY_PASSWORD")
      }
    }
  }

  buildTypes {
    debug {
      buildConfigField("String", "DEV_DRIVER_ID", "\"DRV-8492\"")
      buildConfigField("String", "DEV_DRIVER_PIN", "\"1234\"")
      buildConfigField("boolean", "SHOW_DEV_CREDENTIALS", "true")
      buildConfigField("String", "TMS_BASE_URL", "\"$tmsBaseUrlDebug\"")
      buildConfigField("String", "ENVIRONMENT_NAME", "\"development\"")
    }
    create("staging") {
      initWith(getByName("debug"))
      applicationIdSuffix = ".staging"
      matchingFallbacks += listOf("debug")
      buildConfigField("String", "DEV_DRIVER_ID", "\"DRV-8492\"")
      buildConfigField("String", "DEV_DRIVER_PIN", "\"1234\"")
      buildConfigField("boolean", "SHOW_DEV_CREDENTIALS", "true")
      buildConfigField("String", "TMS_BASE_URL", "\"$tmsBaseUrlStaging\"")
      buildConfigField("String", "ENVIRONMENT_NAME", "\"staging\"")
    }
    release {
      buildConfigField("String", "TMS_BASE_URL", "\"${requireHttps(tmsBaseUrl, "release")}\"")
      buildConfigField("String", "ENVIRONMENT_NAME", "\"production\"")
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      if (releaseSigningValues.values.all { it.isNotBlank() }) {
        signingConfig = signingConfigs.getByName("release")
      }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// A release build must fail before packaging if signing is not configured, rather than emitting an
// unsigned artifact. The checks below are resolved at configuration time into plain values: a task
// action that calls a build-script function or `file()` captures a Gradle script object reference,
// which the configuration cache cannot serialize — that previously made every release task fail.
tasks.configureEach {
  if (name == "packageRelease" || name == "bundleRelease" || name == "assembleRelease") {
    val missingSigningValues = releaseSigningValues.filterValues { it.isBlank() }.keys.sorted()
    val keystorePath = releaseSigningValues.getValue("KEYSTORE_PATH")
    doFirst {
      if (missingSigningValues.isNotEmpty()) {
        throw GradleException(
          "Release signing is not configured. Set: ${missingSigningValues.joinToString(", ")}."
        )
      }
      if (!File(keystorePath).isFile) {
        throw GradleException("KEYSTORE_PATH does not reference a readable file: $keystorePath")
      }
    }
  }
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.core.splashscreen)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.exifinterface)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.messaging)
  // Firebase Messaging is safe without google-services.json: runtime initialization is gated.

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.play.services.location)
  implementation(libs.play.services.maps)
  implementation(libs.maps.compose)
  implementation(libs.retrofit)
  implementation(libs.androidx.security.crypto)
  implementation(libs.androidx.work.runtime.ktx)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.mockwebserver)
  testImplementation(libs.androidx.work.testing)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
