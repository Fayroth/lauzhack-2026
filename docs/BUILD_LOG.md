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

## Reused resources
| Resource | Link | Used for |
|----------|------|----------|
| Kotlin Multiplatform / Compose Multiplatform project template (structure: `composeApp`, version catalog) | https://kmp.jetbrains.com | Project skeleton |
| Kotlin, Compose Multiplatform, kotlinx.coroutines | https://kotlinlang.org · https://www.jetbrains.com/compose-multiplatform/ | Language, desktop/Android UI, async streams |
| Gradle + Android Gradle Plugin | https://gradle.org · https://developer.android.com/build | Build |
| kotlinx.serialization | https://github.com/Kotlin/kotlinx.serialization | Parse sidecar JSON |
| Ktor client (CIO + WebSockets) | https://ktor.io | WebSocket client to the vision sidecar |
| JNativeHook | https://github.com/kwhat/jnativehook | Global mouse/keyboard hooks (timings/counts only) |
| MediaPipe Face Mesh | https://github.com/google-ai-edge/mediapipe | Face landmarks for EAR/PERCLOS/blinks |
| OpenCV (opencv-python) | https://opencv.org | Webcam capture |
| websockets (Python) | https://github.com/python-websockets/websockets | Sidecar WebSocket server |
| LogiInsight Python SDK (v1.5.2, MIT) | https://logitech.github.io/hackathons/logiinsight/ | On-device face mesh, RGB + IR streams of the prototype webcam |
| Logi Gaze Tracker | https://logitech.github.io/hackathons/logi-gaze-tracker/setup/ | Gaze on/off screen |
| Logi Hands Tracker | https://logitech.github.io/hackathons/logi-hands-tracker/ | Eye rubbing, head on hand |
| Logi Actions SDK (haptics) | https://logitech.github.io/actions-sdk-docs/ | MX Master 4 haptic feedback |
| MX Master 4 WebHID haptics library | https://github.com/mario-gutierrez/mx-master-4-webhid | MX Master 4 haptics (alternative path) |
| OpenRouter | https://openrouter.ai | LLM API for the EnergyCoach |
