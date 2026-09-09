package com.jmotors.presentation.ar.car

import android.content.Context
import android.util.Log
import com.jmotors.domain.model.ai.ShowcaseCar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

/**
 * Resolves a glossy GLB for the floating showroom car.
 *
 * Lookup order for [loadCarModel]:
 * 1. Local `assets/models/cars/{carType}.glb` (Avante / Sonata / Santa Fe).
 * 2. Cached CDN download in `cacheDir/car_models/`.
 * 3. Public PBR sample from the catalog (class stand-in until inventory GLBs arrive).
 */
data class CarModelSpec(
    val carType: String,
    val localAssetPath: String,
    val remoteUrl: String,
    val cacheFileName: String,
)

object CarModelLoader {

    private const val TAG = "JMotors"
    private const val ASSET_DIR = "models/cars"

    /** Sedan class — Khronos ToyCar (PBR). */
    private const val CDN_SEDAN: String =
        "https://cdn.jsdelivr.net/gh/KhronosGroup/glTF-Sample-Assets@main/" +
            "Models/ToyCar/glTF-Binary/ToyCar.glb"

    /** Sport / business sedan class — three.js Ferrari sample (embedded PBR). */
    private const val CDN_SPORT_SEDAN: String =
        "https://cdn.jsdelivr.net/gh/mrdoob/three.js@r160/examples/models/gltf/ferrari.glb"

    /** SUV / crossover class — Khronos CesiumMilkTruck (larger body stand-in). */
    private const val CDN_SUV: String =
        "https://cdn.jsdelivr.net/gh/KhronosGroup/glTF-Sample-Models@master/" +
            "2.0/CesiumMilkTruck/glTF-Binary/CesiumMilkTruck.glb"

    private val CDN_BY_TYPE: Map<String, String> = mapOf(
        ShowcaseCar.AVANTE.key to CDN_SEDAN,
        ShowcaseCar.SONATA.key to CDN_SPORT_SEDAN,
        ShowcaseCar.SANTA_FE.key to CDN_SUV,
        "sedan" to CDN_SEDAN,
        "sports" to CDN_SPORT_SEDAN,
        "suv" to CDN_SUV,
        "default" to CDN_SEDAN,
    )

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Maps a free-form car type to a local-asset path and a CDN fallback.
     * Does not hit the network.
     */
    fun loadCarModel(carType: String): CarModelSpec {
        val key = normalize(carType)
        val remote = CDN_BY_TYPE[key] ?: CDN_SEDAN
        return CarModelSpec(
            carType = key,
            localAssetPath = "$ASSET_DIR/$key.glb",
            remoteUrl = remote,
            cacheFileName = "$key.glb",
        )
    }

    /**
     * Reads GLB bytes: bundled asset → disk cache → HTTPS CDN.
     */
    suspend fun fetchCarModel(context: Context, carType: String): ByteBuffer = withContext(Dispatchers.IO) {
        val spec = loadCarModel(carType)
        readAsset(context, spec.localAssetPath)?.let { bytes ->
            Log.i(TAG, "loadCarModel asset ${spec.localAssetPath} (${bytes.remaining()} bytes)")
            return@withContext bytes
        }
        val cacheFile = File(File(context.cacheDir, "car_models").apply { mkdirs() }, spec.cacheFileName)
        if (cacheFile.isFile && cacheFile.length() > 256) {
            Log.i(TAG, "loadCarModel cache ${cacheFile.name} (${cacheFile.length()} bytes)")
            return@withContext readFile(cacheFile)
        }
        Log.i(TAG, "loadCarModel CDN ${spec.remoteUrl}")
        val body = download(spec.remoteUrl)
        cacheFile.writeBytes(body)
        wrap(body)
    }

    fun normalize(carType: String): String {
        ShowcaseCar.fromTag(carType)?.let { return it.key }
        ShowcaseCar.fromUtterance(carType)?.let { return it.key }
        val trimmed = carType.trim().lowercase()
        if (trimmed.isEmpty()) return ShowcaseCar.AVANTE.key
        return ShowcaseCar.AVANTE.key
    }

    private fun readAsset(context: Context, path: String): ByteBuffer? {
        return runCatching {
            context.assets.open(path).use { input -> wrap(input.readBytes()) }
        }.getOrNull()
    }

    private fun readFile(file: File): ByteBuffer = wrap(file.readBytes())

    private fun download(url: String): ByteArray {
        val request = Request.Builder().url(url).get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("GLB download HTTP ${response.code} for $url")
            }
            return response.body?.bytes() ?: error("Empty GLB body for $url")
        }
    }

    private fun wrap(bytes: ByteArray): ByteBuffer =
        ByteBuffer.allocateDirect(bytes.size)
            .order(ByteOrder.nativeOrder())
            .put(bytes)
            .apply { rewind() }
}
