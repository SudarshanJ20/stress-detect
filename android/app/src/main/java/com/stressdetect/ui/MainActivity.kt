package com.stressdetect.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.stressdetect.data.AnalysisResult
import com.stressdetect.data.AppPreferences
import com.stressdetect.data.CheckInRepository
import com.stressdetect.data.ExtractionGateway
import com.stressdetect.data.ResultRepository
import com.stressdetect.survey.Pss4
import com.stressdetect.ui.components.DemoBanner
import com.stressdetect.ui.screens.AboutScreen
import com.stressdetect.ui.screens.AnalysisScreen
import com.stressdetect.ui.screens.FirstRunScreen
import com.stressdetect.ui.screens.HistoryScreen
import com.stressdetect.ui.screens.HomeScreen
import com.stressdetect.ui.screens.PermissionsScreen
import com.stressdetect.ui.screens.Pss4Screen
import com.stressdetect.ui.screens.ResultScreen
import com.stressdetect.ui.theme.LocalCalmColors
import com.stressdetect.ui.theme.StressDetectTheme
import com.stressdetect.ui.theme.ThemeChoice
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Single-activity host.
 *
 * The app used to be a linear wizard that opened onto a consent wall. It now opens onto
 * Home, and the wizard is one path through it. Screens reach `data` and `survey` only —
 * never `sensing`, never `inference` (both Konsist-enforced), so no screen can hold a
 * sensor reader or a model handle.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { StressDetectApp() }
    }
}

@Composable
private fun StressDetectApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { AppPreferences(context) }
    val gateway = remember { ExtractionGateway(context) }
    val resultRepository = remember { ResultRepository(context) }
    val checkInRepository = remember { CheckInRepository(context) }

    var themeChoice by remember {
        mutableStateOf(
            runCatching { ThemeChoice.valueOf(preferences.themeChoice) }
                .getOrDefault(ThemeChoice.SYSTEM)
        )
    }
    var demoMode by remember { mutableStateOf(preferences.demoMode) }
    var responses by remember { mutableStateOf(List<Int?>(Pss4.ITEMS.size) { null }) }
    var completedSteps by remember { mutableIntStateOf(0) }
    var history by remember { mutableStateOf<List<CheckInRepository.Entry>>(emptyList()) }
    var lastScore by remember { mutableStateOf<Int?>(null) }
    // Held so About can show the technical read-out of the most recent check-in.
    var lastResult by remember { mutableStateOf<AnalysisResult?>(null) }

    // Usage access is granted in Settings, so there is no callback — it is re-read whenever
    // the permissions screen is opened.
    var usageGranted by remember { mutableStateOf(false) }
    var commsGranted by remember { mutableStateOf(false) }

    val backStack = rememberBackStack(
        if (preferences.hasSeenIntro) Screen.Home else Screen.FirstRun
    )

    val requestComms = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { commsGranted = gateway.isCallLogGranted() || gateway.isSmsGranted() }

    // Demo and real check-ins are stored separately, so switching modes reloads the list
    // that belongs to the mode you are now in.
    LaunchedEffect(demoMode) {
        history = checkInRepository.history(isDemo = demoMode)
        lastScore = history.lastOrNull()?.score
    }

    StressDetectTheme(choice = themeChoice) {
        val calm = LocalCalmColors.current

        BackHandler(enabled = backStack.canGoBack) { backStack.pop() }

        Surface(Modifier.fillMaxSize(), color = calm.paper) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                if (demoMode) DemoBanner()

                Box(Modifier.fillMaxSize()) {
                    when (val current = backStack.current) {
                        is Screen.FirstRun -> FirstRunScreen(
                            onContinue = {
                                preferences.hasSeenIntro = true
                                usageGranted = gateway.isUsageAccessGranted()
                                commsGranted = gateway.isCallLogGranted() || gateway.isSmsGranted()
                                backStack.replaceAll(Screen.Permissions)
                            },
                        )

                        is Screen.Permissions -> PermissionsScreen(
                            usageAccessGranted = usageGranted,
                            commsGranted = commsGranted,
                            demoMode = demoMode,
                            onOpenUsageSettings = {
                                context.startActivity(gateway.usageAccessSettingsIntent())
                            },
                            onRequestComms = {
                                requestComms.launch(gateway.auxiliaryRuntimePermissions())
                            },
                            onContinue = { backStack.replaceAll(Screen.Home) },
                        )

                        is Screen.Home -> HomeScreen(
                            lastScore = lastScore,
                            onCheckIn = {
                                responses = List(Pss4.ITEMS.size) { null }
                                backStack.push(Screen.CheckIn(0))
                            },
                            onHistory = {
                                scope.launch {
                                    history = checkInRepository.history(isDemo = demoMode)
                                }
                                backStack.push(Screen.History)
                            },
                            onAbout = { backStack.push(Screen.About) },
                        )

                        is Screen.CheckIn -> Pss4Screen(
                            itemIndex = current.itemIndex,
                            selected = responses[current.itemIndex],
                            onSelect = { value ->
                                responses = responses.toMutableList()
                                    .also { it[current.itemIndex] = value }
                            },
                            onNext = {
                                if (current.itemIndex == Pss4.ITEMS.lastIndex) {
                                    completedSteps = 0
                                    backStack.push(Screen.Analysing)
                                } else {
                                    backStack.replaceTop(Screen.CheckIn(current.itemIndex + 1))
                                }
                            },
                            onBack = {
                                if (current.itemIndex == 0) backStack.pop()
                                else backStack.replaceTop(Screen.CheckIn(current.itemIndex - 1))
                            },
                        )

                        is Screen.Analysing -> {
                            AnalysisScreen(completedSteps = completedSteps)
                            LaunchedEffect(Unit) {
                                val answers = responses.map { it ?: 0 }
                                repeat(3) { completedSteps = it + 1; delay(280) }
                                val analysis = resultRepository.analyse(answers)
                                completedSteps = 4
                                delay(220)
                                history = checkInRepository.history(isDemo = demoMode)
                                lastScore = history.lastOrNull()?.score
                                // Replaces the stack: backing into a submitted questionnaire
                                // would show stale answers and invite a second submission.
                                lastResult = analysis
                                backStack.replaceAll(Screen.Result(analysis))
                            }
                        }

                        is Screen.Result -> ResultScreen(
                            result = current.result,
                            onDone = { backStack.replaceAll(Screen.Home) },
                            onAbout = { backStack.push(Screen.About) },
                        )

                        is Screen.History -> HistoryScreen(
                            entries = history,
                            onBack = { backStack.pop() },
                        )

                        is Screen.About -> AboutScreen(
                            lastResult = lastResult,
                            themeChoice = themeChoice,
                            onThemeChange = { themeChoice = it },
                            onOpenUsageSettings = {
                                context.startActivity(gateway.usageAccessSettingsIntent())
                            },
                            onDeleteHistory = {
                                scope.launch {
                                    checkInRepository.deleteAll()
                                    history = emptyList()
                                    lastScore = null
                                }
                            },
                            onSecretDemoToggle = {
                                demoMode = !demoMode
                                preferences.demoMode = demoMode
                            },
                            onBack = { backStack.pop() },
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(themeChoice) { preferences.themeChoice = themeChoice.name }
}
