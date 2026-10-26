#!/data/data/com.termux/files/usr/bin/bash
# fast_probe.sh v2.1 — 1-Shot Surgical Directory, Git & Symbol X-Ray (<45 lines output)
set -euo pipefail

TARGET="${1:-.}"
PATTERN="${2:-}"

echo "=== [1] STRUCTURE ($TARGET, depth<=2, max 25) ==="
find "$TARGET" -maxdepth 2 \( -name '.git' -o -name 'node_modules' -o -name '__pycache__' \) -prune -o -print 2>/dev/null | sort | head -n 25

if git -C "$TARGET" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "=== [2] GIT STATUS (short, max 10) ==="
  git -C "$TARGET" status -sb 2>/dev/null | head -n 10
fi

if [ -n "$PATTERN" ]; then
  echo "=== [3] MATCHES ('$PATTERN', max 25) ==="
  if command -v rg >/dev/null 2>&1; then
    rg -n --no-heading --max-count 4 --max-columns 140 "$PATTERN" "$TARGET" 2>/dev/null | head -n 25 || true
  else
    grep -rnI "$PATTERN" "$TARGET" 2>/dev/null | cut -c1-140 | head -n 25 || true
  fi
fi
