package com.stressdetect.data

import android.content.Context
import com.stressdetect.features.FeatureExtractor
import com.stressdetect.features.SpecConstants
import com.stressdetect.features.WindowFeatureVector
import java.time.LocalDate
import java.time.ZoneId

/**
 * This week's features and the person's own earlier weeks — the one place that rule lives.
 *
 * Two screens now compare a week to the weeks before it, and "the person's own earlier weeks"
 * is a definition with three parts that are easy to get subtly wrong. Two implementations of
 * it would drift, and the drift would be invisible: both screens would still show arrows,
 * they would just quietly disagree about what "usual" meant.
 */
internal object WeekFeatures {

    /** Four weeks back at most: "usual" should be a recent habit, not an average over a term. */
    private const val MAX_PRIOR_WEEKS = 4

    /** A week and what it is being compared against. */
    data class Week(
        val values: Map<String, Double>,
        val prior: List<Map<String, Double>>,
    )

    /**
     * The person's own earlier weeks, newest first.
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
    suspend fun priorWeeks(
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

    /** One demo window through the real extractor — the demo runs the actual pipeline. */
    fun vectorOf(window: DemoTraceSource.DemoWindow): WindowFeatureVector =
        FeatureExtractor.extract(
            locked = window.lockedIntervals,
            callTimestamps = window.calls,
            smsTimestamps = window.sms,
            labelDate = window.labelDate,
            zone = ZoneId.of(window.zoneId),
            hasCalls = true,
            hasSms = true,
        )

    /**
     * The most recent week this phone knows about, for a screen that is not running an
     * analysis — Home, which shows a line of context without asking anyone to check in.
     *
     * Reads the CACHE rather than the OS: no query, no permission prompt, nothing new
     * collected just to put a sentence on the front door. `null` when there is nothing
     * cached yet, which is the honest state before anyone's first check-in.
     *
     * Demo mode reads the fixture, so a demo front door cannot quietly show real phone data
     * under a banner announcing that it is not.
     */
    suspend fun latest(context: Context, isDemo: Boolean): Week? {
        if (isDemo) {
            val source = DemoTraceSource(context)
            return Week(
                values = vectorOf(source.load()).values,
                prior = source.loadPriorWeeks().map { vectorOf(it).values },
            )
        }

        val database = StressDetectDatabase.get(context)
        val cached = database.featureVectorDao()
            .allForSpecVersion(SpecConstants.SPEC_VERSION)
            .maxByOrNull { it.labelDate }
            ?: return null

        return Week(
            values = FeatureVectorMapper.toValues(cached),
            prior = priorWeeks(LocalDate.parse(cached.labelDate), database),
        )
    }
}
