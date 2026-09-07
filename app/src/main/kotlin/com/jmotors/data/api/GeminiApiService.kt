package com.jmotors.data.api

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Google Gemini generateContent REST API.
 * API key is passed as the `key` query parameter — never baked into the path.
 */
interface GeminiApiService {

    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") key: String,
        @Body request: GeminiGenerateRequest,
    ): GeminiGenerateResponse
}
