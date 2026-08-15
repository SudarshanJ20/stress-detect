package com.stressdetect.features

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/**
 * Reader for `fixtures/synthetic_trace.json`, the cross-language parity contract.
 *
 * The same file is read by `ml/tests/test_parity_fixture.py`. Missing values are `null` in
 * the JSON (strict JSON has no NaN token) and are mapped back to [Double.NaN] here, which is
 * the in-memory representation both extractors use.
 */
internal object ParityFixture {

    data class Case(
        val name: String,
        val exercises: String,
        val labelDate: LocalDate,
        val expectedWindowStart: Long,
        val expectedWindowEnd: Long,
        val lockedIntervals: List<LockedInterval>,
        val calls: List<Long>,
        val sms: List<Long>,
        val hasCalls: Boolean,
        val hasSms: Boolean,
        val expectedFeatures: Map<String, Double>,
        val expectedMeetsCoverage: Boolean,
    )

    data class Trace(
        val specVersion: String,
        val parityTimezone: String,
        val tolerance: Double,
        val featureNames: List<String>,
        val cases: List<Case>,
    )

    fun load(): Trace {
        val root = JSONObject(file().readText())
        val cases = root.getJSONArray("cases").objects().map { case ->
            val window = case.getJSONObject("expected_window")
            Case(
                name = case.getString("name"),
                exercises = case.getString("exercises"),
                labelDate = LocalDate.parse(case.getString("label_date")),
                expectedWindowStart = window.getLong("start_utc"),
                expectedWindowEnd = window.getLong("end_utc"),
                lockedIntervals = case.getJSONArray("locked_intervals").arrays().map {
                    LockedInterval(it.getLong(0), it.getLong(1))
                },
                calls = case.getJSONArray("calls").longs(),
                sms = case.getJSONArray("sms").longs(),
                hasCalls = case.getBoolean("has_calls"),
                hasSms = case.getBoolean("has_sms"),
                expectedFeatures = case.getJSONObject("expected_features").doubles(),
                expectedMeetsCoverage = case.getBoolean("expected_meets_coverage"),
            )
        }
        return Trace(
            specVersion = root.getString("spec_version"),
            parityTimezone = root.getString("parity_timezone"),
            tolerance = root.getDouble("tolerance"),
            featureNames = root.getJSONArray("feature_names").strings(),
            cases = cases,
        )
    }

    /** Walks up from the Gradle module dir until the repo's `fixtures/` appears. */
    fun file(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, "fixtures/synthetic_trace.json")
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        throw AssertionError("could not locate fixtures/synthetic_trace.json from ${System.getProperty("user.dir")}")
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun JSONArray.arrays(): List<JSONArray> = (0 until length()).map { getJSONArray(it) }
    private fun JSONArray.longs(): List<Long> = (0 until length()).map { getLong(it) }
    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

    /** `null` in the fixture means "missing" and becomes NaN — never 0.0. */
    private fun JSONObject.doubles(): Map<String, Double> {
        val out = LinkedHashMap<String, Double>()
        for (key in keys()) {
            out[key] = if (isNull(key)) Double.NaN else getDouble(key)
        }
        return out
    }
}
