# ⚡ Modul 2: Deterministic 1-Turn Execution & Zero-Bloat Protocol

Tujuan modul ini: **Memaksimalkan rasio hasil per turn, memangkas waktu tunggu eksekusi, dan menjaga context window tetap bersih (zero token pollution).**

---

## 1. Deterministic 1-Turn Pipeline

- **Parallel Tool Batching & 1-Turn Pipeline Execution**:
  - Dilarang keras memecah alur aksi UI menjadi beberapa turn terpisah (misal: turn 1 buka app, turn 2 inspeksi UI, turn 3 tap tombol, turn 4 tap tombol lagi).
  - Satukan seluruh rantai aksi navigasi ke dalam **1 paket tunggal** `agi-ctrl do` menggunakan fallback selector chain dan timing atomik (`wait ms`).
  - Eksekusi seluruh pembacaan atau pembaruan multi-file secara **paralel dalam 1 turn**.
- **Composite Chained Shell (`&&` / `||`)**:
  - Gabungkan **eksekusi + kompilasi/uji + verifikasi** dalam satu panggilan `run_command`:
    ```bash
    clang -O3 -flto src/app.c -o bin/app && ./bin/app --selftest || tail -n 20 build.err
    ```
- **Zero Polling Loop**:
  - Dilarang membuat loop `sleep` manual untuk menunggu proses asinkron. Gunakan mekanisme event-driven atau flag kondisi deterministik.

---

## 2. Surgical Context Bounds (Zero Token Pollution)

Context window yang bersih menghasilkan penalaran yang lebih tajam dan respons yang jauh lebih cepat:

- **ReconnaissanceSebelum Baca Penuh**:
  - Gunakan [`scripts/fast_probe.sh`](file:///data/data/com.termux/files/home/.agents/skills/autonomous-mobile-agi/scripts/fast_probe.sh) atau `rg -n "pattern" path` untuk menemukan nomor baris pasti sebelum memanggil `read_file`.
- **Mandatory Line Bounding**:
  - Selalu sertakan `StartLine` dan `EndLine` saat membaca file berukuran menengah/besar.
- **Strict Shell Output Throttling**:
  - Setiap perintah shell yang berpotensi menghasilkan output panjang **WAJIB** dibatasi (`| head -n 35`, `| tail -n 25`, `-q`, `--silent`).
- **Pre-Filter di Shell**:
  - Gunakan `jq`, `awk`, `sed`, atau `rg` untuk mengekstrak hanya field yang relevan daripada mencetak seluruh dump JSON/log ke layar.

---

## 3. High-Density Communication (Zero Basa-Basi)

- **Dilarang Menulis Kalimat Basa-Basi**:
  - Hapus semua pembuka (*"Baik, saya akan..."*, *"Tentu!"*) dan penutup (*"Semoga membantu!"*, *"Silakan beri tahu jika..."*).
- **Struktur Respons Padat**:
  - Langsung tampilkan status/hasil akhir, perubahan utama, dan tautan file yang dapat diklik (`[nama_file](file:///path#L10-L20)`).
  - Jangan menyalin ulang isi file atau menjelaskan kode baris demi baris kecuali diminta secara eksplisit.

---

## 4. ⚡ Eliminasi Bottleneck AI Cloud (Super-Velocity Protocol)

Untuk memangkas jeda generasi LLM di cloud (dari 4 detik menjadi < 400 milidetik):

1. **Zero-Prose Direct Tool Dispatch**:
   - Dilarang memproduksi paragraf pembuka atau narasi langkah di tengah tugas aktif.
   - Pangkas 95% token generasi: model langsung mengalirkan perintah makro/tool-call tanpa teks pengantar.
2. **Streaming Early-Action Pipeline**:
   - Eksekusi paralel: jangan menunggu seluruh respons cloud selesai. Begitu token aksi pertama (`open`, `search`) tiba di aliran stream, eksekusi lokal di HP langsung dimulai.
3. **Daemon Reflex Shield (Eliminasi Turn Dialog Sepele)**:
   - Dialog mekanis (seperti *"Yakin ingin menghapus? [Ya]"*, *"Hapus produk tidak tersedia? [Konfirmasi]"*, atau banner voucher) **WAJIB** ditangani langsung oleh daemon lokal (`satset if-tap 'Ya || Konfirmasi || Tutup || OK'`).
   - Dilarang membuang 1 turn LLM (2–4 detik) hanya untuk mengonfirmasi dialog ya/tidak.
4. **Dynamic Model Cascading (Perencanaan Cerdas + Navigasi Kilat)**:
   - **Tier 1 (Strategic Planner)**: Model cerdas (Gemini Flash) dipakai 1 kali di awal untuk menyusun graf fase tugas.
   - **Tier 2 (High-Velocity Navigator)**: Langkah mikro/inspeksi UI dinamis diarahkan ke model ultra-cepat sub-200ms (`Flash-Lite` / low-token) tanpa hardcode template, sehingga tetap 100% fleksibel di semua aplikasi dan skenario baru.
