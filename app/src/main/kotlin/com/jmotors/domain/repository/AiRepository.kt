package com.jmotors.domain.repository

import com.jmotors.domain.model.ai.ChonikState
import com.jmotors.domain.model.ai.UserProfile

/**
 * Cloud LLM port. MVP talks to Gemini; a local Llama implementation can replace it later.
 * Implementations must keep an active chat session so later turns see the full dialogue.
 */
interface AiRepository {

    /**
     * Appends [userMessage] to the session, sends the **entire** history to the LLM,
     * stores the model reply, and returns plain text.
     */
    suspend fun generateReply(
        userMessage: String,
        profile: UserProfile,
        state: ChonikState,
    ): String

    /** Seeds the session with the locally spoken greeting so Gemini continues, not restarts. */
    fun recordOpeningLine(greeting: String)

    fun clearSession()
}
