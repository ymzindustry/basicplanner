package com.ymz.basicplanner.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import com.ymz.basicplanner.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

object TutorialTargets {
    var agendaCreateButton by mutableStateOf<Rect?>(null)
    var creationAddExerciseButton by mutableStateOf<Rect?>(null)
}

data class TutorialStepSpec(
    val dialogTitle: String? = null,
    val dialogBody: String? = null,
    val guideText: String? = null,
    val targetKey: String? = null,
    /** Guidance for this step is rendered inside the entity creation dialog itself. */
    val inDialog: Boolean = false,
    val goLabel: String = "Go",
    val showSkip: Boolean = true
)

private fun targetRectFor(key: String?): Rect? = when (key) {
    "agenda_create" -> TutorialTargets.agendaCreateButton
    "creation_add_exercise" -> TutorialTargets.creationAddExerciseButton
    else -> null
}

// ── 2-step test tutorial ──────────────────────────────────────

val tutorialSteps = listOf(
    TutorialStepSpec(
        dialogTitle = "Welcome to BasicPlanner!",
        dialogBody = "We are going to create your first exercise together.\nPress Go to start.",
        guideText = null,
        targetKey = null
    ),
    TutorialStepSpec(
        dialogTitle = null,
        dialogBody = null,
        guideText = "Click the + button to open the Creation screen.",
        targetKey = "agenda_create"
    ),
    TutorialStepSpec(
        dialogTitle = "Your first exercise",
        dialogBody = "Okay, we are on the Creation screen. You are lucky — I already created some movements, but now you have to create your first exercise.",
        guideText = null,
        targetKey = null
    ),
    TutorialStepSpec(
        dialogTitle = null,
        dialogBody = null,
        guideText = "Click the + button to create your first exercise.",
        targetKey = "creation_add_exercise"
    ),
    // Guidance is rendered inside the create-exercise dialog (locked until OK or Skip).
    TutorialStepSpec(
        inDialog = true
    ),
    // Completion slide.
    TutorialStepSpec(
        dialogTitle = "Well done!",
        dialogBody = "You created your first exercise. Tap any exercise later to rename it or change its sets.\nEnjoy your training!",
        guideText = null,
        targetKey = null,
        goLabel = "Finish",
        showSkip = false
    )
)

// ── Dialog slide (with Go button) ─────────────────────────────

@Composable
fun TutorialGoDialog(
    title: String,
    body: String,
    onGo: () -> Unit,
    onSkip: () -> Unit,
    goLabel: String = "Go",
    showSkip: Boolean = true
) {
    val strSkip = stringResource(id = R.string.tutorial_skip)
    Dialog(onDismissRequest = onSkip) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DarkSurfaceAlt
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = body,
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 21.sp
                )
                Spacer(modifier = Modifier.height(22.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (showSkip) Arrangement.SpaceBetween else Arrangement.End,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    if (showSkip) {
                        Button(
                            onClick = onSkip,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(strSkip, color = TextMuted)
                        }
                    }
                    Button(
                        onClick = onGo,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(goLabel, color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ── Guidance overlay (dim + hole + pulsing ring + if-logic) ───

@Composable
fun TutorialGuidance(
    guideText: String,
    targetKey: String?,
    onSkip: () -> Unit
) {
    val target = targetRectFor(targetKey)
    val density = LocalDensity.current
    val strSkip = stringResource(id = R.string.tutorial_skip)

    // Pulsing ring animation
    val pulse = rememberInfiniteTransition(label = "ringPulse")
    val pulseScale by pulse.animateFloat(
        0f, 1f, infiniteRepeatable(tween(750), RepeatMode.Reverse), label = "scale"
    )

    Box(modifier = Modifier.fillMaxSize()) {

        // Dim background with a transparent hole where the target button is circled.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val rect = target
            if (rect != null) {
                val holeRadius = minOf(rect.width, rect.height) * 0.55f + with(density) { 8.dp.toPx() }
                val path = Path().apply {
                    addRect(Rect(0f, 0f, size.width, size.height))
                    addOval(
                        Rect(
                            rect.center.x - holeRadius,
                            rect.center.y - holeRadius,
                            rect.center.x + holeRadius,
                            rect.center.y + holeRadius
                        )
                    )
                    fillType = PathFillType.EvenOdd
                }
                drawPath(path = path, color = Color.Black.copy(alpha = 0.68f))
            } else {
                drawRect(color = Color.Black.copy(alpha = 0.68f))
            }
        }

        // Pulsing ring tightly around the button
        Canvas(modifier = Modifier.fillMaxSize()) {
            val rect = target
            if (rect != null) {
                val baseR = minOf(rect.width, rect.height) * 0.55f + with(density) { 8.dp.toPx() }
                val animatedR = baseR * (1f + 0.06f * pulseScale)
                drawCircle(
                    color = AccentOrange,
                    radius = animatedR,
                    center = rect.center,
                    style = Stroke(width = 4.dp.toPx())
                )
            }
        }

        // Instruction label
        if (target != null && guideText.isNotBlank()) {
            val labelY = with(density) { (target.top - 58.dp.toPx()).toInt() }
            val labelX = with(density) { (target.right - 16.dp.toPx()).toInt() }

            Column(
                modifier = Modifier
                    .offset { IntOffset(
                        (labelX - 280.dp.roundToPx()).coerceAtLeast(0),
                        labelY.coerceAtLeast(0)
                    ) }
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E1E1E))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.End
            ) {
                Text(
                    text = guideText,
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 3
                )
            }
        }

        // Skip button
        Button(
            onClick = onSkip,
            modifier = Modifier.offset(x = 16.dp, y = 48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E1E)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(strSkip, color = TextMuted)
        }
    }
}
