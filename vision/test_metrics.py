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
    assert out["blinkCount"] == 10
    assert 200 <= out["lastBlinkMs"] <= 320
    assert out["perclos"] < 0.15


def test_long_closure_sets_eyes_closed_and_raises_perclos():
    em, out = run([(0.26, 27 * 6), (0.04, 27 * 3)])
    assert out["eyesClosed"] is True
    assert out["perclos"] > 0.25


def test_half_closed_eyes_are_not_blinks():
    em, out = run([(0.26, 27 * 6), (0.17, 27 * 10)])  # ~65 % of baseline, like spike #25 'miclos'
    assert out["blinkCount"] == 0 and out["eyesClosed"] is False


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
    assert out["eyesClosed"] is False and out["blinkCount"] == 1 and out["lastBlinkMs"] > 13000
