# ⚡ Autonomous Mobile AGI Skill for Google Antigravity

High-velocity Autonomous Mobile AGI Engine for Android (Termux) & Google Antigravity.
Equips your agent with **Sub-Millisecond UI Control**, **SQLite World Model**, **Dynamic In-RAM Tool Synthesis**, and **Cross-Domain Goal Autopilot**.

---

## ⚡ 1-Step Quick Install

Run this single command in Termux:

```bash
git clone https://github.com/papertop221/autonomous-mobile-agi.git && cd autonomous-mobile-agi && ./install.sh
```

> **Automated**: The script deploys the skill directly into Antigravity's discovery directory (`~/.agents/skills/autonomous-mobile-agi/`), compiles the native C core, and sets up global CLI commands (`agi`, `agi-ctrl`, `agi-memory`). Antigravity detects it immediately.

---

## 🚀 Usage Guide

### 1. In Antigravity Chat (Natural Language)
Issue direct instructions in plain English to the agent:
- *"Open WhatsApp and send 'Ready!' to Alex"*
- *"Inspect RAM usage and clear system cache"*
- *"Search for lofi coding live streams on YouTube"*

Antigravity automatically activates the `autonomous-mobile-agi` skill and executes the request deterministically in a single turn.

---

### 2. Via Terminal CLI

| Task | Command | Example |
| :--- | :--- | :--- |
| **Autonomous Goal Solver** | `agi solve "<goal>"` | `agi solve "open whatsapp search Alex"` |
| **Cognitive Dry-Run Plan** | `agi plan "<goal>"` | `agi plan "open youtube search rust tutorial"` |
| **Native 1-Packet UI Control**| `agi-ctrl do "<action>"` | `agi-ctrl do "open yt" "tap-set 'Search' 'agi' --enter" "done"` |
| **Read/Write World Model** | `agi-memory <set\|get\|list>`| `agi-memory set "partner" '{"name": "Alex"}'` |
| **In-RAM Ephemeral C Tool** | `agi-exec c '<code>'` | `agi-exec c 'int main(){printf("Fast!\n");return 0;}'` |

---

## 🏛️ Skill Directory Structure

```
~/.agents/skills/autonomous-mobile-agi/
├── SKILL.md                          # Antigravity progressive router
├── modules/
│   ├── agi-cognitive-engine.md       # Cognitive reasoning & world model
│   ├── android-control.md            # Sub-millisecond UI effector (agi-ctrl)
│   ├── autonomous-learning.md        # Passive sensing & no-regression rule
│   ├── execution-rules.md            # Deterministic 1-turn batching rules
│   └── innovative-solver.md          # 4-Layer architectural bypass matrix
└── scripts/
    ├── agi_kernel.py                 # Core goal autopilot (`agi`)
    ├── agi_memory.py                 # SQLite memory database (`agi-memory`)
    ├── ephemeral_runner.sh           # In-RAM polyglot compiler (`agi-exec`)
    ├── fast_probe.sh                 # Throttled codebase reconnaissance (`agi-probe`)
    └── learn.sh                      # Autonomous error distillation (`agi-learn`)
```

---

## 📄 License
MIT License. Engineered for maximum autonomy, deterministic reliability, and extreme speed.
