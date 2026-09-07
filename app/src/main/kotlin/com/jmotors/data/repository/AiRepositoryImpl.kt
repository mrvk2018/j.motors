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
import com.jmotors.domain.model.ai.ChatMessage
import com.jmotors.domain.model.ai.ChatRole
import com.jmotors.domain.model.ai.ChonikState
import com.jmotors.domain.model.ai.ChonikSystemPrompt
import com.jmotors.domain.model.ai.UserProfile
import com.jmotors.domain.repository.AiRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException

/**
 * Gemini-backed [AiRepository] with an in-memory chat session.
 * Each user turn is appended; generateContent always receives the full history.
 */
class AiRepositoryImpl(
    private val api: GeminiApiService = GeminiNetwork.api,
    private val model: String = GeminiNetwork.MODEL,
    private val apiKey: String = BuildConfig.GEMINI_API_KEY,
) : AiRepository {

    private val mutex = Mutex()
    private val session = mutableListOf<ChatMessage>()

    override fun recordOpeningLine(greeting: String) {
        val line = greeting.trim()
        if (line.isEmpty()) return
        synchronized(session) {
            if (session.isNotEmpty()) return
            session += ChatMessage(
                role = ChatRole.USER,
                text = "Сессия цифрового шоурума J Motors началась. Ты уже поздоровался вслух.",
            )
            session += ChatMessage(role = ChatRole.MODEL, text = line)
        }
    }

    override fun clearSession() {
        synchronized(session) { session.clear() }
    }

    override suspend fun generateReply(
        userMessage: String,
        profile: UserProfile,
        state: ChonikState,
    ): String = mutex.withLock {
        require(userMessage.isNotBlank()) { "User message must not be blank" }
        check(apiKey.isNotBlank() && apiKey != MOCK_CI_KEY) {
            "Сборка без живого ключа Gemini. CI mock не ходит в API — нужен gemini.api.key в local.properties."
        }

        val trimmed = userMessage.trim()
        session += ChatMessage(role = ChatRole.USER, text = trimmed)
        trimSessionLocked()

        Log.i(TAG, "Gemini generateReply chars=${trimmed.length} history=${session.size} keyLen=${apiKey.length}")

        val request = GeminiGenerateRequest(
            systemInstruction = GeminiContent(
                parts = listOf(GeminiPart(text = ChonikSystemPrompt.build(profile, state))),
            ),
            contents = session.map { it.toGeminiContent() },
            generationConfig = GeminiGenerationConfig(
                temperature = 0.85,
                maxOutputTokens = GeminiNetwork.MAX_OUTPUT_TOKENS,
            ),
        )

        val text = try {
            val response = api.generateContent(
                model = model,
                key = apiKey,
                request = request,
            )
            val reply = response.plainText()
            check(reply.isNotBlank()) { "Gemini returned an empty reply" }
            reply
        } catch (error: HttpException) {
            rollbackLastUserTurnLocked(trimmed)
            val body = error.response()?.errorBody()?.string().orEmpty().take(400)
            Log.e(TAG, "Gemini HTTP ${error.code()} $body")
            throw error
        } catch (error: Exception) {
            rollbackLastUserTurnLocked(trimmed)
            throw error
        }
        session += ChatMessage(role = ChatRole.MODEL, text = text)
        trimSessionLocked()
        Log.i(TAG, "Gemini reply chars=${text.length} history=${session.size}")
        text
    }

    private fun trimSessionLocked() {
        val max = GeminiNetwork.MAX_HISTORY_MESSAGES
        if (session.size <= max) return
        val opening = session.take(2)
        val tail = session.drop(2).takeLast(max - 2)
        session.clear()
        session.addAll(opening)
        session.addAll(tail)
    }

    private fun rollbackLastUserTurnLocked(userText: String) {
        val last = session.lastOrNull() ?: return
        if (last.role == ChatRole.USER && last.text == userText) {
            session.removeAt(session.lastIndex)
        }
    }

    private fun ChatMessage.toGeminiContent(): GeminiContent = GeminiContent(
        role = if (role == ChatRole.USER) "user" else "model",
        parts = listOf(GeminiPart(text = text)),
    )

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
