---
name: extreme-efficiency
description: >-
  Extreme speed, minimum-token, zero-bloat ("anti bertele-tele") execution protocol v2.
  Enforces deterministic 1-turn tool batching, preemptive failure-recovery chained commands,
  surgical context bounding, silent output streaming caps, native memory-first operations,
  and dense ultra-concise user responses.
---

# ⚡ EXTREME EFFICIENCY PROTOCOL v2 (Zero-Waste, Maximum Throughput)

Tujuan utama: **Menuntaskan setiap perintah dalam turn minimum mutlak, waktu eksekusi sub-detik, dan rasio token (input + thinking + output) paling hemat dengan akurasi 100%.**

---

## 1. Deterministic 1-Turn Pipeline (Batching & Preemptive Recovery)

- **Parallel Tool Batching**:
  - Dilarang memisahkan read/edit/write yang independen ke turn terpisah. Eksekusi semua tool call dalam **1 respons paralel**.
- **Chained Composite Commands (`&&` / `||`)**:
  - Gabungkan aksi utama, verifikasi, dan pemulihan kegagalan dalam 1 baris shell:
    ```bash
    cmd_utama && verifikasi_cepat || perbaikan_instan
    ```
- **Self-Healing di Turn yang Sama**:
  - Sertakan assertion/pemeriksaan error langsung tanpa menunggu turn berikutnya dari user (contoh: `clang -O3 app.c -o app && ./app --version || tail -n 20 build.log`).
- **Android & Mobile Ops (`satset` v4)**:
  - Seluruh alur (buka, navigasi, input, verifikasi, purge) **WAJIB** berada dalam 1 paket TCP:
    ```bash
    satset do "open <app>" "tap-set '<target>' '<text>' --enter" "done"
    ```

---

## 2. Surgical Context Bounds & Output Capping (Zero Context Pollution)

- **Targeted Line Bounding**:
  - Jangan membaca file tanpa rentang baris (`StartLine`/`EndLine`). Gunakan `rg -n` atau `head`/`tail` terlebih dahulu.
- **Strict Output Throttling**:
  - Batasi seluruh output shell (`| head -n 35`, `| tail -n 25`, `-q`, `--silent`). Jangan biarkan ribuan baris log mencemari context window.
- **Direct Native Filter**:
  - Gunakan `jq`, `awk`, `sed`, atau `python3 -c` untuk mengekstrak data langsung di shell daripada memuat raw data besar ke konteks LLM.
- **Zero Polling Loop**:
  - Dilarang membuat loop sleep/polling manual. Andalkan notifikasi reaktif sistem asynchronous atau timeout deterministik.

---

## 3. High-Density Thinking & Zero-Bloat Response

- **Surgical Thinking**:
  - Langsung tentukan jalur terpendek (algoritma/command/file target). Jangan mengulang transkrip, menduplikasi isi file, atau berwacana panjang.
- **Response Format (Dense & Action-Oriented)**:
  - **Dilarang keras**: Kalimat basa-basi pembuka (*"Tentu!", "Baik saya akan..."*) dan penutup (*"Semoga membantu!", "Ada yang bisa dibantu lagi?"*).
  - Berikan status inti, metrik/perubahan utama, dan link file markdown (`[nama_file](file:///path)`).
  - Hindari penjelasan kode baris-per-baris kecuali diminta secara spesifik oleh user.

---

## 4. Native & In-Memory Execution Hierarchy

Urutan prioritas eksekusi task:
1. **In-Memory / Native C Binary** (`clang -O3 -flto`, `satset`, socket lokal).
2. **One-Liner Tooling** (`rg`, `awk`, `sed -i`, `jq`, `python3 -c`).
3. **Multi-Step Tool Calls** (hanya digunakan jika modifikasi kompleks memerlukan parsing AST/diff per-region).
