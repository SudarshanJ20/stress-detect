package com.stressdetect.ui.content

import com.stressdetect.features.SequenceFeatures
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Turns a ranked feature into something a person can read.
 *
 * Two rules govern every string produced here:
 *
 *  1. **Comparisons are to the user's OWN week, never to a population.** For the daily
 *     (dynamic) features we have seven values, so "more than usual" means more than *their*
 *     own seven-day average. For window-level (static) features there is no within-window
 *     baseline, so we state the value plainly and make NO comparison at all rather than
 *     borrowing the training distribution.
 *  2. **Description, not diagnosis or causation.** No "because", no "this is why you are
 *     stressed", no clinical vocabulary. These are things that happened on a phone.
 */
object Factors {

    /** Human labels. Every rankable feature must appear here — `FactorsTest` enforces it. */
    private val LABELS: Map<String, String> = mapOf(
        "unlock_count" to "How often you picked up your phone",
        "session_count" to "How many separate times you used it",
        "session_duration_median" to "How long a typical use lasted",
        "screen_on_fraction" to "Total time on the phone",
        "nighttime_use_fraction_fixed" to "Use late at night",
        "call_count" to "Calls",
        "sms_count" to "Messages",
        "sleep_duration_median" to "Length of your longest phone-off stretch",
        "sleep_onset_hours" to "When your phone went quiet",
        "sleep_wake_hours" to "When you first picked it up",
        "sleep_onset_regularity" to "How consistent your evenings were",
        "sleep_midpoint_regularity" to "How consistent your nights were",
        "circadian_regularity" to "How alike your days looked",
    )

    /**
     * Suggestions, keyed by feature. A fixed lookup table on purpose: generated text would
     * be unverifiable, could drift into advice, and cannot be reviewed once for safety.
     * Every one is a general, low-stakes habit — none is personal, medical or prescriptive.
     */
    private val SUGGESTIONS: Map<String, String> = mapOf(
        "unlock_count" to
            "Picking the phone up often is usually habit rather than need. Moving one " +
                "app off the home screen is a small, reversible experiment.",
        "session_count" to
            "Lots of short check-ins tend to fragment attention. Batching them — say, " +
                "looking at messages at set times — is worth a try.",
        "session_duration_median" to
            "If sessions run longer than you meant, a timer on the one app that tends to " +
                "absorb you gives you a nudge without blocking anything.",
        "screen_on_fraction" to
            "Screen time is easier to shift by replacing than by cutting: having " +
                "something specific to do instead beats resolving to use it less.",
        "nighttime_use_fraction_fixed" to
            "Late-night use is often about proximity. Charging the phone outside the " +
                "bedroom removes the decision rather than relying on willpower.",
        "call_count" to
            "Time in contact with people tends to track how weeks feel. No action needed " +
                "here — it is just something that varied.",
        "sms_count" to
            "Messaging volume moves with the week's demands. Worth noticing, not worth " +
                "changing on purpose.",
        "sleep_duration_median" to
            "Protecting the length of that quiet stretch matters more than perfecting " +
                "when it starts. Aim for the easiest end to move.",
        "sleep_onset_hours" to
            "A consistent wind-down time is one of the easier things to shift. Pick one " +
                "you could keep even on a bad day.",
        "sleep_wake_hours" to
            "A steady wake time anchors the rest of the day and is usually easier to hold " +
                "than a steady bedtime.",
        "sleep_onset_regularity" to
            "Evenings that vary a lot are worth a look before evenings that are simply " +
                "late — consistency is often the easier win.",
        "sleep_midpoint_regularity" to
            "If nights land at very different times, shifting the anchor by a little on " +
                "the most irregular days does more than a large change on one day.",
        "circadian_regularity" to
            "Days that look alike are easier to plan around. One fixed point — a meal, a " +
                "walk, a start time — is often enough to create one.",
    )

    /** Shown once under the suggestion list. Non-negotiable framing. */
    const val SUGGESTION_DISCLAIMER: String =
        "These are general suggestions, not advice for you personally, and not treatment " +
            "for anything."

    /** Shown once above the factor list. Non-negotiable framing. */
    const val FACTOR_DISCLAIMER: String =
        "These describe what the model reacted to in your phone data — context, not " +
            "causes, and not a reason you feel any particular way."

    fun label(featureName: String): String =
        LABELS[featureName] ?: featureName.replace('_', ' ')

    fun suggestion(featureName: String): String? = SUGGESTIONS[featureName]

    /** Every feature the attribution can rank; used by the totality test. */
    fun rankableFeatures(): List<String> =
        (SequenceFeatures.DYNAMIC_FEATURE_NAMES + SequenceFeatures.STATIC_FEATURE_NAMES)
            .filter { it !in NON_BEHAVIOURAL }

    private val NON_BEHAVIOURAL =
        setOf("has_data", "days_with_data", "call_present", "sms_present")

