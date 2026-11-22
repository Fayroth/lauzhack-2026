# API contract

Update this file BEFORE changing a message format, and warn whoever depends on it.

## Vision sidecar → desktop app
WebSocket server in `vision/`, `ws://localhost:8765`, one JSON message per frame batch (~10 Hz).

```json
{
  "type": "vision",
  "ts": 1795260000123,
  "face": true,
  "ear": 0.27,
  "eyesClosed": false,
  "blinkCount": 14,
  "lastBlinkMs": 180,
  "perclos": 0.06,
  "yawn": false,
  "headPitch": -4.0,
  "headYaw": 2.5,
  "source": "logiinsight-rgb",

  "gazeOnScreen": true,
  "gazeX": 0.42,
  "gazeY": 0.61,
  "offScreenRatio": 0.08,

  "handNearEyes": false,
  "headOnHand": false
}
```
- **Clock (one time base):** `ts` is the sidecar's `time.time()` in epoch ms, informational only. The Kotlin client
  (`WebSocketVisionSource`) **re-stamps every frame on arrival** with `System.currentTimeMillis()`, the same clock used for
  `onInput`, so the engine never mixes two clocks (Python vs JVM, or a frame buffered during a hiccup). Latency on localhost ≈ ms.
- **Sidecar down/restart:** the client retries every 2 s (WebSocket ping every 2 s detects a hung sidecar), never throws;
  meanwhile the engine runs on input only and the UI vision panel shows "-" after 2 s without frames.
- `ear`: eye aspect ratio (avg of both eyes). `eyesClosed`: eye closure >= 60 % (reopen < 30 %), closure = blendshape
  `eyeBlink` or EAR vs the personal baseline. Partial closure counts on purpose: micro-sleeps (0.5-15 s) happen with the eyes
  fully or partially closed. Only sent `true` once the closure is held >= 200 ms (no single noisy frame); the engine needs
  > 3 s of it for a micro-sleep.
- `blinkCount`: blinks **per minute** over the last 60 s. During the first minute it is extrapolated (`n * 60 / covered s`)
  and null before 15 s of eye data, so the engine's 60 s calibration does not learn a ramp from 0.
- `lastBlinkMs`: duration of the last blink. A blink is a 50-500 ms closure; longer closures (a glance down, a micro-sleep)
  go through `eyesClosed` / PERCLOS and are neither counted nor reported as blinks.
