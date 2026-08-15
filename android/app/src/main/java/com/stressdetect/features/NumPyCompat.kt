package com.stressdetect.features

import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Exact Kotlin equivalents of the handful of NumPy/pandas reductions the Python extractor
 * uses. These exist because "median" and "IQR" are NOT unambiguous — a naive
 * implementation silently disagrees with NumPy on even-length inputs or on quantile
 * interpolation, and that disagreement would surface as a feature-parity failure with no
 * obvious cause.
 *
 * Mirrors, in order: `np.median`, `np.percentile(..., method="linear")`,
 * `pandas.Series.std(ddof=0)`, `np.corrcoef`.
 */
internal object NumPyCompat {

    /**
     * `np.percentile(a, q)` with the default `method="linear"`: the value at the
     * fractional index `q/100 * (n - 1)` of the SORTED input, linearly interpolated
     * between its neighbours.
     */
    fun percentile(values: DoubleArray, q: Double): Double {
        require(values.isNotEmpty()) { "percentile of an empty array" }
        val sorted = values.sortedArray()
        if (sorted.size == 1) return sorted[0]
        val pos = q / 100.0 * (sorted.size - 1)
        val lo = floor(pos).toInt()
        val hi = if (lo + 1 < sorted.size) lo + 1 else lo
        val frac = pos - lo
        return sorted[lo] + (sorted[hi] - sorted[lo]) * frac
    }

    /** `np.median` — equivalent to [percentile] at q = 50 (averages the two middle values). */
    fun median(values: DoubleArray): Double = percentile(values, 50.0)

    /** `np.subtract(*np.percentile(a, [75, 25]))` — the interquartile range. */
    fun iqr(values: DoubleArray): Double = percentile(values, 75.0) - percentile(values, 25.0)

    /**
     * POPULATION standard deviation — `pandas.Series.std(ddof=0)` / `np.std`.
     * pandas defaults to `ddof=1`; the Python extractor passes `ddof=0` explicitly for
     * `unlock_count_sd`, so this must too.
     */
    fun stdPopulation(values: DoubleArray): Double {
        if (values.isEmpty()) return Double.NaN
        val mean = values.average()
        var acc = 0.0
        for (v in values) {
            val d = v - mean
            acc += d * d
        }
        return sqrt(acc / values.size)
    }

    /**
     * Pearson correlation between two equal-length vectors, matching a single off-diagonal
     * cell of `np.corrcoef`. Returns NaN when either vector has zero variance (as NumPy
     * does, modulo its warning) — callers filter those out first.
     */
    fun pearson(a: DoubleArray, b: DoubleArray): Double {
        require(a.size == b.size) { "pearson on unequal lengths" }
        val ma = a.average()
        val mb = b.average()
        var num = 0.0
        var da = 0.0
        var db = 0.0
        for (i in a.indices) {
            val x = a[i] - ma
            val y = b[i] - mb
            num += x * y
            da += x * x
            db += y * y
        }
        val den = sqrt(da) * sqrt(db)
        return if (den == 0.0) Double.NaN else num / den
    }

    /**
     * `np.nanmean` of the upper triangle (k=1) of `np.corrcoef(rows)` — the mean pairwise
     * correlation between rows, skipping NaN pairs. Returns NaN when no pair is defined.
     */
    fun meanPairwiseCorrelation(rows: List<DoubleArray>): Double {
        var sum = 0.0
        var n = 0
        for (i in rows.indices) {
            for (j in i + 1 until rows.size) {
                val r = pearson(rows[i], rows[j])
                if (!r.isNaN()) {
                    sum += r
                    n++
                }
            }
        }
        return if (n == 0) Double.NaN else sum / n
    }
}
