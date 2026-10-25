#!/usr/bin/env bash
# ⚡ install.sh — 1-Line Installer for Autonomous Mobile AGI Skill
set -euo pipefail

echo "=========================================="
echo "⚡ Installing Autonomous Mobile AGI Skill"
echo "=========================================="

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SKILL_SRC="$REPO_DIR/skills/autonomous-mobile-agi"

# 1. Target Directories
mkdir -p ~/.agents/skills ~/.agents/agi ~/.agents/knowledge ~/.local/bin

# 2. Deploy Skill to Antigravity Discovery Path (~/.agents/skills/)
echo "[1/4] Deploying skill to ~/.agents/skills/autonomous-mobile-agi..."
rm -rf ~/.agents/skills/autonomous-mobile-agi
cp -r "$SKILL_SRC" ~/.agents/skills/

# 3. Compile Native C Effector (~0.4ms startup)
echo "[2/4] Compiling native sub-millisecond effector..."
if command -v clang >/dev/null 2>&1; then
    clang -O3 "$REPO_DIR/mobile-core-engine/satset.c" -o ~/.local/bin/satset
else
    echo "Notice: clang not found, using precompiled native binary if present."
    cp "$REPO_DIR/mobile-core-engine/satset" ~/.local/bin/satset 2>/dev/null || true
fi
chmod +x ~/.local/bin/satset

# 4. Link Global CLI Suite
echo "[3/4] Linking CLI tools..."
ln -sf ~/.local/bin/satset ~/.local/bin/agi-ctrl
ln -sf ~/.agents/skills/autonomous-mobile-agi/scripts/agi_kernel.py ~/.local/bin/agi
ln -sf ~/.agents/skills/autonomous-mobile-agi/scripts/agi_kernel.py ~/.local/bin/agi-run
ln -sf ~/.agents/skills/autonomous-mobile-agi/scripts/agi_memory.py ~/.local/bin/agi-memory
ln -sf ~/.agents/skills/autonomous-mobile-agi/scripts/ephemeral_runner.sh ~/.local/bin/agi-exec
ln -sf ~/.agents/skills/autonomous-mobile-agi/scripts/fast_probe.sh ~/.local/bin/agi-probe
ln -sf ~/.agents/skills/autonomous-mobile-agi/scripts/learn.sh ~/.local/bin/agi-learn
chmod +x ~/.local/bin/*

# Ensure ~/.local/bin is in PATH
if ! echo "$PATH" | grep -q "$HOME/.local/bin"; then
    echo 'export PATH="$HOME/.local/bin:$PATH"' >> ~/.bashrc
fi

# 5. Initialize Database
echo "[4/4] Initializing local World Model (SQLite)..."
python3 -I -S ~/.agents/skills/autonomous-mobile-agi/scripts/agi_memory.py init >/dev/null 2>&1 || true

echo "=========================================="
echo "✅ Autonomous Mobile AGI Skill Installed!"
echo "   Antigravity will automatically discover this skill."
echo "   Try: agi solve \"cek memori dan optimasi\""
echo "=========================================="
