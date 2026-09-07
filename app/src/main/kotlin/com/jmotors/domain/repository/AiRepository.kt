package com.jmotors.domain.repository

import com.jmotors.domain.model.ai.ChonikState
import com.jmotors.domain.model.ai.UserProfile

/**
 * Cloud LLM port. MVP talks to Gemini; a local Llama implementation can replace it later.
 */
interface AiRepository {

    /**
     * Sends the user's line plus funnel context and returns Gemini's plain-text reply.
     */
    suspend fun generateReply(
        userMessage: String,
        profile: UserProfile,
        state: ChonikState,
    ): String
}
