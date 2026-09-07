package com.jmotors.domain.model.ai

/**
 * Emotional palette of the floating Чоник sphere.
 * Colors are applied in presentation; names stay in the Gemini `[EMOTION: …]` tag.
 */
enum class ChonikEmotion {
    /** Calm / thinking — neon blue / turquoise. */
    CALM,

    /** Approval / success — emerald green. */
    JOY,

    /** Attention / taxes / car cons — yellow / orange. */
    WARNING,

    /** Irony / junk-car warning — ruby red. */
    SARCASM,

    /** Perfect match — pink / magenta. */
    DELIGHT,
}
