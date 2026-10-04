package com.mascill.healthtrack.util

/**
 * Monotonic milliseconds for session deadlines.
 * The app binding uses `SystemClock.elapsedRealtime`.
 */
fun interface ElapsedClock {
    /**
     * Milliseconds since an arbitrary monotonic origin.
     */
    fun millis(): Long
}
