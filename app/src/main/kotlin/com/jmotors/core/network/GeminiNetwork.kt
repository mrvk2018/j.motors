package com.jmotors.core.network

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

    val api: GeminiApiService by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeminiApiService::class.java)
    }
}
