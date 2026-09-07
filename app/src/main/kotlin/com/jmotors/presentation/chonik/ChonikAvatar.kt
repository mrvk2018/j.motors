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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jmotors.domain.model.ai.ChonikEmotion

/**
 * Premium cyberpunk Unitree Go2 hologram: angular chassis, jointed legs, sensor head,
 * BlurMaskFilter neon glow, mouth lip-sync from [audioAmplitude].
 */
@Composable
fun ChonikAvatar(
    emotion: ChonikEmotion,
    modifier: Modifier = Modifier,
    audioAmplitude: Float = 0f,
    size: Dp = 176.dp,
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
        initialValue = 0.985f,
        targetValue = 1.025f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "chonikBreathScale",
    )
    val scan by breathTransition.animateFloat(
        initialValue = 0f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "chonikScan",
    )
    val glowPulse by breathTransition.animateFloat(
        initialValue = 0.62f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "chonikGlowPulse",
    )

    val amplitude = audioAmplitude.coerceIn(0f, 1f)

    Canvas(
        modifier = modifier
            .size(width = size * 1.62f, height = size)
            .scale(breathScale),
    ) {
        drawGo2Wireframe(
            neon = coreColor,
            glow = glowColor,
            pulse = glowPulse,
            scan = scan,
            amplitude = amplitude,
        )
    }
}

