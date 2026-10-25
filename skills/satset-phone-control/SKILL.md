---
name: satset-phone-control
description: >-
  Ultra-responsive ("satset" v4) sub-millisecond Android phone operation, real-time game playing,
  and zero-footprint automation engine. Use this skill whenever the user asks to control their phone,
  play mobile games in real-time, open apps, send messages on WhatsApp/Instagram/Discord,
  browse or checkout on Shopee, edit photos in Snapseed/Lightroom, post to TikTok/Instagram,
  change system settings, or perform any UI navigation on Android.
  Automatically purges temporary files and returns to Termux (`satset done`) after finishing a task.
---

# ⚡ SATSET v4 — Sub-Millisecond Native C CLI + Event-Driven Daemon & Zero-Footprint Auto-Reaper

`satset` (`/data/data/com.termux/files/home/.local/bin/satset` -> `/data/data/com.termux/files/usr/bin/satset`) is a **Native ARM64 C Binary** (`clang -O3 -flto`, **~0.4ms startup**) connected to a resident DEX bytecode engine (`SatsetDaemon v4` on `127.0.0.1:19888`) powered by Android's real-time `OnAccessibilityEventListener`, zero-allocation multi-pointer `InputDispatcher`, and an automatic Ephemeral File & RAM Reaper.

## Why `satset` v4 Eliminates Every Single Millisecond of Delay

| Operation | Legacy ADB / Python v3 | `satset` v4 Engine |
| :--- | :--- | :--- |
| **CLI Startup Overhead** | `40 – 55 ms` (Python interpreter + imports) | **`~0.4 ms`** (Native ARM64 `-O3` C binary) |
| **UI Hierarchy Dump** | `1,500 – 4,000 ms` (ADB) / `15 – 35 ms` (v3) | **`0.01 ms` (Event-Cache Hit) / `4 – 12 ms` (`PREFETCH_HYBRID`)** |
| **Tap / Click** | `60 – 100 ms` (`InteractionController`) | **`5 ms`** (Direct `MotionEvent` Slot Injection) |
| **Text Injection (`set`)** | `40 – 85 ms` (Click + sleep + set) | **`2 – 4 ms`** (Direct `ACTION_FOCUS` + `ACTION_SET_TEXT`) |
| **Multi-Step Pipeline (`do`)** | Multiple socket roundtrips + `590 ms` blind sleeps | **1 TCP Packet In-Daemon + `<0.2 ms` `AccessibilityEvent` Wakeups** |
| **Real-Time Game Loop** | Vision blocked control thread every 220ms | **Decoupled 85Hz–120Hz Control Thread + Async Zero-Alloc Vision Thread** |
| **Temp File & RAM Cleanup** | Manual cleanup (leftover `.jpg` / `.log` files) | **Automatic (`0` leftovers): Instant purge on `done` + 15s TTL Reaper** |

---

## 🔔 Mandatory Rules: Speed ("1ms Berharga") & Zero-Footprint Cleanup

1. **Execute Entire Workflows in ONE Single `satset do` Call Ending with `"done"`**:
   - Never split sequential steps (`open`, `tap`, `set`, `tap`, `done`) across multiple tool calls or shell commands.
   - `satset do` sends all steps in a **single TCP packet** to `SatsetDaemon v4`, which executes them back-to-back inside the JVM using real-time `AccessibilityEvent` notifications (zero blind `sleep`s!), captures `--- CURRENT UI ---` of the target app, **purges all temporary files & RAM caches**, and switches back to Termux in **~8ms**:
     ```bash
     satset do "open whatsapp" "tap 'Gilang'" "set 'Otw sekarang bro! 🔥'" "tap 'Kirim || Send || id:send'" "done"
     ```
2. **Use Atomic `tap-set` for Search Bars & Chat Inputs**:
   - Instead of separate `tap` + `set` + `enter` steps, you can also use `"tap-set '<target>' '<text>' --enter"` inside `satset do` or standalone:
     ```bash
     satset do "open youtube" "tap-set 'Search || Telusuri || id:search' 'lofi hip hop radio' --enter" "done"
     ```
