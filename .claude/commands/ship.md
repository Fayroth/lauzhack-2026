---
description: Ouvre une PR propre pour mon travail en cours et attend que la CI soit verte
---
Ship my current work as a pull request:

1. If I'm on `main`, create a branch `<my-name>/<short-topic>` and move my changes there. Never commit to `main` directly.
2. Run `git diff` and scan it for secrets (API keys, tokens, passwords, private keys). If you find one, STOP and tell me.
3. Make sure the project still builds/runs (the quickest relevant command). Fix obvious breakages.
4. Update `docs/BUILD_LOG.md` (what was built + any reused resource) and `docs/API.md` if an interface changed.
5. Commit with a short imperative message, `git fetch origin && git rebase origin/main`, resolve conflicts with me if any, then push the branch.
6. Create the PR with `gh pr create --fill-first` (or `--title`/`--body` following `.github/pull_request_template.md`), with a clear "How to test".
7. Do NOT enable auto-merge (it is disabled on this private repo). Instead, wait for the CI checks with `gh pr checks --watch`. If they pass, tell me "CI green, ready to squash-merge". If one fails, show me the failure (`gh run view --log-failed`), fix it, push, and watch again.
8. Give me the PR URL and tell me which teammate should look at it if it touches their area.

$ARGUMENTS
