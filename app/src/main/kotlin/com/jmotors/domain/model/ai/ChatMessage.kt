package com.jmotors.domain.model.ai

/**
 * One turn in the Gemini chat session. Roles map 1:1 to REST `contents[].role`.
 */
data class ChatMessage(
    val role: ChatRole,
    val text: String,
)

enum class ChatRole {
    USER,
    MODEL,
}