3. **Zero Residual Files Policy (Automatic Ephemeral Storage `/sdcard/.satset_tmp/shot.jpg`)**:
   - By default, `satset shot` and `satset game-shot` write directly from daemon RAM to `/sdcard/.satset_tmp/shot.jpg` (protected by `.nomedia` so Android Gallery/MediaScanner never indexes temporary screenshots).
   - **Auto-Purge Guarantee**:
     - Taking a new `shot` / `game-shot` automatically deletes any previous temporary screenshot first.
     - Running `satset done` (or ending `satset do ... "done"`) **immediately deletes all temporary files** (`purged=N temp files`) and frees daemon RAM caches.
     - Even if `done` is not called yet, `SatsetDaemon v4`'s background Reaper thread automatically deletes any temporary file after **15 seconds**!
     - You can also manually trigger an instant purge at any time with `satset clean`.

---

## 1. Real-Time Gaming Engine (`SatsetDaemon v4`)

`SatsetDaemon v4` uses **pre-allocated static `PointerProperties`/`PointerCoords` and static vision arena buffers (`0 bytes` heap allocation per frame)** so garbage collection never causes frame stutters.

### A. Real-Time Gemini Live RAM Buffer & Tactical Game Vision (`live`, `stream`, `game-shot`)
- **Instant Live Vision (Gemini Live RAM Pipe - `0ms` wait / ~20ms total CLI)**:
  ```bash
  satset live
  ```
  Menyimpan frame paling mutakhir dari circular buffer RAM daemon ke `/sdcard/.satset_tmp/shot.jpg` secara instan tanpa memblokir thread eksekusi. Otomatis menyalakan background stream jika belum aktif.
- **Background Stream Control**:
  ```bash
  satset stream start [interval_ms] [scale] [quality] # e.g. satset stream start 60 0.5 60
  satset stream status                                # Cek fps, counter, dan latency frame
  satset stream stop                                  # Hentikan background stream (otomatis juga di-stop saat satset done)
  ```
- **Capture Screenshot Manual with Tactical 200px Coordinate Grid**:
  ```bash
  satset game-shot
  ```
  Saves to `/sdcard/.satset_tmp/shot.jpg` (auto-purged on `satset done` or after 15s). Automatically detects Portrait (`1220x2712`) vs Landscape (`2712x1220`) and draws a 200px cyan coordinate grid with `(x,y)` labels and SoM boxes. Read `/sdcard/.satset_tmp/shot.jpg` with `read_file` to get exact HUD coordinates.
- **Instant Pixel Telemetry**:
  ```bash
  satset pixel "350,120 2400,980"
  ```
- **Zero-Allocation In-RAM Color Cluster Scanner & Auto-Tap**:
  ```bash
  # Find red enemy health bars or glowing interactive objects in region
  satset scan-color "#E52525" --tol 30 --region 200,100,2500,1000

  # Find and immediately tap (5ms) the largest matching color cluster
  satset tap-color "#FFD700" --tol 28 --region 200,100,2500,1000

  # Wait until pixel matches (or stops matching with --gone) a target color
  satset wait-color 1350,600 "#FFFFFF" 5000 --tol 25
  ```

### B. Multi-Pointer Touch Physics (Simultaneous Left-Thumb Joystick + Right-Thumb Combat)
- **Pointer Slot 0** is dedicated to the **Virtual Joystick** (~85Hz smooth updates).
- **Pointer Slots 1–4** are used for **5ms Taps, Burst Mashing, Holds, and 120Hz Skillshot Aiming** without interrupting Slot 0!

- **Virtual Joystick (`satset joy`)**:
  ```bash
  satset joy ne 1200
  satset joy strafe 2500 --at 380,880 --radius 160
  satset joy circle 5000 --bg
  satset joy stop
  ```
