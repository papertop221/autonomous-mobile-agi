#!/usr/bin/env bash
# satset-learn: Append autonomous observation/knowledge pattern into persistent store
set -euo pipefail

STORE_DIR="$HOME/.agents/knowledge"
STORE_FILE="$STORE_DIR/learned_patterns.jsonl"
mkdir -p "$STORE_DIR"

DOMAIN="${1:-system}"
TRIGGER="${2:-observation}"
SOLUTION="${3:-}"
CONFIDENCE="${4:-0.95}"
TIMESTAMP=$(date +%s)

if [ -z "$SOLUTION" ]; then
    echo "Usage: satset-learn <domain> <trigger> <solution> [confidence]" >&2
    exit 1
fi

python3 -I -S -c '
import sys, json
entry = {
    "ts": int(sys.argv[1]),
    "domain": sys.argv[2],
    "trigger": sys.argv[3],
    "solution": sys.argv[4],
    "confidence": float(sys.argv[5])
}
with open(sys.argv[6], "a") as f:
    f.write(json.dumps(entry) + "\n")
' "$TIMESTAMP" "$DOMAIN" "$TRIGGER" "$SOLUTION" "$CONFIDENCE" "$STORE_FILE"

echo "[satset-learn] Logged autonomous pattern ($DOMAIN): $TRIGGER -> $SOLUTION"
