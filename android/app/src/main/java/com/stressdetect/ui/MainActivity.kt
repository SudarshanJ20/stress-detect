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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.stressdetect.data.AnalysisResult
import com.stressdetect.data.AppPreferences
import com.stressdetect.data.CheckInRepository
import com.stressdetect.data.ExtractionGateway
import com.stressdetect.data.ExtractionSummary
import com.stressdetect.data.ResultRepository
import com.stressdetect.data.WeekContext
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
    // Held so About can show the technical read-out of the most recent check-in, and what
    // the OS actually returned the last time the phone was read.
    var lastResult by remember { mutableStateOf<AnalysisResult?>(null) }
    var lastExtraction by remember { mutableStateOf<ExtractionSummary?>(null) }
    // Home's line of phone context: the CACHED week, read once when Home is shown. Nothing on
    // the front door queries the OS or asks for a permission.
    var weekContext by remember { mutableStateOf<WeekContext?>(null) }

    // Usage access is granted in Settings, in another app, with no callback and no result to
    // await. The only way to know is to ask again every time we come back to the foreground —
    // asking once, on the way out of the intro, left someone who had just granted it looking
    // at a screen still asking them to.
    val permissions = remember {
        PermissionState(
            usageAccess = { gateway.isUsageAccessGranted() },
            comms = { gateway.isCallLogGranted() || gateway.isSmsGranted() },
        )
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissions.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val backStack = rememberBackStack(
        if (preferences.hasSeenIntro) Screen.Home else Screen.FirstRun
    )

    val requestComms = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions.refresh() }

    // Demo and real check-ins are stored separately, so switching modes reloads the list
    // that belongs to the mode you are now in.
    LaunchedEffect(demoMode) {
        history = checkInRepository.history(isDemo = demoMode)
    }
    LaunchedEffect(demoMode, history.size) {
        weekContext = gateway.weekContext(isDemo = demoMode)
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
                                permissions.refresh()
                                backStack.replaceAll(Screen.Permissions)
                            },
                        )

                        is Screen.Permissions -> PermissionsScreen(
                            usageAccessGranted = permissions.usageAccessGranted,
                            commsGranted = permissions.commsGranted,
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
                            history = history,
                            weekContext = weekContext,
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
                                    // No default for a missing answer: under reverse scoring
                                    // it would score 8/16 and look like a real result. The
                                    // button is disabled until the item is answered, so this
                                    // holds the invariant rather than handling a live case.
                                    Pss4.completedResponses(responses)?.let { answers ->
                                        completedSteps = 0
                                        backStack.push(Screen.Analysing(answers))
                                    }
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
                                repeat(3) { completedSteps = it + 1; delay(280) }
                                val analysis = resultRepository.analyse(current.answers)
                                completedSteps = 4
                                delay(220)
                                history = checkInRepository.history(isDemo = demoMode)
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
                            isDemo = demoMode,
                            onBack = { backStack.pop() },
                        )

                        is Screen.About -> {
                            // Loaded HERE rather than at each call site: About is reached
                            // from Home and from the result screen, and the read-out went
                            // missing on the second route when only the first loaded it.
                            LaunchedEffect(Unit) { lastExtraction = gateway.lastExtraction() }
                            AboutScreen(
                                lastResult = lastResult,
                                lastExtraction = lastExtraction,
                                themeChoice = themeChoice,
                                onThemeChange = { themeChoice = it },
                                onOpenUsageSettings = {
                                    context.startActivity(gateway.usageAccessSettingsIntent())
                                },
                                onDeleteHistory = {
                                    scope.launch {
                                        checkInRepository.deleteAll()
                                        history = emptyList()
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
    }

    LaunchedEffect(themeChoice) { preferences.themeChoice = themeChoice.name }
}
