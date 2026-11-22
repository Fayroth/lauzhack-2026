"""Quick checks for metrics.py on a synthetic mesh (run: python -m pytest vision/test_metrics.py)."""

from metrics import LEFT_EYE, RIGHT_EYE, EyeMetrics


def mesh_with_ear(ear: float, yaw_dz: float = 0.0):
    m = [[100.0, 100.0, 0.0] for _ in range(478)]
    for idx, x0 in ((RIGHT_EYE, 0.0), (LEFT_EYE, 100.0)):
        c1, u1, u2, c2, l1, l2 = idx
        m[c1], m[c2] = [x0, 0.0, 0.0], [x0 + 40.0, 0.0, 0.0]  # width 40 -> vertical = ear * 40
        h = ear * 40 / 2
        m[u1], m[u2], m[l1], m[l2] = [x0 + 13, -h, 0], [x0 + 27, -h, 0], [x0 + 27, h, 0], [x0 + 13, h, 0]
    m[263][2] = yaw_dz  # depth difference between eye corners -> head yaw
    m[10], m[152] = [70.0, -60.0, 0.0], [70.0, 120.0, 0.0]
    return m


def run(seq, dt=1 / 27):
    em, t, out = EyeMetrics(), 0.0, None
    for ear, n in seq:
        for _ in range(n):
            out = em.update(t, mesh_with_ear(ear))
            t += dt
    return em, out


def test_ten_blinks_counted_and_perclos_low():
    seq = [(0.26, 27 * 6)] + [(0.05, 7), (0.26, 54)] * 10  # 6 s open, then 10 blinks of ~260 ms every 2 s
    em, out = run(seq)
    assert em.calibrated
    assert len(em.blinks) == 10
    assert 20 <= out["blinkCount"] <= 26  # 10 blinks in ~26 s of eye data -> ~23 per minute (extrapolated)
    assert 200 <= out["lastBlinkMs"] <= 320
    assert out["perclos"] < 0.15


def test_long_closure_sets_eyes_closed_and_raises_perclos():
    em, out = run([(0.26, 27 * 6), (0.04, 27 * 3)])
    assert out["eyesClosed"] is True
    assert out["perclos"] > 0.25


def test_half_closed_eyes_are_not_blinks():
    em, out = run([(0.26, 27 * 6), (0.17, 27 * 10)])  # ~65 % of baseline, like spike #25 'miclos'
    assert not em.blinks and out["eyesClosed"] is False


def test_turned_head_drops_eye_fields():
    em = EyeMetrics()
    out = em.update(0.0, mesh_with_ear(0.26, yaw_dz=40.0))  # atan2(40, 140) ~ 16 deg -> still trusted
    assert "ear" in out
    out = em.update(0.04, mesh_with_ear(0.26, yaw_dz=100.0))  # ~ 35 deg -> dropped
    assert "ear" not in out and abs(out["headYaw"]) > 25


def test_oscillating_droop_is_one_closure():
    # bs_miclos test: eyeBlink oscillates 0.45-0.77 while eyes are half closed -> one closure, not 16 blinks
    em, t = EyeMetrics(), 0.0
    for i in range(27 * 6):
        em.update(t, mesh_with_ear(0.26), blink_score=0.05)
        t += 1 / 27
    for i in range(27 * 14):
        out = em.update(t, mesh_with_ear(0.12), blink_score=0.45 + 0.32 * (i % 2))
        t += 1 / 27
    assert out["eyesClosed"] is True and out["blinkCount"] == 0
    for _ in range(27):
        out = em.update(t, mesh_with_ear(0.26), blink_score=0.05)
        t += 1 / 27
    # a 14 s closure is not a blink (it went through eyesClosed = micro-sleep path)
    assert out["eyesClosed"] is False and out["blinkCount"] == 0 and out["lastBlinkMs"] is None


def test_blink_rate_is_extrapolated_during_the_first_minute():
    # 12 blinks/min from the start: the rate must read ~12 at 20 s and at 90 s, not ramp from 0 (engine calibration)
    em, t, rates = EyeMetrics(), 0.0, {}
    for i in range(27 * 90):
        ear = 0.05 if (i % (27 * 5)) < 7 and t > 6 else 0.26  # one ~260 ms blink every 5 s after the EAR calibration
        out = em.update(t, mesh_with_ear(ear))
        rates[round(t)] = out.get("blinkCount")
        t += 1 / 27
    assert rates[10] is None, "no rate before 15 s of eye data"
    assert 9 <= rates[25] <= 15 and 10 <= rates[89] <= 14, rates


def test_short_closures_are_not_eyes_closed():
    em, t = EyeMetrics(), 0.0
    for _ in range(27 * 6):
        em.update(t, mesh_with_ear(0.26))
        t += 1 / 27
    out = em.update(t, mesh_with_ear(0.04))  # one noisy frame
    assert out["eyesClosed"] is False
    for _ in range(3):  # a ~150 ms blink: counted as a blink, never reported as eyes closed
        t += 1 / 27
        out = em.update(t, mesh_with_ear(0.04))
        assert out["eyesClosed"] is False
    for _ in range(27):
        t += 1 / 27
        out = em.update(t, mesh_with_ear(0.26))
    assert len(em.blinks) == 1 and out["lastBlinkMs"] < 250
    for _ in range(27):  # a ~1 s glance at the keyboard: eyes closed (>= 200 ms) but not a blink
        t += 1 / 27
        out = em.update(t, mesh_with_ear(0.04))
    assert out["eyesClosed"] is True
    for _ in range(27):
        t += 1 / 27
        out = em.update(t, mesh_with_ear(0.26))
    assert len(em.blinks) == 1, "a 1 s closure is not a blink"


def test_heavy_lids_raise_perclos():
    # bs_miclos (real camera): eyeBlink 0.45-0.77 for 15 s. P80 saw ~nothing, our 0.5 level must see it.
    def run(level):
        em, t = EyeMetrics(level), 0.0
        for _ in range(27 * 20):
            out = em.update(t, mesh_with_ear(0.26), blink_score=0.05)
            t += 1 / 27
        for i in range(27 * 15):
            out = em.update(t, mesh_with_ear(0.15), blink_score=0.45 + 0.32 * ((i % 7) / 6))
            t += 1 / 27
        return out["perclos"]
    assert run(0.8) < 0.05
    assert run(0.5) > 0.3  # ~15 s closed in the last ~35 s
