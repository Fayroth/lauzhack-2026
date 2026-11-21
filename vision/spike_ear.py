"""THROWAWAY spike for issue #25 (owner: Isma): does the LogiInsight face mesh give a usable EAR?

Reads the 478-point mesh from LogiInsight (MediaPipe topology, pixel coords, per the SDK docs),
computes EAR from the standard MediaPipe eye indices, and prints fps / stability / blink stats.
No image is read or stored: only landmark numbers are used.

Run (from anywhere; the SDK folder must be on PYTHONPATH so `logiwebcam` imports):
    export LOGIWEBCAM_USER=... LOGIWEBCAM_PASSWORD=...        # never in code / files
    PYTHONPATH=~/Downloads/logiinsight_api uv run --project ~/Downloads/logiinsight_api \
        python vision/spike_ear.py --label rgb_nolunettes --duration 60

Protocol per run: keep eyes open for the calibration, then do the scenario
(10 voluntary blinks / half-closed eyes / glasses / slight profile) and read the summary.
Do NOT run the camera AI continuously for ~10 min (overheating warning in the SDK docs).
"""

import argparse
import csv
import statistics
import time

import numpy as np
from logiwebcam import LogiWebcamClient  # from the LogiInsight SDK (not our code)

# Standard MediaPipe Face Mesh indices (p1..p6: corner, upper, upper, corner, lower, lower).
# The SDK docs say its topology follows MediaPipe; THIS SCRIPT IS WHAT VERIFIES that for the eyes.
RIGHT_EYE = (33, 160, 158, 133, 153, 144)
LEFT_EYE = (362, 385, 387, 263, 373, 380)


def eye_ear(mesh, idx) -> float:
    p = [np.asarray(mesh[i][:2], dtype=float) for i in idx]
    horiz = np.linalg.norm(p[0] - p[3])
    if horiz < 1e-6:
        return float("nan")
    return float((np.linalg.norm(p[1] - p[5]) + np.linalg.norm(p[2] - p[4])) / (2 * horiz))


def main() -> None:
    ap = argparse.ArgumentParser(description="LogiInsight EAR spike (issue #25)")
    ap.add_argument("--duration", type=float, default=60, help="seconds after calibration")
    ap.add_argument("--calib", type=float, default=4, help="seconds, eyes open, to get the baseline")
    ap.add_argument("--ratio", type=float, default=0.70, help="closed if EAR < ratio * baseline")
    ap.add_argument("--label", default="run", help="scenario name, shown in the summary")
    ap.add_argument("--csv", default=None, help="optional path to dump t,earL,earR,ear")
    args = ap.parse_args()
    if args.duration + args.calib > 300:
        print("[warn] >5 min of continuous AI: the SDK docs warn about overheating from ~10 min")

    rows = []  # (t, earL, earR, ear) for each NEW mesh frame
    polls = polls_with_data = polls_no_mesh = 0
    last_key = None
    go_said = False

    with LogiWebcamClient() as cam:  # reads LOGIWEBCAM_USER / LOGIWEBCAM_PASSWORD
        cam.set_ai_config(enable_face_detection=True, enable_face_mesh=True)
        time.sleep(1.0)  # camera rereads its config ~2x/s
        print(f"[{args.label}] calibration {args.calib:.0f}s: eyes OPEN, look at the screen...")
        t0 = time.time()
        next_print = t0
        try:
            while time.time() - t0 < args.calib + args.duration:
                polls += 1
                data = cam.get_latest_ai_data()
                if not data:
                    time.sleep(0.005)
                    continue
                polls_with_data += 1
                faces = data.get("faces") or []
                mesh = faces[0].get("landmarks", []) if faces else []
                if len(mesh) != 478:
                    polls_no_mesh += 1
                    time.sleep(0.005)
                    continue
                key = (tuple(mesh[1]), tuple(mesh[152]))  # dedupe: only count really new results
                if key == last_key:
                    time.sleep(0.005)
                    continue
                last_key = key
                t = time.time() - t0
                el, er = eye_ear(mesh, LEFT_EYE), eye_ear(mesh, RIGHT_EYE)
                rows.append((t, el, er, (el + er) / 2))
                if t >= args.calib and not go_said:
                    print(f"[{args.label}] >>> GO: do the scenario now <<<")
                    go_said = True
                if time.time() >= next_print:
                    tag = "CALIB" if t < args.calib else "TEST "
                    left = max(0.0, args.calib + args.duration - t)
                    print(f"  {tag} t={t:5.1f}s  EAR={rows[-1][3]:.3f}  (L {el:.3f} / R {er:.3f})  [reste {left:.0f}s]")
                    next_print = time.time() + 0.5
        except KeyboardInterrupt:
            print("[interrupted] Ctrl+C: summary on the frames collected so far")

    if args.csv:
        with open(args.csv, "w", newline="") as f:
            w = csv.writer(f)
            w.writerow(["t", "earL", "earR", "ear"])
            w.writerows(rows)

    calib = [r for r in rows if r[0] < args.calib]
    test = [r for r in rows if r[0] >= args.calib]
    print(f"\n===== SUMMARY [{args.label}] =====")
    if len(calib) < 10 or len(test) < 10:
        print("Not enough mesh frames (face lost? mesh empty?). Check lighting / camera / credentials.")
        print(f"mesh frames: calib={len(calib)} test={len(test)}  polls={polls} no_mesh_polls={polls_no_mesh}")
        return

    base = statistics.median(r[3] for r in calib)
    base_std = statistics.pstdev(r[3] for r in calib)
    dur = test[-1][0] - test[0][0]
    fps = len(test) / dur if dur > 0 else float("nan")
    asym = statistics.mean(abs(r[1] - r[2]) for r in calib)

    thr = args.ratio * base
    blinks, closed_start, last_t = [], None, None
    for t, _, _, e in test:
        if e < thr and closed_start is None:
            closed_start = t
        elif e >= thr and closed_start is not None:
            d = (t - closed_start) * 1000
            if d >= 50:  # ignore 1-frame glitches
                blinks.append(d)
            closed_start = None
        last_t = t

    def below(frac: float) -> float:
        return 100 * sum(1 for r in test if r[3] < frac * base) / len(test)

    print(f"mesh fps (new results/s): {fps:.1f}   frames: {len(test)}   "
          f"polls without mesh: {100 * polls_no_mesh / max(polls_with_data, 1):.0f}%")
    print(f"EAR open baseline: {base:.3f}  (std {base_std:.3f} -> {100 * base_std / base:.1f}% jitter, "
          f"L/R asymmetry {asym:.3f})")
    print(f"EAR min in test: {min(r[3] for r in test):.3f}  "
          f"({100 * min(r[3] for r in test) / base:.0f}% of baseline)")
    if blinks:
        print(f"blinks (EAR < {args.ratio:.2f} x baseline, >= 50 ms): {len(blinks)}  "
              f"(median {statistics.median(blinks):.0f} ms, max {max(blinks):.0f} ms)")
    else:
        print(f"blinks (EAR < {args.ratio:.2f} x baseline, >= 50 ms): 0")
    print(f"time below 85% / 70% / 50% of baseline: {below(0.85):.0f}% / {below(0.70):.0f}% / {below(0.50):.0f}%")
    print("Compare 'blinks' with what you really did (e.g. 10 voluntary blinks).")


if __name__ == "__main__":
    main()
