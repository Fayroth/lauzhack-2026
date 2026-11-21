"""Vision sidecar (owner: Isma).

Face mesh (LogiInsight on-camera mesh, or MediaPipe on any webcam) -> eye metrics -> JSON over WebSocket.
Contract: docs/API.md (update it FIRST if you change the message format).
Privacy: no image ever leaves this process (or is stored); only the numeric features below are sent.

Run:
    python vision/main.py                      # auto: LogiInsight if reachable, else MediaPipe webcam
    python vision/main.py --source mediapipe   # force the webcam fallback
    python vision/main.py --source fake        # fake tiredness curve, no camera (old stub)
"""

import argparse
import asyncio
import json
import math
import random
import time
from pathlib import Path

import websockets

from metrics import EyeMetrics
from sources import DEFAULT_MODEL, SourceUnavailable, open_source

HOST = "localhost"
PORT = 8765
HZ = 10


class FakeSource:
    """Pretends the user slowly gets tired then recovers (period ~2 min)."""

    name = "mock"

    def __init__(self) -> None:
        self.start = time.time()

    def next_features(self) -> dict:
        t = time.time() - self.start
        tired = (1 - math.cos(2 * math.pi * t / 120)) / 2  # 0..1
        ear = 0.30 - 0.10 * tired + random.uniform(-0.01, 0.01)
        return {
            "face": True,
            "ear": round(ear, 3),
            "eyesClosed": ear < 0.21,
            "blinkCount": int(14 + 10 * tired),
            "lastBlinkMs": int(160 + 300 * tired),
            "perclos": round(0.05 + 0.30 * tired, 3),
            "yawn": tired > 0.8 and random.random() < 0.01,
            "headPitch": round(-4.0 - 10 * tired, 1),
            "headYaw": round(random.uniform(-3, 3), 1),
            "source": "mock",
        }


def make_message(features: dict) -> str:
    return json.dumps({"type": "vision", "ts": int(time.time() * 1000), **features})


class Pipeline:
    """Reads the source as fast as it delivers, keeps the latest features; clients get them at HZ."""

    def __init__(self, args) -> None:
        self.args = args
        self.latest: dict | None = None
        self.src = None
        self.metrics = EyeMetrics()
        self.n_new = 0

    def _open(self, kind: str) -> None:
        self.src = open_source(kind, Path(self.args.model), self.args.camera, self.args.rgb_device,
                               blendshapes=not self.args.no_blendshapes)
        self.metrics = EyeMetrics()  # new source = new personal baseline
        bs = "blendshapes" if getattr(self.src, "blendshapes", True) else "EAR + baseline"
        print(f"[vision] source: {self.src.name} (eye closure from {bs}); calibrating ~5 s, keep eyes open")

    def _step(self) -> None:
        try:
            status, mesh, bs = self.src.read()
        except SourceUnavailable as e:
            print(f"[vision] {self.src.name} failed: {e}")
            self.src.close()
            if self.args.source != "auto" or self.src.name == "mediapipe":
                raise
            self._open("mediapipe")  # live fallback, e.g. SSH link dropped
            return
        t = time.monotonic()
        if status == "stale":
            time.sleep(0.005)
            return
        if status == "noface":
            f = self.metrics.no_face(t)
        else:
            self.n_new += 1
            f = self.metrics.update(t, mesh, bs["blink"] if bs else None, bs["jaw"] if bs else None)
        f["source"] = self.src.name
        self.latest = f

    async def run(self) -> None:
        if self.args.source == "fake":
            fake = FakeSource()
            while True:
                self.latest = fake.next_features()
                await asyncio.sleep(1 / HZ)
        await asyncio.to_thread(self._open, self.args.source)
        last_log, n0 = time.monotonic(), 0
        while True:
            await asyncio.to_thread(self._step)
            now = time.monotonic()
            if now - last_log >= 1.0 and self.latest is not None:
                f = self.latest
                state = "CALIB" if not self.metrics.calibrated and f.get("face") else ""
                print(f"[vision] {f['source']} {(self.n_new - n0) / (now - last_log):4.1f} fps  face={f.get('face')} "
                      f"ear={f.get('ear')} closed={f.get('eyesClosed')} blinks/60s={f.get('blinkCount')} "
                      f"last={f.get('lastBlinkMs')}ms perclos={f.get('perclos')} yaw={f.get('headYaw')} "
                      f"yawn={f.get('yawn')} {state}")
                last_log, n0 = now, self.n_new


async def main() -> None:
    parser = argparse.ArgumentParser(description="EnergyGuard vision sidecar")
    parser.add_argument("--port", type=int, default=PORT)
    parser.add_argument("--source", choices=["auto", "logiinsight", "mediapipe", "fake"], default="auto")
    parser.add_argument("--model", default=str(DEFAULT_MODEL), help="MediaPipe face_landmarker.task")
    parser.add_argument("--camera", type=int, default=0, help="webcam index for the MediaPipe fallback")
    parser.add_argument("--rgb-device", type=int, default=None,
                        help="index of the prototype RGB camera to keep its stream open (only if results freeze)")
    parser.add_argument("--no-blendshapes", action="store_true",
                        help="LogiInsight: eye closure from EAR + baseline only (if blendshapes look wrong)")
    args = parser.parse_args()

    pipe = Pipeline(args)

    async def handler(ws):
        print(f"[vision] client connected: {ws.remote_address}")
        try:
            while True:
                if pipe.latest is not None:
                    await ws.send(make_message(pipe.latest))
                await asyncio.sleep(1 / HZ)
        except websockets.ConnectionClosed:
            print("[vision] client disconnected")

    async with websockets.serve(handler, HOST, args.port):
        print(f"[vision] serving ws://{HOST}:{args.port} at {HZ} Hz (source={args.source})")
        await pipe.run()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except SourceUnavailable as e:
        raise SystemExit(f"[vision] no face mesh source available: {e}")
