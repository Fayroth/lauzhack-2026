"""Eye metrics from a 478-point face mesh (MediaPipe topology). Pure logic: no camera, no image.

Our code. Formulas from the literature (cited in docs/BUILD_LOG.md):
- EAR (Soukupova & Cech 2016) on the standard MediaPipe eye indices, averaged over both eyes.
- PERCLOS (Wierwille 1994, Dinges 1998): fraction of the last 60 s with eyes >= 80 % closed.
- eyesClosed: closure >= 60 % (hysteresis, reopen < 30 %). Micro-sleeps happen with the eyes fully OR partially closed
  (0.5-15 s), so heavy-lidded eyes held > 1 s count, like in the engine's 1 s rule. Blinks: 50 ms .. 1 s closures.
Eye "closure" (0 = open, 1 = closed) comes from the MediaPipe blendshape model (eyeBlinkLeft/Right) when it is
available, otherwise from EAR relative to a personal baseline. Thresholds are our spike choices, not validated values.
"""

from __future__ import annotations

import math
import statistics
from collections import deque

RIGHT_EYE = (33, 160, 158, 133, 153, 144)  # p1..p6: corner, upper, upper, corner, lower, lower
LEFT_EYE = (362, 385, 387, 263, 373, 380)

CALIB_S = 5.0          # first seconds WITH a face: EAR baseline (eyes assumed mostly open)
WINDOW_S = 60.0        # blinkCount and PERCLOS window (docs/API.md)
CLOSE_ON, CLOSE_OFF = 0.6, 0.3  # hysteresis (spike #25 + bs_miclos: tighter = fragmented closures)
PERCLOS_LEVEL = 0.8    # "eyes >= 80 % closed"
EAR_CLOSED_RATIO = 0.25  # EAR path: closure = 1 when EAR <= 25 % of baseline (spike #25: blinks reach ~12-20 %)
MIN_BLINK_MS = 50
MAX_BLINK_MS = 1000    # longer = prolonged closure (micro-sleep path via eyesClosed), not a blink; engine uses 1 s too
MIN_RATE_S = 15.0      # blinkCount: none before 15 s of eye data, then extrapolated to 60 s until the window is full
MAX_YAW_DEG = 25.0     # head turned more than this -> eye signals unreliable (spike #25 profile test). Initial guess.
YAWN_JAW, YAWN_MIN_S = 0.6, 1.5  # blendshape path only


def _dist(a, b) -> float:
    return math.hypot(a[0] - b[0], a[1] - b[1])


def eye_ear(mesh, idx) -> float:
    p = [mesh[i] for i in idx]
    horiz = _dist(p[0], p[3])
    if horiz < 1e-6:
        return float("nan")
    return (_dist(p[1], p[5]) + _dist(p[2], p[4])) / (2 * horiz)


def mean_ear(mesh) -> float:
    return (eye_ear(mesh, LEFT_EYE) + eye_ear(mesh, RIGHT_EYE)) / 2


def head_pose(mesh) -> tuple[float, float]:
    """Rough (pitch, yaw) in degrees from mesh depth: eye corners for yaw, forehead-chin for pitch.

    Uses z, which both LogiInsight and MediaPipe give in the same units as x/y. Approximate, sign not calibrated;
    good enough to tell "facing the screen" from "head clearly turned".
    """
    right, left = mesh[33], mesh[263]
    yaw = math.degrees(math.atan2(left[2] - right[2], left[0] - right[0])) if len(right) > 2 else 0.0
    top, chin = mesh[10], mesh[152]
    pitch = math.degrees(math.atan2(chin[2] - top[2], chin[1] - top[1])) if len(top) > 2 else 0.0
    return round(pitch, 1), round(yaw, 1)


