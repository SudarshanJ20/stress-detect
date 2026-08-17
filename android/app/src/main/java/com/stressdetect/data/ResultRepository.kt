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

        return AnalysisResult(
            questionnaireScore = questionnaireScore,
            modelEstimate = estimate?.toDouble(),
            modelUnavailableReason = when {
                model == null -> "No model is bundled in this build."
                estimate == null -> "The model could not run on this window."
                else -> null
            },
            contributions = contributions,
            dailyValues = window.dailySeries,
            staticValues = window.staticByName,
            weekValues = window.values,
            priorWeekValues = window.priorValues,
            priorWeekCount = window.priorWeekCount,
            daysWithData = window.daysWithData,
            meetsCoverage = window.meetsCoverage,
            commsIncluded = window.commsIncluded,
            isDemo = demo,
        )
    }

    // ── window construction ────────────────────────────────────────────────────────────

    private data class Window(
        val sequence: Array<DoubleArray>,
        val static: DoubleArray,
        val dailySeries: Map<String, List<Double>>,
        val staticByName: Map<String, Double>,
        /** The flat window vector — the same shape the cache stores, so weeks compare like with like. */
        val values: Map<String, Double>,
        /** Feature-wise mean of this person's earlier weeks; empty when there are none. */
        val priorValues: Map<String, Double>,
        val priorWeekCount: Int,
        val daysWithData: Double,
        val meetsCoverage: Boolean,
        val commsIncluded: Boolean,
        val excludedFeatures: Set<String>,
    )

    private suspend fun deviceWindow(): Window? {
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

        return buildWindow(
            locked, calls, sms, vector.labelDate, zone, vector.values, commsIncluded,
            priorWeeks = priorWeeks(vector.labelDate, database),
        )
    }

    private fun demoWindow(): Window {
        val source = DemoTraceSource(context)
        val demo = source.load()
        val zone = ZoneId.of(demo.zoneId)
        val vector = extractDemo(demo, zone)

        // Demo prior weeks come from the fixture, never from the real cache: a rehearsal must
        // not be compared against this phone's actual history, and the real history must not
        // pick up anything a demo left behind.
        val prior = source.loadPriorWeeks().map { extractDemo(it, ZoneId.of(it.zoneId)).values }

        return buildWindow(
            demo.lockedIntervals, demo.calls, demo.sms, demo.labelDate, zone, vector.values,
            commsIncluded = true, priorWeeks = prior,
        )
    }

    private fun extractDemo(demo: DemoTraceSource.DemoWindow, zone: ZoneId) =
        FeatureExtractor.extract(
            locked = demo.lockedIntervals,
            callTimestamps = demo.calls,
            smsTimestamps = demo.sms,
            labelDate = demo.labelDate,
            zone = zone,
            hasCalls = true,
            hasSms = true,
        )

    /**
     * The person's own earlier weeks, newest first — the only thing this app ever compares
     * anyone against.
     *
     * Three rules make the comparison mean what it says:
     *  - **Non-overlapping.** Cached vectors exist for every day the app ran, and windows a
     *    day apart share six of their seven days. Walking back in 7-day steps takes distinct
     *    weeks instead of counting the same Tuesday five times.
     *  - **Same SPEC_VERSION only**, which the DAO already enforces: feature definitions
     *    changed means the numbers are not the same measurement.
     *  - **Covered weeks only.** A thin week is not a quiet one, and averaging it in would
     *    drag someone's "usual" toward a week we could barely see.
     */
    private suspend fun priorWeeks(
        labelDate: LocalDate,
        database: StressDetectDatabase,
    ): List<Map<String, Double>> {
        val rows = database.featureVectorDao()
            .allForSpecVersion(SpecConstants.SPEC_VERSION)
            .filter { it.meetsCoverage }
            .map { LocalDate.parse(it.labelDate) to it }
            .sortedByDescending { (date, _) -> date }

        val weeks = mutableListOf<Map<String, Double>>()
        var cutoff = labelDate.minusDays(SpecConstants.WINDOW_DAYS.toLong())
        for ((date, row) in rows) {
            if (weeks.size >= MAX_PRIOR_WEEKS) break
            if (date > cutoff) continue
            weeks += FeatureVectorMapper.toValues(row)
            cutoff = date.minusDays(SpecConstants.WINDOW_DAYS.toLong())
        }
        return weeks
    }

    /** Feature-wise mean across the earlier weeks, skipping the ones where a value is missing. */
    private fun meanOfWeeks(weeks: List<Map<String, Double>>): Map<String, Double> {
        if (weeks.isEmpty()) return emptyMap()
        val names = weeks.flatMap { it.keys }.toSet()
        return names.mapNotNull { name ->
            val present = weeks.mapNotNull { it[name]?.takeIf { value -> !value.isNaN() } }
            if (present.isEmpty()) null else name to present.average()
        }.toMap()
    }

    private fun buildWindow(
        locked: List<LockedInterval>,
        calls: List<Long>,
        sms: List<Long>,
        labelDate: LocalDate,
        zone: ZoneId,
        values: Map<String, Double>,
        commsIncluded: Boolean,
        priorWeeks: List<Map<String, Double>>,
    ): Window {
        val sequence = SequenceFeatures.dynamicSequence(locked, calls, sms, labelDate, zone)
        val static = SequenceFeatures.staticVector(values, values)

        val dailySeries = SequenceFeatures.DYNAMIC_FEATURE_NAMES.mapIndexed { index, name ->
            name to sequence.map { it[index] }
        }.toMap()
        val staticByName = SequenceFeatures.STATIC_FEATURE_NAMES.mapIndexed { index, name ->
            name to static[index]
        }.toMap()

        // Call/SMS features are EXCLUDED from ranking, not scored as zero, when the stream
        // was unavailable: absent is not the same as average, and the screen says so.
        val excluded = if (commsIncluded) emptySet() else setOf("call_count", "sms_count")

        val days = values.getValue("days_with_data")
        return Window(
            sequence = sequence,
            static = static,
            dailySeries = dailySeries,
            staticByName = staticByName,
            values = values,
            priorValues = meanOfWeeks(priorWeeks),
            priorWeekCount = priorWeeks.size,
            daysWithData = days,
            meetsCoverage = days >= SpecConstants.COVERAGE_MIN_DAYS,
            commsIncluded = commsIncluded,
            excludedFeatures = excluded,
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

        /**
         * Four weeks back at most. "Your usual" should be a recent habit, not an average over
         * a term — someone whose term ended a month ago has a different usual now.
         */
        const val MAX_PRIOR_WEEKS = 4
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
