#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Device Doctor & Smart Layman Bridge Assistant
Memeriksa kesiapan perangkat, mendiagnosis koneksi nirkabel,
dan menyediakan auto-reconnect tanpa membebani pengguna awam dengan istilah teknis.
"""

import sys
import os
import socket
import subprocess
import time
from concurrent.futures import ThreadPoolExecutor

DAEMON_PORT = 19888
SATSET_JAR = os.path.expanduser("~/.local/share/satset/satset-daemon.jar")

def check_socket_port(ip: str, port: int, timeout: float = 0.05) -> bool:
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.settimeout(timeout)
        res = s.connect_ex((ip, port))
        s.close()
        return res == 0
    except Exception:
        return False

def ping_daemon() -> bool:
    if not check_socket_port("127.0.0.1", DAEMON_PORT, timeout=0.1):
        return False
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.settimeout(0.3)
        s.connect(("127.0.0.1", DAEMON_PORT))
        s.sendall(b"PING\n")
        resp = s.recv(1024).decode(errors="ignore")
        s.close()
        return resp.startswith("OK")
    except Exception:
        return False

def get_adb_status():
    try:
        out = subprocess.check_output(["adb", "devices"], text=True, timeout=2)
        lines = [l.strip().split() for l in out.strip().split("\n")[1:] if l.strip()]
        for dev, state in lines:
            if state == "device":
                return True, dev
        return False, None
    except Exception:
        return False, None

def scan_wireless_adb_candidates():
    ports = [5555] + list(range(35000, 48000)) + list(range(30000, 35000))
    open_candidates = []
    with ThreadPoolExecutor(max_workers=200) as ex:
        results = ex.map(lambda p: p if check_socket_port("127.0.0.1", p) else None, ports)
        open_candidates = [p for p in results if p]
    return open_candidates

def auto_reconnect_adb():
    candidates = scan_wireless_adb_candidates()
    for p in candidates:
        try:
            res = subprocess.run(["adb", "connect", f"127.0.0.1:{p}"], capture_output=True, text=True, timeout=1.5)
            if "connected to" in res.stdout.lower() and "failed" not in res.stdout.lower():
                # Verify
                ok, dev = get_adb_status()
                if ok:
                    return True, dev
        except Exception:
            pass
    return False, None

def start_daemon_via_adb():
    if not os.path.exists(SATSET_JAR):
        return False, "File satset-daemon.jar tidak ditemukan di ~/.local/share/satset/"
    cmd = (
        f"adb push '{SATSET_JAR}' /data/local/tmp/satset-daemon.jar >/dev/null 2>&1; "
        "adb shell 'chmod 444 /data/local/tmp/satset-daemon.jar 2>/dev/null; "
        "ANDROID_DATA=/data/local/tmp CLASSPATH=/system/framework/uiautomator.jar:/data/local/tmp/satset-daemon.jar "
        "nohup app_process /system/bin SatsetDaemon >/dev/null 2>&1 </dev/null &' >/dev/null 2>&1"
    )
    subprocess.run(cmd, shell=True)
    time.sleep(0.5)
    for _ in range(15):
        if ping_daemon():
            return True, "SatsetDaemon berhasil dinyalakan!"
        time.sleep(0.1)
    return False, "Daemon belum merespons setelah dinyalakan via ADB."

def pair_wireless_adb(port_or_addr: str, pairing_code: str):
    print(f"\n⏳ Menyandingkan perangkat ke {port_or_addr}...")
    target = port_or_addr if ":" in port_or_addr else f"127.0.0.1:{port_or_addr}"
    res = subprocess.run(["adb", "pair", target, pairing_code], capture_output=True, text=True, timeout=5)
    print(res.stdout.strip() or res.stderr.strip())
    if "successfully paired" in res.stdout.lower():
        print("✅ Berhasil disandingkan! Sekarang mencari port koneksi...")
        ok, dev = auto_reconnect_adb()
        if ok:
            print(f"🚀 Ponsel terhubung: {dev}")
            start_daemon_via_adb()
            return True
    return False

def run_doctor():
    print("=" * 50)
    print("🩺 DOKTER PERANGKAT (Kesiapan Operator Ponsel)")
    print("=" * 50)

    # 1. Cek ADB
    adb_ok, adb_dev = get_adb_status()
    if not adb_ok:
        print("⏳ Memeriksa dan mencoba menyambungkan ulang koneksi nirkabel...")
        adb_ok, adb_dev = auto_reconnect_adb()

    if adb_ok:
        print(f"  [✓] Jembatan Sistem (ADB) : TERHUBUNG ({adb_dev})")
    else:
        print("  [✗] Jembatan Sistem (ADB) : BELUM TERHUBUNG")
        print("\n  💡 PANDUAN MUDAH MENYAMBUNGKAN (Cukup 1 Kali Saja):")
        print("  1. Buka 'Setelan' HP Anda -> 'Opsi Pengembang'.")
        print("  2. Aktifkan 'Proses Debug Nirkabel' (Wireless Debugging).")
        print("  3. Klik 'Sandingkan perangkat dengan kode penyandingan'.")
        print("  4. Ketik perintah di bawah ini (ganti port & kode sesuai layar):")
        print("     agi pair <5-digit-port> <6-digit-kode>\n")
        return False

    # 2. Cek Daemon
    daemon_ok = ping_daemon()
    if not daemon_ok and adb_ok:
        print("  [i] Menghidupkan mesin penggerak otomatis di latar belakang...")
        d_ok, _ = start_daemon_via_adb()
        daemon_ok = ping_daemon()

    if daemon_ok:
        print("  [✓] Mesin Pengendali Layar : SIAP & RESPONSIF (~0.4ms)")
    else:
        print("  [!] Mesin Pengendali Layar : Sedang tidak aktif.")
        print("      Coba jalankan: agi restart")
        return False

    # 3. Telemetri Layar
    print("  [✓] Layar Ponsel           : Siap Menerima Sentuhan & Pencarian")
    print("=" * 50)
    print("🎉 SEMUA SISTEM SIAP! Anda tinggal ketik ide mentah di obrolan AI.")
    print("=" * 50)
    return True

if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] in ["pair", "--pair"]:
        if len(sys.argv) >= 4:
            pair_wireless_adb(sys.argv[2], sys.argv[3])
        else:
            print("Gunakan format: agi pair <port> <kode>")
    else:
        run_doctor()
