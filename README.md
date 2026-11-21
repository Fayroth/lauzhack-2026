# Project name — LauzHack 2026

> One sentence: what it does and for whom.

**Team:** Ali · Mehdi · Zyad · Isma
**Sponsor challenge:** _none / name of the challenge_

## The problem
_2–3 sentences. Who suffers, how much, why existing solutions don't cut it._

## Our solution
_2–3 sentences + a screenshot or GIF._

## Demo
1. _Step 1 of the demo_
2. _Step 2_
3. _The "wow" moment_

Backup demo video: _link_

## What we built during LauzHack
_This is what the jury evaluates: be explicit about what is ours._
- _Feature / component we wrote during the 24h_
- _..._

## Reused resources
_Libraries, APIs, datasets, templates we used (see `docs/BUILD_LOG.md` for the full list)._
- _..._

## Tech stack
- _Frontend:_
- _Backend:_
- _AI / data:_

## How to run
Requirements: JDK 17+ and the Android SDK (installed with Android Studio; Gradle finds it via
`ANDROID_HOME` or a `local.properties` file with `sdk.dir=...`, which Android Studio creates for you).

```bash
# 1. Desktop app (mock vision + mock inputs, no hardware needed)
./gradlew :composeApp:run            # Windows: .\gradlew.bat :composeApp:run

# 2. Vision sidecar (Python 3.12 recommended for mediapipe)
python -m venv .venv && .venv/Scripts/activate   # macOS/Linux: source .venv/bin/activate
pip install -r vision/requirements.txt
python vision/main.py                # streams JSON on ws://localhost:8765 (fake frames for now)

# 3. Desktop app connected to the sidecar instead of the mock
EG_VISION=ws ./gradlew :composeApp:run           # PowerShell: $env:EG_VISION="ws"; .\gradlew.bat :composeApp:run

# 4. Real mouse/keyboard stats (JNativeHook global hooks) instead of the mock
EG_INPUT=real ./gradlew :composeApp:run          # PowerShell: $env:EG_INPUT="real"; .\gradlew.bat :composeApp:run
# macOS: allow the terminal/IDE in Privacy & Security > Accessibility + Input Monitoring, otherwise no events.
# Windows: input inside elevated (admin) windows is not captured.

# Android companion (debug APK)
./gradlew :composeApp:assembleDebug
```

If a feature needs API keys: `cp .env.example .env` and fill it in (never commit `.env`).

### Code layout
| Folder | What | Owner |
|--------|------|-------|
| `composeApp/src/commonMain/.../model`, `engine`, `guardrail` | Data models (see `docs/API.md`), FatigueEngine, Guardrail | Ali |
| `composeApp/src/commonMain/.../ui` | Compose dashboard shared by desktop + Android | Mehdi |
| `composeApp/src/desktopMain/.../input`, `haptics` | Mouse/keyboard collectors (JNativeHook), MX Master 4 haptics | Zyad |
| `composeApp/src/desktopMain/.../vision` | WebSocket client to the sidecar + mock | Ali / Isma |
| `vision/` | Python sidecar: webcam/IR → MediaPipe → eye metrics → WebSocket | Isma |

## What's next
_What we would build with more time._
