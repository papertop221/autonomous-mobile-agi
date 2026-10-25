# 📱 Modul 3: Sub-Millisecond Android UI & OS Control (`satset` v4)

`satset` ([`~/.local/bin/satset`](file:///data/data/com.termux/files/home/.local/bin/satset)) adalah **Native ARM64 C Binary** (`~0.4ms` startup) yang terhubung ke [`SatsetDaemon v4`](file:///data/data/com.termux/files/home/.local/share/satset/src/SatsetDaemon.java) (`127.0.0.1:19888`) berbasis `OnAccessibilityEventListener` real-time (`0.01ms` cache hit), direct `MotionEvent` injection (`5ms`), dan **Automatic Ephemeral File & RAM Reaper**.

---

## 1. Tiga Aturan Emas Operasional Android

1. **1-Packet Pipeline (`satset do ...`)**:
   - Jangan pernah memecah langkah ke beberapa tool call jika bisa digabungkan, dan jangan gunakan `adb shell uiautomator dump` / `sleep` buta.
   - Kirim seluruh rangkaian aksi dalam **1 paket TCP** via `satset do`:
     ```bash
     satset do \
       "open whatsapp" \
       "tap 'Gilang'" \
       "set 'Otw sekarang bro! 🔥'" \
       "tap 'Kirim || Send || id:send'"
     ```
2. **Atomic `tap-set` untuk Kolom Pencarian & Input**:
   - Gabungkan fokus kolom + injeksi teks + tombol Enter dalam 1 instruksi atomik:
     ```bash
     satset do "open youtube" "tap-set 'Search || Telusuri || id:search' 'ted talk innovation' --enter"
     ```
3. **STAY IN TARGET APP & Call `"done"` ONLY When Completely Finished**:
   - `satset do` **tanpa** `"done"` otomatis menangkap dan menampilkan `--- CURRENT UI ---` dari aplikasi target di terminal dalam `~40ms` **tanpa beralih ke Termux**. Layar tetap di aplikasi target!
   - **HANYA sertakan `"done"`** (atau panggil `satset done`) saat task multi-step telah selesai 100%.

---

## 2. Sintaks Target Selector Fleksibel

Perintah `tap`, `longtap`, `set`, `tap-set`, dan `wait` mendukung selector berantai:
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
| **Inspeksi UI** | `satset ui [filter \| --all]` | Dump hierarki elemen interaktif dari RAM cache (`0.01ms` - `12ms`) |
| **Interaksi Elemen** | `satset tap <target>` / `satset longtap <target>` | Ketuk (`5ms`) atau tekan lama elemen/koordinat |
| **Injeksi Teks** | `satset set "<text>"` / `satset tap-set "<target>" "<text>" [--enter]` | Injeksi teks langsung via `ACTION_SET_TEXT` (`2-4ms`) |
| **Navigasi & Gestur** | `satset scroll <down\|up\|left\|right> [n]` | Scroll halus atau swipe koordinat: `satset swipe <x1> <y1> <x2> <y2> [steps]` |
| **Tombol Sistem** | `satset <back\|home\|recents\|enter\|wake\|sleep>` | Navigasi sistem global instan |
| **Buka Aplikasi** | `satset open <alias\|package>` | Alias bawaan: `wa`, `ig`, `tt`, `shopee`, `snapseed`, `lr`, `capcut`, `canva`, `brave`, `discord`, `reddit`, `pinterest`, `chatgpt`, `livin`, `keep`, `zarchiver`, `poweramp`, `camera`, `settings`, `yt`, `maps`, `photos`, `playstore`, `files`, `termux` |
| **Vision & Warna** | `satset shot [--som]` / `satset live` | Tangkap layar dengan Set-of-Mark (`--som`) ke `/sdcard/.satset_tmp/shot.jpg` |
| **Lifecycle & Clean** | `satset status` / `satset clean` / `satset restart` / `satset done` | Cek status daemon, hapus file temp paksa, hot-reload JAR, atau akhiri sesi |
