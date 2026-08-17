package com.stressdetect.data

import android.content.Context
import com.stressdetect.features.AnalysisWindow
import com.stressdetect.features.FeatureExtractor
import com.stressdetect.features.LockedInterval
import com.stressdetect.features.SequenceFeatures
import com.stressdetect.features.SpecConstants
import com.stressdetect.inference.FeatureContribution
import com.stressdetect.inference.OcclusionAttribution
import com.stressdetect.inference.OnnxStressModel
import com.stressdetect.survey.Pss4
import java.io.FileNotFoundException
import java.time.LocalDate
import java.time.ZoneId

/**
 * Assembles everything the result screen shows: the questionnaire score, the (unvalidated)
 * model estimate, and the local attribution.
 *
 * Every part can be absent — no usage access, thin coverage, declined call/SMS, no model
 * shipped — and each absence is represented EXPLICITLY in [AnalysisResult] rather than by a
 * zero or an empty list. The screen is required to say what is missing and why; a silently
 * shorter factor list would quietly misrepresent the week.
 */
class ResultRepository(
    private val context: Context,
    private val preferences: AppPreferences = AppPreferences(context),
) {

    suspend fun analyse(pss4Responses: List<Int>): AnalysisResult {
        val questionnaireScore = Pss4.score(pss4Responses)
        val demo = preferences.demoMode
        // Recorded before the phone data is touched: the check-in stands on its own, and a
        // failure to read usage history must not lose what the person just told us.
        CheckInRepository(context).record(questionnaireScore, demo)

        val window = if (demo) demoWindow() else deviceWindow()
            ?: return AnalysisResult.noUsageAccess(questionnaireScore)

        val model = loadModel()
        val estimate = model?.let { runCatching { it.predict(window.sequence, window.static) }.getOrNull() }
        val contributions = if (model != null && estimate != null) {
            runCatching {
                OcclusionAttribution.rank(model, window.sequence, window.static, window.excludedFeatures)
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        model?.close()

        return WindowAssembly.toResult(
            questionnaireScore = questionnaireScore,
            window = window,
            modelEstimate = estimate?.toDouble(),
            modelUnavailableReason = when {
                model == null -> "No model is bundled in this build."
                estimate == null -> "The model could not run on this window."
                else -> null
            },
            contributions = contributions,
            isDemo = demo,
        )
    }

    // ── window construction ────────────────────────────────────────────────────────────

    private suspend fun deviceWindow(): WindowAssembly.Assembled? {
        val extractor = RetrospectiveExtractor(context)
        val outcome = extractor.run()
        val vector = when (outcome) {
            is ExtractionOutcome.MissingUsageAccess -> return null
            is ExtractionOutcome.InsufficientCoverage -> outcome.vector
            is ExtractionOutcome.Success -> outcome.vector
        }

        val zone = ZoneId.systemDefault()
        val window = AnalysisWindow.endingAtMidnightOf(vector.labelDate, zone)
        val database = StressDetectDatabase.get(context)
        val locked = database.lockedIntervalDao()
            .overlapping(window.startUtc, window.endUtc)
            .map { LockedInterval(it.startUtc, it.endUtc) }
        val calls = database.commEventDao().timestamps("call", window.startUtc, window.endUtc)
        val sms = database.commEventDao().timestamps("sms", window.startUtc, window.endUtc)

        val commsIncluded = vector.values.getValue("call_present") > 0.0 ||
            vector.values.getValue("sms_present") > 0.0

        return WindowAssembly.assemble(
            locked, calls, sms, vector.labelDate, zone, vector.values, commsIncluded,
            priorWeeks = WeekFeatures.priorWeeks(vector.labelDate, database),
        )
    }

    private fun demoWindow(): WindowAssembly.Assembled {
        val source = DemoTraceSource(context)
        val demo = source.load()
        val zone = ZoneId.of(demo.zoneId)
        val vector = WeekFeatures.vectorOf(demo)

        // Demo prior weeks come from the fixture, never from the real cache: a rehearsal must
        // not be compared against this phone's actual history, and the real history must not
        // pick up anything a demo left behind.
        val prior = source.loadPriorWeeks().map { WeekFeatures.vectorOf(it).values }

        return WindowAssembly.assemble(
            demo.lockedIntervals, demo.calls, demo.sms, demo.labelDate, zone, vector.values,
            commsIncluded = true, priorWeeks = prior,
        )
    }

    private fun loadModel(): OnnxStressModel? = try {
        val bytes = context.assets.open(MODEL_ASSET).use { it.readBytes() }
        OnnxStressModel.load(bytes)
    } catch (e: FileNotFoundException) {
        null    // no model bundled — the questionnaire result still stands on its own
    } catch (e: IllegalStateException) {
        null    // spec_version or scaler mismatch: refuse rather than show a wrong number
    }

    private companion object {
        const val MODEL_ASSET = "stress_model.onnx"
    }
}

/**
 * What the result screen renders. Absences are explicit fields, never implied by empty
 * collections, so the UI is forced to account for them.
 */
data class AnalysisResult(
    val questionnaireScore: Int,
    val modelEstimate: Double?,
    val modelUnavailableReason: String?,
    val contributions: List<FeatureContribution>,
    val dailyValues: Map<String, List<Double>>,
    val staticValues: Map<String, Double>,
    /** This week's flat window vector. */
    val weekValues: Map<String, Double>,
    /**
     * Feature-wise mean of this person's earlier, non-overlapping weeks — the ONLY thing the
     * screen compares them against. Empty until there is a second week, and an empty map has
     * to mean "no comparison", never "no change": the screen shows no arrow at all rather
     * than an arrow that would be about nothing.
     */
    val priorWeekValues: Map<String, Double>,
    val priorWeekCount: Int,
    val daysWithData: Double,
    val meetsCoverage: Boolean,
    val commsIncluded: Boolean,
    val isDemo: Boolean,
    val usageAccessMissing: Boolean = false,
) {
    val questionnairePercent: Int get() = Pss4.percentOfMaximum(questionnaireScore)

    companion object {
        /** Nothing could be read from the phone; the questionnaire result still stands. */
        fun noUsageAccess(questionnaireScore: Int) = AnalysisResult(
            questionnaireScore = questionnaireScore,
            modelEstimate = null,
            modelUnavailableReason = "Usage access was not granted, so there was no phone " +
                "data to run the model on.",
            contributions = emptyList(),
            dailyValues = emptyMap(),
            staticValues = emptyMap(),
            weekValues = emptyMap(),
            priorWeekValues = emptyMap(),
            priorWeekCount = 0,
            daysWithData = 0.0,
            meetsCoverage = false,
            commsIncluded = false,
            isDemo = false,
            usageAccessMissing = true,
        )
    }
}
