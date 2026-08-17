package com.stressdetect.data

import com.stressdetect.features.LockedInterval
import com.stressdetect.features.SequenceFeatures
import com.stressdetect.features.SpecConstants
import com.stressdetect.inference.FeatureContribution
import java.time.LocalDate
import java.time.ZoneId

/**
 * Turns a computed feature vector into everything the result screen needs.
 *
 * Lifted out of [ResultRepository] so it can be tested: the repository needs a `Context`, a
 * database and the OS, and none of that is involved in carrying values from a feature vector
 * to a screen. That carry-through is where a whole week of real phone data can silently
 * become an empty section — the rows read from [Assembled.values], and a vector that arrives
 * populated but leaves empty looks exactly like a phone with nothing on it.
 *
 * Pure: no Android, no I/O. Both the device path and the demo path go through here, so they
 * cannot drift apart either.
 */
internal object WindowAssembly {

    data class Assembled(
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

    fun assemble(
        locked: List<LockedInterval>,
        calls: List<Long>,
        sms: List<Long>,
        labelDate: LocalDate,
        zone: ZoneId,
        values: Map<String, Double>,
        commsIncluded: Boolean,
        priorWeeks: List<Map<String, Double>>,
    ): Assembled {
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
        return Assembled(
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

    /** Feature-wise mean across the earlier weeks, skipping the ones where a value is missing. */
    fun meanOfWeeks(weeks: List<Map<String, Double>>): Map<String, Double> {
        if (weeks.isEmpty()) return emptyMap()
        val names = weeks.flatMap { it.keys }.toSet()
        return names.mapNotNull { name ->
            val present = weeks.mapNotNull { it[name]?.takeIf { value -> !value.isNaN() } }
            if (present.isEmpty()) null else name to present.average()
        }.toMap()
    }

    /**
     * The result as the screen receives it.
     *
     * Every field the rows depend on is copied here and nowhere else, which is the point:
     * one place to test, and one place for a future edit to drop something.
     */
    fun toResult(
        questionnaireScore: Int,
        window: Assembled,
        modelEstimate: Double?,
        modelUnavailableReason: String?,
        contributions: List<FeatureContribution>,
        isDemo: Boolean,
    ): AnalysisResult = AnalysisResult(
        questionnaireScore = questionnaireScore,
        modelEstimate = modelEstimate,
        modelUnavailableReason = modelUnavailableReason,
        contributions = contributions,
        dailyValues = window.dailySeries,
        staticValues = window.staticByName,
        weekValues = window.values,
        priorWeekValues = window.priorValues,
        priorWeekCount = window.priorWeekCount,
        daysWithData = window.daysWithData,
        meetsCoverage = window.meetsCoverage,
        commsIncluded = window.commsIncluded,
        isDemo = isDemo,
    )
}
