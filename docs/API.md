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
  "source": "webcam"
}
```
- `ear`: eye aspect ratio (avg of both eyes), `eyesClosed`: ear below calibrated threshold.
- `blinkCount`: blinks in the last 60 s, `lastBlinkMs`: duration of the last blink.
- `perclos`: fraction of the last 60 s with eyes >= 80 % closed.
- `source`: `"webcam"` or `"ir"` (prototype).
- `face=false` → other fields may be absent.

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

## Fatigue score (commonMain)
```kotlin
data class FatigueState(
    val score: Int,              // 0 = fresh, 100 = exhausted
    val level: Level,            // FRESH, OK, TIRED, EXHAUSTED
    val reasons: List<String>,   // e.g. ["long blinks", "mouse jitter +40%"]
    val calibrated: Boolean,
)
```

## Haptics (desktop)
```kotlin
interface HapticOutput { fun pulse(pattern: HapticPattern) }   // GENTLE, WARNING
```
