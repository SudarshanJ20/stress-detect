package com.stressdetect.data

import android.content.Context
import com.stressdetect.features.LockedInterval
import org.json.JSONObject
import java.time.LocalDate

/**
 * Demo mode: replays the committed synthetic fixture instead of reading the phone.
 *
 * It substitutes ONLY the OS queries. The real feature extractor, the real model and the
 * real attribution all run on the replayed data, so a demo exercises the actual pipeline
 * rather than a mock of it — and a demo that passes is evidence the pipeline works.
 *
 * The trace is `fixtures/synthetic_trace.json`, the same file the parity tests use, so the
 * demo can never drift from what is verified. It contains no participant data.
 *
 * Everything downstream marks results from here as demo data, and the UI shows a permanent
 * chip: in a viva, a demo reading must never be mistakable for a real one.
 */
class DemoTraceSource(private val context: Context) {

    data class DemoWindow(
        val caseName: String,
        val labelDate: LocalDate,
        val zoneId: String,
        val lockedIntervals: List<LockedInterval>,
        val calls: List<Long>,
        val sms: List<Long>,
    )

    /**
     * Loads the demo case. `demo_week` exists in the fixture specifically for this: the
     * other cases are minimal rule-tests (a handful of intervals, ~0.7 unlocks a day),
     * which are correct for what they pin but nonsense to show as an example week. A demo
     * should look like a person's week, not like a boundary test.
     */
    fun load(caseName: String = DEFAULT_CASE): DemoWindow {
        val raw = context.assets.open(ASSET).use { it.readBytes() }
        val trace = JSONObject(String(raw, Charsets.UTF_8))
        val zone = trace.getString("parity_timezone")
        val cases = trace.getJSONArray("cases")

        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            if (case.getString("name") != caseName) continue

            val intervals = case.getJSONArray("locked_intervals")
            val calls = case.getJSONArray("calls")
            val sms = case.getJSONArray("sms")
            return DemoWindow(
                caseName = caseName,
                labelDate = LocalDate.parse(case.getString("label_date")),
                zoneId = zone,
                lockedIntervals = (0 until intervals.length()).map {
                    val pair = intervals.getJSONArray(it)
                    LockedInterval(pair.getLong(0), pair.getLong(1))
                },
                calls = (0 until calls.length()).map { calls.getLong(it) },
                sms = (0 until sms.length()).map { sms.getLong(it) },
            )
        }
        error("demo case '$caseName' not found in $ASSET")
    }

    private companion object {
        const val ASSET = "synthetic_trace.json"
        const val DEFAULT_CASE = "demo_week"
    }
}
