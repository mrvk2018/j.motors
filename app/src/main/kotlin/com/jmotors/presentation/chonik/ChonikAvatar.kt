package com.jmotors.presentation.chonik

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jmotors.domain.model.ai.ChonikEmotion

/**
 * Floating neon sphere for Чоник. Color follows [emotion]; size breathes and reacts to [audioAmplitude].
 */
@Composable
fun ChonikAvatar(
    emotion: ChonikEmotion,
    modifier: Modifier = Modifier,
    audioAmplitude: Float = 0f,
    size: Dp = 220.dp,
    /**
     * SBS highlight bias: `-1` left eye (specular left), `+1` right eye (specular right).
     * Zero keeps a slight upper-left studio highlight for previews.
     */
    stereoHighlightBias: Float = 0f,
) {
    val coreColor by animateColorAsState(
        targetValue = emotion.coreColor(),
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "chonikCoreColor",
    )
    val glowColor by animateColorAsState(
        targetValue = emotion.glowColor(),
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "chonikGlowColor",
    )
    val highlightColor by animateColorAsState(
        targetValue = emotion.highlightColor(),
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "chonikHighlightColor",
    )

    val breathTransition = rememberInfiniteTransition(label = "chonikBreath")
    val breathScale by breathTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "chonikBreathScale",
    )
    val glowPulse by breathTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "chonikGlowPulse",
    )

    val speechBoost = 1f + audioAmplitude.coerceIn(0f, 1f) * 0.42f
    val scale = breathScale * speechBoost

    Canvas(
        modifier = modifier
            .size(size)
            .scale(scale),
    ) {
        val radius = this.size.minDimension / 2f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val highlightShift = if (stereoHighlightBias == 0f) {
            -radius * 0.22f
        } else {
            radius * 0.20f * stereoHighlightBias
        }
        val highlightCenter = Offset(
            x = center.x + highlightShift,
            y = center.y - radius * 0.28f,
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    glowColor.copy(alpha = 0.35f * glowPulse),
                    glowColor.copy(alpha = 0.12f * glowPulse),
                    Color.Transparent,
                ),
                center = center,
                radius = radius * 1.05f,
            ),
            radius = radius * 0.98f,
            center = center,
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    highlightColor.copy(alpha = 0.95f),
                    coreColor,
                    glowColor.copy(alpha = 0.85f),
                    glowColor.copy(alpha = 0.15f),
                ),
                center = highlightCenter,
                radius = radius * 0.92f,
            ),
            radius = radius * 0.62f,
            center = center,
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.85f),
                    Color.White.copy(alpha = 0.25f),
                    Color.Transparent,
                ),
                center = highlightCenter,
                radius = radius * 0.28f,
            ),
            radius = radius * 0.28f,
            center = highlightCenter,
        )
    }
}

internal fun ChonikEmotion.coreColor(): Color = when (this) {
    ChonikEmotion.CALM -> Color(0xFF00E5FF)
    ChonikEmotion.JOY -> Color(0xFF00E676)
    ChonikEmotion.WARNING -> Color(0xFFFFC107)
    ChonikEmotion.SARCASM -> Color(0xFFFF1744)
    ChonikEmotion.DELIGHT -> Color(0xFFE040FB)
}

internal fun ChonikEmotion.glowColor(): Color = when (this) {
    ChonikEmotion.CALM -> Color(0xFF00BCD4)
    ChonikEmotion.JOY -> Color(0xFF00C853)
    ChonikEmotion.WARNING -> Color(0xFFFF9800)
    ChonikEmotion.SARCASM -> Color(0xFFD50000)
    ChonikEmotion.DELIGHT -> Color(0xFFAA00FF)
}

internal fun ChonikEmotion.highlightColor(): Color = when (this) {
    ChonikEmotion.CALM -> Color(0xFFB2EBF2)
    ChonikEmotion.JOY -> Color(0xFFB9F6CA)
    ChonikEmotion.WARNING -> Color(0xFFFFF8E1)
    ChonikEmotion.SARCASM -> Color(0xFFFFCDD2)
    ChonikEmotion.DELIGHT -> Color(0xFFF8BBD0)
}

@Preview(showBackground = true, backgroundColor = 0xFF050510)
@Composable
private fun ChonikAvatarCalmPreview() {
    ChonikAvatar(emotion = ChonikEmotion.CALM)
}

@Preview(showBackground = true, backgroundColor = 0xFF050510)
@Composable
private fun ChonikAvatarDelightPreview() {
    ChonikAvatar(emotion = ChonikEmotion.DELIGHT, audioAmplitude = 0.35f)
}
