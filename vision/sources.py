"""Face mesh sources behind one interface (owner: Isma).

FaceMeshSource.read() -> (status, mesh, blendshapes)
  status: "new" (a new mesh), "stale" (no new result yet), "noface"
  mesh: 478 x [x, y, z] in pixels (MediaPipe topology), blendshapes: {"blink": 0..1, "jaw": 0..1} or None
No image is ever stored or sent: camera frames (MediaPipe path) live only in memory for one detection.

Reused (see docs/BUILD_LOG.md): LogiInsight SDK (`logiwebcam`), MediaPipe Face Landmarker + its blendshape model,
LiteRT (`ai-edge-litert`) to run that blendshape model on the LogiInsight mesh, OpenCV for webcam capture.
"""

from __future__ import annotations

import threading
import time
import zipfile
from pathlib import Path

DEFAULT_MODEL = Path(__file__).parent / "models" / "face_landmarker.task"
MODEL_URL = ("https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/"
             "float16/latest/face_landmarker.task")

# MediaPipe face_blendshapes_graph.cc (kLandmarksSubsetIdxs): the blendshape model's 146 input landmarks.
BS_SUBSET = [
    0, 1, 4, 5, 6, 7, 8, 10, 13, 14, 17, 21, 33, 37, 39, 40, 46, 52, 53, 54, 55, 58, 61, 63, 65, 66, 67, 70,
    78, 80, 81, 82, 84, 87, 88, 91, 93, 95, 103, 105, 107, 109, 127, 132, 133, 136, 144, 145, 146, 148, 149,
    150, 152, 153, 154, 155, 157, 158, 159, 160, 161, 162, 163, 168, 172, 173, 176, 178, 181, 185, 191, 195,
    197, 234, 246, 249, 251, 263, 267, 269, 270, 276, 282, 283, 284, 285, 288, 291, 293, 295, 296, 297, 300,
    308, 310, 311, 312, 314, 317, 318, 321, 323, 324, 332, 334, 336, 338, 356, 361, 362, 365, 373, 374, 375,
    377, 378, 379, 380, 381, 382, 384, 385, 386, 387, 388, 389, 390, 397, 398, 400, 402, 405, 409, 415, 454,
    466, 468, 469, 470, 471, 472, 473, 474, 475, 476, 477,
]
BLINK_L, BLINK_R, JAW_OPEN = 9, 10, 25  # kBlendshapeNames order


class SourceUnavailable(RuntimeError):
    pass


class BlendshapeModel:
    """MediaPipe's blendshape MLP-Mixer run on any 478-pt mesh (input: 146 x (x, y) in pixels)."""

    def __init__(self, task_path: Path) -> None:
        import numpy as np
        from ai_edge_litert.interpreter import Interpreter
        self.np = np
        with zipfile.ZipFile(task_path) as z:
            name = next(n for n in z.namelist() if "blendshape" in n.lower())
            self.interp = Interpreter(model_content=z.read(name))
        self.interp.allocate_tensors()
        self.inp = self.interp.get_input_details()[0]
        if list(self.inp["shape"][-2:]) != [146, 2]:
            raise ValueError(f"unexpected blendshape input shape {self.inp['shape']}")

    def __call__(self, mesh) -> dict:
        x = self.np.asarray([mesh[i][:2] for i in BS_SUBSET], dtype=self.np.float32)[None]
        self.interp.set_tensor(self.inp["index"], x)
        self.interp.invoke()
        out = next(o for o in (self.interp.get_tensor(d["index"]).reshape(-1)
                               for d in self.interp.get_output_details()) if o.size == 52)
        return {"blink": float(out[BLINK_L] + out[BLINK_R]) / 2, "jaw": float(out[JAW_OPEN])}


class LogiInsightSource:
    """On-camera face mesh of the Logitech prototype (LogiInsight SDK, RGB stream)."""

    name = "logiinsight-rgb"

    def __init__(self, model_path: Path | None = DEFAULT_MODEL, rgb_device: int | None = None) -> None:
        try:
            from logiwebcam import LogiWebcamClient
        except ImportError as e:
            raise SourceUnavailable(f"LogiInsight SDK not importable ({e}); add the SDK folder to PYTHONPATH") from e
        try:
            self.cam = LogiWebcamClient()  # reads LOGIWEBCAM_USER / LOGIWEBCAM_PASSWORD from the env
            self.cam.__enter__()
            self.cam.set_ai_config(enable_face_detection=True, enable_face_mesh=True)
        except Exception as e:  # SSH / network / credentials
            raise SourceUnavailable(f"LogiInsight camera not reachable ({type(e).__name__}: {e})") from e
        self.last_key = None
        self.last_new = time.time()
        self.started = time.time()
        self.warned_heat = False
        self.grabber = _RgbGrabber(rgb_device) if rgb_device is not None else None
        deadline = time.time() + 4.0  # camera rereads its config ~2x/s
        while time.time() < deadline:
            if self.cam.get_latest_ai_data():
                break
            time.sleep(0.1)
        else:
            self.close()
            raise SourceUnavailable("LogiInsight: no AI results within 4 s (is the RGB stream being read?)")
        self.blendshapes = _load_blendshapes(model_path)

    def read(self):
        if not self.warned_heat and time.time() - self.started > 9 * 60:
            print("[vision] WARNING: camera AI on for 9 min; the SDK docs warn about overheating from ~10 min")
            self.warned_heat = True
        data = self.cam.get_latest_ai_data()
        faces = (data or {}).get("faces") or []
        mesh = faces[0].get("landmarks", []) if faces else []
        if len(mesh) != 478:
            # results with an empty mesh happen even with a face (SDK docs): only "noface" if it lasts
            return ("noface", None, None) if not faces or time.time() - self.last_new > 0.5 else ("stale", None, None)
        key = (tuple(mesh[1]), tuple(mesh[152]))
        if key == self.last_key:
            if time.time() - self.last_new > 3.0:
                raise SourceUnavailable("LogiInsight results frozen for 3 s")
            return "stale", None, None
        self.last_key, self.last_new = key, time.time()
        return "new", mesh, (self.blendshapes(mesh) if self.blendshapes else None)

    def close(self) -> None:
        if self.grabber:
            self.grabber.stop()
        try:
            self.cam.__exit__(None, None, None)
        except Exception:
            pass