- **Simultaneous 2-Thumb Multi-Touch Choreography (`satset combo`)**:
  ```bash
  satset combo 2000 strafe "mash:2420,980:85|aim:2120,1020:ne:170@400|tap:2220,820@1100"
  ```
- **Directional Skillshot Aiming (`satset aim`)**:
  ```bash
  satset aim 2120 1020 ne 180 60
  ```
- **Ultra-Fast Burst Mashing (`satset mash`)**:
  ```bash
  satset mash 2420,980 15 25
  satset mash "2420,980;2120,1020;2220,820" 18 25
  ```
- **120Hz Smooth Hold & Drag (`satset hold` / `satset drag`)**:
  ```bash
  satset hold 1350 700 800
  satset drag 600 500 1200 500 180
  ```
- **Hardware Keyboard / Gamepad Injection (`satset key-hold`)**:
  ```bash
  satset key-hold w 1000
  satset key-hold btn_a 200
  ```

### C. Decoupled Dual-Thread Real-Time Combat Autopilot (`satset autopilot`)
Runs two independent threads inside `SatsetDaemon v4`:
1. **`satset-autopilot` (85Hz Control Thread)**: Moves joystick on Slot 0 and fires attacks/skills on Slot 1 with **0ms blocking**.
2. **`satset-ap-vision` (Async Vision Thread)**: Scans frames in RAM using zero-allocation static buffers to track enemy color clusters (`--track`) and monitor HP bars (`--hp`) in parallel!
```bash
satset autopilot start \
  --duration 6000 \
  --joy strafe \
  --joy-at 380,880,160 \
  --atk "2420,980" \
  --skills "2120,1020;2220,820;2380,700" \
  --track "#E52525,30,200,100,2500,1000" \
  --hp "450,80,#2ECC71,1920,1030"

satset autopilot status
satset autopilot stop
```

---

## 2. Standard App Automation (`satset do`)

Always combine multi-step phone interactions into **one single `satset do` command**, ending with `"done"`:

```bash
# Open WhatsApp, open a chat, inject a message with emojis, tap Send, purge temp files, and return to Termux
satset do \
  "open whatsapp" \
  "tap 'Gilang'" \
  "set 'Gass lur, jam piro budhal? 🔥'" \
  "tap 'Kirim || Send || id:send'" \
  "done"
```

```bash
# Autonomous In-Daemon Shopee Best-Value Finder + Add to Cart + Return to Termux
satset shopee-cart "cincin perak pria 925" --max 85000 --done
```

---

## 3. Target Selector Syntax

`satset tap`, `satset longtap`, `satset set`, `satset tap-set`, and `satset wait` accept rich target selectors:
- **By Index**: `#12` or `12` (taps item `[12]` from the last `satset ui` output in **5ms**)
- **By Coordinates**: `610 1350` or `610,1350`
- **By Fallback Chain (`||`)**: `"Kirim || Send || desc:Send || id:send"`
- **By Prefixed Attribute**: `id:`, `desc:`, `text:`, `exact:`, `class:`

---

## 4. Daemon & Memory Management

- `satset status` — Check daemon version (`v4`), connection, event counter, active game loops, and ephemeral file count (`0`).
- `satset clean` — Immediately purge all temporary files (`/sdcard/.satset_tmp/*`, `/sdcard/satset*`) and free daemon RAM caches.
- `satset restart` — Zero-ADB hot-reload (`HOT_RELOAD`) of `/data/local/tmp/satset-daemon.jar`.
- `satset done` — Stop any active game loops, release touch pointers, purge all temporary files, and return to Termux in ~8ms.
- Source code:
  - Daemon (Java): [`SatsetDaemon.java`](file:///data/data/com.termux/files/home/.local/share/satset/src/SatsetDaemon.java)
  - Native CLI (C): [`satset.c`](file:///data/data/com.termux/files/home/.local/share/satset/src/satset.c)
  - Compiled CLI Binary: [`satset`](file:///data/data/com.termux/files/home/.local/bin/satset)
