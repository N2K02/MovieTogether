plugins { id("com.android.application"); id("org.jetbrains.kotlin.plugin.compose") }

// Firebase remains optional while waiting for the Android app's google-services.json.
// Without the file CI produces a working local-only APK instead of a misconfigured crash.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

val appVersion = (findProperty("appVersion") as String?) ?: "1.0"
val appVersionCode = (findProperty("appVersionCode") as String?)?.toIntOrNull() ?: 10
val appVersionSuffix = (findProperty("appVersionSuffix") as String?) ?: ""

android {
    namespace = "com.movietogether.app"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.movietogether.app"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersion + appVersionSuffix
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation("dev.n2k02:theme:0.1.4")
    implementation("dev.n2k02:updater:0.1.19")
    implementation("androidx.compose.material3:material3:1.5.0-beta01")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.media3:media3-exoplayer:1.6.1")
    implementation("androidx.media3:media3-ui:1.6.1")
    implementation("org.nanohttpd:nanohttpd-websocket:2.3.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation(platform("com.google.firebase:firebase-bom:35.0.2"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-database")
    testImplementation("junit:junit:4.13.2")
}