class EyeMetrics:
    """Feed one mesh per new camera result; get the docs/API.md eye fields back."""

    def __init__(self) -> None:
        self.calib: list[float] = []
        self.calib_face_s = 0.0
        self.baseline: float | None = None
        self.closed = False
        self.closed_since: float | None = None
        self.blinks: deque[tuple[float, float]] = deque()   # (end time, duration ms)
        self.last_blink_ms: int | None = None
        self.samples: deque[tuple[float, float, bool]] = deque()  # (t, dt, >= 80 % closed) for PERCLOS
        self.eye_s = 0.0  # seconds of trusted eye data since start (for the blink-rate extrapolation)
        self.last_t: float | None = None
        self.jaw_since: float | None = None
        self.last_ear: float | None = None

    @property
    def calibrated(self) -> bool:
        return self.baseline is not None

    def _closure(self, ear: float, blink_score: float | None) -> float | None:
        if blink_score is not None:
            return min(1.0, max(0.0, blink_score))
        if self.baseline is None:
            return None
        closed_ear = EAR_CLOSED_RATIO * self.baseline
        return min(1.0, max(0.0, (self.baseline - ear) / (self.baseline - closed_ear)))

    def update(self, t: float, mesh, blink_score: float | None = None, jaw_open: float | None = None) -> dict:
        """t: seconds (monotonic). mesh: 478 x [x, y, z] in pixels. blink_score/jaw_open: blendshapes or None."""
        pitch, yaw = head_pose(mesh)
        out = {"face": True, "headPitch": pitch, "headYaw": yaw, "yawn": False}
        dt = 0.0 if self.last_t is None else min(t - self.last_t, 0.2)  # cap gaps (lost frames)
        self.last_t = t

        if abs(yaw) > MAX_YAW_DEG:  # don't trust eyes: send nulls, the engine drops the signals
            self._end_closure(t, count=False)
            return out

        ear = mean_ear(mesh)
        if math.isnan(ear):
            return out
        self.last_ear = ear
        if self.baseline is None:
            self.calib.append(ear)
            self.calib_face_s += dt
            if len(self.calib) >= 10 and self.calib_face_s >= CALIB_S:
                # 75th percentile: robust to blinks during calibration (median was skewed in spike #25)
                self.baseline = statistics.quantiles(self.calib, n=4)[2]

        closure = self._closure(ear, blink_score)
        out["ear"] = round(ear, 3)
        if closure is None:  # EAR path, still calibrating
            return out

        if not self.closed and closure >= CLOSE_ON:
            self.closed, self.closed_since = True, t
        elif self.closed and closure < CLOSE_OFF:
            self._end_closure(t, count=True)

        self.eye_s += dt
        self.samples.append((t, dt, closure >= PERCLOS_LEVEL))
        while self.samples and self.samples[0][0] < t - WINDOW_S:
            self.samples.popleft()
        while self.blinks and self.blinks[0][0] < t - WINDOW_S:
            self.blinks.popleft()
        total = sum(s[1] for s in self.samples)

        if jaw_open is not None and jaw_open >= YAWN_JAW:
            self.jaw_since = self.jaw_since if self.jaw_since is not None else t
        else:
            self.jaw_since = None

        out.update({
            "eyesClosed": self.closed,
            "blinkCount": self._blink_rate(),
            "lastBlinkMs": self.last_blink_ms,
            "perclos": round(sum(s[1] for s in self.samples if s[2]) / total, 3) if total > 0 else 0.0,
            "yawn": self.jaw_since is not None and t - self.jaw_since >= YAWN_MIN_S,
        })
        return out

    def _blink_rate(self) -> int | None:
        """Blinks per minute. While the 60 s window is not full yet, the raw count would ramp up from 0 exactly
        during the engine's 60 s calibration and halve its baseline: extrapolate instead (n * 60 / covered s)."""
        if self.eye_s < MIN_RATE_S:
            return None
        covered = min(WINDOW_S, self.eye_s)
        return round(len(self.blinks) * WINDOW_S / covered)

    def no_face(self, t: float) -> dict:
        self._end_closure(t, count=False)
        self.last_t = None
        return {"face": False}

    def _end_closure(self, t: float, count: bool) -> None:
        if self.closed and count and self.closed_since is not None:
            ms = (t - self.closed_since) * 1000
            if MIN_BLINK_MS <= ms <= MAX_BLINK_MS:
                self.blinks.append((t, ms))
                self.last_blink_ms = int(ms)
        self.closed, self.closed_since = False, None
