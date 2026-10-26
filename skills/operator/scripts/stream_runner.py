#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
⚡ AGI-STREAM v1.0 — Streaming Early-Action Dispatcher
Membaca token streaming secara real-time dari stdin/pipe dan langsung
mengeksekusi aksi 'open', 'search', atau 'tap' ke SatsetDaemon di background
sebelum seluruh output LLM selesai di-generate.
"""

import sys
import os
import re
import time
import subprocess
import threading
from typing import Set

SATSET_BIN = os.path.expanduser("~/.local/bin/satset")

# Pola aksi yang lengkap (membutuhkan spasi/delimiter penutup, bukan $)
EARLY_ACTION_PATTERNS = [
    # Pola: satset/agi-ctrl do "open <app>"
    re.compile(r'(?:agi-ctrl|satset)\s+do\s+["\']open\s+([a-zA-Z0-9_\.]+)["\']', re.IGNORECASE),
    # Pola: satset/agi-ctrl open <app> (wajib diakhiri spasi, newline, atau quote)
    re.compile(r'(?:agi-ctrl|satset)\s+open\s+([a-zA-Z0-9_\.]+)[ \t\r\n\'"`]', re.IGNORECASE),
    # Pola JSON tool call: {"action": "open", "target": "shopee"}
    re.compile(r'["\']action["\']\s*:\s*["\']open["\']\s*,\s*["\']target["\']\s*:\s*["\']([a-zA-Z0-9_\.]+)["\']', re.IGNORECASE),
    # Pola makro ber-tanda petik: "open shopee"
    re.compile(r'["\']open\s+([a-zA-Z0-9_\.]+)["\']', re.IGNORECASE)
]

def async_dispatch(cmd_args: list):
    """Menjalankan aksi ke satset di background thread tanpa blocking stream."""
    try:
        t0 = time.time()
        subprocess.run(cmd_args, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=5)
        dt = int((time.time() - t0) * 1000)
        sys.stderr.write(f"\n⚡ [EARLY-ACTION DISPATCHED] {' '.join(cmd_args)} ({dt}ms)\n")
        sys.stderr.flush()
    except Exception as e:
        sys.stderr.write(f"\n⚠️ [EARLY-ACTION ERROR] {e}\n")
        sys.stderr.flush()

def main():
    dispatched_actions: Set[str] = set()
    active_workers = []
    buffer = ""
    
    # Membaca token karakter per karakter / chunk per chunk dari stdin
    while True:
        chunk = sys.stdin.read(1)
        if not chunk:
            break
            
        sys.stdout.write(chunk)
        sys.stdout.flush()
        buffer += chunk
        
        # Batasi buffer geser agar tidak membengkak di RAM
        if len(buffer) > 1024:
            buffer = buffer[-512:]
            
        # Pindai pola early-action di dalam sliding buffer
        for pattern in EARLY_ACTION_PATTERNS:
            match = pattern.search(buffer)
            if match:
                app_target = match.group(1).strip().lower()
                
                if app_target not in dispatched_actions:
                    dispatched_actions.add(app_target)
                    worker = threading.Thread(
                        target=async_dispatch,
                        args=([SATSET_BIN, "open", app_target],)
                    )
                    active_workers.append(worker)
                    worker.start()

    # Tunggu worker yang sedang berjalan selesai sebelum exit
    for w in active_workers:
        w.join(timeout=3.0)

if __name__ == "__main__":
    main()
