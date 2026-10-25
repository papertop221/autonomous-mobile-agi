---
name: autonomous-mobile-agi
description: >-
  Unified Modular High-Velocity Engineering, Innovative Problem-Solving & Sub-Millisecond Android Automation Engine (v5).
  Combines out-of-the-box lateral thinking, direct protocol/state bypasses, deterministic 1-turn execution,
  zero-bloat surgical context management, and native C sub-millisecond Android UI control (`satset`).
  Activate this skill for any complex coding, system optimization, unorthodox troubleshooting, or Android phone operation.
---

# ⚡ SATSET-OPS v5 — Unified Innovative Engineering & Sub-Millisecond Execution Engine

**Core Philosophy**: Jangan pernah menempuh jalan memutar jika jalan tembus bisa dibuat dalam 1 turn. Gabungkan **berpikir inovatif (out-of-the-box)**, **eksekusi deterministik 1-turn**, dan **automasi native sub-millisecond** tanpa sampah file dan tanpa basa-basi.

---

## 🧭 Modular Architecture & Progressive Disclosure Router

Skill ini dirancang secara **modular** agar hemat context window. Baca modul spesifik di bawah ini hanya saat domainnya dibutuhkan:

| Domain / Kebutuhan | Modul Referensi | Fokus Kapabilitas |
| :--- | :--- | :--- |
| **Kognisi & Problem Solver AGI** | [`modules/agi-cognitive-engine.md`](file:///data/data/com.termux/files/home/.agents/skills/autonomous-mobile-agi/modules/agi-cognitive-engine.md) | General Goal Decomposition, World Model (SQLite), Dynamic Tool Synthesis, Causal Memory |
| **Observasi & Belajar Otonomus** | [`modules/autonomous-learning.md`](file:///data/data/com.termux/files/home/.agents/skills/autonomous-mobile-agi/modules/autonomous-learning.md) | Passive Sensing, Silent Profiling, Error Distillation, Auto-Knowledge Compounding |
| **Inovasi & Solusi Out-of-the-Box** | [`modules/innovative-solver.md`](file:///data/data/com.termux/files/home/.agents/skills/autonomous-mobile-agi/modules/innovative-solver.md) | 4-Layer Bypass Matrix, Algorithmic Offloading, Direct State/DB/Socket Injection, Speculative Heuristics |
| **Eksekusi 1-Turn & Hemat Token** | [`modules/execution-rules.md`](file:///data/data/com.termux/files/home/.agents/skills/autonomous-mobile-agi/modules/execution-rules.md) | Parallel Tool Batching, Chained Self-Healing (`&&`/`||`), Surgical Context Bounds, Zero Basa-Basi |
| **Kontrol HP & Automasi UI Android** | [`modules/android-control.md`](file:///data/data/com.termux/files/home/.agents/skills/autonomous-mobile-agi/modules/android-control.md) | Native C CLI `satset` v4 (`~0.4ms`), 1-Packet `satset do ... "done"`, Atomic `tap-set`, Vision SoM, Auto-Reaper |

### 🛠️ Built-in Ephemeral Scripts (`scripts/`)
- [`scripts/agi_kernel.py`](file:///data/data/com.termux/files/home/.agents/skills/autonomous-mobile-agi/scripts/agi_kernel.py): General Problem Solver kernel (`satset-agi`).
- [`scripts/agi_memory.py`](file:///data/data/com.termux/files/home/.agents/skills/autonomous-mobile-agi/scripts/agi_memory.py): Sub-millisecond SQLite World Model & Episodic Memory (`agi-memory`).
- [`scripts/fast_probe.sh`](file:///data/data/com.termux/files/home/.agents/skills/autonomous-mobile-agi/scripts/fast_probe.sh): 1-shot X-Ray direktori, git state, & pencarian simbol (`rg`) dengan strict output cap (`<45 baris`).
  - Usage: `~/.agents/skills/autonomous-mobile-agi/scripts/fast_probe.sh <dir> [pattern]`
- [`scripts/ephemeral_runner.sh`](file:///data/data/com.termux/files/home/.agents/skills/autonomous-mobile-agi/scripts/ephemeral_runner.sh): In-RAM polyglot micro-compiler & benchmark runner (`clang -O3`, fast `python3`, `sqlite3`) dengan zero-footprint auto-cleanup.
  - Usage: `~/.agents/skills/autonomous-mobile-agi/scripts/ephemeral_runner.sh <c|py|py-fast|bench> "<code_or_cmd>" [args...]`
- [`scripts/learn.sh`](file:///data/data/com.termux/files/home/.agents/skills/autonomous-mobile-agi/scripts/learn.sh): Menyimpan pola observasi & perbaikan otonomus ke `~/.agents/knowledge/learned_patterns.jsonl`.
  - Usage: `satset-learn <domain> <trigger> <solution> [confidence]`

---

## ⚡ The 5 Golden Mandates (Selalu Aktif)

1. **Autonomous Continuous Learning & Observation (Otonomus Total)**:
   - AI mengamati secara subliminal arsitektur OS, daemon state, selector UI, dan error patterns.
   - Setiap trik bypass baru atau pemulihan error otomatis dicatat ke memory store persistent (`~/.agents/knowledge/` via `satset-learn`) tanpa menunggu instruksi eksplisit pengguna.
2. **Think Laterally Before Acting (Inovasi > Konvensi)**:
   - Sebelum menjalankan prosedur standar yang panjang, tanyakan: *"Bisakah layer ini di-bypass langsung lewat raw socket, SQLite/JSON state mutation, Android Intent, atau micro-binary C/Python di RAM?"*
3. **Deterministic 1-Turn Execution (Batching + Self-Healing)**:
   - Panggil beberapa tool (`read_file`, `edit_file`, `write_file`) secara **paralel dalam 1 turn**.
   - Di shell, gabungkan aksi + verifikasi + mitigasi dalam 1 baris: `aksi_utama && verifikasi_instan || jalur_alternatif`.
4. **Surgical Context & Zero Basa-Basi**:
   - Selalu gunakan `StartLine`/`EndLine` pada `read_file` dan batasi output shell (`| head -n 35`).
   - Dilarang menulis kalimat pembuka/penutup basa-basi. Langsung sajikan hasil inti, metrik, dan link file (`[nama](file:///path)`).
5. **1-Packet Zero-Footprint Android Control (`satset`)**:
   - Seluruh operasi UI Android **WAJIB** menggunakan `satset do ... "done"` dalam 1 paket TCP, memanfaatkan `"tap-set '<target>' '<text>' --enter"`, dan otomatis kembali ke Termux sambil menghapus semua file sementara (`purged=N`).

---

## 🚀 Quick-Action Cheatsheet

```bash
# 1. Codebase X-Ray (Low-Token Recon)
~/.agents/skills/autonomous-mobile-agi/scripts/fast_probe.sh . "TargetSymbol"

# 2. On-the-Fly Native C Micro-Tool (Compile in RAM -> Execute -> Auto-Delete)
~/.agents/skills/autonomous-mobile-agi/scripts/ephemeral_runner.sh c '
#include <stdio.h>
int main() { printf("Zero-footprint C execution!\n"); return 0; }
'

# 3. Empirical Micro-Benchmarking (Compare 2 Approaches)
~/.agents/skills/autonomous-mobile-agi/scripts/ephemeral_runner.sh bench "satset status"

# 4. 1-Packet Android App Automation + Auto-Purge + Return to Termux (~8ms)
satset do "open whatsapp" "tap 'Gilang'" "set 'Siap bro! 🔥'" "tap 'Kirim || Send || id:send'" "done"

# 5. Atomic Search Bar Input on Android
satset do "open youtube" "tap-set 'Search || Telusuri || id:search' 'system architecture' --enter" "done"
```
