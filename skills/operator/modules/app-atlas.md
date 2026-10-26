# 🗺️ Modul: App Knowledge Atlas & Pola Navigasi Layman

Atlas ini memetakan arsitektur navigasi, fallback selector berantai (`||`), dan penanganan dialog pop-up untuk aplikasi harian di Indonesia.

---

## 1. Filosofi Navigasi Mandiri untuk Pengguna Awam

Pengguna awam tidak memberikan parameter teknis. Mereka hanya memberikan tujuan akhir:
- *"Beli kopi susu mixue di GoFood/GrabFood"*
- *"Kirim WA ke Budi bilang saya telat"*
- *"Putar video resep nasi goreng di YouTube"*

AI wajib secara proaktif:
1. **Memilih Aplikasi yang Tepat** dari alias atau maksud percakapan.
2. **Mengurai Alur Menjadi Makro 1-Paket**: Buka App -> Bersihkan Iklan (`if-tap`) -> Cari -> Pilih Hasil Terbaik -> Tindakan Akhir.
3. **Mengamankan Transaksi**: Berhenti di keranjang/halaman ringkasan saat melibatkan pengeluaran uang.

---

## 2. Peta Alur Navigasi Aplikasi Populer

### A. Komunikasi (WhatsApp)
- **Buka & Cari Kontak**:
  ```bash
  agi-ctrl do "open wa" "if-tap 'Lanjut || Tutup'" "tap 'Search || Telusuri || id:menuitem_search'" "set '<Nama Kontak>'" "tap '<Nama Kontak>'"
  ```
- **Kirim Pesan**:
  ```bash
  agi-ctrl do "tap-set 'Ketik pesan || Message || id:entry' '<Isi Pesan>'" "tap 'Kirim || Send || id:send'" "done"
  ```

### B. Belanja & Makanan (Shopee / Grab / Gojek)
- **Shopee (Cari Promo & Masukkan Keranjang)**:
  ```bash
  agi-ctrl do "open shopee" "if-tap 'Tutup || Lewati || Nanti Saja'" "search '<kata_kunci>'" "tap-best --discount '<kata_kunci>'" "tap 'Masukkan Keranjang || Tambah ke Keranjang'"
  ```
  *(Catatan: Jangan mengeksekusi tombol "Beli Sekarang" atau checkout final tanpa persetujuan verbal pengguna).*

- **GrabFood / GoFood (Cari Menu Terlaris/Promo)**:
  ```bash
  agi-ctrl do "open gojek" "if-tap 'Tutup || Lewati'" "search '<nama_makanan>'" "tap-best --discount '<nama_makanan>'"
  ```

### C. Media & Hiburan (YouTube / TikTok / Instagram)
- **YouTube (Cari & Langsung Putar)**:
  ```bash
  agi-ctrl do "open yt" "if-tap 'Tidak, terima kasih || Batal'" "search '<judul_video>'" "tap '#1 || #2'" "done"
  ```

### D. Utilitas Harian (Google Maps / Setelan / Jam)
- **Google Maps (Cari Rute)**:
  ```bash
  agi-ctrl do "open maps" "search '<tujuan>'" "tap 'Rute || Directions'" "done"
  ```
- **Pasang Alarm**:
  ```bash
  agi-ctrl do "open com.google.android.deskclock" "tap 'Alarm || desc:Alarm'" "tap 'Tambah alarm || id:fab'"
  ```

---

## 3. Protokol Pop-up Annihilator (Anti-Macet)
Sebelum atau sesudah setiap transisi halaman, selalu sisipkan pelindung popup otomatis:
```bash
if-tap 'Tutup || Lewati || Nanti Saja || Batal || Tidak Sekarang || id:close'
```
Perintah `if-tap` memerlukan waktu `0ms` jika tidak ada dialog, sehingga tidak memperlambat eksekusi sama sekali namun menjamin alur tidak tersangkut oleh dialog iklan/update mendadak.
