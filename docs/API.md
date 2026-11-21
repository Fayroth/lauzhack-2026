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
- `ear`: eye aspect ratio (avg of both eyes), `eyesClosed`: ear below calibrated threshold.
- `blinkCount`: blinks in the last 60 s, `lastBlinkMs`: duration of the last blink.
- `perclos`: fraction of the last 60 s with eyes >= 80 % closed.
- `source`: which perception path produced the eye metrics:
  `"logiinsight-rgb"` (LogiInsight SDK face mesh on the RGB stream), `"logiinsight-ir"` (same on the IR stream: low light / glasses),
  `"mediapipe"` (fallback on any webcam). Mocks/fakes may send `"mock"`; older stubs may still send `"webcam"`.
- `face=false` → other fields may be absent.

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
- Eyes closed > 1 s → instant `TIRED` (reason `"eyes closed 1.2 s"`).
- Levels use hysteresis: up at 30/55/80, down below 20/45/70.

## EnergyCoach (commonMain)
Frugal generative layer: turns the fusion result into one short, personalized intervention.
LLM via OpenRouter (Ktor client, key `OPENROUTER_API_KEY` in `.env` only).

```kotlin
data class CoachInput(
    val state: FatigueState,       // score, level, reasons
    val previousLevel: Level,      // the transition that triggered the call
    val sleepPriorHours: Double?,  // from the "sleep last night" slider or Health Connect (null if unknown)
    val localTime: String,         // "HH:mm", local
)
data class CoachMessage(
    val text: String,              // one short intervention, e.g. "Eyes closing +40% at 16:20: 5-min walk, then the hard email."
    val fromLlm: Boolean,          // false = offline template fallback
)
interface EnergyCoach {
    suspend fun advise(input: CoachInput): CoachMessage
    val llmCallCount: StateFlow<Int>   // shown by the frugality counter
}
```
Rules:
- Called ONLY on level transitions (e.g. OK → TIRED), never on every frame.
- At most 1 LLM call per 10 min. Inside that window, or on any error/timeout/no key → offline template (`fromLlm = false`).
- The prompt contains numbers and short reason strings only: never images, audio, or keystroke content.
- Every real LLM call increments `llmCallCount`.

## Haptics (desktop)
```kotlin
enum class HapticPattern { GENTLE, WARNING }
interface HapticOutput { fun pulse(pattern: HapticPattern) }
```
When (`HapticTrigger`, desktopMain): GENTLE on entering TIRED, WARNING on entering EXHAUSTED, only on the way up,
at most once per 2 min (except an escalation GENTLE → WARNING, never swallowed).
Real implementation (`MxMaster4HapticOutput`, `EG_HAPTICS=mx`): sends HID report `0x11` + `[0x02, 0x0B, 0x4E, effectId, 0…]` (19 bytes) to the vendor HID collection of the MX Master 4 (046D:B042 / B048), bytes from the WebHID library. `GENTLE` / `WARNING` effect numbers can be overridden with `EG_HAPTIC_GENTLE` / `EG_HAPTIC_WARNING` (0–14). If the mouse is absent: console line + system beep. One gentle pulse at startup confirms the mouse is connected.
Also: one WARNING every time the Guardrail returns `Block` (see below).

## Guardrail (commonMain, `guardrail/Guardrail.kt`)
"Double vigilance": a risky action is checked harder when energy is low. Pure and deterministic: no ML, no network, no clock.
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

## Sources (desktop)
```kotlin
interface VisionSource { fun frames(): Flow<VisionFrame> }   // MockVisionSource, WebSocketVisionSource
interface InputSource  { fun stats(): Flow<InputStats> }     // MockInputSource, JNativeHookInputSource (EG_INPUT=real)
```
- `MockVisionSource(tiredness)` / `MockInputSource(tiredness)` take an optional `() -> Double` (0..1, default = slow oscillation).
  Demo mode (`EG_DEMO=1`) passes the scripted ramp of `demo/DemoMode.kt` and uses `FatigueEngine(calibrationMs = 15 s, windowMs = 10 s)`.
