#!/data/data/com.termux/files/usr/bin/bash
# fast_probe.sh — 1-shot low-token directory & symbol reconnaissance
set -euo pipefail
TARGET="${1:-.}"
PATTERN="${2:-}"

echo "=== TREE (depth 2, max 40) ==="
find "$TARGET" -maxdepth 2 -not -path '*/.*' 2>/dev/null | head -n 40

if [ -n "$PATTERN" ]; then
  echo "=== MATCHES ($PATTERN) ==="
  if command -v rg >/dev/null 2>&1; then
    rg -n --max-count 5 --max-columns 140 "$PATTERN" "$TARGET" 2>/dev/null | head -n 40 || true
  else
    grep -rnI "$PATTERN" "$TARGET" 2>/dev/null | cut -c1-140 | head -n 40 || true
  fi
fi
