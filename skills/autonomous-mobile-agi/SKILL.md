---
name: autonomous-mobile-agi
description: >-
  Unified Modular High-Velocity Engineering, Cognitive World Model & Sub-Microsecond Android Automation Engine (v5.5 SUB-ZERO).
  Combines cross-domain goal autopilot, local SQLite episodic memory, dynamic tool synthesis in RAM,
  native C sub-millisecond control (`agi-ctrl`), and lock-free sub-microsecond shared memory ring buffer (`subzero_shm.h`).
  Activate this skill for any complex coding, system optimization, unorthodox troubleshooting, or Android phone operation.
---

# ⚡ Autonomous Mobile AGI — High-Velocity Cognitive Execution Engine

**Core Philosophy**: Never take the long path when a direct architectural bypass can be fabricated in a single turn. Combines **lateral problem solving**, **deterministic 1-turn batching**, and **native sub-millisecond automation** with zero file clutter and zero conversational fluff.

---

## 🧭 Modular Architecture & Progressive Disclosure Router

This skill is designed **modularly** to preserve context window tokens. Consult specific modules below only when their domain is required:

| Domain / Requirement | Module Reference | Core Capabilities |
| :--- | :--- | :--- |
| **Cognitive AGI & Problem Solver** | [`modules/agi-cognitive-engine.md`](file:///data/data/com.termux/files/home/.agents/skills/autonomous-mobile-agi/modules/agi-cognitive-engine.md) | General Goal Decomposition, World Model (SQLite), Dynamic Tool Synthesis, Causal Memory |
| **Autonomous Continuous Learning** | [`modules/autonomous-learning.md`](file:///data/data/com.termux/files/home/.agents/skills/autonomous-mobile-agi/modules/autonomous-learning.md) | Passive Sensing, Silent Profiling, Error Distillation, Auto-Knowledge Compounding |
| **App Knowledge Atlas** | [`modules/app-atlas.md`](file:///data/data/com.termux/files/home/.agents/skills/autonomous-mobile-agi/modules/app-atlas.md) | Pre-mapped Popular Indonesian Apps (WA, Shopee, Grab, Gojek, Maps, YT), Fallback Chains |
| **Layman Intent & Safety Guide** | [`modules/layman-intent-guide.md`](file:///data/data/com.termux/files/home/.agents/skills/autonomous-mobile-agi/modules/layman-intent-guide.md) | Zero-Jargon Interaction, Raw Intent Parsing, Purchase & Financial Guardrails |
| **Innovative & Lateral Bypasses** | [`modules/innovative-solver.md`](file:///data/data/com.termux/files/home/.agents/skills/autonomous-mobile-agi/modules/innovative-solver.md) | 4-Layer Bypass Matrix, Algorithmic Offloading, Direct State/DB/Socket Injection, Speculative Heuristics |
| **Deterministic 1-Turn Execution** | [`modules/execution-rules.md`](file:///data/data/com.termux/files/home/.agents/skills/autonomous-mobile-agi/modules/execution-rules.md) | Parallel Tool Batching, Chained Self-Healing (`&&`/`||`), Surgical Context Bounds, Zero Fluff |
| **Sub-Millisecond UI Automation** | [`modules/android-control.md`](file:///data/data/com.termux/files/home/.agents/skills/autonomous-mobile-agi/modules/android-control.md) | Native C CLI `agi-ctrl` (`~0.4ms`), 1-Packet `agi-ctrl do ... "done"`, SUB-ZERO Shared Memory (200ns), O(1) Hash Matcher (2.28ns) |

### 🛠️ Built-in Scripts (`scripts/`)
- [`scripts/agi_kernel.py`](file:///data/data/com.termux/files/home/.agents/skills/autonomous-mobile-agi/scripts/agi_kernel.py): General Problem Solver & Layman Intent Engine (`agi` / `agi-run`).
- [`scripts/device_doctor.py`](file:///data/data/com.termux/files/home/.agents/skills/autonomous-mobile-agi/scripts/device_doctor.py): Layman Device Doctor, Auto-reconnect & Guided Wireless Debugging (`agi doctor` / `agi pair`).
- [`scripts/app_atlas.json`](file:///data/data/com.termux/files/home/.agents/skills/autonomous-mobile-agi/scripts/app_atlas.json): Comprehensive selector and pop-up dismissal database for top apps.
- [`scripts/subzero_shm.h`](file:///data/data/com.termux/files/home/.agents/skills/autonomous-mobile-agi/scripts/subzero_shm.h): Ultra-low latency Lock-Free ARM64 Shared Memory Ring Buffer header (200ns IPC, O(1) FNV-1a decision engine).
- [`scripts/agi_memory.py`](file:///data/data/com.termux/files/home/.agents/skills/autonomous-mobile-agi/scripts/agi_memory.py): Sub-millisecond SQLite World Model & Episodic Memory (`agi-memory`).
- [`scripts/fast_probe.sh`](file:///data/data/com.termux/files/home/.agents/skills/autonomous-mobile-agi/scripts/fast_probe.sh): 1-shot X-Ray directory and symbol scanner (`rg`) with strict output throttling (`<45 lines`).
  - Usage: `agi-probe <dir> [pattern]`
- [`scripts/ephemeral_runner.sh`](file:///data/data/com.termux/files/home/.agents/skills/autonomous-mobile-agi/scripts/ephemeral_runner.sh): In-RAM polyglot micro-compiler & benchmark runner (`clang -O3`, fast `python3`, `sqlite3`) with zero-footprint auto-cleanup.
  - Usage: `agi-exec <c|py|py-fast|bench> "<code_or_cmd>" [args...]`
- [`scripts/learn.sh`](file:///data/data/com.termux/files/home/.agents/skills/autonomous-mobile-agi/scripts/learn.sh): Records observation patterns & autonomous fixes to persistent storage.
  - Usage: `agi-learn <domain> <trigger> <solution> [confidence]`
- [`scripts/reflex_shield.sh`](file:///data/data/com.termux/files/home/.agents/skills/autonomous-mobile-agi/scripts/reflex_shield.sh): In-daemon reflex shield that auto-clears mechanical confirmation popups in 5ms without cloud turns.
  - Usage: `agi-reflex "<step1>" "<step2>..."`

---

## ⚡ The 5 Golden Mandates (Always Active)

1. **Autonomous Continuous Learning & Observation**:
   - Silently observe OS architecture, daemon state, dynamic UI hierarchy, and execution bottlenecks.
   - Automatically distill error post-mortems and record self-healing bypass paths via `agi-learn` without waiting for manual instructions.
2. **Think Laterally Before Acting (Innovation > Convention)**:
   - Before executing lengthy standard procedures, ask: *"Can this layer be bypassed directly via raw sockets, SQLite/JSON state mutation, Android Intents, or in-RAM C micro-binaries?"*
3. **Deterministic 1-Turn Execution (Batching + Self-Healing)**:
   - Invoke multiple independent tool calls (`read_file`, `edit_file`, `write_file`) **in parallel in a single turn**.
   - In shell commands, chain action + verification + fallback: `primary_action && instant_verify || fallback_path`.
4. **Surgical Context & Zero Fluff**:
   - Always restrict file bounds with `StartLine`/`EndLine` and pipe shell outputs (`| head -n 35`).
   - Zero pleasantries or conversational filler. Output actionable results, metrics, and file links directly (`[name](file:///path)`).
5. **1-Packet Zero-Footprint Android Control (`agi-ctrl`)**:
   - All Android UI operations MUST use `agi-ctrl do ... "done"` in a single TCP packet, leverage `"tap-set '<target>' '<text>' --enter"`, and automatically return to Termux while purging ephemeral artifacts.

---

## 🚀 Quick-Action Cheatsheet

```bash
# 1. Codebase Reconnaissance (Low-Token X-Ray)
agi-probe . "TargetSymbol"

# 2. On-the-Fly Native C Micro-Tool (Compile in RAM -> Execute -> Auto-Delete)
agi-exec c '
#include <stdio.h>
int main() { printf("Zero-footprint C execution!\n"); return 0; }
'

# 3. Empirical Micro-Benchmarking
agi-exec bench "agi-ctrl status"

# 4. Layman Direct Intent Execution (Zero-Jargon)
agi "pesan ayam geprek di gofood cari promo diskon"
agi "wa mama bilang otw pulang"

# 5. Device Doctor & Wireless Pairing Assistant
agi doctor
agi pair <port> <pairing_code>

# 6. 1-Packet Android App Automation (~8ms dispatch)
agi-ctrl do "open whatsapp" "tap 'Alex'" "set 'Ready to roll! 🔥'" "tap 'Send || id:send'" "done"

# 7. Atomic Search Input on Android
agi-ctrl do "open youtube" "tap-set 'Search || id:search' 'autonomous mobile agi' --enter" "done"
```
