package com.ymz.basicplanner.ui

import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.ymz.basicplanner.data.AppStateStore
import com.ymz.basicplanner.model.CreationState
import com.ymz.basicplanner.model.shouldSeedGeneratedData
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    private val creationState = CreationState()

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        enableEdgeToEdge()

        setContent {

            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                color = Color.Black
            ) {
                val context = LocalContext.current
                var loaded by remember { mutableStateOf(false) }
                var showCreationScreen by remember { mutableStateOf(false) }
                var showDashboard by remember { mutableStateOf(false) }
                var showModules by remember { mutableStateOf(false) }
                var activeSessionId by remember { mutableStateOf<Long?>(null) }
                var showTutorial by remember { mutableStateOf(false) }
                var tutorialStep by remember { mutableIntStateOf(0) }
                /** Whether we are in guidance mode (dialog hidden, arrow shown) */
                var tutorialGuiding by remember { mutableStateOf(false) }

                val count = 3
                val duration = 800L

                LaunchedEffect(Unit) {
                    val saved = AppStateStore.load(context)
                    creationState.loadFrom(saved)
                    if (shouldSeedGeneratedData(saved)) {
                        creationState.populateWithGeneratedData(LocalDate.now())
                        AppStateStore.save(context, creationState.toAppState())
                    }
                    kotlinx.coroutines.delay(count * duration)
                    loaded = true
                }

                if (!loaded) {
                    SplashScreen(count, duration)
                } else {

                    if (activeSessionId != null) {
                        BackHandler { activeSessionId = null }
                    } else if (showDashboard) {
                        BackHandler { showDashboard = false }
                    } else if (showModules) {
                        BackHandler { showModules = false }
                    } else if (showCreationScreen) {
                        // While the tutorial drives the create dialog, leaving the screen is blocked.
                        val tutorialDialogLocked = showTutorial && tutorialStep == 4
                        BackHandler {
                            if (!tutorialDialogLocked) showCreationScreen = false
                        }
                    }

                    when {
                        activeSessionId != null -> {
                            SessionScreen(
                                sessionId = activeSessionId!!,
                                creationState = creationState,
                                onBack = { activeSessionId = null }
                            )
                        }
                        showDashboard -> {
                            DashboardScreen(
                                creationState = creationState,
                                onBack = { showDashboard = false }
                            )
                        }
                        showModules -> {
                            ModulesScreen(
                                creationState = creationState,
                                onBack = { showModules = false }
                            )
                        }
                        showCreationScreen -> {
                            CreationScreen(
                                creationState = creationState,
                                tutorialActive = showTutorial && tutorialGuiding,
                                tutorialDialogActive = showTutorial && tutorialStep == 4,
                                onCreateRequested = {
                                    if (showTutorial && tutorialStep == 3) {
                                        tutorialStep++
                                        tutorialGuiding = false
                                    }
                                },
                                onTutorialDialogConfirmed = {
                                    if (showTutorial) tutorialStep = 5
                                },
                                onTutorialDialogSkipped = {
                                    showTutorial = false
                                    tutorialStep = 0
                                    tutorialGuiding = false
                                }
                            )
                        }
                        else -> {
                            ProgramAgendaScreen(
                                creationState = creationState,
                                programStartDate = LocalDate.now(),
                                tutorialActive = showTutorial && tutorialGuiding,
                                onOpenCreation = {
                                    showCreationScreen = true
                                    if (showTutorial && tutorialStep == 1 && tutorialGuiding) {
                                        tutorialStep++
                                        tutorialGuiding = false
                                    }
                                },
                                onOpenDashboard = {
                                    if (!showTutorial || !tutorialGuiding) {
                                        showDashboard = true
                                    }
                                },
                                onStartSession = { newSessionId ->
                                    if (!showTutorial || !tutorialGuiding) {
                                        activeSessionId = newSessionId
                                    }
                                },
                                onOpenModules = { showModules = true },
                                onOpenTutorial = {
                                    tutorialStep = 0
                                    tutorialGuiding = false
                                    showTutorial = true
                                }
                            )
                        }
                    }

                    if (showTutorial) {
                        val step = tutorialSteps.getOrNull(tutorialStep)
                        if (step == null) {
                            showTutorial = false
                            tutorialStep = 0
                            tutorialGuiding = false
                        } else if (step.inDialog) {
                            // Guidance for this step is rendered inside the entity creation dialog.
                        } else if (!tutorialGuiding && step.dialogTitle != null) {
                            // Dialog slide with Go button
                            TutorialGoDialog(
                                title = step.dialogTitle!!,
                                body = step.dialogBody.orEmpty(),
                                goLabel = step.goLabel,
                                showSkip = step.showSkip,
                                onGo = {
                                    tutorialStep++
                                    tutorialGuiding = true
                                },
                                onSkip = {
                                    showTutorial = false
                                    tutorialStep = 0
                                    tutorialGuiding = false
                                }
                            )
                        } else if (tutorialGuiding && step.guideText != null) {
                            // Guidance mode: instruction + pulsing ring, waiting for the user's action
                            TutorialGuidance(
                                guideText = step.guideText!!,
                                targetKey = step.targetKey,
                                onSkip = {
                                    showTutorial = false
                                    tutorialStep = 0
                                    tutorialGuiding = false
                                }
                            )
                        } else {
                            showTutorial = false
                            tutorialStep = 0
                            tutorialGuiding = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SplashScreen(
    loopCount: Int = 3,
    loopDurationMs: Long = 1000L
) {
    var animationStep by remember { mutableIntStateOf(0) }
    val stepDuration = loopDurationMs / 5

    LaunchedEffect(Unit) {
        repeat(loopCount) {

            for (i in 0..4) {
                animationStep = i
                kotlinx.coroutines.delay(stepDuration)
            }

            kotlinx.coroutines.delay(100L)

            if (it < loopCount - 1) {
                animationStep = 0
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedDumbbellLogo(
                step = animationStep,
                modifier = Modifier.size(180.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Basic Planner",
                color = AccentOrange,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.2.em
            )
        }
    }
}

@Composable
fun AnimatedDumbbellLogo(
    step: Int,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.aspectRatio(1f)) {
        val scale = size.width / 24f
        val color = AccentOrange

        drawRect(
            color = color,
            topLeft = Offset(9f * scale, 11f * scale),
            size = Size(6f * scale, 2f * scale)
        )

        val leftWeights = listOf(7f, 5.5f, 4f, 2.5f)
        val rightWeights = listOf(16f, 17.5f, 19f, 20.5f)

        for (i in 0 until step) {

            drawRect(
                color = color,
                topLeft = Offset(leftWeights[i] * scale, 8f * scale),
                size = Size(1f * scale, 8f * scale)
            )

            drawRect(
                color = color,
                topLeft = Offset(rightWeights[i] * scale, 8f * scale),
                size = Size(1f * scale, 8f * scale)
            )
        }
    }
}
