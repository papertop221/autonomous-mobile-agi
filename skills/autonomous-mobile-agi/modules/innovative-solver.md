# 🧠 Modul 1: Innovative & Lateral Problem-Solving Engine

Tujuan modul ini: **Mengganti cara kerja linier/konvensional yang lambat dengan rekayasa cerdas (lateral engineering), sintesis micro-tool instan, dan manipulasi arsitektur langsung.**

---

## 1. The 5-Layer Bypass Matrix (Potong Kompas Arsitektur)

Setiap sistem memiliki lapisan abstraksi. Semakin tinggi lapisannya, semakin lambat dan rapuh eksekusinya. Selalu turun ke lapisan terendah yang paling deterministik:

| Layer Konvensional (Lambat) | Inovasi Bypass (10x–1000x Lebih Cepat) | Contoh Implementasi Nyata |
| :--- | :--- | :--- |
| **L4: Navigasi UI Bertahap** (Buka menu -> klik -> scroll -> klik) | **Direct Deep-Link / Intent / 1-Packet Pipeline** | Lompati 5 layar UI dengan `am start -a android.intent.action.VIEW -d "<uri>"` atau 1 paket `satset do ... "done"`. |
| **L3: Wrapper CLI Berat** (Startup interpreter lambat, overhead berulang) | **Direct Socket / Native Binary / IPC** | Tembak langsung TCP/UNIX socket daemon (`127.0.0.1:19888`) atau panggil binary C native (`satset`). |
| **L2: Konfigurasi via Menu/API Sekuensial** | **Direct State / Database / AST Mutation** | Edit langsung SQLite DB (`sqlite3 db "UPDATE ..."`), patch JSON/XML/binary config, atau modifikasi AST kode secara terprogram. |
| **L1: Multi-Turn LLM Loop** (Baca 1 file -> mikir -> edit 1 file -> ulangi 10x) | **Algorithmic Offloading (Ephemeral Micro-Tool)** | Buat 1 script C/Python/awk via `ephemeral_runner.sh` yang memproses seluruh graf file + memverifikasi hasilnya dalam **1 turn**. |
| **L0: Socket Kernel Overhead (38µs)** | **SUB-ZERO Lock-Free Shared Memory Ring & O(1) RAM Reflex** | Hubungkan agent ke daemon via anonymous `mmap` + atomic ARM64 memory barrier (`subzero_shm.h`) dengan latensi **200 nanodetik**. |

---

## 2. Prinsip Berpikir Inovatif (First-Principles & TRIZ Heuristics)

Saat menghadapi masalah rumit, limitasi environment (seperti sandbox Android/Termux), atau bug yang sulit dilacak:

1. **Inversion (Balikkan Masalah)**:
   - *Masalah*: Sulit mengekstrak data dari output aplikasi yang terenkripsi/tertutup.
   - *Inovasi*: Jangan baca output akhirnya; sadap input/state di memori atau baca struktur UI live langsung dari cache `AccessibilityEvent` (`satset ui --all`).
2. **Algorithmic Offloading (Delegasikan Logika Kompleks ke Kode Ephemeral)**:
   - Jangan gunakan token LLM untuk menghitung, mengurutkan, atau mencocokkan ratusan item secara manual.
   - Tulis micro-program dengan [`scripts/ephemeral_runner.sh`](file:///data/data/com.termux/files/home/.agents/skills/autonomous-mobile-agi/scripts/ephemeral_runner.sh) yang melakukan komputasi berat di CPU dalam `<5ms` dan hanya mencetak hasil akhirnya ke stdout.
3. **Speculative Branching (Antisipasi Cabang Kegagalan di Turn Pertama)**:
   - Jangan tunggu perintah gagal baru mencari alternatif di turn berikutnya.
   - Rangkai hipotesis A (paling cepat) dan hipotesis B (fallback aman) dalam satu eksekusi:
     ```bash
     fast_direct_method 2>/dev/null || fallback_method && verify_state
     ```
4. **Zero-Footprint Self-Elimination**:
   - Solusi profesional tidak meninggalkan jejak sampah (`.tmp`, `.bak`, binary uji coba, screenshot residu).
   - Gunakan subshell dengan `trap` otomatis (`ephemeral_runner.sh`) atau daemon auto-reaper (`satset done`).

---

## 3. Pola Penggunaan `ephemeral_runner.sh`

Ketika membutuhkan pemrosesan data cepat, transformasi massal, atau pengujian hipotesis performa:

```bash
# Kompilasi & jalankan C one-shot di RAM (otomatis terhapus setelah selesai)
~/.agents/skills/autonomous-mobile-agi/scripts/ephemeral_runner.sh c '
#include <stdio.h>
int main(int argc, char **argv) {
    printf("Processed %d args natively\n", argc - 1);
    return 0;
}
' arg1 arg2

# Jalankan transformasi Python cepat tanpa site-packages overhead (py-fast)
~/.agents/skills/autonomous-mobile-agi/scripts/ephemeral_runner.sh py-fast '
import sys, os
print("Files count:", len(os.listdir(".")))
'

# Benchmark akurasi milidetik antara dua metode
~/.agents/skills/autonomous-mobile-agi/scripts/ephemeral_runner.sh bench "rg -l main ."
```
