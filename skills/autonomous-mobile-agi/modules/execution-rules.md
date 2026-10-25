# ⚡ Modul 2: Deterministic 1-Turn Execution & Zero-Bloat Protocol

Tujuan modul ini: **Memaksimalkan rasio hasil per turn, memangkas waktu tunggu eksekusi, dan menjaga context window tetap bersih (zero token pollution).**

---

## 1. Deterministic 1-Turn Pipeline

- **Parallel Tool Batching**:
  - Dilarang keras memanggil `read_file`, `edit_file`, atau `write_file` satu per satu di turn terpisah jika targetnya sudah diketahui.
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
