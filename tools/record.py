#!/usr/bin/env python3
"""Enregistre le flux UDP d'AMS2 dans un fichier, pour le rejouer avec replay.py.

  ./record.py session.ams2rec      (Ctrl+C pour arrêter)

À lancer sur le PC du jeu (ou n'importe quelle machine du réseau local) pendant une session.
"""
import collections
import signal
import socket
import sys
import time

from ams2udp import PORT, REC_HEADER, REC_MAGIC, TYPE_NAMES


def stop(*_):
    raise KeyboardInterrupt


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    signal.signal(signal.SIGTERM, stop)
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    if hasattr(socket, "SO_REUSEPORT"):
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEPORT, 1)
    sock.bind(("", PORT))
    sock.settimeout(1.0)

    counts = collections.Counter()
    sizes = {}
    start = time.monotonic()
    last_print = start
    with open(sys.argv[1], "wb") as f:
        f.write(REC_MAGIC)
        print(f"Écoute sur le port {PORT}… Ctrl+C pour arrêter")
        try:
            while True:
                try:
                    data, _ = sock.recvfrom(2048)
                except socket.timeout:
                    data = None
                now = time.monotonic()
                if data:
                    f.write(REC_HEADER.pack(now - start, len(data)))
                    f.write(data)
                    if len(data) >= 12:
                        ptype = data[10]
                        counts[ptype] += 1
                        sizes[ptype] = (len(data), data[11])
                if now - last_print >= 2:
                    last_print = now
                    summary = "  ".join(f"{TYPE_NAMES.get(t, t)}={n}" for t, n in sorted(counts.items()))
                    print(f"\r{now - start:6.0f} s  {summary or 'aucun paquet'}", end="", flush=True)
        except KeyboardInterrupt:
            pass
    print("\n\nType de paquet : nombre (taille, version)")
    for t, n in sorted(counts.items()):
        size, version = sizes[t]
        print(f"  {TYPE_NAMES.get(t, t):26} {n:7}  ({size} octets, v{version})")


if __name__ == "__main__":
    main()
