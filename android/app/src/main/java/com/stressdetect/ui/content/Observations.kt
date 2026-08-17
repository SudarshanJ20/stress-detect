package com.stressdetect.ui.content

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Turns a week of extracted features into a few sentences a tired person can read, each
 * paired with one concrete thing they could try.
 *
 * Three deliberate properties:
 *
 *  1. **Model-free.** These come straight from the extracted features, not from the model's
 *     attribution. That keeps them true when no model is bundled or usage access was
 *     declined, and it keeps the user-facing text independent of a model our own evaluation
 *     says does not work.
 *  2. **Counts and clock times, not deviations.** "You were up after midnight on four
 *     nights" is a fact about the week. "Your most unusual day was 36m above your own
 *     average" is a fact about arithmetic.
 *  3. **Comparisons are to the person, never to other people.** Nothing here says
 *     "more than average" in the population sense, because we have no basis for it.
 *
 * Every sentence is assembled from a template whose claims are entailed by its inputs —
 * see `docs/feature-spec.md` §10.3 for why that rule exists.
 */
object Observations {

    data class Observation(
        val id: String,
        /** What happened, in plain language. */
        val sentence: String,
        /** One concrete, small, reversible thing to try. Never medical advice. */
        val suggestion: String,
    )

    data class Result(
        val observations: List<Observation>,
        /** Non-null when we cannot say anything, with a reason a person can act on. */
        val unavailableReason: String?,
    )

    private const val MAX_OBSERVATIONS = 3

    fun build(
        dailyValues: Map<String, List<Double>>,
        staticValues: Map<String, Double>,
        usageAccessMissing: Boolean,
        meetsCoverage: Boolean,
        daysWithData: Double,
    ): Result {
        if (usageAccessMissing) {
            return Result(
                emptyList(),
                "This part needs permission to see your screen activity. You can turn that " +
                    "on in About — your check-in above works either way.",
            )
        }
        if (!meetsCoverage) {
            val days = daysWithData.roundToInt()
            return Result(
                emptyList(),
                "There wasn't enough history on this phone yet — only " +
                    "${if (days == 1) "one day" else "$days days"} of it. Check back in a " +
                    "few days and there will be more to look at.",
            )
        }

        // Notable first, then fill from the always-available set — never two observations
        // about the same underlying thing.
        val chosen = LinkedHashMap<String, Observation>()
        for (observation in candidates(dailyValues, staticValues)) {
            if (chosen.size >= MAX_OBSERVATIONS) break
            chosen.putIfAbsent(observation.id.substringBefore(':'), observation)
        }

        if (chosen.isEmpty()) {
            return Result(
                emptyList(),
                "Nothing in this week stood out enough to be worth pointing at.",
            )
        }
        return Result(chosen.values.toList(), null)
    }

    /**
     * Every observation this week supports, best first: the ones that only fire when there
     * is something to say, then the always-available ones.
     *
     * [build] takes the top few for its own list. [WeekSummary] walks the whole thing to
     * find the suggestion tied to a given row, which is why the cap lives in `build` rather
     * than here — a row whose topic came fourth still deserves its suggestion.
     */
    internal fun candidates(
        daily: Map<String, List<Double>>,
        static: Map<String, Double>,
    ): List<Observation> {
        val notable = listOfNotNull(
            lateNights(daily),
            shortRest(static),
            bedtimeDrift(static),
            lateBedtime(static),
            heavyScreenTime(daily),
            manyPickups(daily),
            irregularDays(static),
        )
        val fallback = listOfNotNull(
            bedtime(static),
            restLength(static),
            steadyDays(static),
            screenTime(daily),
        )
        return notable + fallback
    }

    // ── notable: each fires only when there is something to say ─────────────────────────

    private fun lateNights(daily: Map<String, List<Double>>): Observation? {
        val nights = daily["nighttime_use_fraction_fixed"]
            ?.count { !it.isNaN() && it > 0.0 } ?: return null
        if (nights < 2) return null
        val nightWord = if (nights == 1) "night" else "nights"
        return Observation(
            id = "night:late_nights",
            sentence = "You were still on your phone after midnight on $nights $nightWord " +
                "this week.",
            suggestion = "Pick a spot outside the bedroom to charge it tonight. That way " +
                "the decision is already made when you're tired.",
        )
    }

    private fun shortRest(static: Map<String, Double>): Observation? {
        val hours = static["sleep_duration_median"]?.takeIf { !it.isNaN() } ?: return null
        if (hours >= 6.5) return null
        return Observation(
            id = "rest:short_rest",
            sentence = "Your longest phone-free stretch was about ${duration(hours)} a night.",
            suggestion = "Protect the last half hour before bed — even that much on its " +
                "own tends to help.",
        )
    }

    private fun bedtimeDrift(static: Map<String, Double>): Observation? {
        val drift = static["sleep_onset_regularity"]?.takeIf { !it.isNaN() } ?: return null
        if (drift < 1.0) return null
        return Observation(
            id = "bedtime:drift",
            sentence = "Your evenings moved around a lot — about ${duration(drift)} " +
                "between the earliest and the latest.",
            suggestion = "Try one fixed wind-down time on three nights this week, not all " +
                "seven. Three is a target you can actually hit.",
        )
    }

    private fun lateBedtime(static: Map<String, Double>): Observation? {
        val onset = static["sleep_onset_hours"]?.takeIf { !it.isNaN() } ?: return null
        val late = onset >= 23.5 || onset < 5.0
        if (!late) return null
        return Observation(
            id = "bedtime:late",
            sentence = "Your phone usually went quiet around ${clock(onset)}.",
            suggestion = "Move it fifteen minutes earlier rather than an hour. Small " +
                "shifts are the ones that stick.",
        )
    }

