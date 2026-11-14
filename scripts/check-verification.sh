#!/usr/bin/env bash
# check-verification.sh — release gate: refuse to ship an unverified release.
#
# Usage: scripts/check-verification.sh <tag>   (e.g. v1.4.3)
#
# Fails (exit 1, clear message) when:
#   - docs/verification/<tag>.md is missing
#   - it still contains unchecked boxes (`- [ ]` / `| [ ] |`) or "TODO"
#   - "All gates passed" is not **yes**
#   - section B has no numeric Janky-frames value, or any value is over
#     budget (<5% latest API, <10% lowest API)
#   - docs/screenshots/<tag>/ lacks the full matrix (16 scene + 6 locale)
set -euo pipefail

MSG="Release blocked: verification record incomplete — see RELEASING.md."
TAG="${1:?usage: $0 <tag>}"
FILE="docs/verification/$TAG.md"
DIR="docs/screenshots/$TAG"

fail() { echo "::error::$MSG ($*)" >&2; exit 1; }

[ -f "$FILE" ] || fail "missing $FILE"

if grep -qE '\- \[ \]|\| \[ \] \|' "$FILE"; then
  fail "unchecked boxes in $FILE"
fi
if grep -qi 'TODO' "$FILE"; then
  fail "TODO present in $FILE"
fi
if ! grep -q 'All gates passed: \*\*yes\*\*' "$FILE"; then
  fail "sign-off is not yes in $FILE"
fi

# Section B: budget check (latest <5%, lowest <10%).
python - "$FILE" <<'PYEOF'
import re, sys

t = open(sys.argv[1], encoding="utf-8").read()
rows = re.findall(r"\| List scroll 10 s, Calm, ([^|]+)\|[^|]*\|[^|]*\| *([0-9.]+)% *", t)
budgets = {"latest": 5.0, "lowest": 10.0}
errors = []
if len(rows) < 2:
    errors.append("no numeric Janky-frames values found in section B")
for label, value in rows:
    cap = budgets["lowest" if "lowest" in label else "latest"]
    if float(value) >= cap:
        errors.append(f"{label.strip()} janky {value}% over budget ({cap}%)")
for e in errors:
    print("::error::Release blocked: verification record incomplete — see RELEASING.md. (%s)" % e)
sys.exit(1 if errors else 0)
PYEOF

# Screenshot matrix.
count=$(ls "$DIR" 2>/dev/null | grep -cE '^scene-(monk|lotus|temple|abstract)-(day|night)-(light|dark)\.png$' || true)
[ "$count" -eq 16 ] || fail "scene screenshots: $count/16 in $DIR"
count=$(ls "$DIR" 2>/dev/null | grep -cE '^locale-(en|zh-Hans-CN|ms|th|my|si)\.png$' || true)
[ "$count" -eq 6 ] || fail "locale screenshots: $count/6 in $DIR"

echo "Verification record gate passed for $TAG."