- `perclos`: fraction of the last 60 s with eye closure >= **0.5** on the sidecar's closure scale (blendshape `eyeBlink`,
  or EAR vs baseline). Inspired by PERCLOS (the studies' P80 = >= 80 % of the pupil covered, dedicated sensors); on our
  scale heavy half-closed lids read 0.45-0.77, so P80 missed them. Tunable: `python vision/main.py --perclos-level 0.6`.
- `source`: which perception path produced the eye metrics:
  `"logiinsight-rgb"` (LogiInsight SDK face mesh on the RGB stream), `"logiinsight-ir"` (same on the IR stream: low light / glasses),
  `"mediapipe"` (fallback on any webcam). Mocks/fakes may send `"mock"`; older stubs may still send `"webcam"`.
- `face=false` → other fields may be absent.
- `face=true` with null eye fields (`ear`, `eyesClosed`, `blinkCount`, `lastBlinkMs`, `perclos`): the sidecar does not trust
  the eyes right now: head turned more than ~25° (`headYaw`), or the first ~5 s of personal EAR calibration
  (only `ear` is sent then). Consumers drop the missing signals, as for any absent field.
- `headPitch` / `headYaw`: rough degrees from the mesh depth (sign not calibrated). `yawn`: only from the blendshape
  `jawOpen` (> 0.6 for 1.5 s), otherwise always `false`.

**Optional fields (added for the Logitech AI layers, all may be absent; consumers must handle null):**
- Gaze, from Logi Gaze Tracker:
  - `gazeOnScreen` (bool): gaze currently on the screen.
  - `gazeX`, `gazeY` (0..1): normalized gaze point on the screen (0,0 = top-left).
  - `offScreenRatio` (0..1): fraction of the last 60 s with gaze off screen.
- Hands, from Logi Hands Tracker:
  - `handNearEyes` (bool): a hand is near the eyes (eye rubbing).
  - `headOnHand` (bool): head resting on a hand.
- Kotlin mirror: `VisionFrame` in `composeApp/src/commonMain/.../model/Models.kt` (unknown fields are ignored).

## Input stats (desktop, in-process, Kotlin)
```kotlin
data class InputStats(
    val windowMs: Long,          // e.g. 30_000
    val mouseDistancePx: Double,
    val clickCount: Int,
    val overshootRatio: Double,  // path length / straight distance near clicks
    val jitter: Double,          // high-frequency micro-movement while hovering
    val keyCount: Int,           // counts only, never key contents
    val backspaceRatio: Double,
    val meanInterKeyMs: Double,
)
```
Real implementation (`JNativeHookInputSource`, `EG_INPUT=real`): one `InputStats` every 5 s over the last 30 s.
- `overshootRatio`: path length / straight distance over the 800 ms before each click (straight ≥ 40 px), averaged; `1.0` when no click.
- `jitter`: direction reversals per second between consecutive small fast cursor steps (1–80 px, ≤ 100 ms apart), computed over the last 10 s only (so it reacts within 1–2 emissions); `0.0` when the mouse is still. Calm use ≈ 0–0.5; shaking the mouse in place for 5–10 s → several per second.
- `backspaceRatio`: Backspace/Delete presses / all key presses (a held Backspace counts once, not per auto-repeat); `meanInterKeyMs`: mean gap between key presses, pauses > 2 s excluded. Both `0.0` when nobody typed.
- Note for the engine: `jitter`, `backspaceRatio` and `meanInterKeyMs` can be `0.0` during calibration (no activity), so a relative baseline needs a floor.

## Fatigue score (commonMain)
```kotlin
data class FatigueState(
    val score: Int,              // 0 = fresh, 100 = exhausted
    val level: Level,            // FRESH, OK, TIRED, EXHAUSTED
    val reasons: List<String>,   // e.g. ["long blinks", "mouse jitter +40%"]
    val calibrated: Boolean,
)
```
Engine API (pure Kotlin, no internal clock: timestamps are passed in, epoch ms):
```kotlin
class FatigueEngine {
    val state: StateFlow<FatigueState>
    fun onVision(frame: VisionFrame)            // uses frame.ts
    fun onInput(stats: InputStats, ts: Long)    // InputStats has no ts: pass System.currentTimeMillis()
}
```
- Calibration: first 60 s *with a face* (or 60 s of input if no vision sidecar at all) → per-signal baseline.
- Signals missing (no face, no typing, no gaze/hands tracker) are dropped and weights renormalized.
- Every signal is one-sided: only a change in the fatigue direction scores. Blink rate counts when it RISES (rises with
  sleepiness); a drop (focused reading / screen work lowers the blink rate) does not count as fatigue.
- Eyes closed > 3 s → micro-sleep: instant `TIRED` (reason `"eyes closed 3.2 s"`), score floor 60. Closures < 3 s (blinks,
  a glance at the keyboard) never force the level; they only count through PERCLOS. Euro NCAP tests micro-sleep detection
  with eyes closed up to 4 s. If the face disappears after the eyes have been closed >= 1.5 s (head dropping), the closure
  keeps counting for up to 3 s; a blink or a ~1 s glance followed by losing the face does not.
- `FatigueState.concentration = 100 - score` is what the gauge ("Concentration"), the coach and the guardrail show.
  The word "energy" is only used for the slow `EnergyBattery`.
- Levels use hysteresis: up at 30/55/80, down below 20/45/70.
- Read-only `fun baselineSummary(): BaselineSummary` (for the Profile tab, no behaviour change):
  ```kotlin
  data class BaselineSummary(
      val calibrated: Boolean,
      val blinkRatePerMin: Double?,  // baseline mean of blinkCount (blinks / 60 s)
      val blinkMs: Double?,          // baseline mean blink duration
      val perclos: Double?,          // baseline PERCLOS (0..1)
      val mouseJitter: Double?,      // baseline jitter (reversals / s)
      val interKeyMs: Double?,       // baseline typing rhythm (mean gap between keys)
      val backspaceRatio: Double?,   // baseline typo rate
  )
  ```
  A value is `null` until that signal has a frozen (calibrated) baseline.

### Engine timing for the UI (`DashboardState`)
`main.kt` tells the dashboard how the current engine is configured, so the UI never hardcodes it:
```kotlin
val calibrationMs: Long // real 60 000 (FatigueEngine.DEFAULT_CALIBRATION_MS), demo 15 000 (DemoScript.CALIBRATION_MS)
val windowMs: Long      // real 60 000 (FatigueEngine.DEFAULT_WINDOW_MS),      demo 10 000 (DemoScript.WINDOW_MS)
val runId: Int          // 0 at start, +1 on each F5 demo replay (fresh engine)
```
How the UI should use them (owner: Mehdi):
- Calibration countdown: count down from `calibrationMs / 1000` instead of the hardcoded `CALIBRATION_SECONDS = 60`.
- Window label: build "over the last ..." from `windowMs` (60 000 → "last minute", 10 000 → "last 10 s").
- Per-run state: key it on the run, e.g. `remember(state.runId) { ... }` for the 5-min trend history
  (and the countdown start mark), so F5 clears the old curve instead of drawing a jump to 100%.

### Sleep prior (#15, `model/SleepPrior.kt`)
`SleepPrior.hours` (default 7.0, set by the "Slept" slider in the dashboard header, range 3–9 h) shifts the
fatigue score **after** EMA smoothing and **before** the levels: `SleepPrior.penalty()` = `round((7 - hours) * 5)`
clamped to **-5..+20** points (5 h → +10, 3 h → +20, 8 h → -5). When the penalty is ≥ 5, the engine adds the
reason `short night (N h)`. Shared object, no new parameter: the UI writes it, the engine reads it on every frame.

## EnergyCoach (commonMain)
Frugal generative layer: turns the fusion result into one short, personalized intervention.
LLM via OpenRouter (Ktor client, key `OPENROUTER_API_KEY` in `.env` only).

```kotlin
data class CoachInput(
    val state: FatigueState,       // score, level, reasons
    val previousLevel: Level,      // the transition that triggered the call
    val sleepPriorHours: Double?,  // from the "sleep last night" slider or Health Connect (null if unknown)
    val localTime: String,         // "HH:mm", local
    val minutesSinceBreak: Int? = null, // since session start or the last recovery (null if unknown)
)
enum class Intervention { BREAK, STAND_UP, RECOVERED }  // BREAK = eyes off screen 5 min, STAND_UP = stand up / walk a bit
data class CoachMessage(
    val text: String,              // one short intervention, e.g. "Eyes closing +40% at 16:20: 5-min walk, then the hard email."
    val fromLlm: Boolean,          // false = offline template fallback
    val kind: Intervention = Intervention.BREAK,  // chosen by the coach from the numbers (#29 folded in)
)
interface EnergyCoach {
    suspend fun advise(input: CoachInput): CoachMessage
    val llmCallCount: StateFlow<Int>   // shown by the frugality counter
}
```
Implementation: `FrugalEnergyCoach(llm: LlmClient?)` in `coach/EnergyCoach.kt` (`llm = null` → templates only).
- `suspend fun onFatigue(state, localTime, sleepPriorHours = null, minutesSinceBreak = null): CoachMessage?` — the shells pass
  `SleepPrior.hours` and `AppStore` `currentFocusMs / 60 000` (real breaks reset it); feed every state; it tracks the
  previous level itself and returns `null` unless the level changed **and** TIRED/EXHAUSTED is involved (FRESH ↔ OK is silent,
  so the LLM budget is kept for the moments that matter). `reset()` forgets the level (demo F5), never the rate limit or counter.
- `val lastMessage: StateFlow<CoachMessage?>` + `val llmCallCount: StateFlow<Int>` → `DashboardState.coach` / `DashboardState.llmCallCount`
  (break alert #8 shows `coach.text` + `coach.kind`, frugality counter #14 shows `llmCallCount`).
- `LlmClient` = `fun interface { suspend fun complete(system: String, user: String): String }`; real one = `OpenRouterLlmClient`
  (Ktor, model from `EG_COACH_MODEL`, default `anthropic/claude-haiku-5.5`, max 60 tokens). 5 s timeout. Output sanitized
  to one line ≤ 160 chars.
Rules:
- Called ONLY on level transitions (e.g. OK → TIRED), never on every frame.
- At most 1 LLM call per 10 min. Inside that window, or on any error/timeout/no key → offline template (`fromLlm = false`).
- The prompt contains numbers and short reason strings only: never images, audio, or keystroke content.
- Every real LLM call that returned a message increments `llmCallCount` (a failed attempt still uses the 10-min slot).
- Reason strings are filtered to the engine's vocabulary (lowercase words, numbers, `%`, `+`, `-`, `.`, max 40 chars, top 3).

## Haptics (interface + trigger in commonMain; MX Master 4 on desktop, phone Vibrator on Android)
```kotlin
enum class HapticPattern { GENTLE, WARNING }
interface HapticOutput { fun pulse(pattern: HapticPattern) }
```
When (`HapticTrigger`, commonMain): GENTLE on entering TIRED, WARNING on entering EXHAUSTED, only on the way up,
at most once per 2 min (except an escalation GENTLE → WARNING, never swallowed).
Real implementation (`MxMaster4HapticOutput`, `EG_HAPTICS=mx`): sends HID report `0x11` + `[0x02, 0x0B, 0x4E, effectId, 0…]` (19 bytes) to the vendor HID collection of the MX Master 4 (046D:B042 / B048), bytes from the WebHID library. `GENTLE` / `WARNING` effect numbers can be overridden with `EG_HAPTIC_GENTLE` / `EG_HAPTIC_WARNING` (0–14). If the mouse is absent: console line + system beep. One gentle pulse at startup confirms the mouse is connected.
Also: one WARNING every time the Guardrail returns `Block` (see below).

## Guardrail (commonMain, `guardrail/Guardrail.kt`)
"Double vigilance": a risky action is checked harder when concentration is low. Pure and deterministic: no ML, no network, no clock.
```kotlin
sealed interface GuardedAction {
    /** A link click. displayText = what the user sees, href = where it really goes, context = email subject + body (optional). */
    data class OpenLink(val displayText: String, val href: String, val context: String = "") : GuardedAction
    data class Payment(val iban: String, val amountChf: Double) : GuardedAction
}
sealed interface Verdict {
    data object Allow : Verdict
    data class Warn(val reason: String, val signals: List<String> = emptyList()) : Verdict   // ask "Open anyway?"
    data class Block(val reason: String, val signals: List<String> = emptyList()) : Verdict  // no way through
}
class Guardrail(trustedDomains: Set<String> = …) {
    fun check(action: GuardedAction, fatigue: FatigueState): Verdict
}
```
- `reason`: one plain-words headline for the big warning screen. `signals`: one short line per red flag (bullets).
- Link signals. **Strong:** link text shows a different domain than the real one · lookalike of a known brand
  (`paypa1`, `rn`→`m`, `0`→`o`, brand in a subdomain like `paypal.com.secure-login.xyz`) · punycode / non-ASCII host ·
  raw IP host · `user@host` trick. **Weak:** unknown (not trusted) domain · urgency words in text/context · plain `http` ·
  URL shortener · cheap TLD (`.xyz`, `.top`, ...).
- Decision: | | strong signal | only weak | none |
  |---|---|---|---|
  | FRESH / OK | Warn | Allow | Allow |
  | TIRED / EXHAUSTED | **Block** | Warn | Allow |
- Payment: TIRED/EXHAUSTED → Warn ("double-check IBAN and amount"), otherwise Allow.

**How the UI calls it (inbox, #13):** no need to hold the engine or the haptics. Inside any composable:
```kotlin
val guard = LocalGuard.current                       // (GuardedAction) -> Verdict, provided by main.kt
when (val v = guard(GuardedAction.OpenLink(displayText, href, context = subject + "\n" + body))) {
    Verdict.Allow    -> /* normal flow: show the "page opened" screen */
    is Verdict.Warn  -> /* confirm dialog: v.reason + v.signals, buttons "Go back" / "Open anyway" */
    is Verdict.Block -> /* red block screen: v.reason + v.signals, only "Go back" (the mouse already vibrated) */
}
```
`main.kt` provides `LocalGuard` = `Guardrail.check(action, latest FatigueState)` + haptic WARNING on `Block`.
Default when not provided (Android, previews): same check with `FatigueState.Initial`, no haptics.

Demo phishing email (the one the tests use, copy it into the inbox):
- From `PayPal Security <service@paypa1-secure.com>`, subject `URGENT: your account will be suspended in 24 hours`
- Link text `https://www.paypal.com/signin`, href `https://paypa1-secure.com/signin`
- → TIRED: **Block** · FRESH: Warn. A legit link (`https://actu.epfl.ch/news/`, text "EPFL news") → Allow at every level.

### Local guard endpoint (desktop app ↔ Chrome extension, #43)
The **same** `guard` function as `LocalGuard` (Guardrail + latest FatigueState + haptic WARNING on Block), exposed over HTTP
so the Chrome extension in `extension/` protects real link clicks (incl. real Gmail). Server: Ktor CIO in `desktopMain/guard/`.

`POST http://127.0.0.1:8766/guard` (port: `EG_GUARD_PORT`, default 8766), `Content-Type: application/json`
```json
{ "displayText": "https://www.paypal.com/signin", "href": "https://paypa1-secure.com/signin", "context": "" }
```
→ `200`
```json
{ "verdict": "block", "reason": "Blocked: this looks like phishing and your concentration is low (38%). ...",
  "signals": ["The link text says paypal.com but it actually goes to paypa1-secure.com", "..."], "level": "TIRED" }
```
- `verdict`: `"allow" | "warn" | "block"` (Verdict.Allow / Warn / Block). Allow → `reason = ""`, `signals = []`.
- `level`: current `Level` name (`FRESH|OK|TIRED|EXHAUSTED`), informational.
- Block → the MX Master 4 vibrates (WARNING), exactly like the inbox.

Security (mandatory):
- Bound to **127.0.0.1 only** (never 0.0.0.0). `Host` header must be `127.0.0.1:<port>` or `localhost:<port>` (anti DNS-rebinding).
- `Origin` header must be exactly `chrome-extension://<id>`; id pinned by the `key` in `extension/manifest.json`
  (default `mchiibilichfmibgecnoaheikiajhclo`, override with `EG_EXTENSION_ID`). Anything else → `403`. No CORS headers at all,
  so web pages can never read the user's fatigue.
- Input: body ≤ 16 KB (Content-Length required), JSON object with `displayText` ≤ 1000 chars, `href` ≤ 2048 chars and
  starting with `http://` or `https://` (no whitespace/control chars), optional `context` ≤ 2000 chars. Otherwise `400`.
  Wrong path → `404`, wrong method → `405`.
- Stores and logs nothing but a request counter + the verdict (never the URL or text).
- Extension side: **fail open**. No answer within 300 ms, app closed, or any non-200 → the link opens normally.
- The extension sends only the link text + href (context `""`), only to 127.0.0.1. No page content, images or keystrokes.

## Phone companion (desktop → Android over local Wi-Fi, #48)
The PC is the sensor hub (webcam, MX Master 4, keyboard); the phone mirrors its live state. **Read-only**, separate from the
guard server (which stays 127.0.0.1-only, unchanged). Server: Ktor CIO in `desktopMain/companion/`, off unless `EG_COMPANION=1`.

`GET http://<PC LAN IP>:8767/state` (port: `EG_COMPANION_PORT`, default 8767), header `Authorization: Bearer <code>`
→ `200`, `Content-Type: application/json`, `Cache-Control: no-store`
```json
{
  "score": 62, "level": "TIRED", "reasons": ["long blinks +80%", "mouse jitter +40%"], "calibrated": true,
  "coach": { "text": "Eyes closing x1.8 at 16:20: 5-min walk, then the hard email.", "kind": "BREAK", "fromLlm": true },
  "ts": 1795260000123,
  "llmCalls": 1, "calibrationMs": 15000, "windowMs": 10000, "runId": 0,
  "vision": { "ts": 1795260000100, "face": true, "ear": 0.21, "perclos": 0.18, "blinkCount": 22, "source": "mock" },
  "input": { "windowMs": 30000, "mouseDistancePx": 5200.0, "clickCount": 9, "overshootRatio": 1.4, "jitter": 1.2,
             "keyCount": 80, "backspaceRatio": 0.12, "meanInterKeyMs": 240.0 }
}
```
**Optional, added with the app model (M1), absent from older PCs; old phones ignore them (`ignoreUnknownKeys`):**
```json
{
  "energy": { "battery": 74, "onBreak": false, "breakSource": null },
  "recovery": 68,
  "stats": { "focusedMs": 5400000, "breaksTaken": 2, "breaksNeeded": 1, "longestFocusMs": 3000000,
             "phishingBlocked": 1, "llmCalls": 1, "framesProcessed": 36000, "imagesSent": 0, "audioSent": 0, "keysSent": 0 }
}
```
**Optional, added with the Android shell (M3), absent from older PCs; old phones ignore them:**
```json
{
  "energy": { "battery": 74, "onBreak": false, "breakSource": null, "startFromSleep": 90, "sleepHours": 6.0 },
  "signals": { "source": "Logitech webcam · IR", "face": true, "blinksPerMin": 22, "perclosPct": 18, "eyesClosed": false,
               "jitterX": 1.7, "jitterPerS": 2.0, "precisionPct": 70, "typing": true, "msPerKey": 268, "backspacePct": 12 }
}
```
- `signals` = the PC's Live signals card as numbers (`SignalsSnapshot` in `commonMain/.../ui/today/LiveSignals.kt`, built by
  `SignalsSnapshot.of(vision, input, profile.baseline, demo)`), so the phone shows exactly the PC's rows, incl. "× usual"
  (the phone has no baseline). Every field may be `null` = no data: `face` null = no vision frame, `false` = no face;
  `typing` null = no input stats, `false` = not typing; `jitterX` = jitter / personal baseline (null while calibrating),
  `jitterPerS` raw; `precisionPct` = 100 / overshoot. `source` = display label (`sensingSource`), ≤ 40 chars.
  Phone drops out-of-range values (`SignalsSnapshot.clamped()`). No `signals` (older PC) → phone formats raw `vision` / `input`.
- `energy.startFromSleep` (0..100) and `energy.sleepHours` (3..9, clamped) let the phone draw the Energy / Recovery cards
  read-only ("Change on your PC"). Still **no new endpoint**: the companion stays one read-only `GET /state`.

- `energy.battery` = `EnergyBattery` 0..100, `breakSource` = `"USER" | "AUTO" | null`; `recovery` = `RecoveryScore` 0..100;
  `stats` = `DayStats` (see "App model"). Phone clamps them (0..100, counters ≥ 0). **Never** journal entries or notes.

- `score`, `level` (`FRESH|OK|TIRED|EXHAUSTED`), `reasons`, `calibrated`: the PC's `FatigueState`. `coach`: last `CoachMessage`
  or `null` (`kind` = `BREAK|STAND_UP|RECOVERED`). `ts`: PC time of the dashboard snapshot (epoch ms).
- `llmCalls`, `calibrationMs`, `windowMs`, `runId`: same meaning as in `DashboardState`, so the phone draws exactly the PC dashboard.
- `vision` (a `VisionFrame`, same JSON as the sidecar) and `input` (`InputStats`) are numbers only, `null` when absent.
- Kotlin: `CompanionState` in `commonMain/.../companion/Companion.kt` (`DashboardState.toCompanionState(ts)` /
  `CompanionState.toDashboardState()`), client `CompanionClient.fetch(PairingTarget)` → `Ok | WrongCode | TooManyTries | Unreachable`.
- Errors: missing/wrong code → `401`; 5 wrong codes from one IP within 60 s → `429` for 60 s (even with the right code);
  other path → `404`; any method but `GET` (incl. `OPTIONS`) → `405`; app not ready yet → `503`. Error body `{"error":"..."}`.
- Phone: polls every ~1 s (timeout 1.5 s). 3 failures in a row → "Can't reach your PC" card + local demo replay, keeps retrying.
  `401` → Profile tab "Wrong code" (stops polling, forgets the code). `429` → "Too many wrong codes".
  Phone side: `CompanionState.toAppState(local, day)` + `phoneSignals()` feed the shared phone Today (`ui/phone/`).

Security (mandatory):
- **Read-only:** one `GET`, no endpoint changes anything on the PC. Only numbers and the engine's short reason / coach strings:
  never images, audio, keystrokes, URLs or guardrail data.
- **Pairing code on every request:** 6 random digits (`SecureRandom`), new at every app start, shown on the dashboard
  (`Phone: <IP>:<port> · code 123456`) and in the console. Compared in constant time (`MessageDigest.isEqual`).
- Listens only when `EG_COMPANION=1`, on the LAN IPv4 of the default-route interface (override `EG_COMPANION_HOST`)
  + `127.0.0.1` (for `adb reverse`). Without `EG_COMPANION`, nothing listens beyond the guard server.
- No CORS headers at all (a web page can't send the `Authorization` header cross-origin without a preflight, which gets `405`).
- Logs nothing but a request counter (no IPs, no codes). Plain HTTP on the LAN: use the phone's hotspot (WPA2), allow the
  Windows Firewall prompt on **Private** networks only.

## App model (3-tab app: Today / Journal / Profile, `commonMain/.../app/`, M1)
Pure Kotlin, no Compose. One state holder for both shells (desktop M2, Android M3). The current dashboard keeps rendering
`DashboardState` exactly as before; `AppState` is a superset of it.

```kotlin
data class AppState(
    val dashboard: DashboardState,     // what the current dashboard renders (mirror, set by the shell)
    val concentration: Concentration,  // live
    val energy: EnergyState,           // battery, slow over the day
    val recovery: Int,                 // RecoveryScore 0..100
    val coach: CoachNote?,             // EnergyCoach.lastMessage + "AI" / "offline" tag
    val stats: DayStats,               // today
    val journal: List<JournalEntry>,   // newest first, max 500
    val profile: Profile,              // personal baseline
    val settings: Settings,
    val history: List<DaySummary>,     // past days, newest first, max 7 (today = stats)
    val demo: Boolean,
)
class AppStore(clock: () -> Long, dayKey: (Long) -> String, persistence: AppPersistence, demo: Boolean = false, ...) {
    val state: StateFlow<AppState>
    // feeds (shell → store)
    fun onVision(frame: VisionFrame); fun onInput(stats: InputStats, ts: Long)
    fun onFatigue(state: FatigueState, ts: Long, baseline: BaselineSummary? = null)
    fun onCoach(message: CoachMessage?, llmCallCount: Int); fun onGuardBlock(); fun setDashboard(d: DashboardState)
    fun tick(now: Long = clock())      // every ~1 s: battery, focus time, auto-break, day rollover, autosave
    // user actions
    fun startBreak(); fun endBreak()
    fun addJournalEntry(kss: Int?, stress: Int?, caffeine: Int?, note: String?): JournalEntry?
    fun deleteJournalEntry(id: Long)
    fun updateSettings(transform: (Settings) -> Settings); fun setSleepHours(hours: Double)
    fun resetRun()                     // demo F5: battery + today's counters from zero (LLM count stays real)
    fun flush()                        // save now (app exit)
}
```
**Threading:** like `FatigueEngine`, call it from one thread (the Compose main thread). The guard server's Ktor threads
must post `onGuardBlock()` to the main thread.

### Concentration (live, 0–100)
`value = 100 - FatigueState.score`, plus `level`, `calibrated` and the top-3 `reasons` of the engine.

### EnergyBattery (0–100, slow over the day, `app/EnergyBattery.kt`)
- **Start** from last night's sleep `h` (`Settings.sleepHours`): `100 - 10 * (7 - clamp(h, 3, 7))` → ≥ 7 h = 100, 5 h = 80, ≤ 3 h = 60.
  Changing the sleep hours later in the day shifts the battery by the difference of the two starts.
- **Drain** while awake and not on a break: `5 %/h` at FRESH/OK, `×2` (10 %/h) at TIRED, `×3` (15 %/h) at EXHAUSTED.
- **Refill** during a break: `+30 %/h` (5-min break ≈ +2.5, 20 min = +10). Always clamped to 0..100.
- One integration step = time since the last `tick`, capped at **60 s** (a frozen/suspended app never jumps).
- **Break** = user action `startBreak()` (until `endBreak()`), **or** auto: no face frame **and** no active input
  (`clickCount > 0 || keyCount > 0 || mouseDistancePx >= 50`) for **≥ 2 min** (real time); an auto break ends on the next face
  frame or active input. A break is counted in `breaksTaken` when it starts.
- **Demo mode** (`EG_DEMO=1`, Android demo): same model, battery time ×**120** (1 real s = 2 min), so the 2-min demo drains ≈ 30 %.
  Break detection and `DayStats` stay in real time.

### RecoveryScore (0–100, `app/Recovery.kt`)
Weighted mean of the components present, weights renormalized over them:
- **Sleep 50 %:** `clamp((h - 4) / 3, 0, 1) * 100` (4 h = 0, 5.5 h = 50, ≥ 7 h = 100: the AASM/SRS "7 or more hours"
  recommendation, the same 7 h reference as `EnergyBattery` and `SleepPrior`).
- **Breaks 30 %:** `needed = floor(focusedMs / 60 min)`; `needed == 0` → 100, else `min(1, taken / needed) * 100`.
- **Journal check-in 20 % (optional):** latest entry of today with a KSS or a stress value: mean of
  `(9 - kss) / 8 * 100` and `(5 - stress) / 4 * 100` (those present). No such entry → weights become 62.5 % / 37.5 %.

### DayStats (today, reset at the local day change or demo F5)
```kotlin
data class DayStats(
    val day: String,               // "YYYY-MM-DD", local
    val focusedMs: Long,           // present (not idle >= 2 min) and not on a break, real time
    val breaksTaken: Int, val breaksNeeded: Int,
    val longestFocusMs: Long,      // longest focus streak (a break ends a streak; short pauses < 2 min don't)
    val currentFocusMs: Long,
    val phishingBlocked: Int,      // guard Block verdicts today (inbox + Chrome extension)
    val llmCalls: Int,             // real LLM calls today
    val framesProcessed: Long,     // vision frames processed on this device
    val imagesSent: Int = 0, val audioSent: Int = 0, val keysSent: Int = 0, // always 0, by design
)
```

### Journal, settings, history, profile
```kotlin
data class JournalEntry(
    val id: Long, val ts: Long,    // id = ts (unique, +1 on collision)
    val kss: Int? = null,          // Karolinska sleepiness 1..9 (1 = very alert)
    val stress: Int? = null,       // 1..5
    val caffeine: Int? = null,     // cups today, 0..20
    val note: String? = null,      // PRIVATE: max 1000 chars, never leaves the device (no LLM, no companion)
    val concentration: Int,        // our Concentration.value at that moment
)
data class Settings(val sleepHours: Double = 7.0, val sleepDay: String? = null, val theme: ThemeMode = DARK,
                    val hapticsOn: Boolean = true, val coachOnline: Boolean = true)
data class DaySummary(val day: String, val focusedMs: Long, val breaksTaken: Int, val longestFocusMs: Long,
                      val phishingBlocked: Int, val llmCalls: Int, val framesProcessed: Long, val battery: Int,
                      val minBattery: Int, val recovery: Int, val sleepHours: Double, val avgConcentration: Int?, val checkIns: Int)
data class Profile(val baseline: BaselineSummary)
data class CoachNote(val text: String, val kind: Intervention, val fromLlm: Boolean) { val tag: String /* "AI" | "offline" */ }
```
- Invalid journal values (KSS outside 1..9, stress outside 1..5, caffeine outside 0..20) are dropped (`null`); an entry
  with nothing in it is rejected (`addJournalEntry` returns `null`).
- `sleepHours` (3..9) is synced with `SleepPrior.hours` (the header slider) on every `tick`; at start it is restored only
  if `sleepDay` is today (yesterday's night is not tonight's).
- `theme`, `hapticsOn`, `coachOnline` are stored for M2; `hapticsOn = false` already mutes haptics on desktop.

### Persistence (`app/Persistence.kt`, expect/actual)
Tiny JSON files (kotlinx.serialization), `{"version":1, ...}`: `settings.json`, `journal.json`, `history.json`.
Desktop `~/.energyguard/`, Android `filesDir`. Missing or corrupt file → defaults, never a crash (a corrupt file is copied
to `<name>.bad` first). Atomic write (temp file + rename). Nothing is sent anywhere.
- Settings / journal: saved on every change. History: today's summary saved every 60 s, on day change and on `flush()`;
  same-day restart resumes today's battery and counters.
- Desktop demo mode uses an in-memory store (nothing written). Android (demo engine) keeps journal + settings, no history.

## Desktop shortcuts vs text fields (M2b)
Window shortcuts (`1` / `2` / `3` tabs, `F2` inbox, `F5` demo replay; `desktopMain/ui/Shortcuts.kt`) are ignored while a
text field has focus. **Every text field must use `Modifier.tracksTextFocus()`** (`commonMain/ui/components/TextFieldFocus.kt`),
otherwise typing "1" in it switches tabs.

## Sources (desktop)
```kotlin
interface VisionSource { fun frames(): Flow<VisionFrame> }   // MockVisionSource, WebSocketVisionSource
interface InputSource  { fun stats(): Flow<InputStats> }     // MockInputSource, JNativeHookInputSource (EG_INPUT=real)
```
- `MockVisionSource(tiredness)` / `MockInputSource(tiredness)` take an optional `() -> Double` (0..1, default = slow oscillation).
  Demo mode (`EG_DEMO=1`) passes the scripted ramp of `demo/DemoMode.kt` and uses `FatigueEngine(calibrationMs = 15 s, windowMs = 10 s)`.
