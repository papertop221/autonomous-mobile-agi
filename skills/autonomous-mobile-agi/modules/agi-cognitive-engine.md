# 🧠 AGI Cognitive Architecture: Autonomous Problem Solver & World Model

Modul ini mendefinisikan kapabilitas penalaran kognitif tingkat tinggi (General Problem Solving) untuk agen AI pada level **AGI (Artificial General Intelligence)** di lingkungan Android/Termux.

---

## 1. Empat Pilar Kognisi AGI

### A. Dynamic Goal Decomposition & Hypothesis Generation
- AI tidak lagi menunggu skrip spesifik untuk tiap kasus.
- Menerima instruksi abstrak dalam bahasa alami -> mengklasifikasikan domain masalah (`android_ui`, `system_optimization`, `data_transformation`, `network_bypass`).
- Mengurai goal menjadi rantai sub-task dengan dependensi hierarkis.

### B. Episodic & Semantic World Model (`~/.agents/agi/agi.db`)
- **Entities & Concepts**: Menyimpan pemahaman dunia lokal (karakteristik hardware, alias pengguna, preferensi aplikasi).
- **Episodic Execution Log**: Setiap eksekusi otomatis direkam:
  - Input goal, domain, rencana yang dieksekusi, status keberhasilan, durasi latensi, serta analisis sebab-akibat (causal reasoning).
- **Synthesized Tools Index**: Micro-tool C dan skrip yang dibuat di masa lalu siap dipanggil kembali tanpa kompilasi ulang.

### C. Metacognitive Monitoring & In-Flight Self-Correction
- **Introspection**: Mengetahui kapan suatu asumsi gagal (misal: elemen UI tidak merespon dalam timeout tertentu).
- **Zero Panic Fallback**: Segera beralih dari satu strategi ke strategi lain (UI tap -> Direct Android Intent -> Raw socket injection).
- **Compounding Adaptation**: Memperbarui confidence score di database sehingga strategi yang buruk ditinggalkan dan strategi efektif dipromosikan.

### D. Dynamic Tool Fabrication (On-the-Fly Micro-Tools)
- Ketika menghadapi tugas pemrosesan rumit (kalkulasi besar, parsing biner, hashing masif), AGI tidak membuat loop panggilan tool berkali-kali.
- AGI menulis kode C micro-tool, mengompilasinya langsung di RAM (`clang -O3` via `satset-exec`), mengeksekusi dengan kecepatan native, dan menyimpan hasilnya.

---

## 2. CLI Helpers

```bash
# 1. Menjalankan Goal Otonom (General Problem Solver)
satset-agi solve "buka wa cari kontak Gilang"
satset-agi solve "optimasi penggunaan ram sistem"

# 2. Perencanaan Kognitif (Dry-run / Inspection)
satset-agi plan "analisis disk dan hapus cache sementara"

# 3. Interaksi Database Memori Dunia (World Model)
agi-memory set "user.partner" '{"name": "Gilang", "app": "whatsapp"}' "contacts"
agi-memory get "user.partner"
agi-memory list [category]
```
