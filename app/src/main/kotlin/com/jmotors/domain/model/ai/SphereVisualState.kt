package com.jmotors.domain.model.ai

/**
 * Visual state machine for the central AI sphere in the XREAL showroom.
 * Independent of Gemini [EMOTION] tags; those map into this machine.
 */
enum class SphereVisualState {
    /** Resting assistant — cyan / tech-blue pulse. */
    IDLE,

    /** Voice/text in flight — purple / white energy waves. */
    THINKING,

    /** Agreement, match, or credit approved — emerald neon. */
    SUCCESS,

    /** Engine warning, credit denied, or API failure — alert red. */
    ERROR,
}

/**
 * Resolves sphere color from live assistant signals.
 * Priority: thinking → error → emotion/funnel success → idle.
 */
fun resolveSphereVisualState(
    isGenerating: Boolean,
    errorMessage: String?,
    emotion: ChonikEmotion,
    dialogueState: ChonikState,
    forcedState: SphereVisualState? = null,
): SphereVisualState {
    if (isGenerating) return SphereVisualState.THINKING
    if (forcedState == SphereVisualState.SUCCESS) return SphereVisualState.SUCCESS
    if (!errorMessage.isNullOrBlank()) return SphereVisualState.ERROR
    if (forcedState != null) return forcedState
    return when {
        emotion == ChonikEmotion.WARNING || emotion == ChonikEmotion.SARCASM ->
            SphereVisualState.ERROR
        emotion == ChonikEmotion.JOY || emotion == ChonikEmotion.DELIGHT ->
            SphereVisualState.SUCCESS
        dialogueState is ChonikState.HandoverToOffice ->
            SphereVisualState.SUCCESS
        else -> SphereVisualState.IDLE
    }
}
