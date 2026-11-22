package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

enum class OrbVisualState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    ERROR
}

@Composable
fun MayaOrb(
    state: OrbVisualState,
    rmsLevel: Float,
    avatarStyle: String = "NEURAL_CORE",
    speedScale: Float = 1.0f,
    lowPowerMode: Boolean = true,
    sizeDp: Dp = 180.dp,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val errorCol = MaterialTheme.colorScheme.error

    val coreColor = when (state) {
        OrbVisualState.IDLE -> when (avatarStyle) {
            "SOLAR_PULSE" -> Color(0xFFFFB300)
            "CYBER_RING" -> secondary
            else -> primary
        }
        OrbVisualState.LISTENING -> tertiary
        OrbVisualState.THINKING -> secondary
        OrbVisualState.SPEAKING -> primary
        OrbVisualState.ERROR -> errorCol
    }

    val durationMs = (2400f / speedScale.coerceIn(0.4f, 2.2f)).toInt()
    val transition = rememberInfiniteTransition(label = "orb_transition")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_phase"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = (durationMs * 0.75f).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_pulse"
    )

    Canvas(
        modifier = modifier
            .size(sizeDp)
            .semantics { contentDescription = "Maya AI neural orb state: ${state.name}" }
    ) {
        val minDim = minOf(size.width, size.height)
        val center = Offset(size.width / 2f, size.height / 2f)
        val ampBoost = 1f + (rmsLevel.coerceIn(0f, 1f) * 0.25f)
        val baseRadius = minDim * 0.30f * pulse * ampBoost

        // Outer ambient glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(coreColor.copy(alpha = 0.34f), coreColor.copy(alpha = 0.05f), Color.Transparent),
                center = center,
                radius = minDim * 0.49f
            ),
            radius = minDim * 0.49f,
            center = center
        )

        // Concentric orbital ring(s)
        val rings = if (lowPowerMode) 2 else 3
        for (r in 1..rings) {
            val ringRadius = baseRadius * (1.16f + r * 0.16f)
            val wobble = if (state == OrbVisualState.LISTENING || state == OrbVisualState.SPEAKING) {
                sin(phase + r * 1.4f) * (rmsLevel * 8f + 2f)
            } else 0f
            drawCircle(
                color = coreColor.copy(alpha = 0.42f / r),
                radius = ringRadius + wobble,
                center = center,
                style = Stroke(width = (3.5f - r * 0.6f).coerceAtLeast(1.5f))
            )
            // Orbiting node dot
            val angle = phase * (if (r % 2 == 0) -1f else 1f) + r * 1.1f
            val dotPos = Offset(
                center.x + (ringRadius + wobble) * cos(angle),
                center.y + (ringRadius + wobble) * sin(angle)
            )
            drawCircle(
                color = if (r == 1) Color.White else coreColor,
                radius = 4.5f,
                center = dotPos
            )
        }

        // Core sphere
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.92f), coreColor, secondary.copy(alpha = 0.85f)),
                center = Offset(center.x - baseRadius * 0.25f, center.y - baseRadius * 0.25f),
                radius = baseRadius * 1.3f
            ),
            radius = baseRadius,
            center = center
        )
    }
}

@Composable
fun AudioSpectrumVisualizer(
    bars: List<Float>,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .semantics { contentDescription = "Real-time microphone audio spectrum" }
    ) {
        val count = bars.size.coerceAtLeast(1)
        val totalSpacing = size.width * 0.35f
        val barWidth = ((size.width - totalSpacing) / count).coerceAtLeast(3f)
        val gap = (totalSpacing / (count + 1)).coerceAtLeast(2f)
        val centerY = size.height / 2f

        bars.forEachIndexed { idx, level ->
            val clamped = level.coerceIn(0.06f, 1f)
            val barH = size.height * clamped
            val left = gap + idx * (barWidth + gap)
            val top = centerY - barH / 2f
            drawRoundRect(
                color = activeColor.copy(alpha = 0.45f + clamped * 0.55f),
                topLeft = Offset(left, top),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
