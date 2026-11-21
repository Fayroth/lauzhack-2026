# CLAUDE.md — LauzHack 2026 project

You are working with a team of 4 EPFL students (Ali, Mehdi, Zyad, Isma) at **LauzHack 2026**
(EPFL, BC building), a 24-hour hackathon. Every teammate runs their own Claude Code on this repo,
so this file is the shared rulebook for all agents. Follow it strictly.

## Hard deadlines (Europe/Zurich time)
- Sat 21 Nov, 12:00 — hacking starts
- Sun 22 Nov, ~08:00 — FEATURE FREEZE (bug fixes, polish, demo only after this)
- Sun 22 Nov, 12:00 — hacking ends: everything must be committed, pushed and submitted
- Sun 22 Nov, 12:30–15:00 — judging (live presentation to jury + sponsors)

If the user asks for a new feature after the freeze, remind them of the freeze before doing it.

## The project
- **Working name:** EnergyGuard (rename freely)
- **One-sentence pitch:** Your devices notice when you're getting tired (eyes, mouse, keyboard), help you before you crash, and raise your guard when a tired mistake would cost you (phishing click, wrong payment, wrong email).
- **Challenges targeted:** Logitech (main: webcam as the eyes & ears of AI, real-time multimodal inputs, productivity), JetBrains (Kotlin Multiplatform, app on >= 2 platforms), AXA (frugal multimodal AI). Main prize.
- **Hardware we have:** Logitech exclusive webcam prototype (IR, ambisonic mics, NPU, 6DoF IMU) + MX Master 4 (haptic feedback).
- **Demo story (2 min):**
  1. Live dashboard: energy gauge at 100%, signals streaming (eyes, mouse, keyboard).
  2. A teammate "gets tired": long blinks, eyes half closed, shaky/imprecise mouse, more typos → the gauge drops in real time.
  3. The MX Master 4 vibrates gently + suggestion "5-min break" on screen.
  4. Guardrail: while the gauge is low, he clicks a phishing link in our demo inbox → blocked with a clear warning + haptic pulse. "Tired humans click on phishing: we raise the guard exactly when you lower yours."
  5. Frugality counter: "all signals computed locally, 1 LLM call per hour, 0 images leave the PC".

### Scope — be ruthless, we have ~20 h
- **P0 (must work by Sat 20:00):** webcam eye metrics (EAR, blinks, PERCLOS) + mouse metrics → fatigue score with personal baseline → desktop dashboard with live gauge → haptic alert.
- **P1:** keyboard metrics (typing rhythm, backspace rate — timings/counts only, NEVER key contents) + guardrail demo inbox + prototype IR stream (works in the dark / with glasses).
- **P2 (only if P0+P1 are solid):** hourly LLM summary ("your energy today"), second platform (Android "energy weather" companion), ambisonic yawn/sigh detection, Koog shared agent logic.
- Anything not in this list: say no, or ask the user.

### Stack & architecture
- **Desktop app first:** Kotlin Multiplatform + Compose Multiplatform (Desktop/JVM target). Second target (Android) added later for the JetBrains "2 platforms" requirement — keep logic in `commonMain` so it is cheap.
- **`commonMain` (shared, the brain):** data models (`SensorFrame`, `InputStats`), `FatigueEngine` (baseline calibration during the first ~60 s, rolling windows, weighted score 0–100), `Guardrail` rules, LLM client via Ktor (P2).
- **`desktopMain`:** global mouse/keyboard collectors (JNativeHook), WebSocket client to the vision sidecar, haptic bridge to the MX Master 4, Compose UI (dashboard, gauge, alert overlay, demo inbox).
- **Vision sidecar (Python, `vision/`):** webcam / prototype IR → MediaPipe Face Mesh → EAR, blink duration/rate, PERCLOS, yawn (mouth ratio), head pose → JSON over WebSocket `ws://localhost:8765` at ~10 Hz. Rationale: MediaPipe and the prototype SDK are easiest from Python; the Kotlin app stays clean. Contract in `docs/API.md`.
- **Privacy & security by design:** no image or audio is ever stored or sent; only numeric features leave the sidecar. No keystroke content is ever recorded. API keys only in `.env`.
- Hardware SDK details (prototype IR access, MX Master 4 haptics API) are unknown at start: isolate them behind small interfaces (`HapticOutput`, `FrameSource`) with a mock implementation, so nobody is blocked.

## Judging rule that matters for how we work
LauzHack rules allow reusing existing code/resources, but the project must be **novel compared to the
resources it uses**, and we must make that novelty explicit to the jury.
Therefore:
- Whenever you add a dependency, template, dataset, API or copied snippet, add one line to
  `docs/BUILD_LOG.md` under "Reused resources" (what + link + why).
- Whenever a meaningful piece of our own work is done, add one line under "What we built" with the time.
- Never present boilerplate or library features as our own work.

## Priorities (in this order)
1. **The demo works end to end** (the "happy path"). Ugly but working beats beautiful but broken.
2. The demo looks clear on a projector (big fonts, obvious result).
3. Everything else. Hardcode, mock or fake anything the jury will not see.

Do not over-engineer: no auth systems, no microservices, no exhaustive tests, no premature abstractions,
unless the demo depends on it. Prefer boring, well-known libraries we can debug fast.

## Team workflow (pull requests)
- NEVER commit or push directly to `main`. `main` must ALWAYS run and be demoable.
- One branch per task: `<name>/<topic>` (e.g. `ali/scam-detector`, `mehdi/home-screen`). Small PRs, merged within a few hours.
- Ship with `/ship`: secret check, BUILD_LOG update, rebase on `origin/main`, PR, auto-merge (squash) when CI is green.
- Run `/sync` often (at least every hour) to rebase on the latest `main` and avoid big conflicts.
- CI (`.github/workflows/ci.yml`) adapts to the stack (Gradle/KMP, Node, Python) and always runs a secret scan (gitleaks). The required check is `ci-ok`. If CI fails on your PR, fix it before anything else.
- Each person owns one area (see `docs/PLAN.md` → Roles) and mostly edits their own folder, to avoid merge conflicts.
- Contracts between parts (API routes, JSON shapes, shared Kotlin interfaces) live in `docs/API.md`. Update it BEFORE changing an interface, and tell the user to warn the teammate who depends on it.
- Commit messages: short, imperative (`add link reputation check`). Never force-push `main`, never rewrite shared history.
- Reviews: CODEOWNERS requests the whole team; a quick look is welcome but don't block on it, CI is the gate.

## Security & secrets (we're a cybersecurity team — no embarrassing leaks)
- API keys go in `.env` only (git-ignored). Keep `.env.example` updated with the variable NAMES.
- Never print, log, commit or paste a secret into code, docs or commit messages.
- Before each commit, check the diff for keys/tokens. If one was committed, tell the user immediately
  so they can rotate it (deleting it in a later commit is not enough).
- Validate untrusted input at the boundaries; don't `eval` user content; don't disable TLS.

## How to work with the user
- Be fast and concrete. Propose the simplest approach, then do it.
- Before a large change (>~150 lines or touching another teammate's area), state the plan in 3 lines first.
- After finishing a task: say how to run/test it in one or two commands.
- Keep `README.md` "How to run" accurate whenever setup steps change.

## Useful custom commands
- `/ship` — open a PR for my work, with auto-merge when CI is green.
- `/sync` — rebase my branch on the latest `main`.
- `/checkpoint` — log progress, check for secrets, commit (on my branch).
- `/demo-check` — verify the demo path works from a clean start and list what's broken.
- `/pitch` — draft the pitch and README from the current state of the project.
