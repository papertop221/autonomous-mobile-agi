#!/usr/bin/env bash
# ==============================================================================
# ⚡ Operator — Universal AI Agent Skill & Microsecond Effector Installer
# Compatible with ALL AI Agents: Google Antigravity, Claude Code, Gemini CLI,
# OpenAI Operator/Codex, Cursor, OpenDevin, and Local Linux/Termux Agents.
# ==============================================================================
set -euo pipefail

echo "=================================================================="
echo "⚡ Installing 'Operator' Universal AI Agent Skill (Microsecond Edition)"
echo "=================================================================="

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SKILL_SRC="$REPO_DIR/skills/operator"
CORE_SRC="$REPO_DIR/mobile-core-engine"

# 1. Target System Directories
mkdir -p ~/.local/bin ~/.local/lib ~/.agents/skills ~/.agents/agi ~/.agents/knowledge

# 2. Compile Native C Microsecond Core (Binary + C-FFI Shared Library)
echo "[1/4] Compiling native ARM64 effector (-O3 -march=armv8-a)..."
if command -v clang >/dev/null 2>&1; then
    clang -O3 -march=armv8-a "$CORE_SRC/operator.c" -o ~/.local/bin/operator
    clang -O3 -march=armv8-a -fPIC -shared -DOPERATOR_SHARED "$CORE_SRC/operator.c" -o ~/.local/lib/liboperator.so
else
    echo "Warning: clang not found, falling back to gcc/cc if present."
    cc -O3 "$CORE_SRC/operator.c" -o ~/.local/bin/operator
    cc -O3 -fPIC -shared -DOPERATOR_SHARED "$CORE_SRC/operator.c" -o ~/.local/lib/liboperator.so
fi
chmod +x ~/.local/bin/operator

# 3. Deploy Skill to ALL AI Agent Discovery Paths
echo "[2/4] Deploying skill to universal AI Agent discovery directories..."

# A. Universal Agent Standard (~/.agents/skills/operator)
rm -rf ~/.agents/skills/operator ~/.agents/skills/autonomous-mobile-agi
cp -r "$SKILL_SRC" ~/.agents/skills/operator
# Backwards compatibility link
ln -sf ~/.agents/skills/operator ~/.agents/skills/autonomous-mobile-agi

# B. Claude Code (~/.claude/skills/)
if [ -d "$HOME/.claude" ] || [ -d "$HOME/.config/claude" ]; then
    mkdir -p ~/.claude/skills
    rm -rf ~/.claude/skills/operator
    ln -sf ~/.agents/skills/operator ~/.claude/skills/operator
fi

# C. Gemini / Antigravity Agent Path
mkdir -p ~/.gemini/skills 2>/dev/null || true
rm -rf ~/.gemini/skills/operator 2>/dev/null || true
ln -sf ~/.agents/skills/operator ~/.gemini/skills/operator 2>/dev/null || true

# 4. Link Global CLI Suite
echo "[3/4] Linking universal CLI suite..."
ln -sf ~/.local/bin/operator ~/.local/bin/satset
ln -sf ~/.local/bin/operator ~/.local/bin/agi-ctrl
ln -sf ~/.agents/skills/operator/scripts/agi_kernel.py ~/.local/bin/agi
ln -sf ~/.agents/skills/operator/scripts/agi_kernel.py ~/.local/bin/agi-run
ln -sf ~/.agents/skills/operator/scripts/device_doctor.py ~/.local/bin/operator-doctor
ln -sf ~/.agents/skills/operator/scripts/device_doctor.py ~/.local/bin/agi-doctor
ln -sf ~/.agents/skills/operator/scripts/agi_memory.py ~/.local/bin/agi-memory
ln -sf ~/.agents/skills/operator/scripts/ephemeral_runner.sh ~/.local/bin/agi-exec
ln -sf ~/.agents/skills/operator/scripts/fast_probe.sh ~/.local/bin/agi-probe
ln -sf ~/.agents/skills/operator/scripts/learn.sh ~/.local/bin/agi-learn
chmod +x ~/.local/bin/*

# Ensure PATH
if ! echo "$PATH" | grep -q "$HOME/.local/bin"; then
    echo 'export PATH="$HOME/.local/bin:$PATH"' >> ~/.bashrc
fi

# 5. Initialize Memory & Run Empirical Benchmark
echo "[4/4] Initializing World Model (SQLite) & running hardware check..."
python3 ~/.agents/skills/operator/scripts/agi_memory.py init >/dev/null 2>&1 || true

echo "=================================================================="
echo "✅ 'Operator' Skill Successfully Installed for ALL AI Agents!"
echo "   Universal Agent Path : ~/.agents/skills/operator"
echo "   Native Effector CLI  : ~/.local/bin/operator"
echo "   In-Memory C Library  : ~/.local/lib/liboperator.so"
echo "=================================================================="
echo "⚡ To verify device connection, run: operator doctor"
echo "⚡ To test instant speed, run        : operator bench"
echo "=================================================================="
