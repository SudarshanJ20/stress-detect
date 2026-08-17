package com.stressdetect.ui.content

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The four rows under "What's been going on": what the phone saw this week, and which way
 * each one moved.
 *
 * **Every comparison is to the same person's own earlier weeks.** There is no population
 * norm anywhere in here, and there could not be — this app has never seen anyone else's
 * week. "More than your usual" means more than *your* previous weeks; with no previous
 * weeks the row says what the value was and shows no direction at all, because there is
 * nothing to compare it to. Inventing a comparison from one week of data is exactly the
 * failure this file is written to avoid.
 *
 * Three further rules:
 *
 *  1. **A row appears only when its feature exists.** A missing week is missing, never zero
 *     — a rest row reading "0 hours" for a phone that could not see the nights would be a
 *     claim about someone's sleep that we did not measure.
 *  2. **Direction needs a deadband.** Without one, a fifteen-minute difference in screen
 *     time would draw an arrow, and the arrows would flicker week to week on noise. Each
 *     metric has its own, sized to what would actually be noticeable.
 *  3. **Up is never a verdict.** More screen time is not a bad week and is not coloured as
 *     one; the phrasing describes movement, never approval.
 *
 * The suggestion under each row is the one already tied to that topic in [Observations] —
 * this file chooses which row it belongs under, it does not write advice of its own.
 */
object WeekSummary {

    enum class Direction { UP, LEVEL, DOWN }

    data class Row(
        val id: String,
        val icon: String,
        val label: String,
        /** `null` when there are no earlier weeks — no arrow is drawn and [phrase] is a value. */
        val direction: Direction?,
        val phrase: String,
        /** The tied suggestion, or `null` when this row has nothing to suggest. */
        val suggestion: String?,
    )

    data class Result(
        val rows: List<Row>,
        /** Non-null when we cannot say anything, with a reason a person can act on. */
        val unavailableReason: String?,
    )

    // ── deadbands: below these, the week is "around your normal range" ───────────────────

    /** Screen time within a tenth of usual — half an hour on a five-hour day. */
    private const val SCREEN_BAND = 0.10

    /** Beyond this the change stops being "a little" and the word is dropped. */
    private const val SCREEN_SLIGHT = 0.25

    /** Rest, in hours. Half an hour either way is an ordinary night, not a change. */
    private const val REST_BAND_HOURS = 0.5

    /** Contact within 15% of usual — with a floor, since 15% of two calls is nothing. */
    private const val COMMS_BAND = 0.15
    private const val COMMS_BAND_FLOOR_PER_DAY = 1.0

    /** Regularity is a 0–1 correlation of daily shapes; 0.05 of it is not a different week. */
    private const val RHYTHM_BAND = 0.05

    /** Above this, days genuinely resemble each other enough to call the week steady. */
    private const val RHYTHM_STEADY = 0.5

    fun build(
        weekValues: Map<String, Double>,
        priorWeekValues: Map<String, Double>,
        dailyValues: Map<String, List<Double>>,
        staticValues: Map<String, Double>,
        usageAccessMissing: Boolean,
        meetsCoverage: Boolean,
        daysWithData: Double,
    ): Result {
        // The reasons a week cannot be described are the same ones the observations use, and
        // they are already written to point at the fix rather than just report a failure.
        val observations = Observations.build(
            dailyValues = dailyValues,
            staticValues = staticValues,
            usageAccessMissing = usageAccessMissing,
            meetsCoverage = meetsCoverage,
            daysWithData = daysWithData,
        )
        if (usageAccessMissing || !meetsCoverage) {
            return Result(emptyList(), observations.unavailableReason)
        }

        val suggestions = Observations.candidates(dailyValues, staticValues)
        fun suggestionFor(vararg topics: String): String? = suggestions
            .firstOrNull { it.id.substringBefore(':') in topics }
            ?.suggestion

        val rows = listOfNotNull(
            screenRow(weekValues, priorWeekValues, suggestionFor("screen", "pickups")),
            restRow(weekValues, priorWeekValues, suggestionFor("rest", "bedtime", "night")),
            commsRow(weekValues, priorWeekValues),
            rhythmRow(weekValues, priorWeekValues, suggestionFor("rhythm")),
        )
        if (rows.isEmpty()) {
            return Result(emptyList(), "Nothing in this week stood out enough to be worth pointing at.")
        }
        return Result(rows, null)
    }

    // ── the four rows ───────────────────────────────────────────────────────────────────

    private fun screenRow(
        week: Map<String, Double>,
        prior: Map<String, Double>,
        suggestion: String?,
    ): Row? {
        val hours = week.value("screen_on_fraction")?.times(24) ?: return null
        val usual = prior.value("screen_on_fraction")?.times(24)
        val direction = direction(hours, usual, band = usual?.times(SCREEN_BAND))
        val slight = usual != null && abs(hours - usual) <= usual * SCREEN_SLIGHT
        return Row(
            id = "screen",
            icon = "📱",
            label = "Screen activity",
            direction = direction,
            phrase = when (direction) {
                // "Slightly" is a claim about size, so it is dropped once the change stops
                // being slight — a doubled week must not be described as a nudge.
                Direction.UP -> if (slight) "Slightly more than your usual" else "More than your usual"
                Direction.DOWN -> if (slight) "Slightly less than your usual" else "Less than your usual"
                Direction.LEVEL -> "Around your normal range"
                null -> "Around ${Observations.duration(hours)} a day"
            },
            suggestion = suggestion,
        )
    }

