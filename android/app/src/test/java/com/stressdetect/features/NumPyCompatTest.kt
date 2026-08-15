package com.stressdetect.features

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The reductions checked here are the ones where a "reasonable" Kotlin implementation
 * quietly disagrees with NumPy: quantile interpolation and `ddof`. Expected values are
 * verbatim from NumPy/pandas.
 */
class NumPyCompatTest {

    private val sample = doubleArrayOf(12.0, 5.0, 7.5, 30.0, 2.0, 9.0)
    private val other = doubleArrayOf(3.0, 1.0, 4.0, 1.0, 5.0, 9.0)
    private val tolerance = 1e-12

    @Test
    fun `median averages the two middle values like np median`() {
        assertEquals(8.25, NumPyCompat.median(sample), tolerance)
    }

    @Test
    fun `percentile uses linear interpolation like np percentile`() {
        // A "nearest rank" percentile would give 12.0 and 5.0 here — and an IQR of 7.0.
        assertEquals(11.25, NumPyCompat.percentile(sample, 75.0), tolerance)
        assertEquals(5.625, NumPyCompat.percentile(sample, 25.0), tolerance)
    }

    @Test
    fun `iqr matches np subtract of the 75th and 25th percentiles`() {
        assertEquals(5.625, NumPyCompat.iqr(sample), tolerance)
    }

    @Test
    fun `std is the population sd — pandas defaults to ddof 1, the extractor passes ddof 0`() {
        assertEquals(9.084862256645515, NumPyCompat.stdPopulation(sample), tolerance)
    }

    @Test
    fun `pearson matches an off-diagonal cell of np corrcoef`() {
        assertEquals(-0.39652493458391785, NumPyCompat.pearson(sample, other), 1e-12)
    }

    @Test
    fun `pearson is NaN for a zero-variance vector`() {
        val flat = DoubleArray(6) { 4.0 }
        assertEquals(true, NumPyCompat.pearson(sample, flat).isNaN())
    }

    @Test
    fun `single-element percentile returns that element`() {
        assertEquals(3.0, NumPyCompat.percentile(doubleArrayOf(3.0), 75.0), tolerance)
    }

    @Test
    fun `circular mean handles the midnight wrap`() {
        // 23:00 and 01:00 average to MIDNIGHT, not to noon — the whole reason clock-time
        // central tendency uses the mean-resultant vector.
        //
        // Midnight comes back as 24.0 rather than 0.0: the mean vector's sine is a tiny
        // NEGATIVE float, so `angle mod 2π` lands just below a full turn. Python's `_circ`
        // returns exactly the same 24.0 (verified by running it on this input), so the
        // quirk is mirrored deliberately — "fixing" it here would break parity, and any
        // downstream band check treats 24.0 and 0.0 alike.
        val circular = ScreenLockFeatures.circular(doubleArrayOf(23.0, 1.0))
        assertEquals(24.0, circular.mean, 1e-9)
        assertEquals(1.00580136087699, circular.sd, 1e-9)
    }

    @Test
    fun `circular sd is zero for identical hours`() {
        val circular = ScreenLockFeatures.circular(doubleArrayOf(3.0, 3.0, 3.0))
        assertEquals(3.0, circular.mean, 1e-9)
        assertEquals(0.0, circular.sd, 1e-9)
    }
}
