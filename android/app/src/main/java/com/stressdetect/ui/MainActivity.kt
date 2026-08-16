package com.stressdetect.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.stressdetect.data.AnalysisResult
import com.stressdetect.data.AppPreferences
import com.stressdetect.data.ExtractionGateway
import com.stressdetect.data.ResultRepository
import com.stressdetect.survey.Pss4
import com.stressdetect.ui.components.DemoBanner
import com.stressdetect.ui.screens.AnalysisScreen
import com.stressdetect.ui.screens.OnboardingScreen
import com.stressdetect.ui.screens.PermissionsScreen
import com.stressdetect.ui.screens.Pss4Screen
import com.stressdetect.ui.screens.ResultScreen
import com.stressdetect.ui.theme.LocalCalmColors
import com.stressdetect.ui.theme.StressDetectTheme
import com.stressdetect.ui.theme.ThemeChoice
import kotlinx.coroutines.delay

/**
 * Single-activity host for the whole flow.
 *
 * State is a small sealed hierarchy rather than a navigation library — five linear screens
 * do not need a graph, and this keeps the dependency surface small.
 *
 * This file imports `data`, `survey` and `ui` only. It never touches `sensing` (Konsist
 * enforces it): permissions and extraction both go through [ExtractionGateway] and
 * [ResultRepository], so no screen can hold a sensor reader.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { StressDetectApp() }
    }
}

private sealed interface Step {
    data object Onboarding : Step
    data object Permissions : Step
    data class Questionnaire(val index: Int) : Step
    data object Analysing : Step
    data class Result(val result: AnalysisResult) : Step
}

@Composable
private fun StressDetectApp() {
    val context = LocalContext.current
    val preferences = remember { AppPreferences(context) }
    val gateway = remember { ExtractionGateway(context) }
    val repository = remember { ResultRepository(context) }

    var themeChoice by remember {
        mutableStateOf(runCatching { ThemeChoice.valueOf(preferences.themeChoice) }
            .getOrDefault(ThemeChoice.SYSTEM))
    }
    var demoMode by remember { mutableStateOf(preferences.demoMode) }
    var step by remember { mutableStateOf<Step>(Step.Onboarding) }
    var responses by remember { mutableStateOf(List<Int?>(Pss4.ITEMS.size) { null }) }
    var completedSteps by remember { mutableIntStateOf(0) }

    // Permission state is re-read on every recomposition of the permissions step, because
    // usage access is granted in Settings — the app gets no callback when it changes.
    var usageGranted by remember { mutableStateOf(false) }
    var commsGranted by remember { mutableStateOf(false) }

    val requestComms = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { commsGranted = gateway.isCallLogGranted() || gateway.isSmsGranted() }

    StressDetectTheme(choice = themeChoice) {
        val calm = LocalCalmColors.current
        Surface(Modifier.fillMaxSize(), color = calm.paper) {
            // API 35 draws edge-to-edge by default, so content would otherwise sit under
            // the status bar and the gesture handle. safeDrawing covers both, plus cutouts.
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                if (demoMode) DemoBanner()

                Box(Modifier.fillMaxSize().padding(top = if (demoMode) 4.dp else 0.dp)) {
                    when (val current = step) {
                        is Step.Onboarding -> OnboardingScreen(
                            onContinue = {
                                usageGranted = gateway.isUsageAccessGranted()
                                commsGranted = gateway.isCallLogGranted() || gateway.isSmsGranted()
                                step = Step.Permissions
                            },
                            onSecretDemoToggle = {
                                demoMode = !demoMode
                                preferences.demoMode = demoMode
                            },
                            themeChoice = themeChoice,
                            onThemeChange = { themeChoice = it },
                        )

                        is Step.Permissions -> PermissionsScreen(
                            usageAccessGranted = usageGranted,
                            commsGranted = commsGranted,
                            demoMode = demoMode,
                            onOpenUsageSettings = {
                                context.startActivity(gateway.usageAccessSettingsIntent())
                            },
                            onRequestComms = { requestComms.launch(gateway.auxiliaryRuntimePermissions()) },
                            onContinue = { step = Step.Questionnaire(0) },
                        )

                        is Step.Questionnaire -> Pss4Screen(
                            itemIndex = current.index,
                            selected = responses[current.index],
                            onSelect = { value ->
                                responses = responses.toMutableList().also { it[current.index] = value }
                            },
                            onNext = {
                                step = if (current.index == Pss4.ITEMS.lastIndex) {
                                    completedSteps = 0
                                    Step.Analysing
                                } else {
                                    Step.Questionnaire(current.index + 1)
                                }
                            },
                            onBack = {
                                step = if (current.index == 0) Step.Permissions
                                else Step.Questionnaire(current.index - 1)
                            },
                        )

                        is Step.Analysing -> {
                            AnalysisScreen(completedSteps = completedSteps)
                            LaunchedEffect(Unit) {
                                // The step ticks are cosmetic pacing, not fake progress: the
                                // work below genuinely runs, and on a fast device it would
                                // otherwise flash past unreadably.
                                val answers = responses.map { it ?: 0 }
                                repeat(3) { completedSteps = it + 1; delay(280) }
                                val analysis = repository.analyse(answers)
                                completedSteps = 4
                                delay(220)
                                step = Step.Result(analysis)
                            }
                        }

                        is Step.Result -> ResultScreen(
                            result = current.result,
                            onRestart = {
                                responses = List(Pss4.ITEMS.size) { null }
                                step = Step.Onboarding
                            },
                        )
                    }
                }
            }
        }
    }

    // Persist the theme choice whenever it changes.
    LaunchedEffect(themeChoice) { preferences.themeChoice = themeChoice.name }
}
