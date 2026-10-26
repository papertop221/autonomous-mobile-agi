#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
⚡ Operator Core — Microsecond-Grade Universal Python C-FFI Bridge
Direct in-memory binding to liboperator.so for zero-fork, sub-microsecond Android effector.
Compatible with ANY AI Agent (Antigravity, Claude Code, OpenAI Codex, OpenDevin, Cursor, etc.).
"""

import os
import sys
import ctypes
import subprocess
from typing import List, Optional

SO_PATH = os.path.expanduser("~/.local/lib/liboperator.so")
BIN_PATH = os.path.expanduser("~/.local/bin/operator")

class OperatorCore:
    def __init__(self):
        self._lib = None
        self._initialized = False
        self._load_library()

    def _load_library(self):
        if os.path.exists(SO_PATH):
            try:
                self._lib = ctypes.CDLL(SO_PATH)
                self._lib.op_init.restype = ctypes.c_int
                self._lib.op_send.argtypes = [ctypes.c_char_p]
                self._lib.op_send.restype = ctypes.c_char_p
                self._lib.op_tap.argtypes = [ctypes.c_int, ctypes.c_int]
                self._lib.op_tap.restype = ctypes.c_int
                self._lib.op_do.argtypes = [ctypes.c_char_p]
                self._lib.op_do.restype = ctypes.c_int
                self._lib.op_close.restype = None
                self._lib.op_bench.argtypes = [ctypes.c_int]
                self._lib.op_bench.restype = ctypes.c_double
                self._initialized = True
            except Exception:
                self._lib = None
                self._initialized = False

    def is_native_ffi_ready(self) -> bool:
        return self._initialized and self._lib is not None

    def send(self, cmd_str: str) -> str:
        """Send command via persistent in-memory C connection (sub-microsecond)"""
        if self._initialized:
            try:
                res = self._lib.op_send(cmd_str.encode("utf-8"))
                return res.decode("utf-8", errors="ignore") if res else ""
            except Exception:
                pass
        # Fallback to CLI binary
        res = subprocess.run([BIN_PATH, "sh", cmd_str], capture_output=True, text=True)
        return res.stdout.strip()

    def tap(self, x: int, y: int) -> bool:
        """Instant tap at coordinates (x, y)"""
        if self._initialized:
            try:
                return self._lib.op_tap(int(x), int(y)) == 0
            except Exception:
                pass
        res = subprocess.run([BIN_PATH, "tap", str(x), str(y)], capture_output=True)
        return res.returncode == 0

    def do(self, *steps, done: bool = False) -> str:
        """
        1-Packet Macro Pipeline:
        Executes multi-step flow in 1 single network dispatch.
        """
        args = list(steps)
        if done:
            args.append("done")
        
        # Format payload: DO\t<return_termux>\t<step1>\t<step2>...
        ret_termux = "1" if done else "0"
        payload = f"DO\t{ret_termux}\t" + "\t".join(args)
        return self.send(payload)

    def bench(self, iters: int = 500) -> float:
        """Returns average roundtrip latency in microseconds"""
        if self._initialized:
            try:
                return float(self._lib.op_bench(iters))
            except Exception:
                pass
        return -1.0

# Singleton instance for universal agent use
operator = OperatorCore()

if __name__ == "__main__":
    print(f"Operator Core FFI Ready: {operator.is_native_ffi_ready()}")
    if operator.is_native_ffi_ready():
        print(f"Native shared library loaded from: {SO_PATH}")
