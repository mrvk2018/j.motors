package com.jmotors.presentation.chonik

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jmotors.domain.model.ai.ChonikEmotion

/**
 * Holographic Unitree Go2 silhouette for Чоник: neon wireframe + mouth lip-sync.
 * [audioAmplitude] drives the snout indicator during TTS.
 */
@Composable
fun ChonikAvatar(
    emotion: ChonikEmotion,
    modifier: Modifier = Modifier,
    audioAmplitude: Float = 0f,
    size: Dp = 168.dp,
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

    val breathTransition = rememberInfiniteTransition(label = "chonikHolo")
    val breathScale by breathTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "chonikBreathScale",
    )
    val scan by breathTransition.animateFloat(
        initialValue = 0f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "chonikScan",
    )
    val glowPulse by breathTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "chonikGlowPulse",
    )

    val amplitude = audioAmplitude.coerceIn(0f, 1f)

    Canvas(
        modifier = modifier
            .size(width = size * 1.55f, height = size)
            .scale(breathScale),
    ) {
        drawHoloGo2(
            neon = coreColor,
            glow = glowColor,
            pulse = glowPulse,
            scan = scan,
            amplitude = amplitude,
        )
    }
}

private fun DrawScope.drawHoloGo2(
    neon: Color,
    glow: Color,
    pulse: Float,
    scan: Float,
    amplitude: Float,
) {
    val w = size.width
    val h = size.height
    fun p(nx: Float, ny: Float) = Offset(nx * w, ny * h)

    var y = scan % 8f
    while (y < h) {
        drawLine(
            color = neon.copy(alpha = 0.07f * pulse),
            start = Offset(0f, y),
            end = Offset(w, y),
            strokeWidth = 1.1f,
        )
        y += 8f
    }

    val body = Path().apply {
        addRoundRect(
            RoundRect(
                left = 0.22f * w,
                top = 0.34f * h,
                right = 0.70f * w,
                bottom = 0.56f * h,
                radiusX = w * 0.06f,
                radiusY = h * 0.08f,
            ),
        )
    }
    drawPath(body, color = glow.copy(alpha = 0.14f * pulse))
    drawPath(body, color = neon.copy(alpha = 0.95f), style = Stroke(width = 2.2f))
    drawPath(body, color = glow.copy(alpha = 0.35f * pulse), style = Stroke(width = 6.5f))

    val head = Path().apply {
        addRoundRect(
            RoundRect(
                left = 0.68f * w,
                top = 0.20f * h,
                right = 0.88f * w,
                bottom = 0.42f * h,
                radiusX = w * 0.035f,
                radiusY = h * 0.05f,
            ),
        )
    }
    drawPath(head, color = glow.copy(alpha = 0.10f * pulse))
    drawPath(head, color = neon.copy(alpha = 0.95f), style = Stroke(width = 2.1f))

    holoLine(p(0.68f, 0.38f), p(0.74f, 0.32f), neon, glow, pulse)
    holoLine(p(0.84f, 0.28f), p(0.96f, 0.36f), neon, glow, pulse)
    holoLine(p(0.84f, 0.38f), p(0.96f, 0.36f), neon, glow, pulse)
    holoLine(p(0.76f, 0.22f), p(0.73f, 0.10f), neon, glow, pulse)
    holoLine(p(0.80f, 0.22f), p(0.73f, 0.10f), neon, glow, pulse)
    holoLine(p(0.24f, 0.40f), p(0.12f, 0.28f), neon, glow, pulse)
    holoLine(p(0.12f, 0.28f), p(0.16f, 0.22f), neon, glow, pulse)

    // Rear legs
    holoLine(p(0.32f, 0.56f), p(0.28f, 0.74f), neon, glow, pulse)
    holoLine(p(0.28f, 0.74f), p(0.30f, 0.92f), neon, glow, pulse)
    holoLine(p(0.38f, 0.56f), p(0.40f, 0.76f), neon, glow, pulse)
    holoLine(p(0.40f, 0.76f), p(0.38f, 0.92f), neon, glow, pulse)
    // Front legs
    holoLine(p(0.58f, 0.56f), p(0.60f, 0.76f), neon, glow, pulse)
    holoLine(p(0.60f, 0.76f), p(0.58f, 0.92f), neon, glow, pulse)
    holoLine(p(0.66f, 0.56f), p(0.72f, 0.74f), neon, glow, pulse)
    holoLine(p(0.72f, 0.74f), p(0.70f, 0.92f), neon, glow, pulse)

    listOf(
        p(0.30f, 0.92f), p(0.38f, 0.92f), p(0.58f, 0.92f), p(0.70f, 0.92f),
        p(0.28f, 0.74f), p(0.40f, 0.76f), p(0.60f, 0.76f), p(0.72f, 0.74f),
        p(0.48f, 0.34f),
    ).forEach { joint ->
        drawCircle(color = glow.copy(alpha = 0.35f * pulse), radius = 7f, center = joint)
        drawCircle(color = neon, radius = 2.4f, center = joint)
    }

    // LiDAR dome
    drawCircle(
        color = glow.copy(alpha = 0.18f * pulse),
        radius = w * 0.045f,
        center = p(0.48f, 0.32f),
    )
    drawCircle(
        color = neon.copy(alpha = 0.9f),
        radius = w * 0.045f,
        center = p(0.48f, 0.32f),
        style = Stroke(width = 1.8f),
    )

    val mouth = p(0.96f, 0.36f)
    val mouthRadius = 3.2f + amplitude * 11f
    drawCircle(
        color = Color.White.copy(alpha = 0.18f + amplitude * 0.55f),
        radius = mouthRadius * 2.4f,
        center = mouth,
    )
    drawCircle(
        color = neon.copy(alpha = 0.40f + amplitude * 0.60f),
        radius = mouthRadius,
        center = mouth,
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.35f + amplitude * 0.65f),
        radius = mouthRadius * 0.38f,
        center = mouth,
    )
}

private fun DrawScope.holoLine(
    start: Offset,
    end: Offset,
    neon: Color,
    glow: Color,
    pulse: Float,
) {
    drawLine(color = glow.copy(alpha = 0.30f * pulse), start = start, end = end, strokeWidth = 6.5f, cap = StrokeCap.Round)
    drawLine(color = neon.copy(alpha = 0.95f), start = start, end = end, strokeWidth = 2.05f, cap = StrokeCap.Round)
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
private fun ChonikAvatarSpeakingPreview() {
    ChonikAvatar(emotion = ChonikEmotion.JOY, audioAmplitude = 0.7f)
}
