package ams2.dash.model

import android.os.SystemClock

/**
 * État courant du dashboard. Écrit par le thread UDP, lu par le rendu :
 * tout accès se fait sous synchronized(state).
 */
class TelemetryState {
    // Télémétrie de la voiture regardée
    var viewedIndex = 0
    var gear = 0
    var numGears = 0
    var rpm = 0
    var maxRpm = 0
    var speedKmh = 0f
    var throttle = 0f
    var brake = 0f
    var pitLimiter = false
    var oilTemp = 0
    var waterTemp = 0
    var fuelLiters = 0f
    var fuelCapacity = 0f
    val tyreTemp = IntArray(4) // °C, ordre AVG AVD ARG ARD
    val tyreWear = FloatArray(4) // 0 = neuf, 1 = usé
    val brakeTemp = IntArray(4) // °C

    // Classement / chronos
    var numParticipants = 0
    var position = 0
    var sector = 0
    var flagColour = 0
    var gapAhead = -1f
    var gapBehind = -1f
    var eventTimeRemaining = -1f
    var currentLap = 0
    var currentLapTime = -1f
    var lapDistance = 0
    var lapInvalid = false
    var lastLapTime = -1f
    var bestLapTime = -1f

    // Session
    var trackName = ""
    var trackLength = 0f
    var lapsInEvent = 0
    var timedEvent = false
    var gameState = 0
    var sessionState = 0

    // Valeurs calculées
    var fuelPerLap = -1f
    var delta = Float.NaN

    // Santé du flux
    var lastPacketMs = 0L
    var telemetryHz = 0
    private var hzCount = 0
    private var hzWindowStart = 0L

    private val lapTimes = LapReference()
    private val fuelHistory = FloatArray(FUEL_HISTORY)
    private var fuelHistoryCount = 0
    private var lapStartFuel = -1f

    // Dernier paquet reçu au format "Project CARS 1" (mauvais réglage dans AMS2)
    var lastLegacyPacketMs = 0L

    fun hasData(nowMs: Long) = lastPacketMs != 0L && nowMs - lastPacketMs < STALE_MS

    fun legacyProtocol(nowMs: Long) = lastLegacyPacketMs != 0L && nowMs - lastLegacyPacketMs < STALE_MS

    fun onLegacyProtocol() {
        lastLegacyPacketMs = SystemClock.elapsedRealtime()
    }

    fun onTelemetry() {
        val now = SystemClock.elapsedRealtime()
        lastPacketMs = now
        hzCount++
        if (now - hzWindowStart >= 1000) {
            telemetryHz = (hzCount * 1000 / (now - hzWindowStart).coerceAtLeast(1)).toInt()
            hzCount = 0
            hzWindowStart = now
        }
    }

    fun setTrack(location: String, variation: String) {
        val name = if (variation.isEmpty()) location else "$location – $variation"
        if (name == trackName) return
        trackName = name
        lapTimes.clearReference()
        fuelHistoryCount = 0
    }

    fun updateLap(lap: Int, time: Float, distance: Int, invalid: Boolean) {
        if (lap != currentLap) {
            if (lap == currentLap + 1 && currentLap > 0) completeLap()
            lapTimes.startLap()
            lapStartFuel = fuelLiters
        }
        currentLap = lap
        currentLapTime = time
        lapDistance = distance
        lapInvalid = invalid
        lapTimes.record(distance, time)
        delta = lapTimes.delta(distance, time)
    }

    /** Appelé au passage de la ligne, avec les valeurs du tour qui vient de se terminer. */
    private fun completeLap() {
        val complete = lapTimes.coverage(trackLength) >= MIN_LAP_COVERAGE
        if (!complete) return
        if (!lapInvalid) lapTimes.offerReference(currentLapTime)
        val used = lapStartFuel - fuelLiters
        if (lapStartFuel > 0f && used > 0f) {
            fuelHistory[fuelHistoryCount % FUEL_HISTORY] = used
            fuelHistoryCount++
            val n = minOf(fuelHistoryCount, FUEL_HISTORY)
            fuelPerLap = fuelHistory.take(n).sum() / n
        }
    }

    companion object {
        private const val STALE_MS = 3000L
        private const val FUEL_HISTORY = 5
        private const val MIN_LAP_COVERAGE = 0.8f
    }
}
