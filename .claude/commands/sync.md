---
description: Recupere le dernier main et rebase ma branche dessus
---
1. `git stash` if I have uncommitted changes.
2. `git fetch origin`, then if I'm on a feature branch `git rebase origin/main` (if on main: `git pull --rebase`).
3. `git stash pop` if needed. If there are conflicts, show them and propose a resolution before continuing.
4. Tell me in 2 lines what changed on main since my last sync (from `git log`).

$ARGUMENTS
