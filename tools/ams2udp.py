"""Construction des paquets UDP "Project CARS 2" (cf. docs/SMS_UDP_Definitions.hpp) et format d'enregistrement."""
import struct

PORT = 5606

TELEMETRY, RACE, PARTICIPANTS, TIMINGS, GAME_STATE, TIME_STATS = 0, 1, 2, 3, 4, 7
TYPE_NAMES = {0: "telemetry", 1: "race", 2: "participants", 3: "timings", 4: "game_state",
              5: "weather", 6: "vehicle_names", 7: "time_stats", 8: "participant_vehicle_names"}

# Tailles des paquets du protocole "Project CARS 1" (réglage UDP à changer dans AMS2)
PC1_SIZES = (1367, 1347, 1028)

# Fichier d'enregistrement : MAGIC puis une suite de (horodatage f64, longueur u16, données)
REC_MAGIC = b"AMS2REC1"
REC_HEADER = struct.Struct("<dH")


class PacketBuilder:
    def __init__(self):
        self.packet_number = 0
        self.category_numbers = {}

    def _base(self, size, ptype, version):
        buf = bytearray(size)
        self.packet_number += 1
        cat = self.category_numbers[ptype] = self.category_numbers.get(ptype, 0) + 1
        struct.pack_into("<IIBBBB", buf, 0, self.packet_number, cat, 0, 1, ptype, version)
        return buf

    def telemetry(self, *, rpm, max_rpm, gear, num_gears, speed_ms, throttle, brake, fuel_fraction,
                  fuel_capacity, tyre_temps, tyre_wear, brake_temps, limiter=False, viewed=0):
        b = self._base(559, TELEMETRY, 2)
        struct.pack_into("<b", b, 12, viewed)
        struct.pack_into("<B", b, 17, 0x02 | (0x08 if limiter else 0))
        struct.pack_into("<hHh", b, 18, 105, 450, 88)
        struct.pack_into("<BBB", b, 28, fuel_capacity, int(brake * 255), int(throttle * 255))
        struct.pack_into("<ff", b, 32, fuel_fraction, speed_ms)
        struct.pack_into("<HH", b, 40, rpm, max_rpm)
        struct.pack_into("<B", b, 45, (gear & 0x0F) | (num_gears << 4))
        struct.pack_into("<4B", b, 176, *tyre_temps)
        struct.pack_into("<4B", b, 196, *tyre_wear)
        struct.pack_into("<4h", b, 208, *brake_temps)
        return bytes(b)

    def timings(self, *, num_participants, gap_ahead, gap_behind, position, lap, lap_time, lap_distance,
                sector, flag=0, invalid=False, event_time_remaining=-1.0, viewed=0):
        b = self._base(1063, TIMINGS, 3)
        struct.pack_into("<b", b, 12, num_participants)
        struct.pack_into("<ffff", b, 17, event_time_remaining, gap_ahead, gap_behind, 0.0)
        o = 33 + viewed * 32
        struct.pack_into("<H", b, o + 12, int(lap_distance))
        struct.pack_into("<BBB", b, o + 14, position | 0x80, sector, flag)
        struct.pack_into("<BB", b, o + 20, 0x08 if invalid else 0, lap)
        struct.pack_into("<f", b, o + 22, lap_time)
        return bytes(b)

    def race(self, *, track, variation, track_length, laps):
        b = self._base(308, RACE, 1)
        struct.pack_into("<f", b, 44, track_length)
        struct.pack_into("64s64s", b, 48, track.encode(), variation.encode())
        struct.pack_into("<H", b, 304, laps)
        return bytes(b)

    def time_stats(self, *, fastest, last, viewed=0):
        b = self._base(1040, TIME_STATS, 2)
        struct.pack_into("<ff", b, 16 + viewed * 32, fastest, last)
        return bytes(b)

    def game_state(self, *, game_state=2, session_state=5):
        b = self._base(24, GAME_STATE, 2)
        struct.pack_into("<B", b, 14, (game_state & 7) | ((session_state & 7) << 4))
        return bytes(b)
