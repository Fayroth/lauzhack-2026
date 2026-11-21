# Build log

Used at judging to show what is new compared to reused resources (LauzHack rule: the project must be
novel relative to the resources it uses, and teams must make that explicit).

## What we built (during the hackathon)
| Time | Who | What |
|------|-----|------|
| Sat 12:00 | team | Hacking starts — repo set up |
| Sat 14:30 | Ali | KMP skeleton: models, placeholder FatigueEngine (baseline + weighted score), Guardrail stub, dashboard with energy gauge, mock vision/input/haptics, Ktor WebSocket client, fake vision sidecar |

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
