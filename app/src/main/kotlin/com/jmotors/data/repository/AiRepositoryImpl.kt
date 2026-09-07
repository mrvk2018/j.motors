package com.jmotors.data.repository

import android.util.Log
import com.jmotors.BuildConfig
import com.jmotors.core.network.GeminiNetwork
import com.jmotors.data.api.GeminiApiService
import com.jmotors.data.api.GeminiContent
import com.jmotors.data.api.GeminiGenerateRequest
import com.jmotors.data.api.GeminiGenerateResponse
import com.jmotors.data.api.GeminiGenerationConfig
import com.jmotors.data.api.GeminiPart
import com.jmotors.domain.model.ai.ChonikState
import com.jmotors.domain.model.ai.ChonikSystemPrompt
import com.jmotors.domain.model.ai.UserProfile
import com.jmotors.domain.repository.AiRepository
import retrofit2.HttpException

/**
 * Gemini-backed [AiRepository]. Swap this class for a local Llama client without touching ViewModel.
 */
class AiRepositoryImpl(
    private val api: GeminiApiService = GeminiNetwork.api,
    private val model: String = GeminiNetwork.MODEL,
    private val apiKey: String = BuildConfig.GEMINI_API_KEY,
) : AiRepository {

    override suspend fun generateReply(
        userMessage: String,
        profile: UserProfile,
        state: ChonikState,
    ): String {
        require(userMessage.isNotBlank()) { "User message must not be blank" }
        check(apiKey.isNotBlank() && apiKey != MOCK_CI_KEY) {
            "Сборка без живого ключа Gemini. CI mock не ходит в API — нужен gemini.api.key в local.properties."
        }
        Log.i(TAG, "Gemini generateReply chars=${userMessage.length} keyLen=${apiKey.length}")

        val request = GeminiGenerateRequest(
            systemInstruction = GeminiContent(
                parts = listOf(GeminiPart(text = ChonikSystemPrompt.build(profile, state))),
            ),
            contents = listOf(
                GeminiContent(
                    role = "user",
                    parts = listOf(GeminiPart(text = userMessage.trim())),
                ),
            ),
            generationConfig = GeminiGenerationConfig(temperature = 0.85),
        )

        val response = try {
            api.generateContent(
                model = model,
                key = apiKey,
                request = request,
            )
        } catch (error: HttpException) {
            val body = error.response()?.errorBody()?.string().orEmpty().take(400)
            Log.e(TAG, "Gemini HTTP ${error.code()} $body")
            throw error
        }
        val text = response.plainText()
        Log.i(TAG, "Gemini reply chars=${text.length}")
        check(text.isNotBlank()) { "Gemini returned an empty reply" }
        return text
    }

    private companion object {
        const val TAG = "JMotors"
        const val MOCK_CI_KEY = "MOCK_KEY_FOR_BUILD"
    }

    private fun GeminiGenerateResponse.plainText(): String =
        candidates
            .orEmpty()
            .flatMap { candidate -> candidate.content?.parts.orEmpty() }
            .mapNotNull { part -> part.text }
            .joinToString(separator = "")
            .trim()
}
