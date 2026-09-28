package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GlowCyan
import com.example.ui.theme.GlowMagenta
import com.example.ui.theme.GlowViolet
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.sin

enum class AssistantVisualState {
    IDLE,
    LISTENING,
    SPEAKING,
    EXECUTING
}

@Composable
fun ArushiOrb(
    state: AssistantVisualState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_transition")

    // Breathing pulse scale
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    // Dynamic wave radius for active listening/speaking
    val waveRadius by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_radius"
    )

    // Rotation for executing state
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val coreColors = when (state) {
        AssistantVisualState.LISTENING -> listOf(NeonCyan, NeonViolet, Color(0xFF0284C7))
        AssistantVisualState.SPEAKING -> listOf(NeonMagenta, NeonViolet, NeonCyan)
        AssistantVisualState.EXECUTING -> listOf(NeonAmber, NeonViolet, NeonCyan)
        AssistantVisualState.IDLE -> listOf(NeonViolet, Color(0xFF7C3AED), Color(0xFF4C1D95))
    }

    val glowColor = when (state) {
        AssistantVisualState.LISTENING -> GlowCyan
        AssistantVisualState.SPEAKING -> GlowMagenta
        AssistantVisualState.EXECUTING -> Color(0x66F59E0B)
        AssistantVisualState.IDLE -> GlowViolet
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(200.dp)
                .semantics {
                    contentDescription = "Arushi Voice Assistant Orb. Current state: ${state.name}. Tap to interact."
                }
        ) {
            // Outermost Animated Pulse Ring
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val baseRadius = size.width / 2f - 20f

                if (state == AssistantVisualState.LISTENING || state == AssistantVisualState.SPEAKING) {
                    val currentRadius = baseRadius * (0.8f + 0.35f * waveRadius)
                    val alpha = (1f - waveRadius).coerceIn(0f, 0.8f)
                    drawCircle(
                        color = glowColor.copy(alpha = alpha),
                        radius = currentRadius,
                        center = center
                    )
                }

                // Ambient glow circle
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glowColor, Color.Transparent),
                        center = center,
                        radius = baseRadius * 1.3f
                    ),
                    radius = baseRadius * 1.3f,
                    center = center
                )
            }

            // Interactive Center Orb Core
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(130.dp)
                    .scale(if (state == AssistantVisualState.IDLE) pulseScale else 1f)
                    .rotate(if (state == AssistantVisualState.EXECUTING) rotation else 0f)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = coreColors
                        )
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, radius = 65.dp),
                        onClick = onClick
                    )
                    .testTag("voice_orb_button")
            ) {
                // Audio Waveform Visualizer simulation inside orb when speaking/listening
                if (state == AssistantVisualState.SPEAKING || state == AssistantVisualState.LISTENING) {
                    Canvas(modifier = Modifier.size(90.dp)) {
                        val barCount = 12
                        val spacing = size.width / (barCount + 1)
                        for (i in 0 until barCount) {
                            val x = (i + 1) * spacing
                            val multiplier = sin((i * 0.8f) + (waveRadius * Math.PI * 2f).toFloat())
                            val barHeight = (size.height * 0.2f) + (size.height * 0.35f * Math.abs(multiplier))
                            drawLine(
                                color = TextPrimary.copy(alpha = 0.9f),
                                start = Offset(x, (size.height - barHeight) / 2f),
                                end = Offset(x, (size.height + barHeight) / 2f),
                                strokeWidth = 5f
                            )
                        }
                    }
                } else {
                    // Center Icon
                    val icon = when (state) {
                        AssistantVisualState.EXECUTING -> Icons.Default.Sync
                        AssistantVisualState.IDLE -> Icons.Default.Mic
                        else -> Icons.Default.GraphicEq
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = "Voice State Icon",
                        tint = TextPrimary,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // State label pill
        val stateText = when (state) {
            AssistantVisualState.LISTENING -> "Listening to you..."
            AssistantVisualState.SPEAKING -> "Arushi is speaking..."
            AssistantVisualState.EXECUTING -> "Executing task..."
            AssistantVisualState.IDLE -> "Tap to speak with Arushi"
        }

        val stateColor = when (state) {
            AssistantVisualState.LISTENING -> NeonCyan
            AssistantVisualState.SPEAKING -> NeonMagenta
            AssistantVisualState.EXECUTING -> NeonAmber
            AssistantVisualState.IDLE -> TextSecondary
        }

        Text(
            text = stateText,
            color = stateColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
