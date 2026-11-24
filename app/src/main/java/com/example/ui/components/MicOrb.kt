package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.model.AssistantState
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.SaffronAmber
import kotlin.math.cos
import kotlin.math.sin

/**
 * Signature animated reactive microphone orb with state-aware visual telemetry:
 * - IDLE: soft breathing cyan halo
 * - LISTENING: live RMS-reactive dual ripple rings
 * - THINKING: rotating saffron-amber orbital arc
 * - SPEAKING: emerald harmonic pulse
 * - CONFIRMING / ERROR: coral attention ring
 */
@Composable
fun MicOrb(
    state: AssistantState,
    rmsLevel: Float,
    enabled: Boolean,
    contentDescriptionText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 188.dp
) {
    val infinite = rememberInfiniteTransition(label = "mic_orb_anim")
    val pulse by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )
    val orbitAngle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit"
    )
    val smoothRms by animateFloatAsState(
        targetValue = if (state == AssistantState.LISTENING) rmsLevel.coerceIn(0f, 1f) else 0f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 350f),
        label = "rms"
    )

    val disabledColor = MaterialTheme.colorScheme.outline
    val coreAccent = when {
        !enabled -> disabledColor
        state == AssistantState.LISTENING -> ElectricCyan
        state == AssistantState.THINKING -> SaffronAmber
        state == AssistantState.SPEAKING -> EmeraldPulse
        state == AssistantState.CONFIRMING || state == AssistantState.ERROR -> CoralAlert
        else -> MaterialTheme.colorScheme.primary
    }

    val coreIcon = when (state) {
        AssistantState.LISTENING -> Icons.Rounded.GraphicEq
        AssistantState.THINKING -> Icons.Rounded.AutoAwesome
        AssistantState.SPEAKING -> Icons.Rounded.VolumeUp
        AssistantState.CONFIRMING, AssistantState.ERROR -> Icons.Rounded.PriorityHigh
        AssistantState.IDLE -> Icons.Rounded.Mic
    }

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .testTag("mic_orb_button")
            .semantics { contentDescription = contentDescriptionText }
            .clickable(
                enabled = true,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = coreAccent),
                onClick = onClick
            )
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val center = Offset(cx, cy)
            val maxR = this.size.minDimension / 2f
            val coreR = maxR * (0.50f + smoothRms * 0.08f)

            // Outer ambient glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(coreAccent.copy(alpha = 0.28f), Color.Transparent),
                    center = center,
                    radius = maxR
                ),
                radius = maxR,
                center = center
            )

            // Expanding soundwave rings during Listening / Speaking / Idle
            if (enabled) {
                val ring1Radius = coreR + (maxR - coreR) * pulse
                drawCircle(
                    color = coreAccent.copy(alpha = (1f - pulse) * 0.55f),
                    radius = ring1Radius,
                    center = center,
                    style = Stroke(width = if (state == AssistantState.LISTENING) 4.dp.toPx() else 2.dp.toPx())
                )

                val delayedPulse = (pulse + 0.5f) % 1f
                val ring2Radius = coreR + (maxR - coreR) * delayedPulse
                drawCircle(
                    color = coreAccent.copy(alpha = (1f - delayedPulse) * 0.35f),
                    radius = ring2Radius,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // Rotating orbital satellite dot while Thinking
            if (state == AssistantState.THINKING && enabled) {
                val orbitR = coreR * 1.32f
                drawCircle(
                    color = SaffronAmber.copy(alpha = 0.35f),
                    radius = orbitR,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
                val rad = Math.toRadians(orbitAngle.toDouble())
                val dotPos = Offset(
                    x = cx + (orbitR * cos(rad)).toFloat(),
                    y = cy + (orbitR * sin(rad)).toFloat()
                )
                drawCircle(
                    color = SaffronAmber,
                    radius = 7.dp.toPx(),
                    center = dotPos
                )
            }

            // Tactile solid luminous core sphere
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(
                        coreAccent.copy(alpha = 0.95f),
                        coreAccent.copy(alpha = 0.65f)
                    ),
                    start = Offset(cx - coreR, cy - coreR),
                    end = Offset(cx + coreR, cy + coreR)
                ),
                radius = coreR,
                center = center
            )

            // Crisp inner specular rim
            drawCircle(
                color = Color.White.copy(alpha = 0.35f),
                radius = coreR - 2.dp.toPx(),
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )
        }

        Icon(
            imageVector = coreIcon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(44.dp)
        )
    }
}
