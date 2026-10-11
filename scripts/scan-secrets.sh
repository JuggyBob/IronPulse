#!/usr/bin/env bash
#
# Fails if anything credential-shaped has been committed, or if a file that must never be tracked
# has been.
#
# Deliberately not a hosted scanning action. Those either need a licence key on an organisation
# repository or upload a report off-platform. This repository has exactly one secret-shaped risk
# worth checking — a Gemini API key, entered at runtime — so the patterns are matched here, where
# they can be read, audited and run locally against the same history CI sees.
#
# Usage:  scripts/scan-secrets.sh            (from the root of a git checkout)
# Exit:   0 = clean, 1 = something was found.

set -uo pipefail

# Credential shapes. Deliberately narrow: a match has to look like a real key, not merely contain
# the word "password", or the scan cries wolf and gets ignored.
PATTERNS='AIza[0-9A-Za-z_-]{35}|gh[pousr]_[0-9A-Za-z]{36,255}|-----BEGIN [A-Z ]*PRIVATE KEY-----|xox[baprs]-[0-9A-Za-z-]{10,}|AKIA[0-9A-Z]{16}'

# Files that legitimately match because they document the pattern itself.
ALLOW_PATHS='^\.github/workflows/secret-scan\.yml$|^\.env\.example$|^\.gitignore$|^scripts/scan-secrets\.sh$'

hits=$(mktemp)
trap 'rm -f "$hits"' EXIT

failed=0

# Scan the working tree rather than only HEAD. A secret that is committed and then deleted is still
# reachable in history, which is what `git log -p` below covers; this catches the common case of
# forgetting to unstage a file before committing.
git grep -nI -E "$PATTERNS" -- . >"$hits" 2>/dev/null || true

while IFS= read -r hit; do
    [ -z "$hit" ] && continue
    file=${hit%%:*}
    if printf '%s' "$file" | grep -Eq "$ALLOW_PATHS"; then
        continue
    fi
    printf '::error file=%s::Possible committed secret on this line.' "$file"
    failed=1
done <"$hits"

# Then every commit in the history, so a key that was added and removed is still caught. The
# removal is the point: it looks like the leak has been dealt with, and it has not.
git log --all -p --no-color -- . 2>/dev/null \
    | grep -nE "$PATTERNS" \
    | while IFS= read -r hit; do
        printf '::error::Credential-shaped string in the commit history (not just the tip).'
        failed=1
        break
    done \
    || true

# Files that must never be tracked, whatever they contain.
for forbidden in .env local.properties; do
    if git ls-files --error-unmatch "$forbidden" >/dev/null 2>&1; then
        printf '::error::%s is tracked and must never be committed.\n' "$forbidden"
        failed=1
    fi
done

for glob in '*.jks' '*.keystore' '*.p12' '*.pem'; do
    # `git ls-files` takes globs literally, so expand in the shell and match the results.
    matched=$(git ls-files | grep -E "${glob//./\\.}$" || true)
    if [ -n "$matched" ]; then
        printf '::error::A key or certificate (%s) is tracked: %s\n' "$glob" "$matched"
        failed=1
    fi
done

if [ "$failed" -ne 0 ]; then
    echo
    echo "A credential-shaped string or a forbidden file is present."
    echo "Remove it, then rotate the key — removing it from the repository is not enough once it"
    echo "has been committed."
    exit 1
fi

echo "No secrets found."