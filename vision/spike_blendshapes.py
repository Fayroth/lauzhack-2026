"""THROWAWAY spike (owner: Isma): feed the LogiInsight on-camera mesh into MediaPipe's blendshape model.

Idea: the camera computes the 478-pt mesh on its NPU; MediaPipe's blendshape model is a small MLP-Mixer whose
input is NOT an image but 146 of those 478 landmarks (x, y). So we can run it on the hardware mesh and get
52 ARKit-like coefficients (eyeBlinkLeft/Right, eyeSquint*, jawOpen, ...) with no image ever touching the PC.

Reused (not our code): MediaPipe Face Landmarker bundle (Apache-2.0) for the blendshape weights; the 146-index
subset is copied from mediapipe/tasks/cc/vision/face_landmarker/face_blendshapes_graph.cc; LiteRT runs it.

Setup (Windows cmd, once):
    curl -L -o vision\\face_landmarker.task https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task
Run (python 3.12: ai-edge-litert has no 3.15 wheel; credentials from env as usual):
    set PYTHONPATH=%USERPROFILE%\\Downloads\\logiinsight_api
    uv run --python 3.12 --with ai-edge-litert --with paramiko --with numpy --with opencv-python ^
        python vision\\spike_blendshapes.py --label bs_clignements --duration 30
"""

import argparse
import statistics
import time
import zipfile

import numpy as np
from ai_edge_litert.interpreter import Interpreter  # LiteRT (TFLite) runtime, not our code
from logiwebcam import LogiWebcamClient  # LogiInsight SDK, not our code

from spike_ear import LEFT_EYE, RIGHT_EYE, eye_ear  # our spike #25 code

# Copied from MediaPipe face_blendshapes_graph.cc (kLandmarksSubsetIdxs): the model's 146 input landmarks.
SUBSET = [
    0, 1, 4, 5, 6, 7, 8, 10, 13, 14, 17, 21, 33, 37, 39, 40, 46, 52, 53, 54, 55, 58, 61, 63, 65, 66, 67, 70,
    78, 80, 81, 82, 84, 87, 88, 91, 93, 95, 103, 105, 107, 109, 127, 132, 133, 136, 144, 145, 146, 148, 149,
    150, 152, 153, 154, 155, 157, 158, 159, 160, 161, 162, 163, 168, 172, 173, 176, 178, 181, 185, 191, 195,
    197, 234, 246, 249, 251, 263, 267, 269, 270, 276, 282, 283, 284, 285, 288, 291, 293, 295, 296, 297, 300,
    308, 310, 311, 312, 314, 317, 318, 321, 323, 324, 332, 334, 336, 338, 356, 361, 362, 365, 373, 374, 375,
    377, 378, 379, 380, 381, 382, 384, 385, 386, 387, 388, 389, 390, 397, 398, 400, 402, 405, 409, 415, 454,
    466, 468, 469, 470, 471, 472, 473, 474, 475, 476, 477,
]
# Same file, kBlendshapeNames order. Only the ones we care about are named here.
IDX = {"eyeBlinkLeft": 9, "eyeBlinkRight": 10, "eyeSquintLeft": 19, "eyeSquintRight": 20, "jawOpen": 25}


def load_model(task_path: str) -> Interpreter:
    with zipfile.ZipFile(task_path) as z:  # the .task bundle is a zip of .tflite files
        names = [n for n in z.namelist() if "blendshape" in n.lower()]
        if not names:
            raise SystemExit(f"no blendshape model inside {task_path}: {z.namelist()}")
        interp = Interpreter(model_content=z.read(names[0]))
    interp.allocate_tensors()
    inp, out = interp.get_input_details()[0], interp.get_output_details()
    print(f"[model] {names[0]}  input {inp['shape'].tolist()} {inp['dtype'].__name__}  "
          f"outputs {[o['shape'].tolist() for o in out]}")
    if list(inp["shape"][-2:]) != [146, 2]:
        raise SystemExit("unexpected input shape: the 146x2 assumption is wrong, stop here")
    return interp


def blendshapes(interp: Interpreter, mesh) -> np.ndarray:
    # MediaPipe feeds the 146 points as (x * image_width, y * image_height), i.e. pixels.
    # LogiInsight landmarks are already in stream pixels, so we pass x, y as is.
    x = np.asarray([mesh[i][:2] for i in SUBSET], dtype=np.float32)[None]
    interp.set_tensor(interp.get_input_details()[0]["index"], x)
    interp.invoke()
    outs = [interp.get_tensor(o["index"]).reshape(-1) for o in interp.get_output_details()]
    return next(o for o in outs if o.size == 52)


