# Build log

Used at judging to show what is new compared to reused resources (LauzHack rule: the project must be
novel relative to the resources it uses, and teams must make that explicit).

## What we built (during the hackathon)
| Time | Who | What |
|------|-----|------|
| Sat 12:00 | team | Hacking starts — repo set up |
| Sat 14:30 | Ali | KMP skeleton: models, placeholder FatigueEngine (baseline + weighted score), Guardrail stub, dashboard with energy gauge, mock vision/input/haptics, Ktor WebSocket client, fake vision sidecar |
| Sat 15:20 | Ali | FatigueEngine v1: personal 60 s baseline (face-time only), rolling 60 s windows, clipped z-scores, weighted fusion renormalized over available signals (eyes, mouse, keyboard, gaze, hands), EMA, hysteresis levels, micro-sleep → instant TIRED, plain-words reasons; 8 deterministic commonTest scenarios |
| Sat 15:45 | Zyad | Real mouse/keyboard collector (`JNativeHookInputSource` + pure `InputStatsCalculator`): overshoot ratio before clicks, shake jitter (direction reversals/s), typing rhythm and backspace ratio over a 30 s window — timestamps only, never key contents. Unit tests in `desktopTest` |
| Sat 16:15 | Mehdi | Projector-ready dashboard (#7): big gauge with calibration countdown + level hint + top reasons, Eyes/Mouse/Keyboard tiles with per-signal health colors |
| Sat 16:00 | Ali | Pipeline wired end to end: vision (mock / sidecar) + input → FatigueEngine → UI + haptics. One clock (WS frames stamped on arrival), auto-reconnect that survives sidecar down/restart, no fake micro-sleep when the sidecar dies mid-blink, haptic trigger (upward transitions, 2-min cooldown); tests |
| Sat 16:30 | Zyad | Fix input-only calibration with the real 5 s input cadence (was 150 s instead of 60 s) + regression test |
| Sat 16:38 | Ali | Guardrail "double vigilance": deterministic phishing checks (text/href mismatch, lookalike brands with 0→o / rn→m, punycode, raw IP, user@host trick, urgency words, shorteners, cheap TLDs); strong signals block when TIRED and only warn when FRESH; plain-words reasons; `LocalGuard` for the UI + haptic WARNING on block; 8 unit tests |
| Sat 17:30 | Zyad | Real MX Master 4 haptics (`MxMaster4HapticOutput`, `EG_HAPTICS=mx`): HID report ported from the WebHID library to Kotlin/hid4java, opens the vendor HID collection over Bluetooth, background thread, falls back to console + beep if the mouse is absent; TIRED = gentle pulse, EXHAUSTED = warning pulse |
| Sat 17:45 | Ali | EnergyCoach (#21, folds #29/#31): frugal LLM coach in `commonMain` — fires only on level changes involving TIRED/EXHAUSTED, max 1 OpenRouter call / 10 min, 5 s timeout off the UI thread, prompt = numbers + whitelisted engine reasons only, picks BREAK vs STAND_UP vs RECOVERED, offline templates on no key / error / timeout / rate limit, real `llmCallCount` + last message in `DashboardState`; 7 tests with a fake LLM |
| Sat 17:07 | Ali | Demo mode (`EG_DEMO=1`, #10): scripted fatigue ramp fed through the real pipeline (mock sources → FatigueEngine with 15 s calibration / 10 s window → gauge, reasons, haptics, guardrail), F5 replays it, discreet "DEMO" marker; test checks TIRED ≈ 60 s and EXHAUSTED ≈ 90 s |
| Sat 17:45 | Mehdi | Dashboard polish (#7): 5-min energy trend, "now" vs "last minute" labels, warm evening theme (honey→rose energy scale, energy-tinted background), all colors in `ui/Theme.kt`, 8 dp spacing grid, animated liquid-battery logo tinted by the user's state (static icon for the window), reasons shown as factors (×2.1 vs usual) |
| Sat 18:55 | Mehdi | Break alert (#8) wired to the EnergyCoach: card as soon as TIRED/EXHAUSTED with the coach text (title by kind: "Eyes off the screen, 5 min" / "Stand up and walk a bit", subtle AI/offline tag, fallback "Take a 5-min break" + top reason), "Welcome back" toast on RECOVERED; escalation inspired by cars' drowsiness assistants: snooze → "Break soon?" pill → card, EXHAUSTED ignored or micro-sleep → full-screen "Stop". Dashboard fixes: countdown and "over the last …" label from the engine timing, trend and alert reset on F5, trend legend no longer cut off at 1280×800. Pure `BreakAlertLogic` + 11 unit tests |

| Sat 18:30 | Ali | Dashboard timing contract: `DashboardState.calibrationMs` / `windowMs` / `runId` filled by `main.kt` (demo 15 s / 10 s, real 60 s / 60 s, runId +1 on F5) so the UI countdown, window label and trend reset follow the real engine; usage documented in `docs/API.md` |
| Sat 18:35 | Isma | Spike #25 (throwaway `vision/spike_ear.py`): LogiInsight face mesh (478 pts, MediaPipe topology, pixel coords) DOES expose usable eyelid points. EAR from the standard MediaPipe eye indices, 26-27 fps frontal (24 fps in slight profile), 0 % empty-mesh frames frontal; open EAR ~0.23-0.27, closed ~0.03-0.08. |
| Sat 18:35 | Isma | Blink quality, prototype camera RGB: frontal 10 detected for 10 voluntary blinks, a 6 s closure is unmistakable; half-closed eyes show as a trend (EAR ~50-85 % of personal baseline) but are noisy frame by frame (~8-9 % jitter) -> windowed, baseline-relative thresholds (PERCLOS) + hysteresis, never per-frame. Slight profile (20-30 deg): degraded (13 % mesh drops, left/right EAR diverge, ~2x false blinks) -> gate eye signals on headYaw. Glasses: tested by the team, OK (qualitative, no numbers). IR mesh: not tested (SDK doc does not show how). Matches the booth feedback: blinks OK, eyelid droop imprecise. |
| Sat 18:35 | Isma | Decision: LogiInsight RGB feeds issue #3; MediaPipe stays the fallback behind the same FrameSource interface. Pitch wording: "blinks and long closures are reliable, eyelid droop is detected as a trend, not instantly; accuracy drops when the head is turned". |

| Sat 18:45 | Ali | Demo inbox (#13): `ui/DemoInbox.kt`, 4 emails (PayPal lookalike phishing, legit EPFL news, 2 fillers); every link click goes through `LocalGuard` → Allow = page opened, Warn = "Are you sure?" dialog, Block = full red screen with reason + signals. Opened as a second window from `main.kt` (same `LocalGuard`), at start in demo mode, F2 otherwise |
| Sat 18:57 | Ali | Real-world guardrail (#43): Chrome MV3 extension (`extension/`, plain JS) intercepts cross-site link clicks (real href read before Gmail rewrites it, google.com/url unwrapped), asks the desktop app, shows Block / Warn overlays in a closed Shadow DOM, fails open in 300 ms. Local endpoint `POST 127.0.0.1:8766/guard` (Ktor CIO) runs the SAME guard function as the inbox (Guardrail + live fatigue + haptic on Block): loopback-only bind, pinned extension Origin + Host checks (no CORS, anti DNS-rebinding), input validation, logs only a counter; 7 tests incl. a real loopback server |
| Sat 20:45 | Ali | Android runs the shared engine live (#41): demo script + sample builders, `HapticTrigger` and `HapticOutput` moved to `commonMain` (desktop unchanged, demo test now in `commonTest`); `MainActivity` feeds `FatigueEngine` at 10 Hz / 1 Hz, offline `FrugalEnergyCoach`, shared `App()` scaled to the phone, phone `Vibrator` behind `HapticOutput`, Replay button |
| Sat 20:40 | Mehdi | Frugality counter + light/dark mode (#14): live strip under the tiles (real LLM calls from the coach, frames processed on this PC, 0 images / audio sent, 0 keys recorded); header toggle between the dark "evening" palette and a light "health" palette (white + green), every color contrast-checked for the projector (≥ 4:1); theme switch recomposes the whole UI including the demo inbox |
| Sat 21:20 | Mehdi | Sleep prior (#15): "Slept 3–9 h" slider in the header feeds `SleepPrior`, the engine adds up to +20 fatigue points after a short night (reason "short night (5.0 h)"). KSS self-rating (#32): every 30 min (40 s in demo) a 1–9 Karolinska Sleepiness Scale card, stored locally with our score, dashboard shows "You said 7/9 · we measured 68" and the Pearson match once n ≥ 3 |

## Reused resources
| Resource | Link | Used for |
|----------|------|----------|
| Kotlin Multiplatform / Compose Multiplatform project template (structure: `composeApp`, version catalog) | https://kmp.jetbrains.com | Project skeleton |
| Kotlin, Compose Multiplatform, kotlinx.coroutines | https://kotlinlang.org · https://www.jetbrains.com/compose-multiplatform/ | Language, desktop/Android UI, async streams |
| Gradle + Android Gradle Plugin | https://gradle.org · https://developer.android.com/build | Build |
| kotlinx.serialization | https://github.com/Kotlin/kotlinx.serialization | Parse sidecar JSON |
| Ktor client (CIO + WebSockets) | https://ktor.io | WebSocket client to the vision sidecar |
| JNativeHook | https://github.com/kwhat/jnativehook | Global mouse/keyboard hooks (timings/counts only) |
| hid4java (MIT) | https://github.com/gary-rowe/hid4java | JVM access to HID devices, used to send the haptic report to the MX Master 4 |
| MediaPipe Face Mesh | https://github.com/google-ai-edge/mediapipe | Face landmarks for EAR/PERCLOS/blinks |
| OpenCV (opencv-python) | https://opencv.org | Webcam capture |
| websockets (Python) | https://github.com/python-websockets/websockets | Sidecar WebSocket server |
| LogiInsight Python SDK (v1.5.2, MIT) | https://logitech.github.io/hackathons/logiinsight/ | On-device face mesh, RGB + IR streams of the prototype webcam |
| Logi Gaze Tracker | https://logitech.github.io/hackathons/logi-gaze-tracker/setup/ | Gaze on/off screen |
| Logi Hands Tracker | https://logitech.github.io/hackathons/logi-hands-tracker/ | Eye rubbing, head on hand |
| Logi Actions SDK (haptics) | https://logitech.github.io/actions-sdk-docs/ | MX Master 4 haptic feedback |
| MX Master 4 WebHID haptics library (MIT) | https://github.com/mario-gutierrez/mx-master-4-webhid | Source of the haptic HID report bytes (0x11, 0x0B4E, effect id), ported to Kotlin in `MxMaster4HapticOutput` |
| OpenRouter | https://openrouter.ai | LLM API for the EnergyCoach |
| Claude Haiku 5.5 via OpenRouter (`anthropic/claude-haiku-5.5`) | https://openrouter.ai/anthropic/claude-haiku-5.5 | Small cheap model that writes the one-sentence coach message (override: `EG_COACH_MODEL`) |
| Ktor server (core + CIO engine) | https://ktor.io/docs/server-create-and-configure.html | Local guard endpoint on 127.0.0.1 for the Chrome extension |
| Chrome Extensions Manifest V3 (content scripts, service worker, `key` for a stable id) | https://developer.chrome.com/docs/extensions/mv3 | Real-world guardrail in Chrome / Gmail |
| kotlinx-coroutines-test | https://github.com/Kotlin/kotlinx.coroutines | Virtual time in the EnergyCoach tests (timeout, rate limit) |
| Android `Vibrator` / `VibrationEffect` API | https://developer.android.com/reference/android/os/Vibrator | Phone vibration behind `HapticOutput` on Android |
