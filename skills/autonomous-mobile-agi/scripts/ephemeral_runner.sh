#!/data/data/com.termux/files/usr/bin/bash
# ephemeral_runner.sh — Zero-Footprint In-RAM Polyglot Micro-Tool Compiler & Benchmark Runner
set -euo pipefail

MODE="${1:-help}"
CODE="${2:-}"
shift $(( $# >= 2 ? 2 : $# ))

RAM_BASE="/data/data/com.termux/files/usr/tmp"
[ -d "/dev/shm" ] && [ -w "/dev/shm" ] && RAM_BASE="/dev/shm"
WORK_DIR="${RAM_BASE}/.satset_ephemeral_$$"
mkdir -p "$WORK_DIR"
trap 'rm -rf "$WORK_DIR" >/dev/null 2>&1 || true' EXIT INT TERM

case "$MODE" in
  c)
    BIN="$WORK_DIR/micro_bin"
    printf "%s\n" "$CODE" | clang -O3 -pipe -x c - -o "$BIN"
    "$BIN" "$@"
    ;;
  py-fast)
    python3 -I -S -c "$CODE" "$@"
    ;;
  py)
    python3 -c "$CODE" "$@"
    ;;
  bench)
    python3 -I -S -c '
import subprocess, time, sys
cmd = sys.argv[1]
iters = int(sys.argv[2]) if len(sys.argv) > 2 else 5
times = []
for _ in range(iters):
    t0 = time.perf_counter()
    subprocess.run(cmd, shell=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    times.append((time.perf_counter() - t0) * 1000.0)
print(f"BENCH ({iters}x): min={min(times):.2f}ms | avg={sum(times)/len(times):.2f}ms | max={max(times):.2f}ms")
' "$CODE" "$@"
    ;;
  *)
    echo "Usage: ephemeral_runner.sh <c|py|py-fast|bench> \"<code_or_cmd>\" [args...]"
    exit 1
    ;;
esac
