# ⚡ Autonomous Mobile AGI Skill for Google Antigravity

High-velocity Autonomous Mobile AGI Engine for Android (Termux) & Google Antigravity.
Transforms your agent with **Sub-Millisecond UI Control**, **SQLite World Model**, **Dynamic In-RAM Tool Synthesis**, and **Cross-Domain Goal Autopilot**.

---

## ⚡ 1-Step Quick Install

Jalankan perintah ini di Termux:

```bash
git clone https://github.com/papertop221/autonomous-mobile-agi.git && cd autonomous-mobile-agi && ./install.sh
```

> **Catatan**: Skrip di atas otomatis menyalin skill ke `~/.agents/skills/autonomous-mobile-agi/`, mengompilasi core native C, dan mendaftarkan perintah CLI (`agi`, `agi-ctrl`, `agi-memory`). Antigravity akan langsung mendeteksi skill ini secara otomatis.

---

## 🚀 Cara Pemakaian

### 1. Di Chat Antigravity (Natural Language)
Cukup berikan instruksi langsung ke Antigravity di chat:
- *"Buka WA dan kirim pesan ke Gilang"*
- *"Analisis penggunaan RAM HP dan bersihkan cache"*
- *"Cari video lofi coding di YouTube"*

Antigravity akan otomatis mengaktifkan skill `autonomous-mobile-agi` dan mengeksekusinya dalam 1 turn.

---

### 2. Melalui Terminal CLI

| Kebutuhan | Perintah | Contoh |
| :--- | :--- | :--- |
| **Selesaikan Goal Bebas** | `agi solve "<tujuan>"` | `agi solve "buka wa cari Gilang"` |
| **Preview Rencana (Dry Run)**| `agi plan "<tujuan>"` | `agi plan "buka yt cari tutorial c"` |
| **Kontrol UI Android Cepat** | `agi-ctrl do "<action>"` | `agi-ctrl do "open yt" "tap-set 'Search' 'agi' --enter" "done"` |
| **Simpan / Baca Memori** | `agi-memory <set\|get\|list>` | `agi-memory set "teman" '{"nama": "Budi"}'` |
| **Kompilasi C Mikro di RAM** | `agi-exec c '<code>'` | `agi-exec c 'int main(){printf("Fast!\n");return 0;}'` |

---

## 🏛️ Struktur File Skill

```
~/.agents/skills/autonomous-mobile-agi/
├── SKILL.md                          # Router utama Antigravity
├── modules/
│   ├── agi-cognitive-engine.md       # Teori & penalaran kognisi AGI
│   ├── android-control.md            # Kontrol UI sub-millisecond (agi-ctrl)
│   ├── autonomous-learning.md        # Observasi pasif & rule of no regression
│   ├── execution-rules.md            # Eksekusi deterministik 1-turn
│   └── innovative-solver.md          # 4-Layer architectural bypass
└── scripts/
    ├── agi_kernel.py                 # Core goal solver (`agi`)
    ├── agi_memory.py                 # SQLite memory database (`agi-memory`)
    ├── ephemeral_runner.sh           # In-RAM compiler (`agi-exec`)
    ├── fast_probe.sh                 # Codebase reconnaissance (`agi-probe`)
    └── learn.sh                      # Error distillation logger (`agi-learn`)
```

---

## 📄 License
MIT License. Crafted for extreme autonomy and velocity.
