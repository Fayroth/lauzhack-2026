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
<!-- FILL IN after choosing (around 11:30) -->
- **Name:** TBD
- **One-sentence pitch:** TBD
- **Sponsor challenge targeted (if any):** TBD
- **The demo story (what the jury will see, step by step):** TBD
- **Stack:** TBD

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

## Team workflow
- `main` must ALWAYS run and be demoable.
- Each person owns one area (see `docs/PLAN.md` → Roles) and mostly edits their own folder, to avoid
  merge conflicts.
- Small commits, often. Before pushing: `git pull --rebase`, make sure it still runs, then push.
- Short-lived branches are fine (`ali/scanner`, `mehdi/frontend`), merged within a few hours.
- Commit messages: short, imperative (`add link reputation check`).
- Never force-push to `main`. Never rewrite shared history.
- Contracts between parts (API routes, JSON shapes) live in `docs/API.md`. Update it BEFORE changing
  an interface, and tell the user to warn the teammate who depends on it.

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
- `/checkpoint` — log progress, check for secrets, commit.
- `/demo-check` — verify the demo path works from a clean start and list what's broken.
- `/pitch` — draft the pitch and README from the current state of the project.
