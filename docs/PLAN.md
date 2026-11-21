# Plan — LauzHack 2026 · EnergyGuard

## Roles (adjust if needed)
| Person | Area | Folder |
|--------|------|--------|
| Ali    | KMP project setup, `commonMain` FatigueEngine + Guardrail, CI, integration | `composeApp/src/commonMain` |
| Mehdi  | Vision sidecar: MediaPipe Face Mesh, EAR/PERCLOS/blinks/yawn, prototype IR | `vision/` |
| Zyad   | Input collectors (mouse/keyboard via JNativeHook) + MX Master 4 haptics | `composeApp/src/desktopMain/.../input`, `.../haptics` |
| Isma   | Compose desktop UI (dashboard, gauge, alert overlay, demo inbox), pitch & demo video | `composeApp/src/commonMain/.../ui` |

## Checkpoints (Zurich time)
| When | Goal | Done? |
|---|---|---|
| Sat 13:00 | KMP template pushed (Desktop target runs). `docs/API.md` agreed. Everyone on a branch | [ ] |
| Sat 15:00 | Each part works alone with mock data (sidecar prints JSON, collectors print stats, UI shows a fake gauge) | [ ] |
| Sat 17:30 | Sidecar → app over WebSocket; real gauge moves with eyes + mouse | [ ] |
| Sat 20:00 | **P0 done**: calibration + score + haptic alert, demo path works end to end | [ ] |
| Sat 23:30 | P1: keyboard metrics, guardrail demo inbox, IR stream | [ ] |
| Night | Sleep in shifts (3–4 h each). Someone always keeps `main` green | [ ] |
| Sun 08:00 | **FEATURE FREEZE** (P2 only if everything above is solid) | [ ] |
| Sun 09:30 | Slides + backup demo video recorded | [ ] |
| Sun 11:00 | README final, BUILD_LOG complete, repo public if required, submitted | [ ] |
| Sun 12:00 | Hacking ends · 12:30 judging (main jury + Logitech + JetBrains + AXA) | [ ] |

## Pitch (2–3 min)
1. Hook: "Tired people make mistakes — and attackers know it." (15 s)
2. Live demo: gauge drops → mouse vibrates → phishing click blocked (90 s)
3. How: multimodal fusion on-device, frugal (1 LLM call/hour), privacy by design, KMP shared brain (30 s)
4. Impact + what's next (15 s)

## Open questions
- Prototype: how to read the IR stream / which SDK on Windows? (ask Logitech booth)
- MX Master 4: how to trigger haptics from our app? (ask Logitech booth)