def count_events(ts, vals, on, off, min_ms=50):
    """Hysteresis: event starts when v >= on, ends when v < off. Returns durations in ms."""
    events, start = [], None
    for t, v in zip(ts, vals):
        if start is None and v >= on:
            start = t
        elif start is not None and v < off:
            if (t - start) * 1000 >= min_ms:
                events.append((t - start) * 1000)
            start = None
    return events


def main() -> None:
    ap = argparse.ArgumentParser(description="LogiInsight mesh -> MediaPipe blendshapes spike")
    ap.add_argument("--model", default="vision/face_landmarker.task")
    ap.add_argument("--duration", type=float, default=30)
    ap.add_argument("--calib", type=float, default=4)
    ap.add_argument("--label", default="run")
    args = ap.parse_args()

    interp = load_model(args.model)
    rows, infer_ms, last_key, go_said = [], [], None, False  # rows: t, blinkL, blinkR, squint, jaw, ear
    with LogiWebcamClient() as cam:
        cam.set_ai_config(enable_face_detection=True, enable_face_mesh=True)
        time.sleep(1.0)
        print(f"[{args.label}] calibration {args.calib:.0f}s: eyes OPEN, face the camera...")
        t0 = time.time()
        next_print = t0
        try:
            while time.time() - t0 < args.calib + args.duration:
                data = cam.get_latest_ai_data()
                faces = (data or {}).get("faces") or []
                mesh = faces[0].get("landmarks", []) if faces else []
                if len(mesh) != 478:
                    time.sleep(0.005)
                    continue
                key = (tuple(mesh[1]), tuple(mesh[152]))
                if key == last_key:
                    time.sleep(0.005)
                    continue
                last_key = key
                t = time.time() - t0
                s = time.perf_counter()
                bs = blendshapes(interp, mesh)
                infer_ms.append((time.perf_counter() - s) * 1000)
                ear = (eye_ear(mesh, LEFT_EYE) + eye_ear(mesh, RIGHT_EYE)) / 2
                rows.append((t, bs[IDX["eyeBlinkLeft"]], bs[IDX["eyeBlinkRight"]],
                             (bs[IDX["eyeSquintLeft"]] + bs[IDX["eyeSquintRight"]]) / 2, bs[IDX["jawOpen"]], ear))
                if t >= args.calib and not go_said:
                    print(f"[{args.label}] >>> GO: do the scenario now <<<")
                    go_said = True
                if time.time() >= next_print:
                    r = rows[-1]
                    print(f"  t={t:5.1f}s  blink L {r[1]:.2f} R {r[2]:.2f}  squint {r[3]:.2f}  "
                          f"jaw {r[4]:.2f}  | EAR {r[5]:.3f}  [reste {max(0, args.calib + args.duration - t):.0f}s]")
                    next_print = time.time() + 0.5
        except KeyboardInterrupt:
            print("[interrupted] summary on frames so far")

    calib = [r for r in rows if r[0] < args.calib]
    test = [r for r in rows if r[0] >= args.calib]
    print(f"\n===== SUMMARY [{args.label}] =====")
    if len(calib) < 10 or len(test) < 10:
        print(f"Not enough mesh frames: calib={len(calib)} test={len(test)}")
        return
    ts = [r[0] for r in test]
    blink = [(r[1] + r[2]) / 2 for r in test]
    base_ear = statistics.median(r[5] for r in calib)
    closure = [1 - r[5] / base_ear for r in test]  # EAR turned into a 0..1-ish closure, for comparison
    open_blink = [(r[1] + r[2]) / 2 for r in calib]
    print(f"inference: median {statistics.median(infer_ms):.2f} ms/frame on CPU, {len(test)} test frames")
    print(f"eyes open (calib): blendshape eyeBlink mean {statistics.mean(open_blink):.2f} "
          f"(std {statistics.pstdev(open_blink):.2f}), L/R diff {statistics.mean(abs(r[1] - r[2]) for r in calib):.2f}")
    print(f"eyeBlink range in test: {min(blink):.2f} .. {max(blink):.2f}")
    bs_ev = count_events(ts, blink, on=0.5, off=0.35)
    ear_ev = count_events(ts, closure, on=0.30, off=0.20)
    for name, ev in (("blendshape eyeBlink >= 0.5", bs_ev), ("EAR < 70% baseline", ear_ev)):
        extra = f"(median {statistics.median(ev):.0f} ms, max {max(ev):.0f} ms)" if ev else ""
        print(f"blinks via {name:27s}: {len(ev):3d} {extra}")
    print(f"time with eyeBlink >= 0.5 / >= 0.8: {100 * sum(b >= 0.5 for b in blink) / len(blink):.0f}% / "
          f"{100 * sum(b >= 0.8 for b in blink) / len(blink):.0f}%   (PERCLOS-like)")
    print(f"jawOpen max {max(r[4] for r in test):.2f}  (yawn candidate if it goes > ~0.5)")
    print("Compare both blink counts with what you really did.")


if __name__ == "__main__":
    main()
