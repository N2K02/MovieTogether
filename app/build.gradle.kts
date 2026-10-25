plugins { id("com.android.application"); id("org.jetbrains.kotlin.plugin.compose") }

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
    testImplementation("junit:junit:4.13.2")
}

 
// Shared updater compatibility contract: every Android build must use a pinned
// dev.n2k02:updater version >= 0.1.19. This task is also available standalone.
val verifySharedUpdaterVersion = tasks.register("verifySharedUpdaterVersion") {
    group = "verification"
    description = "Reject missing, dynamic, or outdated shared Android updater dependencies"
    doLast {
        val updaterDependencies = configurations
            .filter { it.name == "implementation" || it.name == "api" || it.name.endsWith("Implementation") }
            .flatMap { it.dependencies }
            .filter { it.group == "dev.n2k02" && it.name == "updater" }
        check(updaterDependencies.isNotEmpty()) {
            "Missing dev.n2k02:updater dependency. Require a pinned version >= 0.1.19."
        }
        updaterDependencies.forEach { dependency ->
            val version = dependency.version ?: error("dev.n2k02:updater must have a pinned version >= 0.1.19")
            val segments = version.split('.').map { it.toIntOrNull() }
            check(segments.size >= 3 && segments.all { it != null }) {
                "dev.n2k02:updater:$version is not a pinned numeric release version."
            }
            val different = segments.filterNotNull().zip(listOf(0, 1, 19))
                .firstOrNull { (actual, minimum) -> actual != minimum }
            check(different == null || different.first > different.second) {
                "dev.n2k02:updater:$version is outdated; require >= 0.1.19."
            }
        }
    }
}
// preBuild precedes both release and debug APK builds; check covers verification.
tasks.matching { it.name == "preBuild" || it.name == "check" }.configureEach {
    dependsOn(verifySharedUpdaterVersion)
}
