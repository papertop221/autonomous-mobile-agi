#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
AGI Cognitive Kernel & General Problem Solver (GPS) v6 - Professional Layman Edition
Autonomous Reasoning, Intent Decomposition, App Atlas Integration, and Self-Healing Phone Operator.
"""
import sys
import os
import re
import json
import time
import subprocess
import shutil

SKILL_DIR = os.path.expanduser("~/.agents/skills/autonomous-mobile-agi")
MEM_SCRIPT = os.path.join(SKILL_DIR, "scripts/agi_memory.py")
EPHEMERAL_RUNNER = os.path.join(SKILL_DIR, "scripts/ephemeral_runner.sh")
DOCTOR_SCRIPT = os.path.join(SKILL_DIR, "scripts/device_doctor.py")
APP_ATLAS_FILE = os.path.join(SKILL_DIR, "scripts/app_atlas.json")
SATSET_BIN = os.path.expanduser("~/.local/bin/satset")

# Load App Atlas
APP_ATLAS = {}
if os.path.exists(APP_ATLAS_FILE):
    try:
        with open(APP_ATLAS_FILE, "r", encoding="utf-8") as f:
            APP_ATLAS = json.load(f)
    except Exception:
        pass

def run_cmd(cmd: list or str, shell: bool = False):
    t0 = time.time()
    try:
        res = subprocess.run(cmd, shell=shell, capture_output=True, text=True, timeout=30)
        dt = int((time.time() - t0) * 1000)
        return res.returncode, res.stdout.strip(), res.stderr.strip(), dt
    except subprocess.TimeoutExpired:
        return -1, "", "Timeout expired", 30000
    except Exception as e:
        return -1, "", str(e), 0

def resolve_context(goal: str):
    """
    Kueri entitas atau preferensi terkait di AGI Memory untuk memperkaya rencana.
    """
    code, out, _, _ = run_cmd([sys.executable, MEM_SCRIPT, "list"])
    context = {}
    if code == 0 and out:
        try:
            items = json.loads(out)
            for item in items:
                key = item.get("key", "").lower()
                for word in goal.lower().split():
                    if len(word) > 2 and word in key:
                        context[key] = item.get("data_json")
        except Exception:
            pass
    return context

def match_atlas_app(goal_lower: str):
    apps = APP_ATLAS.get("apps", {})
    for app_id, meta in apps.items():
        aliases = meta.get("aliases", [])
        for alias in aliases:
            if re.search(r'\b' + re.escape(alias) + r'\b', goal_lower):
                return app_id, meta
    return None, None

def formulate_plan(goal: str, context: dict = None):
    """
    Semantic Intent Decomposition untuk Pengguna Awam:
    Menganalisis kalimat bahasa Indonesia santai dan memetakannya ke langkah makro.
    """
    if context is None:
        context = {}
    goal_lower = goal.lower()
    plan = {
        "goal": goal,
        "domain": "general",
        "action_type": "UNKNOWN",
        "steps": [],
        "context_applied": context,
        "requires_safety_stop": False,
        "human_summary": ""
    }

    # 1. Cek Pembersihan Sistem & RAM
    if any(x in goal_lower for x in ["ram", "memori", "cpu", "proses", "optimasi", "bersih", "lemot"]):
        plan["domain"] = "system_optimization"
        plan["action_type"] = "SYSTEM_CLEAN"
        plan["human_summary"] = "Membersihkan memori RAM dan mengoptimalkan sistem ponsel"
        plan["steps"].append({
            "action": "shell",
            "cmd": "free -h && satset clean 2>/dev/null || true"
        })
        return plan

    # 2. Cek App Atlas Matching
    app_id, app_meta = match_atlas_app(goal_lower)

    # 3. Klasifikasi Intent Berdasarkan Kalimat
    pipeline = []
    
    # --- INTENT: Chat / Kirim Pesan (WhatsApp) ---
    chat_match = re.search(r'(?:kirim\s+pesan|chat|wa|kirim\s+wa)\s+(?:ke\s+)?([^\s,]+)(?:\s+bilang|\s+isi|\s*:\s*|\s+pesan\s+)?(.*)', goal_lower)
    if "wa" in goal_lower or "whatsapp" in goal_lower or chat_match:
        plan["domain"] = "android_ui"
        plan["action_type"] = "CHAT_SEND"
        recipient = ""
        message = ""
        if chat_match:
            recipient = chat_match.group(1).strip()
            message = chat_match.group(2).strip()
            # Bersihkan imbuhan
            message = re.sub(r'^(?:bilang|isi|bahwa|pesan)\s+', '', message).strip()
        
        plan["human_summary"] = f"Mengirim pesan WhatsApp ke '{recipient}': \"{message}\""
        pipeline.append("open wa")
        pipeline.append("if-tap 'Lanjut || Continue || Tutup || Close'")
        if recipient:
            pipeline.append("tap 'Search || Telusuri || id:menuitem_search || desc:Search'")
            pipeline.append(f"set '{recipient}'")
            pipeline.append(f"tap '{recipient}'")
        if message:
            pipeline.append(f"tap-set 'Ketik pesan || Message || id:entry' '{message}'")
            pipeline.append("tap 'Kirim || Send || id:send || desc:Send'")
        pipeline.append("done")
        plan["steps"].append({"action": "satset_pipeline", "commands": pipeline})
        return plan

    # --- INTENT: Belanja / Pesan Makanan (Shopee / Grab / Gojek / Tokopedia) ---
    is_shopping = any(x in goal_lower for x in ["beli", "belikan", "pesan", "order", "shopee", "tokped", "tokopedia", "grab", "gojek", "gofood", "grabfood"])
    if is_shopping:
        target_app = app_id if app_id in ["shopee", "tokopedia", "gojek", "grab"] else "shopee"
        plan["domain"] = "android_ui"
        plan["action_type"] = "ECOMMERCE_ORDER"
        plan["requires_safety_stop"] = True  # GUARDRAIL: Berhenti di keranjang/ringkasan!

        # Ekstrak kata kunci produk
        query = re.sub(r'(?:beli|belikan|pesan|order|di\s+shopee|di\s+tokped|di\s+tokopedia|di\s+grab|di\s+gojek|cari|carikan|yang\s+paling\s+murah|diskon|promo)', '', goal_lower)
        query = re.sub(r'\s+', ' ', query).strip()
        if not query:
            query = "promo hari ini"

        plan["human_summary"] = f"Mencari '{query}' di {target_app.title()}, memilih promo terbaik, dan menyimpannya di keranjang belanja (Aman)"
        pipeline.append(f"open {target_app}")
        pipeline.append("if-tap 'Tutup || Lewati || Nanti Saja || Batal || id:close'")
        pipeline.append(f"search '{query}'")
        pipeline.append(f"tap-best --discount '{query}'")
        pipeline.append("tap 'Masukkan Keranjang || Tambah ke Keranjang || + Keranjang || id:add_to_cart'")
        plan["steps"].append({"action": "satset_pipeline", "commands": pipeline})
        return plan

    # --- INTENT: Media & Video (YouTube) ---
    is_video = any(x in goal_lower for x in ["putar", "tonton", "video", "yt", "youtube", "dengarkan", "lagu", "musik"])
    if is_video or app_id == "youtube":
        plan["domain"] = "android_ui"
        plan["action_type"] = "MEDIA_PLAYBACK"
        query = re.sub(r'(?:putar|tonton|cari\s+video|cari|dengarkan|di\s+youtube|di\s+yt|video)', '', goal_lower).strip()
        if not query:
            query = "lofi hip hop"
        plan["human_summary"] = f"Membuka YouTube, mencari '{query}', dan langsung memutar video"
        pipeline.append("open yt")
        pipeline.append("if-tap 'Tidak, terima kasih || Batal || Lewati'")
        pipeline.append(f"search '{query}'")
        pipeline.append("tap '#1 || #2'")
        pipeline.append("done")
        plan["steps"].append({"action": "satset_pipeline", "commands": pipeline})
        return plan

    # --- INTENT: Navigasi / Peta (Google Maps) ---
    is_maps = any(x in goal_lower for x in ["rute", "peta", "maps", "navigasi", "ke mana", "jalan ke"])
    if is_maps or app_id == "maps":
        plan["domain"] = "android_ui"
        plan["action_type"] = "NAVIGATION"
        destination = re.sub(r'(?:rute\s+ke|jalan\s+ke|ke\s+|cari\s+rute|peta|maps|navigasi)', '', goal_lower).strip()
        plan["human_summary"] = f"Mencari rute navigasi Google Maps menuju '{destination}'"
        pipeline.append("open maps")
        pipeline.append("if-tap 'Batal || Tutup || Nanti'")
        if destination:
            pipeline.append(f"search '{destination}'")
            pipeline.append("tap 'Rute || Directions || id:directions_button'")
        pipeline.append("done")
        plan["steps"].append({"action": "satset_pipeline", "commands": pipeline})
        return plan

    # --- INTENT: Alarm & Jam ---
    if any(x in goal_lower for x in ["alarm", "bangunkan", "jam "]):
        plan["domain"] = "android_ui"
        plan["action_type"] = "CLOCK_ALARM"
        plan["human_summary"] = "Membuka aplikasi Jam untuk menyetel alarm"
        pipeline.append("open com.google.android.deskclock")
        pipeline.append("tap 'Alarm || desc:Alarm'")
        pipeline.append("tap 'Tambah alarm || id:fab || desc:Tambah alarm'")
        plan["steps"].append({"action": "satset_pipeline", "commands": pipeline})
        return plan

    # --- INTENT UMUM: Buka Aplikasi atau Pencarian Umum ---
    if app_id:
        plan["domain"] = "android_ui"
        plan["action_type"] = "APP_INTERACT"
        plan["human_summary"] = f"Membuka aplikasi {app_meta.get('name', app_id)}"
        pipeline.append(f"open {app_id}")
        pipeline.append("if-tap 'Tutup || Lewati || Nanti Saja || Batal'")
        # Jika ada kata pencarian
        search_kw = re.search(r'(?:cari|search)\s+([^,]+)', goal_lower)
        if search_kw:
            q = search_kw.group(1).strip()
            pipeline.append(f"search '{q}'")
        plan["steps"].append({"action": "satset_pipeline", "commands": pipeline})
        return plan

    # --- DEFAULT: General Fallback ---
    plan["domain"] = "general_computation"
    plan["action_type"] = "GENERAL"
    plan["human_summary"] = f"Mengevaluasi instruksi: {goal}"
    plan["steps"].append({"action": "shell", "cmd": f"echo 'Instruksi: {goal}'"})
    return plan

def check_and_ensure_daemon():
    """
    Pemeriksaan pre-flight mandiri agar orang awam tidak terkena error socket.
    """
    # Jalankan doctor silent check
    try:
        import importlib.util
        spec = importlib.util.spec_from_file_location("device_doctor", DOCTOR_SCRIPT)
        doc = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(doc)
        
        if doc.ping_daemon():
            return True, "Daemon ready"
        
        # Coba auto-reconnect
        adb_ok, _ = doc.get_adb_status()
        if not adb_ok:
            adb_ok, _ = doc.auto_reconnect_adb()
        if adb_ok:
            return doc.start_daemon_via_adb()
        return False, "ADB not connected"
    except Exception as e:
        return False, str(e)

def execute_plan(plan: dict):
    t0 = time.time()
    success = True
    details = []

    print(f"\n🚀 [OPERATOR] Memulai eksekusi: {plan.get('human_summary', plan['goal'])}")
    if plan.get("requires_safety_stop"):
        print("🛡️ [GUARDRAIL] Mode Pembelian Aman: Alur akan otomatis berhenti di halaman keranjang/ringkasan pembayaran demi keamanan Anda.")

    for step in plan["steps"]:
        action = step.get("action")
        if action == "satset_pipeline":
            cmds = step.get("commands", [])
            
            # Pre-flight check
            d_ok, d_msg = check_and_ensure_daemon()
            if not d_ok:
                print("\n⚠️ [PERINGATAN] Ponsel sedang tidak terhubung ke jembatan pengendali.")
                print("   Menjalankan asisten dokter perangkat...")
                subprocess.run([sys.executable, DOCTOR_SCRIPT])
                return False, "connection_required", ["Perangkat belum terhubung. Silakan ikuti panduan dokter di atas."], 0

            full_cmd = [SATSET_BIN, "do"] + cmds
            print(f"⚡ [OPERATOR] Menjalankan alur ponsel: {' -> '.join(cmds)}")
            code, out, err, dt = run_cmd(full_cmd)
            if code != 0:
                success = False
                details.append(f"Gagal ({code}): {err}")
                run_cmd([SATSET_BIN, "clean"])
            else:
                details.append(f"Selesai dalam {dt}ms: {out}")

        elif action == "shell":
            cmd = step.get("cmd")
            code, out, err, dt = run_cmd(cmd, shell=True)
            if code != 0:
                success = False
                details.append(f"Gagal ({code}): {err}")
            else:
                details.append(f"Selesai ({dt}ms):\n{out}")

    total_dt = int((time.time() - t0) * 1000)
    outcome = "success" if success else "failure"

    if success:
        print(f"\n✅ [OPERATOR] Tugas Selesai dalam {total_dt}ms!")
        if plan.get("requires_safety_stop"):
            print("👉 Silakan periksa layar ponsel Anda untuk memverifikasi produk di keranjang dan melanjutkan jika sudah sesuai.")
    else:
        print(f"\n❌ [OPERATOR] Terjadi kendala saat eksekusi ({total_dt}ms).")

    # Autonomous Learning: Catat episode ke AGI Memory
    run_cmd([
        sys.executable, MEM_SCRIPT, "record",
        plan["goal"],
        plan["domain"],
        json.dumps(plan),
        outcome,
        "\n".join(details),
        str(total_dt)
    ])

    return success, outcome, details, total_dt

def main():
    if len(sys.argv) < 2:
        print("Penggunaan Operator Ponsel Cerdas:")
        print("  operator \"<ide mentah Anda>\"   -> Eksekusi langsung (cth: operator \"pesan martabak di gofood\")")
        print("  operator doctor                -> Cek kesiapan & koneksi ponsel")
        print("  operator pair <port> <kode>    -> Sambungkan debug nirkabel HP")
        print("  operator plan \"<ide mentah>\"   -> Lihat rencana langkah tanpa mengeksekusi")
        sys.exit(0)

    arg1 = sys.argv[1].lower()

    if arg1 in ["doctor", "check", "--doctor", "--check"]:
        subprocess.run([sys.executable, DOCTOR_SCRIPT])
        return

    if arg1 in ["pair", "--pair"]:
        if len(sys.argv) >= 4:
            subprocess.run([sys.executable, DOCTOR_SCRIPT, "pair", sys.argv[2], sys.argv[3]])
        else:
            print("Format salah. Gunakan: agi pair <port> <kode_pairing>")
        return

    if arg1 == "plan":
        statement = " ".join(sys.argv[2:]) if len(sys.argv) > 2 else ""
        ctx = resolve_context(statement)
        plan = formulate_plan(statement, ctx)
        print(json.dumps(plan, indent=2, ensure_ascii=False))
        return

    if arg1 in ["solve", "run", "do"]:
        statement = " ".join(sys.argv[2:]) if len(sys.argv) > 2 else ""
    else:
        statement = " ".join(sys.argv[1:])

    if not statement:
        print("Silakan masukkan instruksi atau ide mentah Anda.")
        sys.exit(1)

    ctx = resolve_context(statement)
    plan = formulate_plan(statement, ctx)
    execute_plan(plan)

if __name__ == "__main__":
    main()
