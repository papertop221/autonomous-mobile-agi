---
name: extreme-efficiency
description: >-
  Extreme speed, minimum-token, zero-bloat ("anti bertele-tele") execution protocol.
  Enforces 1-turn tool batching, surgical context reads, silent/capped command output,
  atomic native operations, and ultra-concise responses. Core rules are already active
  in GEMINI.md; read this SKILL.md only when orchestrating complex multi-step refactors,
  audits, or heavy automation workflows.
---

# ⚡ EXTREME EFFICIENCY PROTOCOL (Hemat Waktu & Token — Zero Basa-Basi)

Tujuan utama: **Selesaikan setiap task dengan jumlah turn paling sedikit, eksekusi tercepat, dan pemakaian token (input + thinking + output) paling minimal.**

---

## 1. Eksekusi 1-Turn (One-Shot Tool Batching)

- **DILARANG mencicil tool call berurutan** jika bisa digabung dalam **1 turn**:
  - Panggil beberapa `read_file` / `edit_file` / `write_file` **secara paralel dalam 1 respons**.
  - Gabungkan perintah shell (`grep`, `sed`, `build`, `test`, `verify`) ke dalam **1 command komposit** (`cmd1 && cmd2 && cmd3`).
- **Self-Verify di Turn yang Sama**:
  - Jangan gunakan turn tambahan hanya untuk mengecek hasil. Sertakan verifikasi langsung di akhir command eksekusi (contoh: `clang -O3 app.c -o app && ./app --test`).
- **Kontrol HP Android (`satset` v4)**:
  - Selalu bungkus seluruh alur kerja dalam **1 panggilan `satset do "..." "done"`** (1 paket TCP, `<0.2ms` event wakeup, 0 `sleep`).

---

## 2. Surgical Context & Output Capping (Hemat Input Tokens)

- **Jangan Baca File Utuh Jika Hanya Butuh Sebagian**:
  - Gunakan `StartLine` dan `EndLine` pada `read_file`.
  - Jika belum tahu barisnya, cari lokasi pastinya dengan `rg -n` / `grep -n` dalam 1 command singkat, atau gunakan helper [`fast_probe.sh`](./scripts/fast_probe.sh).
- **Batasi Output Command (`stdout`/`stderr`)**:
  - Selalu gunakan flag quiet/ringkas (`-q`, `--no-pager`, `--short`) atau batasi output (`| head -n 40`, `| tail -n 30`) agar log panjang tidak memenuhi context window.
- **Hindari Polling**:
  - Jangan pernah melakukan loop polling manual untuk task background/subagent; mengandalkan notifikasi event-driven.

---

## 3. Zero Basa-Basi (Hemat Thinking & Output Tokens)

- **Internal Thinking (`thinking`)**:
  - Langsung menuju solusi terpendek. Jangan mengulang isi file/command di dalam thinking atau berputar-putar menganalisis hal sepele.
- **Respons ke User**:
  - **0 kalimat pembuka/penutup basa-basi** (*"Baik, saya akan..."*, *"Tentu! Berikut..."*, *"Semoga membantu!"* -> **HAPUS SEMUA**).
  - **To-the-point**: Langsung berikan hasil akhir, status eksekusi, dan link file (`[nama_file](file:///path)`).
  - Gunakan poin pendek atau tabel ringkas hanya jika diperlukan. Jangan menjelaskan ulang kode baris demi baris kecuali diminta.

---

## 4. Shortcut Ekstrem (Native & In-Memory First)

- Gunakan tool tercepat yang tersedia di sistem:
  - Manipulasi teks/JSON/file massal: `rg`, `sed -i`, `awk`, `jq`, `perl -pi -e`, atau `python3 -c` one-liner dibandingkan membaca/menulis puluhan file manual lewat LLM.
  - Kompilasi & binary: gunakan native binary (`clang -O3 -flto`) untuk tool yang sering dipanggil berulang kali.
  - Recon cepat direktori/project: jalankan [`scripts/fast_probe.sh`](./scripts/fast_probe.sh) `<path> [pattern]`.
