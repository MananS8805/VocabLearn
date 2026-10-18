package com.example.walllearn.core

/**
 * Smooth weighted round-robin: picks which source (English, Spanish, My texts) is
 * next so the long-run ratio matches the weights exactly and picks are evenly
 * spread, never clumped ("3 : 1" gives A A B A, not A A A B in a random streak).
 *
 * [credits] carries the running balance between calls (persist it) and is updated
 * in place. Pure Kotlin, unit-tested on the JVM.
 */
object WeightedPicker {

    /** Index of the chosen source, or -1 when every weight is zero. */
    fun pick(weights: IntArray, credits: IntArray): Int {
        val total = weights.sumOf { it.coerceAtLeast(0) }
        if (total <= 0) return -1
        var best = -1
        for (i in weights.indices) {
            if (weights[i] <= 0) {
                credits[i] = 0
                continue
            }
            credits[i] += weights[i]
            if (best < 0 || credits[i] > credits[best]) best = i
        }
        credits[best] -= total
        return best
    }
}
