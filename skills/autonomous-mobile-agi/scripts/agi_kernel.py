#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
AGI Cognitive Kernel & General Problem Solver (GPS)
Autonomous Reasoning, In-Memory Tool Synthesis, Intent Resolution, and Self-Healing Loop.
"""
import sys
import os
import re
import json
import time
import subprocess
import shutil

MEM_SCRIPT = os.path.expanduser("~/.agents/skills/autonomous-mobile-agi/scripts/agi_memory.py")
EPHEMERAL_RUNNER = os.path.expanduser("~/.agents/skills/autonomous-mobile-agi/scripts/ephemeral_runner.sh")
SATSET_BIN = os.path.expanduser("~/.local/bin/satset")

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
                # Jika kata kunci ada dalam goal, sertakan
                for word in goal.lower().split():
                    if len(word) > 2 and word in key:
                        context[key] = item.get("data_json")
        except Exception:
            pass
    return context

def synthesize_c_tool(tool_name: str, c_code: str):
    """
    Kompilasi micro-tool C instan di RAM via ephemeral runner.
    """
    print(f"[AGI-KERNEL] Synthesizing ephemeral native tool: {tool_name}")
    code, out, err, dt = run_cmd([EPHEMERAL_RUNNER, "c", c_code])
    if code == 0:
        # Register ke AGI memory
        run_cmd([sys.executable, MEM_SCRIPT, "tool-reg", tool_name, "system", "c", c_code])
    return code == 0, out, err, dt

def formulate_plan(goal: str, context: dict):
    """
    General Goal Decomposition:
    Menganalisis domain tujuan (Android UI, OS Shell, Data/Algoritma, Memory).
    """
    goal_lower = goal.lower()
    plan = {
        "goal": goal,
        "domain": "general",
        "steps": [],
        "context_applied": context
    }

    # Heuristic 1: Android App Interaction (WhatsApp, IG, YouTube, Settings, etc.)
    app_keywords = {
        "wa": "whatsapp", "whatsapp": "whatsapp",
        "ig": "instagram", "instagram": "instagram",
        "yt": "youtube", "youtube": "youtube",
        "settings": "settings", "setelan": "settings",
        "browser": "chrome", "chrome": "chrome"
    }

    detected_app = None
    for kw, app in app_keywords.items():
        if re.search(r'\b' + re.escape(kw) + r'\b', goal_lower):
            detected_app = app
            break

    if detected_app or any(x in goal_lower for x in ["buka", "tap", "kirim", "cari"]):
        plan["domain"] = "android_ui"
        pipeline = []
        if detected_app:
            pipeline.append(f"open {detected_app}")

        # Cek jika ada perintah cari / search
        search_match = re.search(r'(?:cari|search)\s+([^,]+)', goal_lower)
        # Cek pesan / message
        msg_match = re.search(r'(?:kirim\s+pesan|chat)\s+(.+?)\s+ke\s+(.+)', goal_lower)

        if search_match:
            query = search_match.group(1).strip()
            pipeline.append(f"tap-set 'Search || Telusuri || id:search' '{query}' --enter")
        elif msg_match:
            msg = msg_match.group(1).strip()
            recipient = msg_match.group(2).strip()
            pipeline.append(f"tap '{recipient}'")
            pipeline.append(f"set '{msg}'")
            pipeline.append("tap 'Kirim || Send || id:send'")

        pipeline.append("done")
        plan["steps"].append({"action": "satset_pipeline", "commands": pipeline})
    
    # Heuristic 2: System / Memory / Storage Optimization
    elif any(x in goal_lower for x in ["ram", "memori", "cpu", "proses", "optimasi", "bersih"]):
        plan["domain"] = "system_optimization"
        plan["steps"].append({"action": "shell", "cmd": "free -h; satset clean"})

    # Default: Autonomous General Execution
    else:
        plan["domain"] = "general_computation"
        plan["steps"].append({"action": "shell", "cmd": f"echo 'Evaluating goal: {goal}'"})

    return plan

def execute_plan(plan: dict):
    t0 = time.time()
    success = True
    details = []

    for step in plan["steps"]:
        action = step.get("action")
        if action == "satset_pipeline":
            cmds = step.get("commands", [])
            full_cmd = [SATSET_BIN, "do"] + cmds
            print(f"[AGI-KERNEL] Dispatching Sub-Millisecond Native Pipeline: {' '.join(cmds)}")
            code, out, err, dt = run_cmd(full_cmd)
            if code != 0:
                success = False
                details.append(f"Satset failed ({code}): {err}")
                # Autonomous self-healing fallback
                print("[AGI-KERNEL] Triggering Metacognitive Fallback (Screen Refresh & Re-inspect)...")
                run_cmd([SATSET_BIN, "clean"])
            else:
                details.append(f"Satset executed in {dt}ms: {out}")

        elif action == "shell":
            cmd = step.get("cmd")
            print(f"[AGI-KERNEL] Running OS Effector: {cmd}")
            code, out, err, dt = run_cmd(cmd, shell=True)
            if code != 0:
                success = False
                details.append(f"Shell failed ({code}): {err}")
            else:
                details.append(f"Shell output ({dt}ms):\n{out}")

    total_dt = int((time.time() - t0) * 1000)
    outcome = "success" if success else "failure"

    # Autonomous Learning: Record Episode to Memory
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
        print("Usage: satset-agi <goal|plan|solve> \"<statement>\" [options]")
        sys.exit(1)

    cmd = sys.argv[1]
    statement = " ".join(sys.argv[2:]) if len(sys.argv) > 2 else ""

    if cmd in ["solve", "run", "do"]:
        if not statement:
            print("Error: Empty goal statement", file=sys.stderr)
            sys.exit(1)
        print(f"[AGI-KERNEL] Ingesting Goal: \"{statement}\"")
        ctx = resolve_context(statement)
        if ctx:
            print(f"[AGI-KERNEL] Context Resolved from Memory: {list(ctx.keys())}")
        plan = formulate_plan(statement, ctx)
        print(f"[AGI-KERNEL] Generated Plan: Domain={plan['domain']}, Steps={len(plan['steps'])}")
        ok, outcome, details, dt = execute_plan(plan)
        print(f"[AGI-KERNEL] Execution Complete [{outcome.upper()}] in {dt}ms")
        for d in details:
            print(f"  -> {d}")
    elif cmd == "plan":
        ctx = resolve_context(statement)
        plan = formulate_plan(statement, ctx)
        print(json.dumps(plan, indent=2))
    elif cmd == "status":
        print("[AGI-KERNEL] Status: Active & Operational")
        run_cmd([sys.executable, MEM_SCRIPT, "list"])
    else:
        # Fallback treat entire args as goal
        goal = " ".join(sys.argv[1:])
        ctx = resolve_context(goal)
        plan = formulate_plan(goal, ctx)
        execute_plan(plan)

if __name__ == "__main__":
    main()
