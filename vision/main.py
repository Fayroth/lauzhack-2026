"""Vision sidecar (owner: Isma).

Webcam / prototype IR -> MediaPipe Face Mesh -> eye metrics -> JSON over WebSocket.
Contract: docs/API.md (update it FIRST if you change the message format).

Privacy: no image ever leaves this process; only the numeric features below are sent.

STUB: for now it streams FAKE frames so the desktop app can be wired up.
Run:  python vision/main.py           (fake frames)
TODO: implement WebcamSource with cv2.VideoCapture + mediapipe FaceMesh (EAR from eye landmarks,
      blink detection, PERCLOS over 60 s, mouth ratio for yawn, head pose).
"""

import argparse
import asyncio
import json
import math
import random
import time

import websockets

HOST = "localhost"
PORT = 8765
HZ = 10


class FakeSource:
    """Pretends the user slowly gets tired then recovers (period ~2 min)."""

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
            "source": "webcam",
        }


def make_message(features: dict) -> str:
    return json.dumps({"type": "vision", "ts": int(time.time() * 1000), **features})


async def stream(ws, source) -> None:
    print(f"[vision] client connected: {ws.remote_address}")
    try:
        while True:
            await ws.send(make_message(source.next_features()))
            await asyncio.sleep(1 / HZ)
    except websockets.ConnectionClosed:
        print("[vision] client disconnected")


async def main() -> None:
    parser = argparse.ArgumentParser(description="EnergyGuard vision sidecar")
    parser.add_argument("--port", type=int, default=PORT)
    args = parser.parse_args()

    source = FakeSource()  # TODO: WebcamSource() / IrSource() when ready

    async def handler(ws):
        await stream(ws, source)

    async with websockets.serve(handler, HOST, args.port):
        print(f"[vision] streaming fake frames on ws://{HOST}:{args.port} at {HZ} Hz")
        await asyncio.Future()  # run forever


if __name__ == "__main__":
    asyncio.run(main())