    /**
     * A one-line description of what this feature did across the user's own week.
     *
     * @param dailyValues the 7 daily values when this is a dynamic feature, else null.
     * @param windowValue the window-level value when this is a static feature, else null.
     */
    fun describe(
        featureName: String,
        dailyValues: List<Double>?,
        windowValue: Double?,
    ): String {
        if (dailyValues != null) {
            val present = dailyValues.filterNot { it.isNaN() }
            if (present.size >= 2) return describeAgainstOwnWeek(featureName, present)
            if (present.size == 1) {
                return "Only one day this week had enough activity to measure, so there is " +
                    "nothing to compare it against."
            }
        }
        if (windowValue != null && !windowValue.isNaN()) {
            return describeValue(featureName, windowValue)
        }
        return "Not enough data this week to describe this one."
    }

    /**
     * Dynamic features: the most UNUSUAL day against their own average.
     *
     * "Most unusual", not "busiest": the day picked is the one furthest from their own
     * average in either direction, and it is often a quiet day. Calling a below-average day
     * "your busiest" is simply wrong, and the direction word has to follow the sign.
     */
    private fun describeAgainstOwnWeek(featureName: String, values: List<Double>): String {
        val average = values.average()
        val standout = values.maxByOrNull { abs(it - average) } ?: return ""
        val difference = standout - average
        val typical = formatValue(featureName, average)

        // Below the resolution worth mentioning, "1 more than an average of 1" is noise
        // dressed up as a finding. Say the truth instead: the days were alike.
        val amount = formatDelta(featureName, abs(difference))
            ?: return "Your days were much alike here, at around $typical."

        val direction = if (difference > 0) "more" else "less"
        return "Your most unusual day was about $amount $direction than your own week's " +
            "average of $typical."
    }

    /** Static features: state the value, make no comparison — there is no baseline yet. */
    private fun describeValue(featureName: String, value: Double): String = when (featureName) {
        "sleep_onset_hours" -> "Across the week this settled around ${formatClock(value)}."
        "sleep_wake_hours" -> "Across the week this was around ${formatClock(value)}."
        "sleep_duration_median" -> "Typically about ${formatHours(value)}."
        "sleep_onset_regularity", "sleep_midpoint_regularity" ->
            // "moved by about 0m" is a true statement that reads like a glitch.
            if (value * 60 < 5) "This barely moved from night to night."
            else "This moved by about ${formatHours(value)} from night to night."
        "circadian_regularity" ->
            if (value >= 0.5) "Your days followed a fairly similar shape."
            else "Your days varied quite a bit in shape."
        else -> "This week's value was ${formatValue(featureName, value)}."
    }

    private fun formatValue(featureName: String, value: Double): String = when (featureName) {
        // Small counts get a decimal: an average of "1 a day" alongside a difference of
        // "1" is unreadable, and rounding away the difference is what made it unreadable.
        "unlock_count", "session_count", "call_count", "sms_count" -> "${countText(value)} a day"
        "session_duration_median" -> formatMinutes(value)
        "screen_on_fraction" -> formatHours(value * 24)
        "nighttime_use_fraction_fixed" -> "${(value * 100).roundToInt()}% of your use"
        else -> trimNumber(value)
    }

    /** Returns null when the difference is too small to be worth stating as a finding. */
    private fun formatDelta(featureName: String, value: Double): String? = when (featureName) {
        "unlock_count", "session_count", "call_count", "sms_count" ->
            if (value < 0.5) null else countText(value)
        "session_duration_median" -> if (value < 1.0) null else formatMinutes(value)
        "screen_on_fraction" -> if (value * 24 * 60 < 5) null else formatHours(value * 24)
        "nighttime_use_fraction_fixed" ->
            if (value * 100 < 1) null else "${(value * 100).roundToInt()} percentage points"
        else -> if (abs(value) < 0.01) null else trimNumber(value)
    }

    private fun countText(value: Double): String =
        if (value < 3) String.format("%.1f", value) else "${value.roundToInt()}"

    /** Decimal clock hour → local wall-clock, e.g. 23.5 → "11:30 pm". */
    private fun formatClock(hour: Double): String {
        val normalized = ((hour % 24) + 24) % 24
        var h = normalized.toInt()
        val m = ((normalized - h) * 60).roundToInt().coerceIn(0, 59)
        val suffix = if (h < 12) "am" else "pm"
        var display = h % 12
        if (display == 0) display = 12
        return String.format("%d:%02d %s", display, m, suffix)
    }

    private fun formatHours(hours: Double): String {
        val total = (hours * 60).roundToInt()
        val h = total / 60
        val m = total % 60
        return when {
            h == 0 -> "${m}m"
            m == 0 -> "${h}h"
            else -> "${h}h ${m}m"
        }
    }

    private fun formatMinutes(minutes: Double): String {
        val rounded = minutes.roundToInt()
        return if (rounded >= 60) formatHours(minutes / 60) else "${rounded}m"
    }

    private fun trimNumber(value: Double): String =
        if (abs(value - value.roundToInt()) < 0.05) "${value.roundToInt()}"
        else String.format("%.2f", value)
}
