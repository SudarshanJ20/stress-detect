package com.stressdetect.inference

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.stressdetect.features.SpecConstants
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileNotFoundException
import kotlin.math.abs

/**
 * ON-DEVICE inference parity: ONNX Runtime Mobile, running on the device, must reproduce
 * the PyTorch model's output for the same inputs.
 *
 * This is an *instrumented* test on purpose. A JVM test would exercise the desktop ONNX
 * Runtime build, not the Mobile kernels that actually ship in the APK — and the whole
 * question here is whether what runs on the phone agrees with what was trained.
 *
 * Reference values come from `fixtures/model_reference.json`, written by
 * `ml/src/training/run_dl.py` at export time: for each fixture case it records the
 * standardized inputs and the PyTorch output. Without a shared reference, "the app ran the
 * model" would prove only that it produced *a* number.
 *
 * Both the model and the reference are pushed to the device by
 * `android/tools/push_model_assets.sh` (the `.onnx` is gitignored — model binaries are
 * never committed).
 */
@RunWith(AndroidJUnit4::class)
class OnnxParityTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun reference(): JSONObject {
        val bytes = try {
            context.assets.open(REFERENCE_ASSET).use { it.readBytes() }
        } catch (e: FileNotFoundException) {
            throw AssertionError(
                "missing asset '$REFERENCE_ASSET'. Generate it with " +
                    "`ml/.venv/bin/python ml/src/training/run_dl.py`, then run " +
                    "`android/tools/push_model_assets.sh`. Model binaries are gitignored, so a " +
                    "fresh clone has to export them before this test can run.",
                e,
            )
        }
        return JSONObject(String(bytes, Charsets.UTF_8))
    }

    private fun modelBytes(): ByteArray = try {
        context.assets.open(MODEL_ASSET).use { it.readBytes() }
    } catch (e: FileNotFoundException) {
        throw AssertionError(
            "missing asset '$MODEL_ASSET' — see the message on the reference asset.", e,
        )
    }

    @Test
    fun modelMetadataMatchesTheImplementedSpecVersion() {
        // The 4th leg of the SPEC_VERSION check: docs, Python and Kotlin are compared in
        // SpecVersionTest; this compares the ONNX metadata against the Kotlin constant.
        OnnxStressModel.load(modelBytes()).use { model ->
            assertEquals(
                "the exported model's spec_version metadata disagrees with the extractor",
                SpecConstants.SPEC_VERSION,
                model.modelSpecVersion,
            )
        }
        assertEquals(
            "the model reference targets a different SPEC_VERSION than this app",
            SpecConstants.SPEC_VERSION,
            reference().getString("spec_version"),
        )
    }

    @Test
    fun onDeviceOnnxOutputMatchesPyTorch() {
        val ref = reference()
        val tolerance = ref.getDouble("tolerance")
        val cases = ref.getJSONArray("cases")
        val failures = mutableListOf<String>()
        var maxDelta = 0.0

        OnnxStressModel.load(modelBytes()).use { model ->
            for (i in 0 until cases.length()) {
                val case = cases.getJSONObject(i)
                val expected = case.getDouble("expected_stress")

                // Feed the RAW inputs and let the model standardize them from its own
                // embedded scaler — that exercises the real app path, not a shortcut.
                val rawSeq = case.getJSONArray("raw_seq")
                val sequence = Array(rawSeq.length()) { day ->
                    val row = rawSeq.getJSONArray(day)
                    DoubleArray(row.length()) { if (row.isNull(it)) Double.NaN else row.getDouble(it) }
                }
                val rawStatic = case.getJSONArray("raw_static")
                val static = DoubleArray(rawStatic.length()) {
                    if (rawStatic.isNull(it)) Double.NaN else rawStatic.getDouble(it)
                }

                val actual = model.predict(sequence, static).toDouble()
                val delta = abs(actual - expected)
                if (delta > maxDelta) maxDelta = delta
                if (delta > tolerance) {
                    failures += "${case.getString("name")}: PyTorch $expected vs on-device " +
                        "$actual (delta $delta > $tolerance)"
                }
            }
        }

        android.util.Log.i(
            "OnnxParityTest",
            "on-device ORT vs PyTorch over ${cases.length()} fixture cases: " +
                "max|diff| = $maxDelta (tolerance $tolerance)",
        )
        assertTrue(
            "ON-DEVICE INFERENCE PARITY BREAK — ONNX Runtime Mobile disagrees with the " +
                "PyTorch model that was trained:\n  " + failures.joinToString("\n  "),
            failures.isEmpty(),
        )
    }

    @Test
    fun standardizationMatchesTheExportedScaler() {
        // Localises a failure: if this passes but the previous test fails, the model itself
        // diverged; if this fails, the scaler is being applied wrongly.
        val ref = reference()
        val cases = ref.getJSONArray("cases")
        val standardization = ref.getJSONObject("standardization")
        val dynMean = standardization.getJSONArray("dyn_mean")
        val dynSd = standardization.getJSONArray("dyn_sd")

        val case = cases.getJSONObject(0)
        val rawSeq = case.getJSONArray("raw_seq")
        val stdSeq = case.getJSONArray("std_seq")
        for (day in 0 until rawSeq.length()) {
            val rawRow = rawSeq.getJSONArray(day)
            val stdRow = stdSeq.getJSONArray(day)
            for (i in 0 until rawRow.length()) {
                val expected = stdRow.getDouble(i)
                // Compare in FLOAT32, which is what actually enters the model: the
                // reference stores float32-rounded values (the exporter casts before
                // inference), so a float64 recomputation differs by ~5e-8 — real rounding,
                // not a bug, and comparing at float64 precision would fail forever.
                val actual = if (rawRow.isNull(i)) 0.0f
                else (((rawRow.getDouble(i) - dynMean.getDouble(i)) / dynSd.getDouble(i)).toFloat())
                assertEquals("day $day feature $i", expected, actual.toDouble(), 1e-9)
            }
        }
    }

    private companion object {
        const val MODEL_ASSET = "stress_model.onnx"
        const val REFERENCE_ASSET = "model_reference.json"
    }
}
