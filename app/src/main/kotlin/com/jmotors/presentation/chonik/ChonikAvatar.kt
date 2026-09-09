package com.jmotors.presentation.chonik

import android.graphics.BlurMaskFilter
import android.graphics.Paint as AndroidPaint
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jmotors.domain.model.ai.SphereVisualState
import kotlin.math.cos
import kotlin.math.sin

/**
 * Central AI orb for XREAL SBS: emissive neon core, bloom halo, slow breath.
 * [highlightShift] is −1 (left eye) / +1 (right eye) so the specular has volume.
 */
@Composable
fun ChonikAvatar(
    visualState: SphereVisualState,
    modifier: Modifier = Modifier,
    audioAmplitude: Float = 0f,
    highlightShift: Float = 0f,
    size: Dp = 132.dp,
) {
    val coreColor by animateColorAsState(
        targetValue = visualState.coreColor(),
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = "sphereCore",
    )
    val glowColor by animateColorAsState(
        targetValue = visualState.glowColor(),
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = "sphereGlow",
    )

    val breath = rememberInfiniteTransition(label = "sphereBreath")
    val breathScale by breath.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sphereScale",
    )
    val glowPulse by breath.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sphereGlowPulse",
    )
    val wave by breath.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sphereWave",
    )
    val blink by breath.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 720, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sphereBlink",
    )

    val thinkingMix = if (visualState == SphereVisualState.THINKING) blink else 0f
    val displayCore = lerp(coreColor, Color.White, thinkingMix * 0.72f)
    val displayGlow = lerp(glowColor, Color.White, thinkingMix * 0.45f)
    val amplitude = audioAmplitude.coerceIn(0f, 1f)
    val liveScale = breathScale + amplitude * 0.045f

    Canvas(
        modifier = modifier
            .size(size)
            .scale(liveScale),
    ) {
        drawAiSphere(
            core = displayCore,
            glow = displayGlow,
            pulse = glowPulse,
            wave = wave,
            thinking = visualState == SphereVisualState.THINKING,
            highlightShift = highlightShift,
            amplitude = amplitude,
        )
    }
}

private fun DrawScope.drawAiSphere(
    core: Color,
    glow: Color,
    pulse: Float,
    wave: Float,
    thinking: Boolean,
    highlightShift: Float,
    amplitude: Float,
) {
    val c = Offset(size.width * 0.5f, size.height * 0.5f)
    val radius = size.minDimension * 0.28f
    val bloom = 0.55f + pulse * 0.45f + amplitude * 0.15f

    glowDisk(c, radius * 2.35f, glow.copy(alpha = 0.18f * bloom), blur = 42f)
    glowDisk(c, radius * 1.55f, glow.copy(alpha = 0.32f * bloom), blur = 22f)
    glowDisk(c, radius * 1.12f, core.copy(alpha = 0.55f * bloom), blur = 10f)

    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.00f to Color.White.copy(alpha = 0.95f),
                0.18f to lerp(core, Color.White, 0.55f),
                0.52f to core,
                0.82f to glow.copy(alpha = 0.92f),
                1.00f to Color.Black.copy(alpha = 0.35f),
            ),
            center = c + Offset(highlightShift * radius * 0.18f, -radius * 0.16f),
            radius = radius * 1.12f,
        ),
        radius = radius,
        center = c,
    )

    val rim = radius * (0.92f + pulse * 0.04f)
    drawCircle(
        color = core.copy(alpha = 0.55f + pulse * 0.35f),
        radius = rim,
        center = c,
        style = Stroke(width = 2.4f + pulse * 1.4f),
    )

    val spec = c + Offset(highlightShift * radius * 0.38f, -radius * 0.34f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.85f), Color.Transparent),
            center = spec,
            radius = radius * 0.34f,
        ),
        radius = radius * 0.34f,
        center = spec,
    )

    if (thinking) {
        val ringCount = 3
        repeat(ringCount) { i ->
            val t = (wave + i / ringCount.toFloat()) % 1f
            val rr = radius * (0.35f + t * 0.95f)
            val a = (1f - t) * 0.55f
            drawCircle(
                color = lerp(core, Color.White, (sin(t * Math.PI.toFloat()) * 0.5f + 0.5f)).copy(alpha = a),
                radius = rr,
                center = c,
                style = Stroke(width = 2.2f * (1f - t * 0.6f)),
            )
        }
        val sweep = wave * (Math.PI * 2.0).toFloat()
        val arc = Offset(c.x + cos(sweep) * radius * 0.72f, c.y + sin(sweep) * radius * 0.72f)
        glowDisk(arc, radius * 0.16f, Color.White.copy(alpha = 0.55f), blur = 12f)
    }
}

private fun DrawScope.glowDisk(
    center: Offset,
    radius: Float,
    color: Color,
    blur: Float,
) {
    drawIntoCanvas { canvas ->
        val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            style = AndroidPaint.Style.FILL
            this.color = color.toArgb()
            maskFilter = BlurMaskFilter(blur.coerceAtLeast(0.5f), BlurMaskFilter.Blur.NORMAL)
        }
        canvas.nativeCanvas.drawCircle(center.x, center.y, radius, paint)
    }
}

internal fun SphereVisualState.coreColor(): Color = when (this) {
    SphereVisualState.IDLE -> Color(0xFF00F0FF)
    SphereVisualState.THINKING -> Color(0xFFBD00FF)
    SphereVisualState.SUCCESS -> Color(0xFF00FF66)
    SphereVisualState.ERROR -> Color(0xFFFF3333)
}

internal fun SphereVisualState.glowColor(): Color = when (this) {
    SphereVisualState.IDLE -> Color(0xFF00C6D6)
    SphereVisualState.THINKING -> Color(0xFFE0B3FF)
    SphereVisualState.SUCCESS -> Color(0xFF00C853)
    SphereVisualState.ERROR -> Color(0xFFFF6B35)
}

internal fun SphereVisualState.highlightColor(): Color = when (this) {
    SphereVisualState.IDLE -> Color(0xFFB8FBFF)
    SphereVisualState.THINKING -> Color(0xFFFFFFFF)
    SphereVisualState.SUCCESS -> Color(0xFFB9F6CA)
    SphereVisualState.ERROR -> Color(0xFFFFCDD2)
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun SphereIdlePreview() {
    ChonikAvatar(visualState = SphereVisualState.IDLE)
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun SphereThinkingPreview() {
    ChonikAvatar(visualState = SphereVisualState.THINKING, audioAmplitude = 0.4f)
}
