package ams2.dash.model

/**
 * Delta en direct : on échantillonne le temps du tour tous les BUCKET_M mètres,
 * et on compare le tour en cours au meilleur tour valide complet.
 */
class LapReference {
    private var current = newBuckets()
    private var reference = newBuckets()
    private var referenceTime = -1f
    private var filled = 0

    fun startLap() {
        current.fill(Float.NaN)
        filled = 0
    }

    fun clearReference() {
        reference.fill(Float.NaN)
        referenceTime = -1f
    }

    fun record(distance: Int, time: Float) {
        if (time <= 0f) return
        val b = distance / BUCKET_M
        if (b < BUCKETS && current[b].isNaN()) {
            current[b] = time
            filled++
        }
    }

    /** Part du tour échantillonnée (0..1), pour écarter les tours partiels (sortie de stand…). */
    fun coverage(trackLength: Float): Float {
        if (trackLength <= 0f) return 0f
        return filled * BUCKET_M / trackLength
    }

    fun offerReference(lapTime: Float) {
        if (lapTime <= 0f || (referenceTime > 0f && lapTime >= referenceTime)) return
        val swap = reference
        reference = current
        current = swap
        referenceTime = lapTime
    }

    fun delta(distance: Int, time: Float): Float {
        if (referenceTime <= 0f || time <= 0f) return Float.NaN
        val pos = distance.toFloat() / BUCKET_M
        val b = pos.toInt()
        if (b + 1 >= BUCKETS) return Float.NaN
        val t0 = reference[b]
        val t1 = reference[b + 1]
        if (t0.isNaN()) return Float.NaN
        val ref = if (t1.isNaN()) t0 else t0 + (t1 - t0) * (pos - b)
        return time - ref
    }

    companion object {
        private const val BUCKET_M = 5
        private const val BUCKETS = 65536 / BUCKET_M + 1 // sCurrentLapDistance est un u16

        private fun newBuckets() = FloatArray(BUCKETS) { Float.NaN }
    }
}
