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
| Sat 18:30 | Mehdi | Escalating break alert (#8), inspired by cars' drowsiness assistants: "Break soon?" pill when TIRED → break card if ignored (or at once when EXHAUSTED) → full-screen "Stop" if still ignored, and at once on a micro-sleep ("Your eyes just closed", "I'm awake"). Snooze / dismiss per episode, coffee-cup icon, short delays in demo mode, shows the EnergyCoach text when there is one (stand-up variant for #29); pure `BreakAlertLogic` with 9 unit tests |

| Sat 18:30 | Ali | Dashboard timing contract: `DashboardState.calibrationMs` / `windowMs` / `runId` filled by `main.kt` (demo 15 s / 10 s, real 60 s / 60 s, runId +1 on F5) so the UI countdown, window label and trend reset follow the real engine; usage documented in `docs/API.md` |

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
| kotlinx-coroutines-test | https://github.com/Kotlin/kotlinx.coroutines | Virtual time in the EnergyCoach tests (timeout, rate limit) |