    private fun restRow(
        week: Map<String, Double>,
        prior: Map<String, Double>,
        suggestion: String?,
    ): Row? {
        val hours = week.value("sleep_duration_median") ?: return null
        val usual = prior.value("sleep_duration_median")
        val direction = direction(hours, usual, band = REST_BAND_HOURS)
        return Row(
            id = "rest",
            icon = "😴",
            label = "Rest",
            direction = direction,
            phrase = when (direction) {
                Direction.UP -> "Longer than your usual"
                Direction.DOWN -> "Shorter than your usual"
                Direction.LEVEL -> "Around your normal range"
                null -> "About ${Observations.duration(hours)} a night"
            },
            suggestion = suggestion,
        )
    }

    /**
     * Calls and texts are the one AUXILIARY pair: declining those permissions is a supported
     * path, and the presence flags say so explicitly. A count of zero from an unread call log
     * is not a quiet week, so the row is dropped rather than shown as one.
     */
    private fun commsRow(week: Map<String, Double>, prior: Map<String, Double>): Row? {
        val present = (week.value("call_present") ?: 0.0) > 0.0 ||
            (week.value("sms_present") ?: 0.0) > 0.0
        if (!present) return null

        val perDay = (week.value("call_count_per_day") ?: 0.0) +
            (week.value("sms_count_per_day") ?: 0.0)
        val usual = prior.value("call_count_per_day")?.let { calls ->
            calls + (prior.value("sms_count_per_day") ?: 0.0)
        }
        val direction = direction(
            value = perDay,
            usual = usual,
            band = usual?.let { maxOf(it * COMMS_BAND, COMMS_BAND_FLOOR_PER_DAY) },
        )
        return Row(
            id = "comms",
            icon = "📞",
            label = "Calls & messages",
            direction = direction,
            phrase = when (direction) {
                Direction.UP -> "Busier than your usual"
                Direction.DOWN -> "Quieter than your usual"
                Direction.LEVEL -> "Around your normal range"
                null -> if (perDay < 1.0) "Fewer than one a day"
                else "About ${perDay.roundToInt()} a day"
            },
            suggestion = null,
        )
    }

    /**
     * Regularity has no unit anyone would recognise — it is how alike the days' shapes were,
     * on a 0–1 scale. Printing "0.63" would be a lab readout, so with nothing to compare
     * against the row describes the level in words instead of showing the number.
     */
    private fun rhythmRow(
        week: Map<String, Double>,
        prior: Map<String, Double>,
        suggestion: String?,
    ): Row? {
        val regularity = week.value("circadian_regularity") ?: return null
        val usual = prior.value("circadian_regularity")
        val direction = direction(regularity, usual, band = RHYTHM_BAND)
        val steady = regularity >= RHYTHM_STEADY
        return Row(
            id = "rhythm",
            icon = "🕐",
            label = "Daily rhythm",
            direction = direction,
            phrase = when (direction) {
                Direction.UP -> "Steadier than your usual"
                Direction.DOWN -> "More varied than your usual"
                // Unchanged says nothing about the LEVEL, so the level is what gets said —
                // "fairly steady" for a week of consistently irregular days would be false.
                Direction.LEVEL -> if (steady) "Fairly steady" else "About as varied as usual"
                null -> if (steady) "Fairly steady" else "Varied day to day"
            },
            suggestion = suggestion,
        )
    }

    // ── comparison ──────────────────────────────────────────────────────────────────────

    /** `null` usual — no earlier weeks — means no direction at all, never [Direction.LEVEL]. */
    private fun direction(value: Double, usual: Double?, band: Double?): Direction? {
        if (usual == null || usual.isNaN() || band == null || band.isNaN()) return null
        val difference = value - usual
        return when {
            abs(difference) <= band -> Direction.LEVEL
            difference > 0 -> Direction.UP
            else -> Direction.DOWN
        }
    }

    /** NaN is the spec's missing value and must never reach a sentence as a number. */
    private fun Map<String, Double>.value(name: String): Double? =
        this[name]?.takeIf { !it.isNaN() }

    /** Exposed for the copy test: everything a user can read from this file. */
    internal fun allCopyStrings(): List<String> {
        val steady = mapOf(
            "screen_on_fraction" to 0.30, "sleep_duration_median" to 7.5,
            "circadian_regularity" to 0.62, "call_count_per_day" to 1.4,
            "sms_count_per_day" to 2.1, "call_present" to 1.0, "sms_present" to 1.0,
        )
        // A week whose days ran to different shapes, so the level-describing phrases fire.
        val varied = steady + mapOf("circadian_regularity" to 0.2)
        val daily = mapOf(
            "screen_on_fraction" to List(7) { 0.30 },
            "unlock_count" to List(7) { 70.0 },
            "nighttime_use_fraction_fixed" to List(7) { 0.2 },
        )
        val static = mapOf(
            "sleep_duration_median" to 5.0, "sleep_onset_hours" to 23.9,
            "sleep_onset_regularity" to 1.5, "circadian_regularity" to 0.2,
        )

        return listOf(steady, varied).flatMap { week ->
            listOf(
                emptyMap(),                             // no earlier weeks: values, no arrows
                week,                                   // unchanged on usual
                week.mapValues { (_, v) -> v * 0.5 },   // every row up on usual
                week.mapValues { (_, v) -> v * 2.0 },   // every row down on usual
                // A change too big to call slight.
                week + mapOf("screen_on_fraction" to 0.10),
            ).flatMap { prior ->
                build(week, prior, daily, static, false, true, 7.0).rows
            }
        }.flatMap { listOfNotNull(it.label, it.phrase, it.suggestion) }
    }
}