private fun DrawScope.drawGo2Wireframe(
    neon: Color,
    glow: Color,
    pulse: Float,
    scan: Float,
    amplitude: Float,
) {
    val w = size.width
    val h = size.height
    fun p(nx: Float, ny: Float) = Offset(nx * w, ny * h)

    var y = scan % 9f
    while (y < h) {
        drawLine(
            color = neon.copy(alpha = 0.055f * pulse),
            start = Offset(0f, y),
            end = Offset(w, y),
            strokeWidth = 1.05f,
        )
        y += 9f
    }

    val chassis = Path().apply {
        moveTo(p(0.20f, 0.40f).x, p(0.20f, 0.40f).y)
        lineTo(p(0.28f, 0.30f).x, p(0.28f, 0.30f).y)
        lineTo(p(0.66f, 0.28f).x, p(0.66f, 0.28f).y)
        lineTo(p(0.74f, 0.36f).x, p(0.74f, 0.36f).y)
        lineTo(p(0.72f, 0.52f).x, p(0.72f, 0.52f).y)
        lineTo(p(0.64f, 0.58f).x, p(0.64f, 0.58f).y)
        lineTo(p(0.26f, 0.58f).x, p(0.26f, 0.58f).y)
        lineTo(p(0.18f, 0.50f).x, p(0.18f, 0.50f).y)
        close()
    }
    drawPath(chassis, glow.copy(alpha = 0.10f * pulse), style = Fill)
    glowPath(chassis, glow.copy(alpha = 0.85f * pulse), width = 11f, blur = 22f)
    glowPath(chassis, neon, width = 2.4f, blur = 1.2f)

    // Deck seams / battery bay
    glowLine(p(0.24f, 0.44f), p(0.70f, 0.42f), neon, glow, pulse, 2f)
    glowLine(p(0.30f, 0.34f), p(0.30f, 0.56f), neon, glow, pulse, 1.6f)
    glowLine(p(0.48f, 0.30f), p(0.46f, 0.56f), neon, glow, pulse, 1.6f)
    glowLine(p(0.62f, 0.32f), p(0.62f, 0.54f), neon, glow, pulse, 1.6f)
    val xBrace = Path().apply {
        moveTo(p(0.32f, 0.36f).x, p(0.32f, 0.36f).y)
        lineTo(p(0.58f, 0.52f).x, p(0.58f, 0.52f).y)
        moveTo(p(0.58f, 0.34f).x, p(0.58f, 0.34f).y)
        lineTo(p(0.34f, 0.52f).x, p(0.34f, 0.52f).y)
    }
    glowPath(xBrace, neon.copy(alpha = 0.7f), width = 1.5f, blur = 8f)

    // LiDAR turret
    val lidar = p(0.46f, 0.26f)
    glowCircle(lidar, w * 0.055f, neon, glow, pulse)
    glowCircle(lidar, w * 0.028f, neon, glow, pulse)
    drawCircle(color = neon.copy(alpha = 0.35f * pulse), radius = w * 0.012f, center = lidar)

    // Neck + angular sensor head
    glowLine(p(0.72f, 0.38f), p(0.80f, 0.30f), neon, glow, pulse, 2.4f)
    val head = Path().apply {
        moveTo(p(0.78f, 0.22f).x, p(0.78f, 0.22f).y)
        lineTo(p(0.90f, 0.20f).x, p(0.90f, 0.20f).y)
        lineTo(p(0.96f, 0.28f).x, p(0.96f, 0.28f).y)
        lineTo(p(0.94f, 0.38f).x, p(0.94f, 0.38f).y)
        lineTo(p(0.82f, 0.40f).x, p(0.82f, 0.40f).y)
        lineTo(p(0.76f, 0.32f).x, p(0.76f, 0.32f).y)
        close()
    }
    drawPath(head, glow.copy(alpha = 0.12f * pulse), style = Fill)
    glowPath(head, glow.copy(alpha = 0.9f * pulse), width = 10f, blur = 18f)
    glowPath(head, neon, width = 2.2f, blur = 1.1f)

    // Visor slit
    glowLine(p(0.84f, 0.27f), p(0.94f, 0.26f), neon, glow, pulse, 3.2f)
    glowLine(p(0.85f, 0.31f), p(0.93f, 0.30f), neon.copy(alpha = 0.7f), glow, pulse, 1.4f)
    // Ears / antennas
    glowLine(p(0.80f, 0.22f), p(0.77f, 0.10f), neon, glow, pulse, 2f)
    glowLine(p(0.84f, 0.20f), p(0.86f, 0.08f), neon, glow, pulse, 1.6f)
    glowCircle(p(0.77f, 0.10f), 3.2f, neon, glow, pulse)
    glowCircle(p(0.86f, 0.08f), 2.4f, neon, glow, pulse)
    // Rear antenna
    glowLine(p(0.22f, 0.38f), p(0.10f, 0.18f), neon, glow, pulse, 1.8f)
    glowLine(p(0.10f, 0.18f), p(0.14f, 0.14f), neon, glow, pulse, 1.4f)
    glowCircle(p(0.14f, 0.14f), 3f, neon, glow, pulse)

    drawGo2Leg(
        hip = p(0.30f, 0.56f),
        mid = p(0.22f, 0.74f),
        ankle = p(0.26f, 0.90f),
        foot = p(0.30f, 0.94f),
        neon, glow, pulse, w,
    )
    drawGo2Leg(
        hip = p(0.36f, 0.57f),
        mid = p(0.38f, 0.76f),
        ankle = p(0.36f, 0.91f),
        foot = p(0.40f, 0.95f),
        neon, glow, pulse, w,
    )
    drawGo2Leg(
        hip = p(0.58f, 0.56f),
        mid = p(0.60f, 0.74f),
        ankle = p(0.56f, 0.90f),
        foot = p(0.60f, 0.94f),
        neon, glow, pulse, w,
    )
    drawGo2Leg(
        hip = p(0.66f, 0.54f),
        mid = p(0.74f, 0.72f),
        ankle = p(0.70f, 0.89f),
        foot = p(0.74f, 0.94f),
        neon, glow, pulse, w,
    )

    // Hydraulic traces
    glowLine(p(0.30f, 0.56f), p(0.22f, 0.74f), neon.copy(alpha = 0.45f), glow, pulse, 1.1f)
    glowLine(p(0.66f, 0.54f), p(0.74f, 0.72f), neon.copy(alpha = 0.45f), glow, pulse, 1.1f)

    val mouth = p(0.95f, 0.33f)
    val mouthRadius = 3.4f + amplitude * 12f
    glowCircle(mouth, mouthRadius * 2.6f, Color.White.copy(alpha = 0.25f + amplitude * 0.5f), glow, pulse)
    drawCircle(
        color = neon.copy(alpha = 0.40f + amplitude * 0.60f),
        radius = mouthRadius,
        center = mouth,
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.40f + amplitude * 0.60f),
        radius = mouthRadius * 0.34f,
        center = mouth,
    )
    // Pulse ring
    val ring = 2f + amplitude * 10f
    drawCircle(
        color = neon.copy(alpha = 0.18f + amplitude * 0.35f),
        radius = mouthRadius + ring,
        center = mouth,
        style = Stroke(width = 1.3f + amplitude * 1.8f),
    )
}

