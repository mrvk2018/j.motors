package com.jmotors.data.api

import com.google.gson.annotations.SerializedName

data class GeminiGenerateRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GeminiGenerationConfig? = null,
)

data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>,
)

data class GeminiPart(
    val text: String? = null,
)

data class GeminiGenerationConfig(
    val temperature: Double = 0.85,
    @SerializedName("maxOutputTokens")
    val maxOutputTokens: Int = 160,
)

data class GeminiGenerateResponse(
    val candidates: List<GeminiCandidate>? = null,
)

data class GeminiCandidate(
    val content: GeminiContent? = null,
    @SerializedName("finishReason")
    val finishReason: String? = null,
)
