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

# 2. Vision sidecar (Python 3.12: mediapipe + ai-edge-litert have no 3.15 wheels; conda not supported)
python -m venv .venv && .venv/Scripts/activate   # macOS/Linux: source .venv/bin/activate
pip install -r vision/requirements.txt
curl -L -o vision/models/face_landmarker.task --create-dirs https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task
# Logitech prototype: set PYTHONPATH to the unzipped LogiInsight SDK folder (logiinsight_api) and
# LOGIWEBCAM_USER / LOGIWEBCAM_PASSWORD (card in the kit; env only, never in files). See docs/BUILD_LOG.md spike #25.
python vision/main.py                # auto: LogiInsight on-camera mesh, else MediaPipe on the default webcam
python vision/main.py --source mediapipe   # force the webcam fallback (--camera N to pick another webcam)
python vision/main.py --source fake        # fake tiredness curve, no camera
# Console prints source / fps / EAR / blinks / PERCLOS every second. Keep eyes open the first ~5 s (calibration).

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

# 9. Android: same shared engine + demo script, live on a phone (see "Android" below)
./gradlew :composeApp:installDebug      # Windows: .\gradlew.bat :composeApp:installDebug
```

### Phone companion: live energy from the PC (#48)
The PC stays the sensor hub (webcam, MX Master 4, keyboard); the phone mirrors its **live** gauge, reasons and coach message,
vibrates on TIRED (1 buzz) / EXHAUSTED (3 buzzes) and says "you can go back" when the PC is back to FRESH/OK.
Off unless `EG_COMPANION=1`. Read-only `GET /state`, 6-digit pairing code on every request (new at every app start),
5 wrong codes → locked 1 min. Contract and security rules: `docs/API.md` → "Phone companion".

Venue / eduroam Wi-Fi usually blocks device-to-device traffic, so **put the laptop on the phone's hotspot**:
1. Phone: turn on the **mobile hotspot**. Laptop: join that Wi-Fi **before** starting the app (the app picks its address at start).
2. Windows: **Settings → Network & internet → Wi-Fi → (the hotspot) → Network profile type: Private** (hotspots often join as Public).
3. Start the PC app: `$env:EG_DEMO="1"; $env:EG_COMPANION="1"; .\gradlew.bat :composeApp:run`
   (drop `EG_DEMO` for real sensors). Bottom-left of the dashboard + console: `Phone: 192.168.x.y:8767 · code 123456`.
4. **Windows Firewall prompt** ("Java(TM) Platform SE binary"): tick **Private networks only**, then **Allow**.
   If you once clicked Cancel/Deny: `wf.msc` → Inbound Rules → the `java` / `OpenJDK Platform binary` rules → allow on Private.
5. Phone: open EnergyGuard → **Connect to your PC** → type the IP, port `8767`, the code → **Connect**.
   The gauge follows the PC within ~1 s (green "LIVE from your PC" marker). IP and port are remembered; the code
   is asked again after each PC app restart.
- Wrong code → "Wrong code". PC app closed / Wi-Fi lost → red "Not connected to your PC" banner + local demo replay, retries every second.
- Other options: `EG_COMPANION_PORT` (default 8767), `EG_COMPANION_HOST` (force the IP to listen on, if the dashboard shows the wrong one).
- **USB backup** if Wi-Fi fails on stage: `adb reverse tcp:8767 tcp:8767`, then on the phone use address `127.0.0.1`
  (the PC also listens on 127.0.0.1 when `EG_COMPANION=1`).

### Android (2nd platform, #41)
The phone runs the **same `commonMain` code** as the desktop demo mode: `DemoScript` + sample builders → `FatigueEngine`
(baseline, score, reasons) → `FrugalEnergyCoach` (offline templates: no API key on the phone) → `HapticTrigger` →
the shared Compose `App()` dashboard, scaled to the screen in landscape. The gauge drops live: FRESH → TIRED ~60 s → EXHAUSTED ~90 s,
the phone vibrates on TIRED (1 buzz) and EXHAUSTED (3 buzzes). **Replay** (bottom-left) = desktop F5.

| Shared (`commonMain`, both platforms) | Platform-specific |
|---|---|
| models, `FatigueEngine`, `Guardrail`, `EnergyCoach` + OpenRouter client, `DemoScript`, `HapticTrigger`, `HapticOutput` interface, whole dashboard UI | **Desktop:** webcam sidecar over WebSocket, JNativeHook mouse/keyboard, MX Master 4 haptics, Chrome guard endpoint, inbox window. **Android:** `MainActivity`, phone `Vibrator`. No camera, WebSocket or JNativeHook on Android. |

Install on a Samsung Galaxy (USB):
1. Phone: **Settings → About phone → Software information** → tap **Build number** 7 times → enter your PIN ("Developer mode turned on").
2. **Settings → Developer options** → turn on **USB debugging**.
3. Plug the phone into the PC (data cable), unlock it, accept **"Allow USB debugging?"** (tick "Always allow from this computer").
4. Check: `adb devices` lists it as `device` (adb is in `<Android SDK>/platform-tools`). `unauthorized` = accept the prompt on the phone.
5. `.\gradlew.bat :composeApp:installDebug`, then open **EnergyGuard** from the app drawer.

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
| `composeApp/src/commonMain/.../demo`, `androidMain` | Shared demo script + sample builders, Android app | Ali |
| `vision/` | Python sidecar: webcam/IR → MediaPipe → eye metrics → WebSocket | Isma |

## What's next
_What we would build with more time._