class MediaPipeSource:
    """Fallback: MediaPipe Face Landmarker (mesh + blendshapes) on any webcam via OpenCV."""

    name = "mediapipe"

    def __init__(self, model_path: Path = DEFAULT_MODEL, camera: int = 0) -> None:
        if not Path(model_path).exists():
            raise SourceUnavailable(f"MediaPipe model missing: download it to {model_path} from {MODEL_URL}")
        try:
            import cv2
            import mediapipe as mp
            from mediapipe.tasks.python import BaseOptions
            from mediapipe.tasks.python.vision import FaceLandmarker, FaceLandmarkerOptions, RunningMode
        except ImportError as e:
            raise SourceUnavailable(f"mediapipe/opencv not installed ({e})") from e
        self.cv2, self.mp = cv2, mp
        self.cap = cv2.VideoCapture(camera)
        if not self.cap.isOpened():
            raise SourceUnavailable(f"webcam {camera} cannot be opened (camera permission?)")
        self.lm = FaceLandmarker.create_from_options(FaceLandmarkerOptions(
            base_options=BaseOptions(model_asset_path=str(model_path)),
            running_mode=RunningMode.VIDEO, num_faces=1, output_face_blendshapes=True))
        self.t0 = time.monotonic()
        self.last_ts = -1

    def read(self):
        ok, frame = self.cap.read()
        if not ok:
            raise SourceUnavailable("webcam stopped delivering frames")
        h, w = frame.shape[:2]
        rgb = self.cv2.cvtColor(frame, self.cv2.COLOR_BGR2RGB)
        del frame
        ts = max(self.last_ts + 1, int((time.monotonic() - self.t0) * 1000))  # must strictly increase
        self.last_ts = ts
        res = self.lm.detect_for_video(self.mp.Image(image_format=self.mp.ImageFormat.SRGB, data=rgb), ts)
        del rgb  # the image is gone; only numbers below
        if not res.face_landmarks:
            return "noface", None, None
        mesh = [[p.x * w, p.y * h, p.z * w] for p in res.face_landmarks[0]]  # same pixel units as LogiInsight
        bs = None
        if res.face_blendshapes:
            c = {b.category_name: b.score for b in res.face_blendshapes[0]}
            bs = {"blink": (c.get("eyeBlinkLeft", 0) + c.get("eyeBlinkRight", 0)) / 2, "jaw": c.get("jawOpen", 0)}
        return "new", mesh, bs

    def close(self) -> None:
        self.lm.close()
        self.cap.release()


class _RgbGrabber:
    """Keeps the prototype's RGB stream open (AI results only update while it is read). Frames are dropped."""

    def __init__(self, device: int) -> None:
        import cv2
        self.cap = cv2.VideoCapture(device)
        self.running = True
        threading.Thread(target=self._loop, daemon=True).start()

    def _loop(self) -> None:
        while self.running:
            self.cap.grab()  # grab without decoding: nothing is kept

    def stop(self) -> None:
        self.running = False
        self.cap.release()


def _load_blendshapes(model_path: Path | None):
    if model_path is None or not Path(model_path).exists():
        print(f"[vision] no blendshape model at {model_path}: eye closure from EAR + personal baseline")
        return None
    try:
        return BlendshapeModel(Path(model_path))
    except Exception as e:  # ai-edge-litert missing (e.g. Python 3.15) or bad file
        print(f"[vision] blendshape model not usable ({type(e).__name__}: {e}): eye closure from EAR")
        return None


def open_source(kind: str, model_path: Path, camera: int, rgb_device: int | None):
    """kind: auto | logiinsight | mediapipe. auto = LogiInsight first, MediaPipe if it is unavailable."""
    order = {"auto": ["logiinsight", "mediapipe"], "logiinsight": ["logiinsight"], "mediapipe": ["mediapipe"]}[kind]
    errors = []
    for k in order:
        try:
            if k == "logiinsight":
                return LogiInsightSource(model_path, rgb_device)
            return MediaPipeSource(model_path, camera)
        except SourceUnavailable as e:
            print(f"[vision] {k} unavailable: {e}")
            errors.append(f"{k}: {e}")
    raise SourceUnavailable("; ".join(errors))
