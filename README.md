<p align="center">
  <h1 align="center">⚡ Autonomous Mobile AGI Engine</h1>
  <p align="center">
    <strong>A Sub-Millisecond Autonomous Problem Solver, Cognitive World Model & Native Android Effector</strong>
  </p>
  <p align="center">
    <a href="#core-capabilities">Capabilities</a> •
    <a href="#architecture">Architecture</a> •
    <a href="#installation--setup">Setup</a> •
    <a href="#cli-reference">CLI Reference</a> •
    <a href="#benchmark">Benchmarks</a>
  </p>
</p>

---

## 🌟 Overview

**Autonomous Mobile AGI Engine** is a high-velocity, general-purpose cognitive operating layer engineered specifically for Android (Termux) and modern AI agents (e.g. Google Antigravity).

Unlike traditional chat assistants, this engine functions as an **Autonomous AGI Core**:
1. **General Problem Solving (GPS)**: Accepts abstract, cross-domain goals and decomposes them dynamically across OS kernel, filesystem, and Android UI layers.
2. **Sub-Millisecond Native Execution**: Built upon a persistent native C daemon protocol (`satset`, ~0.4ms overhead), bypassing bloated ADB bridges.
3. **Episodic World Model (`agi.db`)**: High-speed local SQLite knowledge graph storing entities, habits, causal failure analyses, and synthesized tools.
4. **Dynamic Tool Fabrication**: Synthesizes and executes ephemeral `clang -O3` C or fast Python micro-binaries in RAM on-the-fly.
5. **Autonomous Continuous Learning**: Silently observes system state and UI hierarchy changes, automatically distilling error post-mortems without manual intervention.

---

## 🏛️ System Architecture

```mermaid
flowchart TD
    subgraph PERCEPTION ["Perception & Telemetry Layer"]
        P1["UI Hierarchy Parser (Native C Socket)"]
        P2["OS & Kernel Telemetry (Memory, Net, Processes)"]
        P3["Abstract Goal Ingestion"]
    end

    subgraph COGNITION ["Cognitive & Metacognitive Kernel"]
        C1["Goal Decomposer (Multi-Domain Planner)"]
        C2["Speculative Heuristic Router (Bypass vs UI)"]
        C3["Self-Reflection & Causal Reasoning"]
    end

    subgraph MEMORY ["World Model & Episodic Graph"]
        M1["SQLite Local Store (~/.agents/agi/agi.db)"]
        M2["Learned Pattern Registry (~/.agents/knowledge)"]
        M3["Synthesized Micro-Tools Index"]
    end

    subgraph ACTION ["Omni-Effector Layer"]
        A1["Satset Native C Pipeline (Android UI Control)"]
        A2["Direct OS Intent & Raw Socket Injections"]
        A3["In-RAM Ephemeral C / Python Micro-Compilers"]
    end

    PERCEPTION --> COGNITION
    COGNITION <--> MEMORY
    COGNITION --> ACTION
    ACTION --> COGNITION
```

---

## ⚡ Core Capabilities

| Capability | Module Reference | Primary Function |
| :--- | :--- | :--- |
| **AGI Cognitive Solver** | [`modules/agi-cognitive-engine.md`](skills/autonomous-mobile-agi/modules/agi-cognitive-engine.md) | Abstract goal decomposition, tree-of-thought routing, and causal memory recording. |
| **Autonomous Learning** | [`modules/autonomous-learning.md`](skills/autonomous-mobile-agi/modules/autonomous-learning.md) | Passive environment sensing, silent profiling, and zero-regression error distillation. |
| **Lateral Solver & Bypasses** | [`modules/innovative-solver.md`](skills/autonomous-mobile-agi/modules/innovative-solver.md) | 4-Layer architectural bypass (raw sockets, direct SQLite mutations, deep links). |
| **Sub-Millisecond UI Engine** | [`modules/android-control.md`](skills/autonomous-mobile-agi/modules/android-control.md) | 1-Packet TCP Android control (`satset do ...`), atomic input, and zero-leak memory management. |
| **Deterministic 1-Turn Rules** | [`modules/execution-rules.md`](skills/autonomous-mobile-agi/modules/execution-rules.md) | Parallel batching, chained self-healing (`&&`/`||`), and strict context throttling. |

---

## 🚀 Quick Start & CLI Reference

### 1. General Autonomous Goal Solving (`satset-agi`)
```bash
# Execute abstract multi-step objectives
satset-agi solve "search photography accounts on instagram and optimize ram afterwards"

# Dry-run cognitive planning and inspection
satset-agi plan "send status report to team on whatsapp"
```

### 2. World Model & Episodic Memory (`agi-memory`)
```bash
# Store system entity or context
agi-memory set "profile.owner" '{"alias": "User", "role": "Architect"}' "identity"

# Retrieve structured knowledge
agi-memory get "profile.owner"

# List world entities
agi-memory list
```

### 3. Native In-RAM Tool Synthesis (`satset-exec`)
```bash
# Compile and run native C code in RAM with zero storage footprint
satset-exec c '
#include <stdio.h>
int main() { printf("Instant native micro-tool execution\n"); return 0; }
'

# Micro-benchmark any shell or binary command
satset-exec bench "satset status"
```

### 4. Sub-Millisecond Android Automation (`satset`)
```bash
# 1-Packet atomic search input on Android (~8ms total dispatch)
satset do "open youtube" "tap-set 'Search || Telusuri || id:search' 'autonomous agents' --enter" "done"
```

---

## 📊 Benchmarks

| Metric | Traditional ADB / Automator | Satset Native AGI Layer | Speedup |
| :--- | :--- | :--- | :--- |
| **Command Startup Latency** | ~250ms - 800ms | **~0.4ms** | **~600x - 2000x** |
| **Atomic Tap + Text Input** | 3 - 5 roundtrips (~1200ms) | **1 TCP Packet (~8ms)** | **~150x** |
| **Context Query Latency** | Cloud RAG (~300 - 1000ms) | **SQLite Local Index (<1ms)** | **~500x** |
| **Memory Clean / Ephemeral Purge**| Manual file removals | **Deterministic Reaper (4ms)** | **Instant** |

---

## 📄 License & Integrity
Crafted for extreme-velocity autonomous agent environments. MIT License.
