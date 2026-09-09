import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}
localProperties.forEach { (key, value) ->
    extra[key.toString()] = value
}

android {
    namespace = "com.jmotors"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jmotors"
        minSdk = 26
        targetSdk = 34
        versionCode = 10
        versionName = "0.9.0"
        buildConfigField("String", "GEMINI_API_KEY", "\"${project.findProperty("gemini.api.key") ?: ""}\"")
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
}

dependencies {
    // Lifecycle & ViewModel — MVVM for Compose
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Coroutines — async LLM / IO work
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Retrofit — modular LLM API client (switchable to local Llama later)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")

    // Jetpack Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Filament — glTF/GLB PBR viewer for the floating car (no ARCore)
    implementation("com.google.android.filament:filament-android:1.57.1")
    implementation("com.google.android.filament:gltfio-android:1.57.1")
    implementation("com.google.android.filament:filament-utils-android:1.57.1")
}

val carGlbDir = file("src/main/assets/models/cars")

/**
 * Class stand-ins (sedan / sport sedan / SUV) until J Motors inventory GLBs land.
 * Files are written next to [placeholder.txt] and reused if already present.
 */
val mvpCarGlbCatalog = mapOf(
    "avante.glb" to listOf(
        "https://cdn.jsdelivr.net/gh/KhronosGroup/glTF-Sample-Assets@main/Models/ToyCar/glTF-Binary/ToyCar.glb",
        "https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Assets/main/Models/ToyCar/glTF-Binary/ToyCar.glb",
    ),
    "sonata.glb" to listOf(
        "https://cdn.jsdelivr.net/gh/mrdoob/three.js@r160/examples/models/gltf/ferrari.glb",
        "https://cdn.jsdelivr.net/gh/KhronosGroup/glTF-Sample-Assets@main/Models/ToyCar/glTF-Binary/ToyCar.glb",
    ),
    "santa_fe.glb" to listOf(
        "https://cdn.jsdelivr.net/gh/KhronosGroup/glTF-Sample-Models@master/2.0/CesiumMilkTruck/glTF-Binary/CesiumMilkTruck.glb",
        "https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Models/master/2.0/CesiumMilkTruck/glTF-Binary/CesiumMilkTruck.glb",
    ),
)

tasks.register("downloadCarModels") {
    group = "jmotors"
    description = "Downloads Avante / Sonata / Santa Fe class-stand-in GLBs into assets/models/cars"
    outputs.dir(carGlbDir)
    doLast {
        carGlbDir.mkdirs()
        mvpCarGlbCatalog.forEach { (fileName, urls) ->
            val dest = carGlbDir.resolve(fileName)
            if (dest.isFile && dest.length() > 1024L) {
                logger.lifecycle("JMotors: skip $fileName (${dest.length()} bytes already on disk)")
                return@forEach
            }
            var lastError: Exception? = null
            for (url in urls) {
                try {
                    logger.lifecycle("JMotors: downloading $fileName ← $url")
                    val connection = URI.create(url).toURL().openConnection() as HttpURLConnection
                    connection.connectTimeout = 20_000
                    connection.readTimeout = 90_000
                    connection.instanceFollowRedirects = true
                    connection.setRequestProperty("User-Agent", "JMotors-MVP/0.7")
                    connection.inputStream.use { input: InputStream ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    }
                    check(dest.length() > 1024L) { "Downloaded $fileName is too small (${dest.length()} bytes)" }
                    logger.lifecycle("JMotors: saved $fileName (${dest.length()} bytes)")
                    lastError = null
                    break
                } catch (error: Exception) {
                    lastError = error
                    logger.warn("JMotors: failed $url (${error.message})")
                    dest.delete()
                }
            }
            if (lastError != null) {
                throw lastError
            }
        }
    }
}

tasks.named("preBuild") {
    dependsOn("downloadCarModels")
}
