package com.stressdetect.inference

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.stressdetect.features.SequenceFeatures
import com.stressdetect.features.SpecConstants
import org.json.JSONObject
import java.io.Closeable
import java.nio.FloatBuffer

/**
 * ONNX Runtime Mobile implementation of [StressModel].
 *
 * Consumes the contract in `docs/feature-spec.md` §7:
 * ```
 * input  seq    : float32 (1, 7, 8)   # dynamic, standardized
 * input  static : float32 (1, 9)      # static, standardized
 * output stress : float32 (1,)        # 0–100, 100 = most stressed
 * ```
 *
 * Two things it refuses to do quietly:
 *  - **Run on a mismatched model.** The exported file carries `spec_version` in its
 *    metadata; if it disagrees with [SpecConstants.SPEC_VERSION] the features this app
 *    computes are not the features the model was fitted on, and the output would be a
 *    confident, meaningless number. That is a load-time error, not a warning.
 *  - **Score raw features.** The model was trained on STANDARDIZED inputs, so the mean/sd
 *    are read from the model's own metadata and applied here. NaN maps to 0 only AFTER
 *    standardizing, so 0 means "the training mean", not "zero hours".
 */
class OnnxStressModel private constructor(
    private val environment: OrtEnvironment,
    private val session: OrtSession,
    private val standardization: Standardization,
    val modelSpecVersion: String,
) : StressModel, Closeable {

    data class Standardization(
        val dynMean: DoubleArray, val dynSd: DoubleArray,
        val staticMean: DoubleArray, val staticSd: DoubleArray,
    )

    override fun predict(sequence: Array<DoubleArray>, static: DoubleArray): Float {
        require(sequence.size == SpecConstants.WINDOW_DAYS) {
            "expected ${SpecConstants.WINDOW_DAYS} days, got ${sequence.size}"
        }
        require(static.size == SequenceFeatures.STATIC_FEATURE_NAMES.size) {
            "expected ${SequenceFeatures.STATIC_FEATURE_NAMES.size} static features, got ${static.size}"
        }

        val dynCount = SequenceFeatures.DYNAMIC_FEATURE_NAMES.size
        val seqBuffer = FloatBuffer.allocate(sequence.size * dynCount)
        for (day in sequence) {
            require(day.size == dynCount) { "expected $dynCount dynamic features, got ${day.size}" }
            for (i in day.indices) {
                seqBuffer.put(standardize(day[i], standardization.dynMean[i], standardization.dynSd[i]))
            }
        }
        seqBuffer.rewind()

        val staticBuffer = FloatBuffer.allocate(static.size)
        for (i in static.indices) {
            staticBuffer.put(
                standardize(static[i], standardization.staticMean[i], standardization.staticSd[i])
            )
        }
        staticBuffer.rewind()

        val seqTensor = OnnxTensor.createTensor(
            environment, seqBuffer, longArrayOf(1, sequence.size.toLong(), dynCount.toLong()),
        )
        val staticTensor = OnnxTensor.createTensor(
            environment, staticBuffer, longArrayOf(1, static.size.toLong()),
        )

        return seqTensor.use { seqT ->
            staticTensor.use { staticT ->
                session.run(mapOf(INPUT_SEQ to seqT, INPUT_STATIC to staticT)).use { result ->
                    // The contract declares `stress:(B,)` — RANK 1 — so ONNX Runtime hands
                    // back a FloatArray, not Array<FloatArray>. Both shapes are accepted
                    // rather than assumed: a rank change in a re-export would otherwise
                    // surface as a ClassCastException at inference time on a user's phone.
                    when (val output = result[0].value) {
                        is FloatArray -> output[0]
                        is Array<*> -> (output[0] as FloatArray)[0]
                        else -> error(
                            "unexpected ONNX output type ${output?.javaClass}; the model must " +
                                "emit float32 stress with shape (B,) per feature-spec §7"
                        )
                    }
                }
            }
        }
    }

    /** Standardize, THEN map missing to 0 — i.e. to the training mean, not to zero hours. */
    private fun standardize(value: Double, mean: Double, sd: Double): Float {
        if (value.isNaN()) return 0.0f
        val z = (value - mean) / sd
        return if (z.isNaN()) 0.0f else z.toFloat()
    }

    override fun close() {
        session.close()
    }

    companion object {
        private const val INPUT_SEQ = "seq"
        private const val INPUT_STATIC = "static"
        private const val META_SPEC_VERSION = "spec_version"
        private const val META_STANDARDIZATION = "standardization"

        /**
         * @param modelBytes the exported `.onnx` file (an app asset).
         * @throws IllegalStateException if the model's `spec_version` metadata does not
         *   equal the extractor's — see the class doc for why that must be fatal.
         */
        fun load(modelBytes: ByteArray): OnnxStressModel {
            val environment = OrtEnvironment.getEnvironment()
            val session = environment.createSession(modelBytes, OrtSession.SessionOptions())

            val metadata = session.metadata.customMetadata
            val modelSpecVersion = metadata[META_SPEC_VERSION]
                ?: error(
                    "the ONNX model has no '$META_SPEC_VERSION' metadata — it was not exported " +
                        "by ml/src/models/onnx_export.py and cannot be trusted to match this " +
                        "extractor's feature definitions"
                )
            check(modelSpecVersion == SpecConstants.SPEC_VERSION) {
                "SPEC_VERSION MISMATCH — the model was trained under $modelSpecVersion but this " +
                    "app computes features under ${SpecConstants.SPEC_VERSION}. The feature " +
                    "definitions differ, so any prediction would be meaningless. Re-export the " +
                    "model or update the app; do not run."
            }

            val raw = metadata[META_STANDARDIZATION]
                ?: error(
                    "the ONNX model has no '$META_STANDARDIZATION' metadata. The model consumes " +
                        "standardized inputs; scoring raw features against it would silently " +
                        "produce wrong numbers."
                )
            val json = JSONObject(raw)
            val standardization = Standardization(
                dynMean = json.doubleArray("dyn_mean"),
                dynSd = json.doubleArray("dyn_sd"),
                staticMean = json.doubleArray("static_mean"),
                staticSd = json.doubleArray("static_sd"),
            )
            return OnnxStressModel(environment, session, standardization, modelSpecVersion)
        }

        private fun JSONObject.doubleArray(key: String): DoubleArray {
            val array = getJSONArray(key)
            return DoubleArray(array.length()) { array.getDouble(it) }
        }
    }
}
