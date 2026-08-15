package com.stressdetect.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.stressdetect.data.ExtractionGateway
import com.stressdetect.data.ExtractionOutcome
import com.stressdetect.features.SpecConstants
import kotlinx.coroutines.launch

/**
 * Phase-5 scaffold screen: grant permissions, trigger the retrospective backfill, read back
 * what was extracted. **Not the product UI** — the self-baseline result screen is Phase 7.
 *
 * It shows raw feature values only because this is a developer/diagnostic surface. The
 * participant-facing UI must never show an absolute or population-relative stress figure,
 * only a comparison against the user's own baseline (root CLAUDE.md).
 *
 * Note this file imports `data` and `features`, never `sensing` — enforced by
 * `ArchitectureTest`.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BackfillScreen()
                }
            }
        }
    }
}

@Composable
private fun BackfillScreen() {
    val context = LocalContext.current
    val gateway = remember { ExtractionGateway(context) }
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf("Idle — spec ${SpecConstants.SPEC_VERSION}") }
    var details by remember { mutableStateOf("") }

    val requestAuxPermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { status = "Auxiliary permissions updated" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("stress-detect — retrospective backfill", style = MaterialTheme.typography.titleMedium)
        Text(status)

        Button(onClick = { context.startActivity(gateway.usageAccessSettingsIntent()) }) {
            Text("1. Grant usage access (required)")
        }

        Button(onClick = { requestAuxPermissions.launch(gateway.auxiliaryRuntimePermissions()) }) {
            Text("2. Grant call/SMS (optional, auxiliary)")
        }

        Button(onClick = {
            scope.launch {
                status = "Extracting ${SpecConstants.WINDOW_DAYS}-day window…"
                details = when (val outcome = gateway.runBackfillNow()) {
                    is ExtractionOutcome.MissingUsageAccess -> {
                        status = "Blocked"
                        "Usage access not granted. queryEvents returns an EMPTY stream " +
                            "without it, which is indistinguishable from a phone that was " +
                            "never unlocked — so nothing was computed."
                    }

                    is ExtractionOutcome.InsufficientCoverage -> {
                        status = "Insufficient coverage"
                        "Window has ${outcome.vector.daysWithData} day(s) of lock data; " +
                            "at least ${SpecConstants.COVERAGE_MIN_DAYS} are required " +
                            "before this window may be scored.\n\n" + outcome.vector.values.render()
                    }

                    is ExtractionOutcome.Success -> {
                        status = "Extracted"
                        outcome.vector.values.render()
                    }
                }
            }
        }) {
            Text("3. Run backfill now")
        }

        if (details.isNotEmpty()) {
            Text(details, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun Map<String, Double>.render(): String =
    entries.joinToString("\n") { (name, value) ->
        "$name = ${if (value.isNaN()) "—" else "%.4f".format(value)}"
    }
