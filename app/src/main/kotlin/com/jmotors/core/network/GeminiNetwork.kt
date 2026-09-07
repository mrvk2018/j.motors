package com.jmotors.core.network

import android.util.Log
import com.jmotors.data.api.GeminiApiService
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit entry point for the Cloud LLM (Gemini). Easy to swap for a local Llama client later.
 */
object GeminiNetwork {

    const val BASE_URL: String = "https://generativelanguage.googleapis.com/"

    /** MVP flash model; switch to gemini-2.5-flash-lite if quota is tighter. */
    const val MODEL: String = "gemini-2.5-flash"

    /** User+model turns kept in the live chat cache (opening pair is always retained). */
    const val MAX_HISTORY_MESSAGES: Int = 28

    /** Caps TTS length: Chonik must stay punchy, not essay-length. */
    const val MAX_OUTPUT_TOKENS: Int = 160

    private const val TAG = "JMotors"

    val api: GeminiApiService by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                val redacted = request.url.newBuilder().removeAllQueryParameters("key").build()
                Log.i(TAG, "Gemini → ${request.method} $redacted")
                val response = chain.proceed(request)
                Log.i(TAG, "Gemini ← HTTP ${response.code}")
                response
            }
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeminiApiService::class.java)
    }
}