    private fun heavyScreenTime(daily: Map<String, List<Double>>): Observation? {
        val hours = mean(daily["screen_on_fraction"])?.times(24) ?: return null
        if (hours < 5.0) return null
        return Observation(
            id = "screen:heavy",
            sentence = "You spent around ${duration(hours)} a day on your phone.",
            suggestion = "Pick the single app that takes the most of it and put a " +
                "20-minute daily limit on that one only.",
        )
    }

    private fun manyPickups(daily: Map<String, List<Double>>): Observation? {
        val pickups = mean(daily["unlock_count"]) ?: return null
        if (pickups < 60) return null
        return Observation(
            id = "pickups:many",
            sentence = "You picked your phone up about ${pickups.roundToInt()} times a day.",
            suggestion = "Move your most-opened app off the home screen for a few days " +
                "and see whether you miss it.",
        )
    }

    private fun irregularDays(static: Map<String, Double>): Observation? {
        val rhythm = static["circadian_regularity"]?.takeIf { !it.isNaN() } ?: return null
        if (rhythm >= 0.3) return null
        return Observation(
            id = "rhythm:irregular",
            sentence = "Your days ran to quite different rhythms this week.",
            suggestion = "Anchor one fixed point tomorrow — the same breakfast time, or a " +
                "short walk at the same hour.",
        )
    }

    // ── fallbacks: always available, so the section is never empty ───────────────────────

    private fun bedtime(static: Map<String, Double>): Observation? {
        val onset = static["sleep_onset_hours"]?.takeIf { !it.isNaN() } ?: return null
        return Observation(
            id = "bedtime:usual",
            sentence = "Your phone usually went quiet around ${clock(onset)}.",
            suggestion = "If you want that earlier, move it in fifteen-minute steps rather " +
                "than all at once.",
        )
    }

    private fun restLength(static: Map<String, Double>): Observation? {
        val hours = static["sleep_duration_median"]?.takeIf { !it.isNaN() } ?: return null
        return Observation(
            id = "rest:usual",
            sentence = "Your longest phone-free stretch was about ${duration(hours)} a night.",
            suggestion = "Keeping that stretch roughly the same length matters more than " +
                "when it starts.",
        )
    }

    private fun steadyDays(static: Map<String, Double>): Observation? {
        val rhythm = static["circadian_regularity"]?.takeIf { !it.isNaN() } ?: return null
        if (rhythm < 0.5) return null
        return Observation(
            id = "rhythm:steady",
            sentence = "Your days followed a fairly similar shape.",
            suggestion = "That regularity is worth keeping — it makes everything else " +
                "easier to plan around.",
        )
    }

    private fun screenTime(daily: Map<String, List<Double>>): Observation? {
        val hours = mean(daily["screen_on_fraction"])?.times(24) ?: return null
        return Observation(
            id = "screen:usual",
            sentence = "You spent around ${duration(hours)} a day on your phone.",
            suggestion = "Worth knowing rather than worth fixing. Notice how it moves week " +
                "to week before changing anything.",
        )
    }

    // ── formatting ──────────────────────────────────────────────────────────────────────

    private fun mean(values: List<Double>?): Double? {
        val present = values?.filterNot { it.isNaN() }?.takeIf { it.isNotEmpty() } ?: return null
        return present.average()
    }

    /** Hours as everyday words: "7 and a half hours", "45 minutes", "6h 10m" → "6 hours 10". */
    internal fun duration(hours: Double): String {
        val totalMinutes = (hours * 60).roundToInt()
        if (totalMinutes < 60) return "$totalMinutes minutes"
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        val hourWord = if (h == 1) "hour" else "hours"
        return when {
            m == 0 -> "$h $hourWord"
            // "seven and a half hours" reads better than "7h 30m" in a sentence.
            m in 25..35 -> "$h and a half $hourWord"
            else -> "$h $hourWord $m"
        }
    }

    /** Decimal clock hour → everyday time: 23.83 → "11:50pm", 0.0 → "midnight". */
    internal fun clock(hour: Double): String {
        val normalized = ((hour % 24) + 24) % 24
        var h = normalized.toInt()
        var m = ((normalized - h) * 60).roundToInt()
        if (m == 60) { m = 0; h = (h + 1) % 24 }
        if (h == 0 && m == 0) return "midnight"
        if (h == 12 && m == 0) return "midday"
        val suffix = if (h < 12) "am" else "pm"
        var display = h % 12
        if (display == 0) display = 12
        return if (m == 0) "$display$suffix" else String.format("%d:%02d%s", display, m, suffix)
    }

    /** Exposed for the copy test: everything a user can read from this file. */
    internal fun allCopyStrings(): List<String> {
        val daily = mapOf(
            "nighttime_use_fraction_fixed" to List(7) { 0.2 },
            "screen_on_fraction" to List(7) { 0.30 },
            "unlock_count" to List(7) { 70.0 },
        )
        val static = mapOf(
            "sleep_duration_median" to 5.0,
            "sleep_onset_hours" to 23.9,
            "sleep_onset_regularity" to 1.5,
            "circadian_regularity" to 0.1,
        )
        val fired = build(daily, static, false, true, 7.0).observations
        val steady = build(
            emptyMap(),
            mapOf("circadian_regularity" to 0.8, "sleep_duration_median" to 8.0,
                  "sleep_onset_hours" to 22.0),
            false, true, 7.0,
        ).observations
        return (fired + steady).flatMap { listOf(it.sentence, it.suggestion) }
    }
}
