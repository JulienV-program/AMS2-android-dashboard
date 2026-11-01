package ams2.dash.udp

import ams2.dash.model.TelemetryState
import java.nio.ByteBuffer

/**
 * Décodeur du protocole UDP "Project CARS 2" émis par AMS2.
 * Offsets tirés de docs/SMS_UDP_Definitions.hpp (structures #pragma pack(1), little endian).
 */
object Packets {
    const val PORT = 5606

    private const val TYPE_TELEMETRY = 0
    private const val TYPE_RACE = 1
    private const val TYPE_TIMINGS = 3
    private const val TYPE_GAME_STATE = 4
    private const val TYPE_TIME_STATS = 7

    private const val SIZE_TELEMETRY = 559
    private const val SIZE_RACE = 308
    private const val SIZE_TIMINGS = 1063
    private const val SIZE_GAME_STATE = 22
    private const val SIZE_TIME_STATS = 1040

    private const val MAX_PARTICIPANTS = 32
    private const val PARTICIPANT_SIZE = 32

    // Tailles des paquets du protocole "Project CARS 1", que l'on ne décode pas
    private val LEGACY_SIZES = intArrayOf(1367, 1347, 1028)

    fun parse(b: ByteBuffer, len: Int, s: TelemetryState) {
        if (len in LEGACY_SIZES) {
            s.onLegacyProtocol()
            return
        }
        if (len < 12) return
        when (u8(b, 10)) {
            TYPE_TELEMETRY -> if (len >= SIZE_TELEMETRY) telemetry(b, s)
            TYPE_RACE -> if (len >= SIZE_RACE) race(b, s)
            TYPE_TIMINGS -> if (len >= SIZE_TIMINGS) timings(b, s)
            TYPE_GAME_STATE -> if (len >= SIZE_GAME_STATE) gameState(b, s)
            TYPE_TIME_STATS -> if (len >= SIZE_TIME_STATS) timeStats(b, s)
        }
    }

    private fun telemetry(b: ByteBuffer, s: TelemetryState) {
        s.viewedIndex = b.get(12).toInt()
        s.pitLimiter = u8(b, 17) and 0x08 != 0 // CAR_SPEED_LIMITER
        s.oilTemp = i16(b, 18)
        s.waterTemp = i16(b, 22)
        s.fuelCapacity = u8(b, 28).toFloat()
        s.brake = u8(b, 29) / 255f
        s.throttle = u8(b, 30) / 255f
        s.fuelLiters = b.getFloat(32) * s.fuelCapacity
        s.speedKmh = b.getFloat(36) * 3.6f
        s.rpm = u16(b, 40)
        s.maxRpm = u16(b, 42)
        val gears = u8(b, 45)
        s.gear = (gears and 0x0F).let { if (it == 15) -1 else it }
        s.numGears = gears shr 4
        for (i in 0 until 4) {
            s.tyreTemp[i] = u8(b, 176 + i)
            s.tyreWear[i] = u8(b, 196 + i) / 255f
            s.brakeTemp[i] = i16(b, 208 + i * 2)
        }
        s.onTelemetry()
    }

    private fun race(b: ByteBuffer, s: TelemetryState) {
        s.trackLength = b.getFloat(44)
        s.setTrack(cstr(b, 48, 64), cstr(b, 112, 64))
        val laps = u16(b, 304)
        s.timedEvent = laps and 0x8000 != 0
        s.lapsInEvent = laps and 0x7FFF
    }

    private fun timings(b: ByteBuffer, s: TelemetryState) {
        s.numParticipants = b.get(12).toInt()
        s.eventTimeRemaining = b.getFloat(17)
        s.gapAhead = b.getFloat(21)
        s.gapBehind = b.getFloat(25)
        val i = s.viewedIndex
        if (i !in 0 until MAX_PARTICIPANTS) return
        val o = 33 + i * PARTICIPANT_SIZE
        s.position = u8(b, o + 14) and 0x7F
        s.sector = u8(b, o + 15) and 0x07
        s.flagColour = u8(b, o + 16) and 0x07
        s.updateLap(
            lap = u8(b, o + 21),
            time = b.getFloat(o + 22),
            distance = u16(b, o + 12),
            invalid = u8(b, o + 20) and 0x08 != 0,
        )
    }

    private fun gameState(b: ByteBuffer, s: TelemetryState) {
        val v = u8(b, 14)
        s.gameState = v and 0x07
        s.sessionState = (v shr 4) and 0x07
    }

    private fun timeStats(b: ByteBuffer, s: TelemetryState) {
        val i = s.viewedIndex
        if (i !in 0 until MAX_PARTICIPANTS) return
        val o = 16 + i * PARTICIPANT_SIZE
        s.bestLapTime = b.getFloat(o)
        s.lastLapTime = b.getFloat(o + 4)
    }

    private fun u8(b: ByteBuffer, o: Int) = b.get(o).toInt() and 0xFF
    private fun u16(b: ByteBuffer, o: Int) = b.getShort(o).toInt() and 0xFFFF
    private fun i16(b: ByteBuffer, o: Int) = b.getShort(o).toInt()

    private fun cstr(b: ByteBuffer, o: Int, max: Int): String {
        var end = o
        while (end < o + max && b.get(end).toInt() != 0) end++
        return String(b.array(), o, end - o, Charsets.UTF_8)
    }
}
