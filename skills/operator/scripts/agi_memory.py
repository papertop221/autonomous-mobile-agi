#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
AGI World Model & Episodic Memory Engine (SQLite Sub-Millisecond Store)
Stores concepts, system entities, synthesized tools, episodes, and learned causal relations.
"""
import sys
import os
import sqlite3
import json
import time

DB_DIR = os.path.expanduser("~/.agents/agi")
DB_PATH = os.path.join(DB_DIR, "agi.db")

def get_connection():
    os.makedirs(DB_DIR, exist_ok=True)
    conn = sqlite3.connect(DB_PATH, timeout=5.0)
    conn.row_factory = sqlite3.Row
    return conn

def init_db():
    conn = get_connection()
    with conn:
        # 1. World Entities & Concepts
        conn.execute("""
            CREATE TABLE IF NOT EXISTS entities (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                key TEXT UNIQUE NOT NULL,
                category TEXT NOT NULL,
                data_json TEXT NOT NULL,
                confidence REAL DEFAULT 1.0,
                updated_at INTEGER NOT NULL
            );
        """)
        # 2. Synthesized Tools & Strategies
        conn.execute("""
            CREATE TABLE IF NOT EXISTS synthesized_tools (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT UNIQUE NOT NULL,
                domain TEXT NOT NULL,
                language TEXT NOT NULL, -- c, py, sh
                code_snippet TEXT NOT NULL,
                success_count INTEGER DEFAULT 1,
                last_used INTEGER NOT NULL
            );
        """)
        # 3. Episodic Execution & Causal Memory
        conn.execute("""
            CREATE TABLE IF NOT EXISTS episodes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                goal TEXT NOT NULL,
                domain TEXT NOT NULL,
                plan_json TEXT NOT NULL,
                outcome TEXT NOT NULL, -- success, failure, recovered
                causal_analysis TEXT,
                duration_ms INTEGER NOT NULL,
                created_at INTEGER NOT NULL
            );
        """)
        # Indexes for ultra-fast query
        conn.execute("CREATE INDEX IF NOT EXISTS idx_entities_key ON entities(key);")
        conn.execute("CREATE INDEX IF NOT EXISTS idx_entities_cat ON entities(category);")
        conn.execute("CREATE INDEX IF NOT EXISTS idx_tools_name ON synthesized_tools(name);")
        conn.execute("CREATE INDEX IF NOT EXISTS idx_episodes_goal ON episodes(goal);")
    conn.close()

def set_entity(key: str, val: str, category: str = "general", confidence: float = 1.0):
    init_db()
    conn = get_connection()
    now = int(time.time())
    with conn:
        conn.execute("""
            INSERT INTO entities (key, category, data_json, confidence, updated_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(key) DO UPDATE SET
                category=excluded.category,
                data_json=excluded.data_json,
                confidence=excluded.confidence,
                updated_at=excluded.updated_at
        """, (key, category, val, confidence, now))
    conn.close()
    print(f"[AGI-MEM] Entity '{key}' saved.")

def get_entity(key: str):
    init_db()
    conn = get_connection()
    row = conn.execute("SELECT * FROM entities WHERE key = ? LIMIT 1", (key,)).fetchone()
    conn.close()
    if row:
        print(row["data_json"])
        return row["data_json"]
    else:
        print(f"[AGI-MEM] Key '{key}' not found", file=sys.stderr)
        return None

def query_entities(category: str = None):
    init_db()
    conn = get_connection()
    if category:
        rows = conn.execute("SELECT key, category, data_json, confidence FROM entities WHERE category = ?", (category,)).fetchall()
    else:
        rows = conn.execute("SELECT key, category, data_json, confidence FROM entities ORDER BY updated_at DESC LIMIT 50").fetchall()
    conn.close()
    res = [dict(r) for r in rows]
    print(json.dumps(res, indent=2))
    return res

def record_episode(goal: str, domain: str, plan_json: str, outcome: str, causal_analysis: str, duration_ms: int):
    init_db()
    conn = get_connection()
    now = int(time.time())
    with conn:
        conn.execute("""
            INSERT INTO episodes (goal, domain, plan_json, outcome, causal_analysis, duration_ms, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """, (goal, domain, plan_json, outcome, causal_analysis, duration_ms, now))
    conn.close()
    print(f"[AGI-MEM] Episode recorded ({outcome}): {goal} [{duration_ms}ms]")

def register_tool(name: str, domain: str, lang: str, code: str):
    init_db()
    conn = get_connection()
    now = int(time.time())
    with conn:
        conn.execute("""
            INSERT INTO synthesized_tools (name, domain, language, code_snippet, success_count, last_used)
            VALUES (?, ?, ?, ?, 1, ?)
            ON CONFLICT(name) DO UPDATE SET
                code_snippet=excluded.code_snippet,
                success_count=success_count + 1,
                last_used=excluded.last_used
        """, (name, domain, lang, code, now))
    conn.close()
    print(f"[AGI-MEM] Synthesized Tool '{name}' registered.")

def find_tool(query: str):
    init_db()
    conn = get_connection()
    rows = conn.execute("SELECT * FROM synthesized_tools WHERE name LIKE ? OR domain LIKE ?", (f"%{query}%", f"%{query}%")).fetchall()
    conn.close()
    res = [dict(r) for r in rows]
    print(json.dumps(res, indent=2))
    return res

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: agi_memory.py <init|set|get|list|record|tool-reg|tool-find> [args...]")
        sys.exit(1)

    cmd = sys.argv[1]
    if cmd == "init":
        init_db()
        print("[AGI-MEM] Database initialized at:", DB_PATH)
    elif cmd == "set":
        # key, val, [category], [confidence]
        k = sys.argv[2]
        v = sys.argv[3]
        c = sys.argv[4] if len(sys.argv) > 4 else "general"
        conf = float(sys.argv[5]) if len(sys.argv) > 5 else 1.0
        set_entity(k, v, c, conf)
    elif cmd == "get":
        get_entity(sys.argv[2])
    elif cmd == "list":
        cat = sys.argv[2] if len(sys.argv) > 2 else None
        query_entities(cat)
    elif cmd == "record":
        # goal, domain, plan_json, outcome, causal, duration_ms
        record_episode(sys.argv[2], sys.argv[3], sys.argv[4], sys.argv[5], sys.argv[6], int(sys.argv[7]))
    elif cmd == "tool-reg":
        # name, domain, lang, code
        register_tool(sys.argv[2], sys.argv[3], sys.argv[4], sys.argv[5])
    elif cmd == "tool-find":
        find_tool(sys.argv[2])
    else:
        print(f"Unknown command: {cmd}", file=sys.stderr)
        sys.exit(1)