private fun DrawScope.drawGo2Leg(
    hip: Offset,
    mid: Offset,
    ankle: Offset,
    foot: Offset,
    neon: Color,
    glow: Color,
    pulse: Float,
    w: Float,
) {
    val thigh = Path().apply {
        moveTo(hip.x, hip.y)
        lineTo(mid.x, mid.y)
    }
    val shank = Path().apply {
        moveTo(mid.x, mid.y)
        lineTo(ankle.x, ankle.y)
        lineTo(foot.x, foot.y)
    }
    glowPath(thigh, glow.copy(alpha = 0.8f * pulse), width = 9f, blur = 16f)
    glowPath(thigh, neon, width = 2.6f, blur = 1.2f)
    glowPath(shank, glow.copy(alpha = 0.8f * pulse), width = 8f, blur = 14f)
    glowPath(shank, neon, width = 2.3f, blur = 1.1f)
    // Parallel actuator
    val ox = (mid.x - hip.x) * 0.12f
    val oy = (mid.y - hip.y) * 0.12f
    glowLine(
        Offset(hip.x + oy, hip.y - ox),
        Offset(mid.x + oy, mid.y - ox),
        neon.copy(alpha = 0.55f),
        glow,
        pulse,
        1.2f,
    )
    glowCircle(hip, w * 0.028f, neon, glow, pulse)
    glowCircle(mid, w * 0.022f, neon, glow, pulse)
    glowCircle(ankle, w * 0.018f, neon, glow, pulse)
    val pad = Path().apply {
        addRect(
            androidx.compose.ui.geometry.Rect(
                left = foot.x - w * 0.03f,
                top = foot.y - 2f,
                right = foot.x + w * 0.04f,
                bottom = foot.y + h * 0.025f,
            ),
        )
    }
    glowPath(pad, neon, width = 1.8f, blur = 6f)
}

private val DrawScope.h: Float get() = size.height

private fun DrawScope.glowLine(
    start: Offset,
    end: Offset,
    neon: Color,
    glow: Color,
    pulse: Float,
    width: Float,
) {
    val path = Path().apply {
        moveTo(start.x, start.y)
        lineTo(end.x, end.y)
    }
    glowPath(path, glow.copy(alpha = 0.75f * pulse), width = width * 3.4f, blur = 14f + width)
    drawLine(color = neon.copy(alpha = 0.95f), start = start, end = end, strokeWidth = width, cap = StrokeCap.Round)
}

private fun DrawScope.glowCircle(
    center: Offset,
    radius: Float,
    neon: Color,
    glow: Color,
    pulse: Float,
) {
    val path = Path().apply {
        addOval(
            androidx.compose.ui.geometry.Rect(
                left = center.x - radius,
                top = center.y - radius,
                right = center.x + radius,
                bottom = center.y + radius,
            ),
        )
    }
    glowPath(path, glow.copy(alpha = 0.8f * pulse), width = 7f, blur = 16f)
    drawCircle(color = neon, radius = radius, center = center, style = Stroke(width = 1.8f))
    drawCircle(color = neon.copy(alpha = 0.9f), radius = radius * 0.28f, center = center)
}

private fun DrawScope.glowPath(
    path: Path,
    color: Color,
    width: Float,
    blur: Float,
) {
    drawIntoCanvas { canvas ->
        val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            style = AndroidPaint.Style.STROKE
            strokeWidth = width
            strokeCap = AndroidPaint.Cap.ROUND
            strokeJoin = AndroidPaint.Join.ROUND
            this.color = color.toArgb()
            maskFilter = BlurMaskFilter(blur.coerceAtLeast(0.5f), BlurMaskFilter.Blur.NORMAL)
        }
        canvas.nativeCanvas.drawPath(path.asAndroidPath(), paint)
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
private fun ChonikAvatarSpeakingPreview() {
    ChonikAvatar(emotion = ChonikEmotion.JOY, audioAmplitude = 0.7f)
}
