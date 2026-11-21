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

# 5. Real MX Master 4 vibration (Bluetooth; Bolt untested) instead of the console mock
EG_HAPTICS=mx ./gradlew :composeApp:run          # PowerShell: $env:EG_HAPTICS="mx"; .\gradlew.bat :composeApp:run
# The mouse buzzes once at startup if it is found; otherwise you hear a beep.

# 6. Demo mode: scripted fatigue ramp through the real engine (FRESH ~20 s, TIRED ~60 s, EXHAUSTED ~90 s)
EG_DEMO=1 ./gradlew :composeApp:run             # PowerShell: $env:EG_DEMO="1"; .\gradlew.bat :composeApp:run
# Overrides EG_VISION / EG_INPUT (combine with EG_HAPTICS=mx for real vibration). F5 = restart the script.
# A second window "EnergyGuard — Inbox" opens too (guardrail demo). Outside demo mode: F2 on the dashboard opens it.
# A small "DEMO" marker is shown bottom-right.

# 7. EnergyCoach (LLM): put OPENROUTER_API_KEY in .env (see .env.example). Without it -> offline templates.
# Coach messages are printed as "[coach] ..." in the console. EG_COACH=offline forces templates, EG_COACH_MODEL=<id> changes the model.

# 8. Real-world guardrail in Chrome (incl. real Gmail): see "Chrome extension" below.

# Android companion (debug APK)
./gradlew :composeApp:assembleDebug
```

### Chrome extension (real-world guardrail, #43)
The desktop app listens on `http://127.0.0.1:8766/guard` (only while it runs; port `EG_GUARD_PORT`). The extension in
`extension/` sends it each link you click to another site and shows the same Allow / Warn / Block as the demo inbox.
1. Start the app (e.g. `$env:EG_DEMO="1"; .\gradlew.bat :composeApp:run`). Console: `[guard] Chrome extension endpoint on ...`.
2. Chrome → `chrome://extensions` → enable **Developer mode** (top right) → **Load unpacked** → pick the `extension/` folder.
   The id must be `mchiibilichfmibgecnoaheikiajhclo` (pinned by the `key` in `manifest.json`; the app only accepts that id,
   override with `EG_EXTENSION_ID`).
3. Click a link. Block = red screen + MX vibration, Warn = "Go back" / "Open anyway", Allow = opens normally.
   App closed or slow (> 300 ms) → links open normally (fail open).

What it can / can't see:
- **Permissions:** content script on http/https pages (to see link clicks) + `http://127.0.0.1/*` (to talk to our app). Nothing else:
  no tabs, history, cookies, storage, or remote server.
- **Sent:** for a click on a link to *another* site, only the link text + real URL, only to 127.0.0.1. Same-site clicks are ignored.
  Never page content, email bodies, images or keystrokes.
- **App side:** accepts requests only from our extension's `Origin` (a web page can't read your fatigue), binds to 127.0.0.1 only,
  validates input, stores/logs nothing but a counter and the verdict.

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
