#!/usr/bin/env bash
# ⚡ Daemon Reflex Shield v1.0
# Menangani popup konfirmasi mekanis (Ya/Hapus/Konfirmasi/Tutup) secara instan
# tanpa membuang 1 turn cloud LLM (2-4 detik).

set -euo pipefail

SATSET_BIN="${HOME}/.local/bin/satset"

# 1. Jalankan pipeline yang diminta
if [ "$#" -gt 0 ]; then
    "$SATSET_BIN" do "$@"
fi

# 2. Reflex Shield: Sweep dialog konfirmasi mekanis umum dalam 5ms
"$SATSET_BIN" do \
    "if-tap 'Ya || Hapus || Konfirmasi || OK || id:buttonDefaultPositive'" \
    "if-tap 'Tutup || Lewati || Nanti || Batal || id:buttonCloseDrawer || id:buttonClose'" \
    2>/dev/null || true
