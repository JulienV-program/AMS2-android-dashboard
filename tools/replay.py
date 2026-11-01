#!/usr/bin/env python3
"""Envoie des paquets AMS2 vers la tablette, sans lancer le jeu.

  ./replay.py simulate                       voiture simulée, broadcast sur le réseau local
  ./replay.py simulate --host 192.168.1.42   vers une adresse précise
  ./replay.py play session.ams2rec --loop    rejoue un enregistrement fait avec record.py
"""
import argparse
import math
import random
import socket
import sys
import time

from ams2udp import PORT, REC_HEADER, REC_MAGIC, PacketBuilder

TRACK_LENGTH = 2400.0
MAX_RPM = 8500
GEAR_TOP_SPEED = [0, 25, 35, 45, 55, 65, 78]  # m/s au régime max, par rapport


def make_socket():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    return s


def simulate(sock, addr, hz, laps):
    pb = PacketBuilder()
    dt = 1.0 / hz
    distance, lap, lap_time = 0.0, 1, 0.0
    lap_pace = 1.0
    fuel, capacity = 60.0, 100
    best, last = -1.0, -1.0
    invalid, invalid_at = False, None
    gear = 1
    tick = 0
    next_frame = time.monotonic()
    while lap <= laps:
        # Profil de vitesse : 3 lignes droites / 3 freinages par tour
        phase = 2 * math.pi * distance / TRACK_LENGTH * 3
        target = (47 + 25 * math.sin(phase)) * lap_pace
        speed = target
        accel = math.cos(phase)
        throttle = max(0.0, min(1.0, 0.3 + accel))
        brake = max(0.0, min(1.0, -accel - 0.4))

        while gear < 6 and speed > GEAR_TOP_SPEED[gear] * 0.96:
            gear += 1
        while gear > 1 and speed < GEAR_TOP_SPEED[gear - 1] * 0.75:
            gear -= 1
        rpm = int(max(3000, min(MAX_RPM, MAX_RPM * speed / GEAR_TOP_SPEED[gear])))

        distance += speed * dt
        lap_time += dt
        fuel -= 0.00012 * throttle * speed * dt * 10
        if invalid_at is not None and distance > invalid_at:
            invalid = True

        if distance >= TRACK_LENGTH:
            distance -= TRACK_LENGTH
            last = lap_time
            if not invalid and (best < 0 or last < best):
                best = last
            print(f"tour {lap} : {last:.3f}{' (invalide)' if invalid else ''}  meilleur {best:.3f}", flush=True)
            lap += 1
            lap_time = 0.0
            lap_pace = random.uniform(0.97, 1.03)
            invalid = False
            invalid_at = random.uniform(0, TRACK_LENGTH) if random.random() < 0.15 else None
            sock.sendto(pb.time_stats(fastest=best, last=last), addr)

        sector = min(2, int(3 * distance / TRACK_LENGTH))
        flag = 6 if lap == 3 and sector == 1 else 0
        wobble = math.sin(tick / 200)
        tyre_temps = [int(88 + 10 * wobble + d) for d in (4, 6, -2, 0)]
        wear = min(255, int(lap * 6))
        brake_temps = [int(350 + 400 * brake + d) for d in (30, 40, -60, -50)]

        sock.sendto(pb.telemetry(rpm=rpm, max_rpm=MAX_RPM, gear=gear, num_gears=6, speed_ms=speed,
                                 throttle=throttle, brake=brake, fuel_fraction=fuel / capacity,
                                 fuel_capacity=capacity, tyre_temps=tyre_temps, tyre_wear=[wear] * 4,
                                 brake_temps=brake_temps, limiter=lap == 1 and distance < 150), addr)
        sock.sendto(pb.timings(num_participants=18, gap_ahead=1.2 + 0.3 * wobble, gap_behind=0.9 - 0.2 * wobble,
                               position=3, lap=lap, lap_time=lap_time, lap_distance=distance,
                               sector=sector + 1, flag=flag, invalid=invalid), addr)
        if tick % (hz * 2) == 0:
            sock.sendto(pb.race(track="Interlagos", variation="GP", track_length=TRACK_LENGTH, laps=laps), addr)
            sock.sendto(pb.game_state(), addr)
            sock.sendto(pb.time_stats(fastest=best, last=last), addr)

        tick += 1
        next_frame += dt
        time.sleep(max(0.0, next_frame - time.monotonic()))


def play(sock, addr, path, loop):
    with open(path, "rb") as f:
        if f.read(len(REC_MAGIC)) != REC_MAGIC:
            sys.exit(f"{path} n'est pas un enregistrement record.py")
        records = []
        while header := f.read(REC_HEADER.size):
            ts, length = REC_HEADER.unpack(header)
            records.append((ts, f.read(length)))
    if not records:
        sys.exit("enregistrement vide")
    print(f"{len(records)} paquets, {records[-1][0] - records[0][0]:.1f} s")
    while True:
        start = time.monotonic()
        t0 = records[0][0]
        for ts, data in records:
            delay = (ts - t0) - (time.monotonic() - start)
            if delay > 0:
                time.sleep(delay)
            sock.sendto(data, addr)
        if not loop:
            break


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--host", default="255.255.255.255", help="destination (broadcast par défaut)")
    p.add_argument("--port", type=int, default=PORT)
    sub = p.add_subparsers(dest="cmd", required=True)
    sim = sub.add_parser("simulate", help="voiture simulée")
    sim.add_argument("--hz", type=int, default=60)
    sim.add_argument("--laps", type=int, default=15)
    pl = sub.add_parser("play", help="rejouer un enregistrement")
    pl.add_argument("file")
    pl.add_argument("--loop", action="store_true")
    args = p.parse_args()

    sock = make_socket()
    addr = (args.host, args.port)
    try:
        if args.cmd == "simulate":
            simulate(sock, addr, args.hz, args.laps)
        else:
            play(sock, addr, args.file, args.loop)
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
