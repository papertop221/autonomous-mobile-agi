# 📱 Modul 3: Sub-Millisecond Android UI & OS Control (`satset` v4.2)

`satset` ([`~/.local/bin/satset`](file:///data/data/com.termux/files/home/.local/bin/satset)) adalah **Native ARM64 C Binary** (`~0.4ms` startup) yang terhubung ke [`SatsetDaemon v4.2`](file:///data/data/com.termux/files/home/.local/share/satset/src/SatsetDaemon.java) (`127.0.0.1:19888`) berbasis `FLAG_PREFETCH_UNINTERRUPTIBLE (38)`, `OnAccessibilityEventListener` real-time (`0.01ms` cache hit), direct `MotionEvent` injection (`5ms`), dan **Automatic Ephemeral File & RAM Reaper**.

---

## 1. Tiga Aturan Emas Operasional Android (Superhuman Reflex v5)

1. **1-Packet Macro-Pipeline (`agi-ctrl do ...`) & Zero Multi-Turn Micro-Stepping**:
   - **DILARANG KERAS** mengeksekusi navigasi 1 langkah per turn (mis. turn 1 `open`, turn 2 `tap search`, turn 3 `set`, turn 4 `tap enter`)! Setiap turn LLM memakan `3-5 detik` latensi cloud.
   - Gabungkan seluruh alur (Buka App -> Dismiss Popup -> Cari & Submit -> Pilih Diskon Tertinggi/Termurah -> Checkout) dalam **1 panggilan tunggal `agi-ctrl do`** yang dieksekusi secara lokal di dalam daemon dengan kecepatan `<25ms` per langkah!
   - Gunakan primitif refleks otonom di dalam `agi-ctrl do`:
     - `"search '<query>'"` — otomatis mencari kolom search di layar, mengisi teks, dan mengeksekusi `ACTION_IME_ENTER (0x01020054)` + tombol Search/IME tanpa perlu menebak koordinat keyboard.
     - `"tap-best --discount '<keywords>'"` atau `"tap-best --cheap '<keywords>'"` — otomatis memindai kartu produk/makanan (dan scroll otomatis jika perlu), menghitung persentase/nominal diskon & harga di RAM (`0.2ms`), lalu langsung mengetuk kartu terbaik!
   - Contoh 1-turn e-commerce / food delivery super-cepat:
     ```bash
     agi-ctrl do "open shopee" "if-tap 'Tutup || Lewati'" "search 'boba mixue brown sugar'" "tap-best --discount 'boba || brown sugar || mixue'"
     ```

2. **Atomic `tap-set`, `if-tap`, `scroll-tap`, & `tap-best`**:
   - Gabungkan fokus + pengisian teks + submit tanpa jeda:
     ```bash
     agi-ctrl do "tap-set 'id:h9x || 599 202' 'boba mixue' --enter"
     ```
   - Gunakan `"if-tap '<target>'"` untuk menutup iklan/dialog opsional secara instan (`0ms` jika tidak ada, aman dari false-positive).
   - Gunakan `"scroll-tap '<target>' [max_scrolls] [down|up]"` atau `"tap-best [--discount|--cheap] '<keywords>'"` untuk mencari dan mengetuk elemen dalam 1 langkah milidetik di dalam daemon.

3. **Zero Polling & Direct Pipeline**:
   - Manfaatkan response stream langsung `--- CURRENT UI ---` (dengan auto-settle anti-transisi kosong) dari satu paket `agi-ctrl do` atau perintah aksi mandiri tanpa bolak-balik memeriksa UI di turn terpisah.
   - Hanya panggil `done` saat task telah selesai sepenuhnya.

---

## 2. Sintaks Target Selector Fleksibel

Perintah `tap`, `if-tap`, `scroll-tap`, `scroll-to`, `longtap`, `set`, `tap-set`, dan `wait` mendukung selector berantai:
- **Fallback Chain (`||`)**: `"Kirim || Send || desc:Send || id:send"` (mencoba satu per satu secara instan dalam memori).
- **Index Cepat (`#idx`)**: `#12` atau `12` (langsung mengetuk elemen `[12]` dari output `satset ui` terakhir dalam `5ms`).
- **Atribut Spesifik**: `id:<res_id>`, `desc:<content_desc>`, `text:<substring>`, `exact:<exact_text>`, `class:<class_name>`.
- **Koordinat Layar**: `610 1350` atau `610,1350` (`moto_g86_power_5G`: `1220x2712` Portrait / `2712x1220` Landscape).

---

## 3. Referensi Cepat Command `satset`

| Kategori | Perintah | Keterangan |
| :--- | :--- | :--- |
| **Pipeline (In-Progress)** | `satset do "<step1>" "<step2>"` | Eksekusi multi-step dalam 1 paket TCP; layar TETAP di target app + output `--- CURRENT UI ---` |
| **Pipeline (Final)** | `satset do "<step1>" "<step2>" "done"` | Eksekusi langkah terakhir + auto-purge file temp + kembali ke Termux |
| **Inspeksi UI** | `satset ui [filter \| --all]` | Dump hierarki elemen interaktif dari RAM cache (`0.01ms` cache / `~15-35ms` uninterruptible prefetch) |
| **Interaksi Elemen** | `satset tap <target>` / `satset longtap <target>` | Ketuk (`5ms`) atau tekan lama elemen/koordinat + auto-stream `--- CURRENT UI ---` |
| **Kondisional & Scroll** | `satset if-tap <target>` / `satset scroll-tap <target> [n]` | Ketuk opsional tanpa gagal (`if-tap`) atau scroll otomatis sampai elemen ditemukan lalu ketuk (`scroll-tap`) |
| **Injeksi Teks** | `satset set "<text>"` / `satset tap-set "<target>" "<text>" [--enter]` | Injeksi teks langsung via `ACTION_SET_TEXT` (`2-4ms`) + auto-stream `--- CURRENT UI ---` |
| **Navigasi & Gestur** | `satset scroll <down\|up\|left\|right> [n]` | Scroll halus atau swipe koordinat: `satset swipe <x1> <y1> <x2> <y2> [steps]` |
| **Tombol Sistem** | `satset <back\|home\|recents\|enter\|wake\|sleep>` | Navigasi sistem global instan |
| **Buka Aplikasi** | `satset open <alias\|package>` | Alias bawaan: `wa`, `ig`, `tt`, `shopee`, `snapseed`, `lr`, `capcut`, `canva`, `brave`, `discord`, `reddit`, `pinterest`, `chatgpt`, `livin`, `keep`, `zarchiver`, `poweramp`, `camera`, `settings`, `yt`, `maps`, `photos`, `playstore`, `files`, `termux` |
| **Vision & Warna** | `satset shot [--som]` / `satset live` | Tangkap layar dengan Set-of-Mark (`--som`) ke `/sdcard/.satset_tmp/shot.jpg` |
| **Lifecycle & Clean** | `satset status` / `satset clean` / `satset restart` / `satset done` | Cek status daemon, hapus file temp paksa, hot-reload JAR, atau akhiri sesi |

---

## 4. ⚡ SUB-ZERO v5.5 — Sub-Microsecond Lock-Free RAM Architecture

Untuk skenario yang membutuhkan kecepatan melampaui socket TCP (misal: flash-sale hunting, high-frequency macro loops, atau instant popup counters), gunakan arsitektur **SUB-ZERO** ([`subzero_shm.h`](file:///data/data/com.termux/files/home/.agents/skills/autonomous-mobile-agi/scripts/subzero_shm.h)):

### Empirically Verified Benchmarks (Motorola G86 5G - Android 14 Kernel 6.1):
- **O(1) In-RAM Hash Decision Engine**: `2.28 nanodetik` (`0.0023 µs` / `0.000002 ms`)
- **Lock-Free Shared Memory Ring (Zero-Syscall)**: `200.18 nanodetik` (`0.200 µs` / `0.000200 ms`)
- **Akselerasi vs TCP Socket (127.0.0.1)**: **190x LEBIH CEPAT** (`0.2µs` vs `38µs`)
- **Akselerasi vs ADB Shell Standar**: **2.497.742x LEBIH CEPAT** (`0.2µs` vs `500ms`)

### Implementasi Pola Sub-Mikrodetik:
```c
#include "subzero_shm.h"

// 1. Buat kanal Lock-Free di RAM anonim
SubzeroChannel *chan = subzero_create_channel();

// 2. Evaluasi keputusan instan O(1) via token hash (2.28 ns)
uint32_t target_hash = subzero_hash("Beli Sekarang", 13);
if (subzero_hash(token, len) == target_hash) {
    // 3. Dispatch instruksi via shared memory ring (200 ns)
    subzero_dispatch_tap(chan, 1, 610, 2400);
}
```
