---
description: Log progress, check the diff for secrets, and commit
---
Do a checkpoint of my current work:

1. Run `git status` and `git diff` to see what changed.
2. Scan the diff for secrets (API keys, tokens, passwords, private keys). If you find any, STOP and tell me — do not commit.
3. Add one line to `docs/BUILD_LOG.md` → "What we built" with the current time, my name and what was done. If new libraries, APIs, datasets or snippets were added, add them under "Reused resources".
4. Update `README.md` "How to run" if setup steps changed.
5. `git add` the relevant files and commit with a short imperative message.
6. `git pull --rebase`; if there are conflicts, show them to me and propose a resolution before continuing.
7. Ask me before running `git push`.

$ARGUMENTS
