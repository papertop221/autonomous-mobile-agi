import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.UiAutomation;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import com.android.uiautomator.core.ShellUiAutomatorBridge;
import com.android.uiautomator.core.UiAutomationShellWrapper;
import com.android.uiautomator.core.UiAutomatorBridge;

import java.io.*;
import java.lang.reflect.Method;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SatsetDaemon v4 — Ultra-Low-Latency Real-Time Android & Game Engine:
 * 1. Event-Driven Accessibility Reactor (OnAccessibilityEventListener) -> <0.2ms wakeups & 0.01ms Smart Tree Cache
 * 2. Zero-Allocation 120Hz Multi-Pointer MotionEvent Synthesizer & Direct 5ms Tap / 55ms Smooth Swipe
 * 3. Decoupled Dual-Thread Real-Time Game Autopilot (Uninterrupted 85Hz Control + Async Zero-Alloc Vision)
 * 4. In-Daemon Single-Roundtrip DO Pipeline (Zero blind sleeps, instant step chaining)
 * 5. Automatic Ephemeral File & RAM Reaper (Zero leftover files on /sdcard or /data/local/tmp, .nomedia isolation)
 */
public class SatsetDaemon {
    public static final int PORT = 19888;
    private static final int PREFETCH_HYBRID = 4; // FLAG_PREFETCH_DESCENDANTS_HYBRID
    private static final int SOURCE_TOUCHSCREEN = 0x00001002;
    private static final int SOURCE_KEYBOARD = 0x00000101;
    private static final int SOURCE_GAMEPAD = 0x00000401;

    private static final String EPHEMERAL_DIR = "/sdcard/.satset_tmp";
    private static final long EPHEMERAL_TTL_MS = 15_000L; // Auto-delete temp shots after 15s even if 'done' wasn't called

    private static final Object lock = new Object();
    private static final Object touchLock = new Object();
    private static final Object visionLock = new Object();
    private static final Object uiEventMonitor = new Object();

    private static UiAutomationShellWrapper wrapper = null;
    private static UiAutomation uiAuto = null;
    private static Object ic = null;
    private static Method mClickNoSync, mLongTapNoSync, mSwipe, mSendKey, mWakeDevice, mSleepDevice;
    private static boolean connected = false;
    private static final long startedAt = System.currentTimeMillis();

    // Real-Time AccessibilityEvent Reactor & Smart Tree Cache
    private static final AtomicLong uiEventSeq = new AtomicLong(0L);
    private static volatile long lastUiEventTime = 0L;
    private static volatile long lastWindowStateChangeTime = 0L;
    private static volatile String activeEventPackage = "";
    private static volatile long cachedTreeEventSeq = -1L;
    private static volatile long cachedTreeTimestamp = 0L;
    private static volatile boolean cachedIncludeAll = false;

    private static final Rect tmpRect = new Rect();
    private static List<UiItem> lastItems = new ArrayList<>(128);
    private static String lastPackage = "";
    private static int cachedScreenW = 1220;
    private static int cachedScreenH = 2712;
    private static long lastScreenDimCheck = 0L;

    // Zero-Allocation 5-Finger Multi-Touch State Machine
    private static final int MAX_POINTERS = 5;
    private static final boolean[] ptrActive = new boolean[MAX_POINTERS];
    private static final float[] ptrX = new float[MAX_POINTERS];
    private static final float[] ptrY = new float[MAX_POINTERS];
    private static long multiDownTime = 0L;
    private static final MotionEvent.PointerProperties[] sharedProps = new MotionEvent.PointerProperties[MAX_POINTERS];
    private static final MotionEvent.PointerCoords[] sharedCoords = new MotionEvent.PointerCoords[MAX_POINTERS];

    // Zero-Allocation Static Vision Arena Buffers (Eliminates 13MB/frame GC pauses!)
    private static int[] sharedPixelBuf = new int[0];
    private static final int MAX_GRID_CELLS = 4096;
    private static final int[] sharedCellCounts = new int[MAX_GRID_CELLS];
    private static final long[] sharedCellSumX = new long[MAX_GRID_CELLS];
    private static final long[] sharedCellSumY = new long[MAX_GRID_CELLS];
    private static final int[] sharedCellMinX = new int[MAX_GRID_CELLS];
    private static final int[] sharedCellMinY = new int[MAX_GRID_CELLS];
    private static final int[] sharedCellMaxX = new int[MAX_GRID_CELLS];
    private static final int[] sharedCellMaxY = new int[MAX_GRID_CELLS];
    private static final boolean[] sharedVisited = new boolean[MAX_GRID_CELLS];
    private static final int[] sharedQueue = new int[MAX_GRID_CELLS];
    private static final ByteArrayOutputStream sharedBaos = new ByteArrayOutputStream(98304);

    // Background Real-Time Game / Joystick / Decoupled Autopilot State
    private static final AtomicBoolean bgJoyRunning = new AtomicBoolean(false);
    private static volatile Thread bgJoyThread = null;
    private static final AtomicBoolean bgMashRunning = new AtomicBoolean(false);
    private static volatile Thread bgMashThread = null;
    private static final AtomicBoolean autopilotRunning = new AtomicBoolean(false);
    private static volatile Thread autopilotThread = null;
    private static volatile Thread autopilotVisionThread = null;
    private static final AtomicInteger apFrames = new AtomicInteger(0);
    private static final AtomicInteger apAttacks = new AtomicInteger(0);
    private static final AtomicInteger apSkills = new AtomicInteger(0);
    private static final AtomicInteger apTargetsFound = new AtomicInteger(0);
    private static final AtomicInteger apHeals = new AtomicInteger(0);
    private static volatile double apTrackedRad = Double.NaN;
    private static volatile String apStatusMsg = "idle";
    private static volatile long apStartedAt = 0L;

    // Real-Time Background Screen Streamer & Circular RAM Buffer (Live Vision)
    private static final AtomicBoolean streamActive = new AtomicBoolean(false);
    private static volatile Thread streamThread = null;
    private static volatile byte[] streamLatestJpeg = null;
    private static volatile long streamLatestTs = 0L;
    private static volatile long streamFramesCaptured = 0L;
    private static volatile int streamTargetW = 610;
    private static volatile int streamTargetH = 1356;
    private static volatile int streamRealW = 1220;
    private static volatile int streamRealH = 2712;
    private static volatile int streamIntervalMs = 70;
    private static volatile float streamScale = 0.5f;
    private static volatile int streamQuality = 60;

    // Ephemeral File Tracker (path -> timestampMs)
    private static final ConcurrentHashMap<String, Long> ephemeralFiles = new ConcurrentHashMap<>();

    // Built-in O(1) App Alias Table
    private static final Map<String, String[]> APP_ALIASES = new HashMap<>(64);

    // 3x5 Bitmap Font Masks for 0-9
    private static final int[] DIGITS = {
        0b111_101_101_101_111, 0b010_110_010_010_111,
        0b111_001_111_100_111, 0b111_001_111_001_111,
        0b101_101_111_001_001, 0b111_100_111_001_111,
        0b111_100_111_101_111, 0b111_001_001_001_001,
        0b111_101_111_101_111, 0b111_101_111_001_111
    };

    static {
        for (int i = 0; i < MAX_POINTERS; i++) {
            MotionEvent.PointerProperties pp = new MotionEvent.PointerProperties();
            pp.id = i;
            pp.toolType = MotionEvent.TOOL_TYPE_FINGER;
            sharedProps[i] = pp;

            MotionEvent.PointerCoords pc = new MotionEvent.PointerCoords();
            pc.pressure = 1.0f;
            pc.size = 1.0f;
            sharedCoords[i] = pc;
        }

        addAlias("wa", "com.whatsapp", "com.whatsapp/.Main");
        addAlias("whatsapp", "com.whatsapp", "com.whatsapp/.Main");
        addAlias("ig", "com.instagram.android", "com.instagram.android/com.instagram.mainactivity.InstagramMainActivity");
        addAlias("instagram", "com.instagram.android", "com.instagram.android/com.instagram.mainactivity.InstagramMainActivity");
        addAlias("tt", "com.ss.android.ugc.trill", "com.ss.android.ugc.trill/com.ss.android.ugc.aweme.splash.SplashActivity");
        addAlias("tiktok", "com.ss.android.ugc.trill", "com.ss.android.ugc.trill/com.ss.android.ugc.aweme.splash.SplashActivity");
        addAlias("shopee", "com.shopee.id", "com.shopee.id/com.shopee.app.ui.home.HomeActivity_");
        addAlias("snapseed", "com.niksoftware.snapseed", "com.niksoftware.snapseed/com.google.android.apps.snapseed.MainActivity");
        addAlias("lr", "com.adobe.lrmobile", "");
        addAlias("lightroom", "com.adobe.lrmobile", "");
        addAlias("capcut", "com.lemon.lvoverseas", "");
        addAlias("canva", "com.canva.editor", "");
        addAlias("brave", "com.brave.browser", "com.brave.browser/com.google.android.apps.chrome.Main");
        addAlias("browser", "com.brave.browser", "com.brave.browser/com.google.android.apps.chrome.Main");
        addAlias("discord", "com.discord", "");
        addAlias("reddit", "com.reddit.frontpage", "");
        addAlias("pinterest", "com.pinterest", "");
        addAlias("chatgpt", "com.openai.chatgpt", "");
        addAlias("livin", "id.bmri.livin", "");
        addAlias("mandiri", "id.bmri.livin", "");
        addAlias("keep", "com.google.android.keep", "");
        addAlias("notes", "com.google.android.keep", "");
        addAlias("zarchiver", "ru.zdevs.zarchiver", "");
        addAlias("poweramp", "com.maxmpz.audioplayer", "");
        addAlias("music", "com.maxmpz.audioplayer", "");
        addAlias("opencamera", "net.sourceforge.opencamera", "");
        addAlias("camera", "net.sourceforge.opencamera", "");
        addAlias("termux", "com.termux", "com.termux/.app.TermuxActivity");
        addAlias("settings", "com.android.settings", "com.android.settings/.Settings");
        addAlias("pengaturan", "com.android.settings", "com.android.settings/.Settings");
        addAlias("setelan", "com.android.settings", "com.android.settings/.Settings");
        addAlias("yt", "com.google.android.youtube", "");
        addAlias("youtube", "com.google.android.youtube", "");
        addAlias("maps", "com.google.android.apps.maps", "");
        addAlias("photos", "com.google.android.apps.photos", "");
        addAlias("gallery", "com.google.android.apps.photos", "");
        addAlias("galeri", "com.google.android.apps.photos", "");
        addAlias("playstore", "com.android.vending", "");
        addAlias("files", "com.google.android.documentsui", "");
        addAlias("byu", "com.byu.id", "");
        addAlias("smartfren", "com.smartfren", "");
        addAlias("cosmos", "so.cosmos.www", "");
        addAlias("wildrift", "com.riotgames.league.wildrift", "");
        addAlias("wr", "com.riotgames.league.wildrift", "");
        addAlias("lol", "com.riotgames.league.wildrift", "");
        addAlias("ml", "com.mobile.legends", "");
        addAlias("mlbb", "com.mobile.legends", "");
        addAlias("mobilelegends", "com.mobile.legends", "");
        addAlias("cotl", "com.cotl.port", "");
        addAlias("cultofthelamb", "com.cotl.port", "");
        addAlias("zomdroid", "com.zomdroid", "");
        addAlias("zomboid", "com.zomdroid", "");
        addAlias("pz", "com.zomdroid", "");
        addAlias("endoparasitic", "com.endoparastic.vocies61", "");
        addAlias("voices", "com.endoparastic.vocies61", "");
        addAlias("mornye", "com.zarz.mornye", "");
        addAlias("swordigo", "com.touchfoo.swordigo", "");
    }

    private static void addAlias(String k, String pkg, String comp) {
        APP_ALIASES.put(k, new String[]{pkg, comp});
    }

    // =========================================================================
    // EPHEMERAL FILE & MEMORY AUTO-REAPER (Zero-Footprint Guarantee)
    // =========================================================================

    public static void ensureEphemeralDir() {
        try {
            File dir = new File(EPHEMERAL_DIR);
            if (!dir.exists()) dir.mkdirs();
            File nomedia = new File(dir, ".nomedia");
            if (!nomedia.exists()) nomedia.createNewFile();
        } catch (Throwable ignored) {}
    }

    public static void registerEphemeralFile(String path) {
        if (path == null || path.isEmpty()) return;
        ephemeralFiles.put(path, System.currentTimeMillis());
    }

    public static int cleanEphemeralNow(boolean purgeAllNow) {
        int deleted = 0;
        long now = System.currentTimeMillis();

        for (Map.Entry<String, Long> entry : ephemeralFiles.entrySet()) {
            String path = entry.getKey();
            long ts = entry.getValue();
            if (purgeAllNow || (now - ts) >= EPHEMERAL_TTL_MS) {
                ephemeralFiles.remove(path);
                try {
                    File f = new File(path);
                    if (f.exists() && f.delete()) deleted++;
                } catch (Throwable ignored) {}
            }
        }

        // Sweep ephemeral directory & legacy temp locations
        try {
            File dir = new File(EPHEMERAL_DIR);
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (".nomedia".equals(f.getName()) || f.getName().endsWith(".jar")) continue;
                    if (purgeAllNow || (now - f.lastModified()) >= EPHEMERAL_TTL_MS) {
                        if (f.delete()) deleted++;
                    }
                }
            }
        } catch (Throwable ignored) {}

        try {
            File shareDir = new File("/sdcard/Pictures/SatsetShare");
            if (shareDir.exists()) {
                File[] sfiles = shareDir.listFiles();
                if (sfiles != null) {
                    for (File f : sfiles) {
                        if (purgeAllNow || (now - f.lastModified()) >= EPHEMERAL_TTL_MS) {
                            if (f.delete()) deleted++;
                        }
                    }
                }
                if (purgeAllNow) shareDir.delete();
            }
        } catch (Throwable ignored) {}

        try {
            File sd = new File("/sdcard");
            File[] sdFiles = sd.listFiles();
            if (sdFiles != null) {
                for (File f : sdFiles) {
                    String name = f.getName();
                    if (f.isFile() && (name.startsWith("satset_") || name.startsWith(".satset_"))
                        && (name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".tmp"))) {
                        if (purgeAllNow || (now - f.lastModified()) >= EPHEMERAL_TTL_MS) {
                            if (f.delete()) deleted++;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        if (purgeAllNow) {
            synchronized (lock) {
                lastItems.clear();
                cachedTreeEventSeq = -1L;
                try { if (uiAuto != null) uiAuto.clearCache(); } catch (Throwable ignored) {}
            }
            synchronized (visionLock) {
                sharedBaos.reset();
                if (sharedPixelBuf.length > 0 && !autopilotRunning.get()) {
                    sharedPixelBuf = new int[0];
                }
            }
        }
        return deleted;
    }

    // =========================================================================
    // REAL-TIME SCREEN STREAMER (Gemini Live RAM Pipeline)
    // =========================================================================

    private static synchronized void startStreamer(int intervalMs, float scale, int quality) {
        streamIntervalMs = Math.max(30, Math.min(1000, intervalMs));
        streamScale = Math.max(0.1f, Math.min(1.0f, scale));
        streamQuality = Math.max(20, Math.min(95, quality));

        if (streamActive.get() && streamThread != null && streamThread.isAlive()) {
            return;
        }

        streamActive.set(true);
        streamThread = new Thread(() -> {
            ByteArrayOutputStream streamBaos = new ByteArrayOutputStream(65536);
            while (streamActive.get()) {
                long frameStart = SystemClock.uptimeMillis();
                try {
                    ensureConnected();
                    Bitmap raw = (uiAuto != null) ? uiAuto.takeScreenshot() : null;
                    if (raw != null) {
                        int origW = raw.getWidth();
                        int origH = raw.getHeight();
                        streamRealW = origW;
                        streamRealH = origH;
                        cachedScreenW = origW;
                        cachedScreenH = origH;
                        lastScreenDimCheck = SystemClock.uptimeMillis();

                        int tw = Math.max(80, Math.round(origW * streamScale));
                        int th = Math.max(80, Math.round(origH * streamScale));
                        streamTargetW = tw;
                        streamTargetH = th;

                        Bitmap work = raw;
                        if (Math.abs(streamScale - 1.0f) > 0.02f) {
                            work = Bitmap.createScaledBitmap(raw, tw, th, true);
                            raw.recycle();
                        }

                        streamBaos.reset();
                        work.compress(Bitmap.CompressFormat.JPEG, streamQuality, streamBaos);
                        byte[] bytes = streamBaos.toByteArray();
                        work.recycle();

                        streamLatestJpeg = bytes;
                        streamLatestTs = System.currentTimeMillis();
                        streamFramesCaptured++;
                    }
                } catch (Throwable ignored) {}

                long elapsed = SystemClock.uptimeMillis() - frameStart;
                long sleepTime = streamIntervalMs - elapsed;
                if (sleepTime > 0) {
                    try { Thread.sleep(sleepTime); } catch (InterruptedException e) { break; }
                }
            }
            streamBaos.reset();
        }, "satset-streamer");
        streamThread.setDaemon(true);
        streamThread.start();
    }

    private static synchronized void stopStreamer() {
        streamActive.set(false);
        Thread t = streamThread;
        if (t != null) {
            try { t.join(120L); } catch (Throwable ignored) {}
            streamThread = null;
        }
    }

    private static void startEphemeralReaperThread() {
        ensureEphemeralDir();
        Thread reaper = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(4000L);
                    cleanEphemeralNow(false);
                } catch (Throwable ignored) {}
            }
        }, "satset-reaper");
        reaper.setDaemon(true);
        reaper.start();
    }

    public static class UiItem {
        public int index;
        public AccessibilityNodeInfo node;
        public String text;
        public String desc;
        public String childLabel;
        public String resId;
        public String shortId;
        public String cls;
        public String shortCls;
        public String pkg;
        public int left, top, right, bottom;
        public int cx, cy;
        public boolean clickable, longClickable, editable, scrollable, checkable, checked, focused, enabled;

        public String formatLine() {
            StringBuilder sb = new StringBuilder(112);
            sb.append("[").append(index).append("] ");
            sb.append("(").append(cx).append(",").append(cy).append(") ");
            sb.append("[").append(left).append(",").append(top).append("-").append(right).append(",").append(bottom).append("] ");
            sb.append(shortCls);

            if (clickable || editable || scrollable || longClickable || checked || focused) {
                sb.append(" [");
                boolean first = true;
                if (clickable) { sb.append("CLICK"); first = false; }
                if (editable) { if (!first) sb.append("|"); sb.append("EDIT"); first = false; }
                if (scrollable) { if (!first) sb.append("|"); sb.append("SCROLL"); first = false; }
                if (longClickable && !clickable) { if (!first) sb.append("|"); sb.append("LONG"); first = false; }
                if (checked) { if (!first) sb.append("|"); sb.append("CHECKED"); first = false; }
                if (focused) { if (!first) sb.append("|"); sb.append("FOCUSED"); }
                sb.append("]");
            }

            if (!text.isEmpty()) {
                sb.append(" text=\"").append(cleanStr(text, 85)).append("\"");
            }
            if (!desc.isEmpty()) {
                sb.append(" desc=\"").append(cleanStr(desc, 65)).append("\"");
            }
            if (text.isEmpty() && desc.isEmpty() && !childLabel.isEmpty()) {
                sb.append(" label=\"").append(cleanStr(childLabel, 100)).append("\"");
            }
            if (!shortId.isEmpty()) {
                sb.append(" id=\"").append(shortId).append("\"");
            }
            return sb.toString();
        }
    }

    private static String cleanStr(String s, int maxLen) {
        if (s == null || s.isEmpty()) return "";
        int len = s.length();
        StringBuilder sb = new StringBuilder(Math.min(len, maxLen));
        boolean lastSpace = false;
        for (int i = 0; i < len; i++) {
            char ch = s.charAt(i);
            if (ch == '\n' || ch == '\r' || ch == '\t' || ch == ' ') {
                if (!lastSpace && sb.length() > 0) {
                    sb.append(' ');
                    lastSpace = true;
                }
            } else {
                sb.append(ch == '"' ? '\'' : ch);
                lastSpace = false;
            }
            if (sb.length() >= maxLen) {
                sb.setLength(maxLen - 3);
                sb.append("...");
                break;
            }
        }
        return sb.toString().trim();
    }

    private static String safeStr(CharSequence cs) {
        return cs == null ? "" : cs.toString().trim();
    }

    private static void ensureConnected() throws Exception {
        synchronized (lock) {
            if (connected && uiAuto != null) return;
            wrapper = new UiAutomationShellWrapper();
            wrapper.connect();
            wrapper.setCompressedLayoutHierarchy(false);
            uiAuto = wrapper.getUiAutomation();

            AccessibilityServiceInfo info = uiAuto.getServiceInfo();
            if (info != null) {
                info.flags |= 64 | 16 | 2;
                uiAuto.setServiceInfo(info);
            }

            // Register Sub-Millisecond AccessibilityEvent Reactor
            try {
                uiAuto.setOnAccessibilityEventListener(event -> {
                    if (event == null) return;
                    long now = SystemClock.uptimeMillis();
                    lastUiEventTime = now;
                    int type = event.getEventType();
                    if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                        || type == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
                        lastWindowStateChangeTime = now;
                        CharSequence pkg = event.getPackageName();
                        if (pkg != null && pkg.length() > 0) {
                            String p = pkg.toString();
                            if (!p.equals("com.android.systemui")) {
                                activeEventPackage = p;
                            }
                        }
                    }
                    uiEventSeq.incrementAndGet();
                    synchronized (uiEventMonitor) {
                        uiEventMonitor.notifyAll();
                    }
                });
            } catch (Throwable ignored) {}

            ShellUiAutomatorBridge bridge = new ShellUiAutomatorBridge(uiAuto);
            Method getIc = UiAutomatorBridge.class.getDeclaredMethod("getInteractionController");
            getIc.setAccessible(true);
            ic = getIc.invoke(bridge);
            Class<?> icCls = ic.getClass();
            mClickNoSync = icCls.getMethod("clickNoSync", int.class, int.class);
            mLongTapNoSync = icCls.getMethod("longTapNoSync", int.class, int.class);
            mSwipe = icCls.getMethod("swipe", int.class, int.class, int.class, int.class, int.class);
            mSendKey = icCls.getMethod("sendKey", int.class, int.class);
            mWakeDevice = icCls.getMethod("wakeDevice");
            mSleepDevice = icCls.getMethod("sleepDevice");

            connected = true;
        }
    }

    private static void disconnectInternal() {
        stopAllGameThreads();
        cleanEphemeralNow(true);
        synchronized (lock) {
            if (!connected || wrapper == null) return;
            try { wrapper.disconnect(); } catch (Throwable ignored) {}
            wrapper = null;
            uiAuto = null;
            ic = null;
            connected = false;
        }
    }

    /**
     * Event-driven wait: wakes up within <0.2ms of ANY AccessibilityEvent firing, or after maxWaitMs.
     */
    private static boolean waitForNextUiEvent(long maxWaitMs) {
        if (maxWaitMs <= 0) return false;
        long seq0 = uiEventSeq.get();
        long deadline = SystemClock.uptimeMillis() + maxWaitMs;
        synchronized (uiEventMonitor) {
            while (uiEventSeq.get() == seq0) {
                long rem = deadline - SystemClock.uptimeMillis();
                if (rem <= 0) return false;
                try {
                    uiEventMonitor.wait(Math.min(12L, rem));
                } catch (InterruptedException e) {
                    return false;
                }
            }
            return true;
        }
    }

    private static void waitForWindowTransition(String expectedPkg, long maxWaitMs) {
        long start = SystemClock.uptimeMillis();
        long win0 = lastWindowStateChangeTime;
        synchronized (uiEventMonitor) {
            while (true) {
                long now = SystemClock.uptimeMillis();
                long el = now - start;
                if (el >= maxWaitMs) break;
                if (expectedPkg != null && !expectedPkg.isEmpty() && expectedPkg.equals(activeEventPackage)) {
                    break;
                }
                if (lastWindowStateChangeTime > win0 && el >= 18L) {
                    break;
                }
                try {
                    uiEventMonitor.wait(Math.min(15L, maxWaitMs - el));
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }

    // =========================================================================
    // ZERO-ALLOCATION MULTI-POINTER REAL-TIME TOUCH SYNTHESIZER
    // =========================================================================

    private static int countActivePointers() {
        int c = 0;
        for (int i = 0; i < MAX_POINTERS; i++) {
            if (ptrActive[i]) c++;
        }
        return c;
    }

    private static boolean injectMultiTouchLocked(int actionSlot, int baseAction) {
        if (uiAuto == null) return false;
        int count = countActivePointers();
        if (count == 0) return false;

        int actionIndex = 0;
        int idx = 0;
        for (int s = 0; s < MAX_POINTERS; s++) {
            if (!ptrActive[s]) continue;
            if (s == actionSlot) actionIndex = idx;
            sharedProps[idx].id = s;
            sharedProps[idx].toolType = MotionEvent.TOOL_TYPE_FINGER;
            sharedCoords[idx].x = ptrX[s];
            sharedCoords[idx].y = ptrY[s];
            sharedCoords[idx].pressure = 1.0f;
            sharedCoords[idx].size = 1.0f;
            idx++;
        }

        long now = SystemClock.uptimeMillis();
        if (multiDownTime == 0L || (count == 1 && baseAction == MotionEvent.ACTION_DOWN)) {
            multiDownTime = now;
        }

        int finalAction;
        if (baseAction == MotionEvent.ACTION_DOWN) {
            finalAction = (count == 1)
                ? MotionEvent.ACTION_DOWN
                : (MotionEvent.ACTION_POINTER_DOWN | (actionIndex << MotionEvent.ACTION_POINTER_INDEX_SHIFT));
        } else if (baseAction == MotionEvent.ACTION_UP) {
            finalAction = (count == 1)
                ? MotionEvent.ACTION_UP
                : (MotionEvent.ACTION_POINTER_UP | (actionIndex << MotionEvent.ACTION_POINTER_INDEX_SHIFT));
        } else {
            finalAction = MotionEvent.ACTION_MOVE;
        }

        MotionEvent ev = MotionEvent.obtain(
            multiDownTime, now, finalAction, count, sharedProps, sharedCoords,
            0, 0, 1.0f, 1.0f, 0, 0, SOURCE_TOUCHSCREEN, 0
        );
        if (ev == null) return false;
        boolean ok = false;
        try {
            ok = uiAuto.injectInputEvent(ev, false);
        } catch (Throwable ignored) {
        } finally {
            ev.recycle();
        }
        return ok;
    }

    public static boolean touchDownSlot(int slot, float x, float y) {
        if (slot < 0 || slot >= MAX_POINTERS) return false;
        synchronized (touchLock) {
            ptrX[slot] = x;
            ptrY[slot] = y;
            if (ptrActive[slot]) {
                return injectMultiTouchLocked(slot, MotionEvent.ACTION_MOVE);
            }
            ptrActive[slot] = true;
            return injectMultiTouchLocked(slot, MotionEvent.ACTION_DOWN);
        }
    }

    public static boolean touchMoveSlot(int slot, float x, float y) {
        if (slot < 0 || slot >= MAX_POINTERS) return false;
        synchronized (touchLock) {
            if (!ptrActive[slot]) {
                ptrX[slot] = x;
                ptrY[slot] = y;
                ptrActive[slot] = true;
                return injectMultiTouchLocked(slot, MotionEvent.ACTION_DOWN);
            }
            ptrX[slot] = x;
            ptrY[slot] = y;
            return injectMultiTouchLocked(slot, MotionEvent.ACTION_MOVE);
        }
    }

    public static boolean touchUpSlot(int slot) {
        if (slot < 0 || slot >= MAX_POINTERS) return false;
        synchronized (touchLock) {
            if (!ptrActive[slot]) return true;
            boolean ok = injectMultiTouchLocked(slot, MotionEvent.ACTION_UP);
            ptrActive[slot] = false;
            if (countActivePointers() == 0) {
                multiDownTime = 0L;
            }
            return ok;
        }
    }

    public static void releaseAllPointers() {
        synchronized (touchLock) {
            for (int s = MAX_POINTERS - 1; s >= 0; s--) {
                if (ptrActive[s]) {
                    try { injectMultiTouchLocked(s, MotionEvent.ACTION_UP); } catch (Throwable ignored) {}
                    ptrActive[s] = false;
                }
            }
            multiDownTime = 0L;
        }
    }

    private static int acquireFreeActionSlot() {
        synchronized (touchLock) {
            for (int s = 1; s < MAX_POINTERS; s++) {
                if (!ptrActive[s]) return s;
            }
            return ptrActive[0] ? 1 : 0;
        }
    }

    /**
     * Ultra-fast direct MotionEvent tap (~5ms total instead of InteractionController's 60-100ms!).
     */
    private static boolean fastTapPoint(float x, float y, long holdMs) {
        int slot = acquireFreeActionSlot();
        boolean ok = touchDownSlot(slot, x, y);
        if (holdMs > 0) {
            try { Thread.sleep(holdMs); } catch (InterruptedException ignored) {}
        }
        touchUpSlot(slot);
        return ok;
    }

    /**
     * Direct 120Hz smooth MotionEvent swipe (~45-65ms instead of InteractionController's 200ms).
     */
    private static boolean fastSwipe(float x1, float y1, float x2, float y2, int steps, long stepDelayMs) {
        int slot = acquireFreeActionSlot();
        int n = Math.max(2, steps);
        boolean ok = touchDownSlot(slot, x1, y1);
        try {
            for (int i = 1; i <= n; i++) {
                if (stepDelayMs > 0) {
                    Thread.sleep(stepDelayMs);
                }
                float f = (float) i / (float) n;
                touchMoveSlot(slot, x1 + (x2 - x1) * f, y1 + (y2 - y1) * f);
            }
        } catch (InterruptedException ignored) {
        } finally {
            touchUpSlot(slot);
        }
        return ok;
    }

    private static void stopAllGameThreads() {
        stopStreamer();
        bgJoyRunning.set(false);
        bgMashRunning.set(false);
        autopilotRunning.set(false);
        Thread t1 = bgJoyThread;
        Thread t2 = bgMashThread;
        Thread t3 = autopilotThread;
        Thread t4 = autopilotVisionThread;
        try { if (t1 != null) t1.join(80L); } catch (Throwable ignored) {}
        try { if (t2 != null) t2.join(80L); } catch (Throwable ignored) {}
        try { if (t3 != null) t3.join(100L); } catch (Throwable ignored) {}
        try { if (t4 != null) t4.join(80L); } catch (Throwable ignored) {}
        releaseAllPointers();
    }

    private static double parseDirectionRadians(String dir) {
        if (dir == null || dir.isEmpty()) return Math.toRadians(270);
        String d = dir.trim().toLowerCase(Locale.ROOT);
        switch (d) {
            case "r": case "right": case "e": case "east": case "d":
                return 0.0;
            case "dr": case "down-right": case "se": case "sd":
                return Math.toRadians(45);
            case "dn": case "down": case "s": case "south":
                return Math.toRadians(90);
            case "dl": case "down-left": case "sw": case "sa":
                return Math.toRadians(135);
            case "l": case "left": case "w_dir": case "west": case "a":
                return Math.toRadians(180);
            case "ul": case "up-left": case "nw": case "wa":
                return Math.toRadians(225);
            case "u": case "up": case "n": case "north": case "w": case "forward":
                return Math.toRadians(270);
            case "ur": case "up-right": case "ne": case "wd":
                return Math.toRadians(315);
            default:
                try {
                    double deg = Double.parseDouble(d.replace("deg", ""));
                    return Math.toRadians(deg);
                } catch (NumberFormatException e) {
                    return Math.toRadians(270);
                }
        }
    }

    private static int[] getScreenDimensions() {
        long now = SystemClock.uptimeMillis();
        if (now - lastScreenDimCheck < 400L && cachedScreenW > 400 && cachedScreenH > 400) {
            return new int[]{cachedScreenW, cachedScreenH};
        }
        try {
            List<AccessibilityWindowInfo> windows = uiAuto.getWindows();
            if (windows != null) {
                int maxW = 0, maxH = 0;
                Rect r = new Rect();
                for (AccessibilityWindowInfo w : windows) {
                    w.getBoundsInScreen(r);
                    if (r.right > maxW) maxW = r.right;
                    if (r.bottom > maxH) maxH = r.bottom;
                }
                if (maxW > 400 && maxH > 400) {
                    cachedScreenW = maxW;
                    cachedScreenH = maxH;
                    lastScreenDimCheck = now;
                    return new int[]{maxW, maxH};
                }
            }
        } catch (Throwable ignored) {}
        return new int[]{cachedScreenW, cachedScreenH};
    }

    private static void runJoystickLoop(String modeOrDir, long durationMs, float cx, float cy, float radius, AtomicBoolean runFlag) {
        String m = modeOrDir.trim().toLowerCase(Locale.ROOT);
        boolean isCircle = m.equals("circle") || m.equals("spin") || m.equals("orbit");
        boolean isStrafe = m.equals("strafe") || m.equals("kite") || m.equals("zigzag") || m.equals("dodge");
        boolean isPatrol = m.equals("patrol") || m.equals("pace");
        double baseRad = (!isCircle && !isStrafe && !isPatrol) ? parseDirectionRadians(m) : 0.0;

        long start = SystemClock.uptimeMillis();
        touchDownSlot(0, cx, cy);
        try {
            int step = 0;
            while (runFlag.get()) {
                long elapsed = SystemClock.uptimeMillis() - start;
                if (elapsed >= durationMs) break;

                double rad = baseRad;
                if (isCircle) {
                    rad = (elapsed * 0.0045) % (Math.PI * 2.0);
                } else if (isStrafe) {
                    int phase = (int) ((elapsed / 300L) % 4);
                    rad = (phase == 0) ? Math.toRadians(210)
                        : (phase == 1) ? Math.toRadians(330)
                        : (phase == 2) ? Math.toRadians(150)
                        : Math.toRadians(30);
                } else if (isPatrol) {
                    int phase = (int) ((elapsed / 450L) % 2);
                    rad = (phase == 0) ? 0.0 : Math.PI;
                }

                float ramp = Math.min(1.0f, (step + 1) / 2.0f);
                float tx = cx + (float) (Math.cos(rad) * radius * ramp);
                float ty = cy + (float) (Math.sin(rad) * radius * ramp);
                touchMoveSlot(0, tx, ty);

                step++;
                Thread.sleep(12L); // ~83Hz smooth updates
            }
        } catch (InterruptedException ignored) {
        } finally {
            touchUpSlot(0);
            runFlag.set(false);
        }
    }

    // =========================================================================
    // ZERO-ALLOCATION PIXEL & COLOR SCANNER (In-RAM Bytecode Vision)
    // =========================================================================

    public static class ColorCluster {
        public int cx, cy;
        public int left, top, right, bottom;
        public int count;
    }

    private static int parseHexColor(String hex) {
        String h = hex.trim();
        if (h.startsWith("#")) h = h.substring(1);
        if (h.startsWith("0x") || h.startsWith("0X")) h = h.substring(2);
        if (h.length() == 8) h = h.substring(2);
        return Integer.parseInt(h, 16) & 0xFFFFFF;
    }

    private static boolean colorMatches(int pixel, int targetRgb, int tol) {
        int r = (pixel >> 16) & 0xFF;
        int g = (pixel >> 8) & 0xFF;
        int b = pixel & 0xFF;
        int tr = (targetRgb >> 16) & 0xFF;
        int tg = (targetRgb >> 8) & 0xFF;
        int tb = targetRgb & 0xFF;
        return Math.abs(r - tr) <= tol && Math.abs(g - tg) <= tol && Math.abs(b - tb) <= tol;
    }

    private static List<ColorCluster> scanBitmapForColor(Bitmap bmp, int targetRgb, int tol, int rx1, int ry1, int rx2, int ry2) {
        synchronized (visionLock) {
            int w = bmp.getWidth();
            int h = bmp.getHeight();
            cachedScreenW = w;
            cachedScreenH = h;
            lastScreenDimCheck = SystemClock.uptimeMillis();

            int x1 = Math.max(0, Math.min(w - 1, rx1));
            int y1 = Math.max(0, Math.min(h - 1, ry1));
            int x2 = (rx2 <= x1) ? w : Math.max(x1 + 1, Math.min(w, rx2));
            int y2 = (ry2 <= y1) ? h : Math.max(y1 + 1, Math.min(h, ry2));
            int rw = x2 - x1;
            int rh = y2 - y1;
            int needed = rw * rh;
            if (sharedPixelBuf.length < needed) {
                sharedPixelBuf = new int[needed];
            }
            bmp.getPixels(sharedPixelBuf, 0, rw, x1, y1, rw, rh);

            final int cellSize = 44;
            int cols = (rw + cellSize - 1) / cellSize;
            int rows = (rh + cellSize - 1) / cellSize;
            int numCells = Math.min(MAX_GRID_CELLS, cols * rows);

            Arrays.fill(sharedCellCounts, 0, numCells, 0);
            Arrays.fill(sharedCellSumX, 0, numCells, 0L);
            Arrays.fill(sharedCellSumY, 0, numCells, 0L);
            Arrays.fill(sharedCellMinX, 0, numCells, Integer.MAX_VALUE);
            Arrays.fill(sharedCellMinY, 0, numCells, Integer.MAX_VALUE);
            Arrays.fill(sharedCellMaxX, 0, numCells, 0);
            Arrays.fill(sharedCellMaxY, 0, numCells, 0);
            Arrays.fill(sharedVisited, 0, numCells, false);

            int step = 2;
            for (int ly = 0; ly < rh; ly += step) {
                int rowOff = ly * rw;
                int cyCell = ly / cellSize;
                for (int lx = 0; lx < rw; lx += step) {
                    int px = sharedPixelBuf[rowOff + lx];
                    if (colorMatches(px, targetRgb, tol)) {
                        int cIdx = cyCell * cols + (lx / cellSize);
                        if (cIdx >= numCells) continue;
                        int ax = x1 + lx;
                        int ay = y1 + ly;
                        sharedCellCounts[cIdx]++;
                        sharedCellSumX[cIdx] += ax;
                        sharedCellSumY[cIdx] += ay;
                        if (ax < sharedCellMinX[cIdx]) sharedCellMinX[cIdx] = ax;
                        if (ay < sharedCellMinY[cIdx]) sharedCellMinY[cIdx] = ay;
                        if (ax > sharedCellMaxX[cIdx]) sharedCellMaxX[cIdx] = ax;
                        if (ay > sharedCellMaxY[cIdx]) sharedCellMaxY[cIdx] = ay;
                    }
                }
            }

            List<ColorCluster> clusters = new ArrayList<>(16);
            for (int i = 0; i < numCells; i++) {
                if (sharedCellCounts[i] == 0 || sharedVisited[i]) continue;
                sharedVisited[i] = true;
                int qHead = 0, qTail = 0;
                sharedQueue[qTail++] = i;

                long totalCount = 0, sumX = 0, sumY = 0;
                int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
                int maxX = 0, maxY = 0;

                while (qHead < qTail) {
                    int cur = sharedQueue[qHead++];
                    int c = sharedCellCounts[cur];
                    totalCount += c;
                    sumX += sharedCellSumX[cur];
                    sumY += sharedCellSumY[cur];
                    if (sharedCellMinX[cur] < minX) minX = sharedCellMinX[cur];
                    if (sharedCellMinY[cur] < minY) minY = sharedCellMinY[cur];
                    if (sharedCellMaxX[cur] > maxX) maxX = sharedCellMaxX[cur];
                    if (sharedCellMaxY[cur] > maxY) maxY = sharedCellMaxY[cur];

                    int cr = cur / cols;
                    int cc = cur % cols;
                    for (int dr = -1; dr <= 1; dr++) {
                        for (int dc = -1; dc <= 1; dc++) {
                            int nr = cr + dr, nc = cc + dc;
                            if (nr >= 0 && nr < rows && nc >= 0 && nc < cols) {
                                int nIdx = nr * cols + nc;
                                if (nIdx < numCells && !sharedVisited[nIdx] && sharedCellCounts[nIdx] > 0) {
                                    sharedVisited[nIdx] = true;
                                    sharedQueue[qTail++] = nIdx;
                                }
                            }
                        }
                    }
                }

                if (totalCount >= 2) {
                    ColorCluster cl = new ColorCluster();
                    cl.count = (int) totalCount;
                    cl.cx = (int) (sumX / totalCount);
                    cl.cy = (int) (sumY / totalCount);
                    cl.left = minX;
                    cl.top = minY;
                    cl.right = maxX;
                    cl.bottom = maxY;
                    clusters.add(cl);
                }
            }

            clusters.sort((a, b) -> Integer.compare(b.count, a.count));
            return clusters;
        }
    }

    private static void drawNumberTag(Canvas canvas, String numStr, float bx, float by, float px, Paint bgPaint, Paint fgPaint, int maxW) {
        float pad = px * 1.5f;
        float charAdv = px * 4f;
        float tagW = (numStr.length() * charAdv) - px + (pad * 2f);
        float tagH = (px * 5f) + (pad * 2f);
        float x0 = Math.max(0f, Math.min(bx, maxW - tagW));
        float y0 = Math.max(0f, by);
        canvas.drawRect(x0, y0, x0 + tagW, y0 + tagH, bgPaint);

        for (int ci = 0; ci < numStr.length(); ci++) {
            char ch = numStr.charAt(ci);
            if (ch == ',') {
                float cx0 = x0 + pad + (ci * charAdv) + px;
                float cy0 = y0 + pad + (px * 3.5f);
                canvas.drawRect(cx0, cy0, cx0 + px, cy0 + px * 1.5f, fgPaint);
                continue;
            }
            int d = ch - '0';
            if (d < 0 || d > 9) continue;
            int mask = DIGITS[d];
            float cx0 = x0 + pad + (ci * charAdv);
            float cy0 = y0 + pad;
            for (int row = 0; row < 5; row++) {
                for (int col = 0; col < 3; col++) {
                    int bit = 14 - (row * 3 + col);
                    if (((mask >> bit) & 1) != 0) {
                        float rx = cx0 + col * px;
                        float ry = cy0 + row * px;
                        canvas.drawRect(rx, ry, rx + px, ry + px, fgPaint);
                    }
                }
            }
        }
    }

    // =========================================================================
    // UI TREE WALKER (Single-pass O(N) with PREFETCH_HYBRID + 0.01ms Event Cache)
    // =========================================================================

    private static String walkNodeFast(AccessibilityNodeInfo node, List<UiItem> out, Set<String> seen) {
        if (node == null) return "";
        if (!node.isVisibleToUser()) return "";

        node.getBoundsInScreen(tmpRect);
        int l = tmpRect.left;
        int t = tmpRect.top;
        int r = tmpRect.right;
        int b = tmpRect.bottom;
        int w = r - l;
        int h = b - t;

        if (w <= 4 || h <= 4 || r <= 0 || b <= 0 || l >= 3200 || t >= 3200) {
            return "";
        }

        String text = safeStr(node.getText());
        String desc = safeStr(node.getContentDescription());
        String resId = safeStr(node.getViewIdResourceName());
        String cls = safeStr(node.getClassName());
        String pkg = safeStr(node.getPackageName());

        boolean clickable = node.isClickable();
        boolean longClickable = node.isLongClickable();
        boolean scrollable = node.isScrollable();
        boolean editable = node.isEditable() || cls.endsWith("EditText");
        boolean checkable = node.isCheckable();
        boolean checked = node.isChecked();
        boolean focused = node.isFocused();
        boolean enabled = node.isEnabled();

        int childCount = node.getChildCount();

        UiItem item = new UiItem();
        out.add(item);

        StringBuilder childSummary = new StringBuilder();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                String cText = walkNodeFast(child, out, seen);
                if (!cText.isEmpty() && childSummary.length() < 120) {
                    if (childSummary.length() > 0) childSummary.append(" | ");
                    childSummary.append(cText);
                }
            }
        }

        String childLabel = childSummary.toString();
        boolean hasContent = !text.isEmpty() || !desc.isEmpty() || ((clickable || editable || checkable) && !childLabel.isEmpty());
        boolean isInteractive = clickable || editable || scrollable || checkable || (longClickable && (!resId.isEmpty() || hasContent));
        boolean isLeafWithId = (childCount == 0 && !resId.isEmpty() && w < 1200 && h < 1000);
        boolean isFullscreenWrapper = ((w >= 1180 && h >= 2200) || (w >= 2200 && h >= 1180)) && !scrollable && !editable && text.isEmpty() && desc.isEmpty();

        String sig = l + "," + t + "," + r + "," + b + ":" + text + ":" + desc + ":" + clickable + ":" + editable;
        if (!isFullscreenWrapper && (hasContent || isInteractive || isLeafWithId) && !seen.contains(sig)) {
            seen.add(sig);
            String shortCls = cls;
            int dot = cls.lastIndexOf('.');
            if (dot >= 0) shortCls = cls.substring(dot + 1);

            String shortId = resId;
            int slash = resId.indexOf('/');
            if (slash >= 0) shortId = resId.substring(slash + 1);

            item.node = node;
            item.text = text;
            item.desc = desc;
            item.childLabel = (clickable || editable || checkable) ? childLabel : "";
            item.resId = resId;
            item.shortId = shortId;
            item.cls = cls;
            item.shortCls = shortCls;
            item.pkg = pkg;
            item.left = l;
            item.top = t;
            item.right = r;
            item.bottom = b;
            item.cx = (l + r) >> 1;
            item.cy = (t + b) >> 1;
            item.clickable = clickable;
            item.longClickable = longClickable;
            item.editable = editable;
            item.scrollable = scrollable;
            item.checkable = checkable;
            item.checked = checked;
            item.focused = focused;
            item.enabled = enabled;

            if (!pkg.isEmpty() && !pkg.equals("com.android.systemui")) {
                lastPackage = pkg;
            }
        } else {
            item.node = null;
        }

        if (!text.isEmpty()) return text;
        if (!desc.isEmpty()) return desc;
        return childLabel;
    }

    private static List<UiItem> refreshTree(boolean includeAllWindows, boolean forceClearCache) throws Exception {
        ensureConnected();
        synchronized (lock) {
            long now = SystemClock.uptimeMillis();
            long curEventSeq = uiEventSeq.get();

            // 0.01ms Smart Event-Coherent Tree Cache Hit!
            if (!forceClearCache && includeAllWindows == cachedIncludeAll && !lastItems.isEmpty()) {
                long age = now - cachedTreeTimestamp;
                if ((curEventSeq == cachedTreeEventSeq && age < 220L) || age < 6L) {
                    return lastItems;
                }
            }

            if (forceClearCache || lastItems.isEmpty()) {
                try { uiAuto.clearCache(); } catch (Throwable ignored) {}
            }

            List<UiItem> raw = new ArrayList<>(112);
            Set<String> seen = new HashSet<>(112);
            lastPackage = "";

            // Fast-path: Single Binder IPC call with PREFETCH_HYBRID on the active window (4-10ms!)
            if (!includeAllWindows) {
                AccessibilityNodeInfo root = null;
                try {
                    root = uiAuto.getRootInActiveWindow(PREFETCH_HYBRID);
                } catch (Throwable t1) {
                    try { root = uiAuto.getRootInActiveWindow(); } catch (Throwable ignored) {}
                }
                if (root != null) {
                    walkNodeFast(root, raw, seen);
                }
            }

            // Multi-window walk only when --all is requested or active window root was empty (e.g. dialogs/overlays)
            if (includeAllWindows || raw.isEmpty()) {
                List<AccessibilityWindowInfo> windows = null;
                try {
                    windows = uiAuto.getWindows();
                } catch (Throwable ignored) {}

                if (windows != null && !windows.isEmpty()) {
                    int maxW = 0, maxH = 0;
                    List<AccessibilityWindowInfo> sorted = new ArrayList<>(windows);
                    sorted.sort((a, b) -> {
                        boolean actA = a.isActive();
                        boolean actB = b.isActive();
                        if (actA != actB) return actA ? -1 : 1;
                        return Integer.compare(b.getLayer(), a.getLayer());
                    });

                    for (AccessibilityWindowInfo w : sorted) {
                        int type = w.getType();
                        w.getBoundsInScreen(tmpRect);
                        if (tmpRect.right > maxW) maxW = tmpRect.right;
                        if (tmpRect.bottom > maxH) maxH = tmpRect.bottom;
                        if (!includeAllWindows) {
                            if (type == 2) continue; // skip soft keyboard
                            if (type == 3 && tmpRect.bottom <= 150) continue; // skip top status bar
                        }
                        AccessibilityNodeInfo wRoot = w.getRoot();
                        if (wRoot != null) {
                            walkNodeFast(wRoot, raw, seen);
                        }
                    }
                    if (maxW > 400 && maxH > 400) {
                        cachedScreenW = maxW;
                        cachedScreenH = maxH;
                        lastScreenDimCheck = now;
                    }
                }
            }

            List<UiItem> items = new ArrayList<>(raw.size());
            for (UiItem it : raw) {
                if (it.node != null) {
                    it.index = items.size() + 1;
                    items.add(it);
                }
            }
            if (lastPackage.isEmpty() && !activeEventPackage.isEmpty()) {
                lastPackage = activeEventPackage;
            }
            lastItems = items;
            cachedTreeEventSeq = curEventSeq;
            cachedTreeTimestamp = SystemClock.uptimeMillis();
            cachedIncludeAll = includeAllWindows;
            return items;
        }
    }

    private static int scoreMatch(UiItem item, String query) {
        if (query == null || query.isEmpty()) return 0;
        String q = query.trim();
        String ql = q.toLowerCase(Locale.ROOT);

        if (ql.startsWith("id:")) {
            String sub = ql.substring(3).trim();
            if (item.shortId.equalsIgnoreCase(sub) || item.resId.equalsIgnoreCase(sub)) return 100 + (item.clickable ? 15 : 0);
            if (item.shortId.toLowerCase(Locale.ROOT).contains(sub) || item.resId.toLowerCase(Locale.ROOT).contains(sub)) {
                return 80 + (item.clickable ? 15 : 0);
            }
            return 0;
        }
        if (ql.startsWith("desc:")) {
            String sub = ql.substring(5).trim();
            if (item.desc.equalsIgnoreCase(sub)) return 100 + (item.clickable ? 15 : 0);
            if (item.desc.toLowerCase(Locale.ROOT).contains(sub)) return 85 + (item.clickable ? 15 : 0);
            return 0;
        }
        if (ql.startsWith("text:")) {
            String sub = ql.substring(5).trim();
            if (item.text.equalsIgnoreCase(sub)) return 100 + (item.clickable ? 15 : 0);
            if (item.text.toLowerCase(Locale.ROOT).contains(sub)) return 85 + (item.clickable ? 15 : 0);
            if (item.childLabel.toLowerCase(Locale.ROOT).contains(sub)) return 75 + (item.clickable ? 15 : 0);
            return 0;
        }
        if (ql.startsWith("class:")) {
            String sub = ql.substring(6).trim();
            if (item.shortCls.equalsIgnoreCase(sub) || item.cls.toLowerCase(Locale.ROOT).contains(sub)) {
                return 90 + (item.editable ? 15 : 0) + (item.clickable ? 10 : 0);
            }
            return 0;
        }
        if (ql.startsWith("exact:")) {
            String sub = ql.substring(6).trim();
            if (item.text.equalsIgnoreCase(sub) || item.desc.equalsIgnoreCase(sub) || item.shortId.equalsIgnoreCase(sub)) {
                return 100 + (item.clickable ? 15 : 0);
            }
            return 0;
        }

        String tl = item.text.toLowerCase(Locale.ROOT);
        String dl = item.desc.toLowerCase(Locale.ROOT);
        String cl = item.childLabel.toLowerCase(Locale.ROOT);
        String il = item.shortId.toLowerCase(Locale.ROOT);

        int bonus = (item.clickable || item.editable) ? 12 : 0;
        if (tl.equals(ql) || dl.equals(ql)) return 100 + bonus;
        if (il.equals(ql)) return 92 + bonus;
        if (tl.startsWith(ql) || dl.startsWith(ql)) return 86 + bonus;
        if (tl.contains(ql) || dl.contains(ql)) return 78 + bonus;
        if (cl.equals(ql)) return 75 + bonus;
        if (cl.contains(ql)) return 65 + bonus;
        if (il.contains(ql)) return 60 + bonus;

        return 0;
    }

    private static UiItem findBestItem(List<UiItem> items, String target) {
        if (target == null || target.isEmpty()) return null;
        String t = target.trim();
        if (t.startsWith("#")) {
            try {
                int idx = Integer.parseInt(t.substring(1));
                for (UiItem item : items) {
                    if (item.index == idx) return item;
                }
            } catch (NumberFormatException ignored) {}
            return null;
        }
        UiItem best = null;
        int bestScore = 0;
        for (UiItem item : items) {
            int s = scoreMatch(item, t);
            if (s > bestScore) {
                bestScore = s;
                best = item;
            }
        }
        return best;
    }

    private static UiItem findEditableItem(List<UiItem> items) {
        for (UiItem item : items) {
            if (item.editable && item.focused) return item;
        }
        for (UiItem item : items) {
            if (item.editable) return item;
        }
        return null;
    }

    private static void execCmdAsync(String[] cmd) {
        try {
            Runtime.getRuntime().exec(cmd);
        } catch (Throwable ignored) {}
    }

    private static String execCmdOutput(String[] cmd) {
        try {
            Process p = Runtime.getRuntime().exec(cmd);
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            p.waitFor();
            return sb.toString().trim();
        } catch (Throwable t) {
            return "";
        }
    }

    private static String fastOpenTarget(String target) {
        long t0 = System.currentTimeMillis();
        String raw = target == null ? "" : target.trim();
        if (raw.isEmpty()) return "ERR\tEmpty open target";
        String key = raw.toLowerCase(Locale.ROOT);

        if (key.equals("termux") || key.equals("com.termux")) {
            return returnToTermuxFast(t0);
        }

        if (raw.contains("://") || key.startsWith("http") || key.startsWith("mailto:") || key.startsWith("tel:")
            || key.startsWith("geo:") || key.startsWith("snssdk") || key.startsWith("shopeeid:")) {
            execCmdAsync(new String[]{"/system/bin/cmd", "activity", "start", "-a", "android.intent.action.VIEW", "-d", raw});
            waitForWindowTransition("", 90L);
            return "OK\tOPEN_URI (" + raw + ") in " + (System.currentTimeMillis() - t0) + "ms";
        }

        String pkg = raw;
        String comp = "";
        if (APP_ALIASES.containsKey(key)) {
            String[] pair = APP_ALIASES.get(key);
            pkg = pair[0];
            comp = pair[1];
        } else if (raw.contains("/")) {
            comp = raw;
            pkg = raw.substring(0, raw.indexOf('/'));
        }

        if (!comp.isEmpty()) {
            execCmdAsync(new String[]{
                "/system/bin/cmd", "activity", "start",
                "-a", "android.intent.action.MAIN",
                "-c", "android.intent.category.LAUNCHER",
                "--activity-single-top",
                "-n", comp
            });
            waitForWindowTransition(pkg, 90L);
            return "OK\tOPEN (" + comp + ") in " + (System.currentTimeMillis() - t0) + "ms";
        }

        String resolved = execCmdOutput(new String[]{"/system/bin/cmd", "package", "resolve-activity", "--brief", pkg});
        String[] lines = resolved.split("\n");
        String lastLine = lines.length > 0 ? lines[lines.length - 1].trim() : "";
        if (lastLine.contains("/") && !lastLine.startsWith("No ")) {
            APP_ALIASES.put(key, new String[]{pkg, lastLine});
            execCmdAsync(new String[]{
                "/system/bin/cmd", "activity", "start",
                "-a", "android.intent.action.MAIN",
                "-c", "android.intent.category.LAUNCHER",
                "--activity-single-top",
                "-n", lastLine
            });
            waitForWindowTransition(pkg, 90L);
            return "OK\tOPEN (" + lastLine + ") in " + (System.currentTimeMillis() - t0) + "ms";
        }

        execCmdAsync(new String[]{"monkey", "-p", pkg, "-c", "android.intent.category.LAUNCHER", "1"});
        waitForWindowTransition(pkg, 110L);
        return "OK\tOPEN (" + pkg + ") in " + (System.currentTimeMillis() - t0) + "ms";
    }

    private static String returnToTermuxFast(long t0) {
        stopAllGameThreads();
        execCmdAsync(new String[]{
            "/system/bin/cmd", "activity", "start",
            "--activity-reorder-to-front",
            "--activity-single-top",
            "-a", "android.intent.action.MAIN",
            "-n", "com.termux/.app.TermuxActivity"
        });
        int purged = cleanEphemeralNow(true);
        long dt = System.currentTimeMillis() - t0;
        return "OK\tRETURNED_TO_TERMUX (purged=" + purged + " temp files) in " + dt + "ms";
    }

    private static String handleShareFast(String filePath, String appName) {
        long t0 = System.currentTimeMillis();
        String realPath = filePath.trim();
        if (realPath.startsWith("/sdcard/")) {
            realPath = "/storage/emulated/0/" + realPath.substring(8);
        }
        String low = realPath.toLowerCase(Locale.ROOT);
        String mime = "*/*";
        if (low.endsWith(".jpg") || low.endsWith(".jpeg") || low.endsWith(".cr2")) mime = "image/jpeg";
        else if (low.endsWith(".png")) mime = "image/png";
        else if (low.endsWith(".webp")) mime = "image/webp";
        else if (low.endsWith(".mp4") || low.endsWith(".mkv")) mime = "video/mp4";

        String uri = "file://" + realPath;
        String pkg = "";
        if (appName != null && !appName.trim().isEmpty()) {
            String k = appName.trim().toLowerCase(Locale.ROOT);
            pkg = APP_ALIASES.containsKey(k) ? APP_ALIASES.get(k)[0] : appName.trim();
        }
        if ("com.niksoftware.snapseed".equals(pkg)) {
            execCmdAsync(new String[]{
                "/system/bin/cmd", "activity", "start",
                "-a", "android.intent.action.EDIT",
                "-d", uri, "-t", mime, "-p", pkg
            });
            waitForWindowTransition(pkg, 90L);
            return "OK\tSHARE_EDIT " + realPath + " -> " + pkg + " in " + (System.currentTimeMillis() - t0) + "ms";
        }
        List<String> cmd = new ArrayList<>(12);
        cmd.add("/system/bin/cmd");
        cmd.add("activity");
        cmd.add("start");
        cmd.add("-a");
        cmd.add("android.intent.action.SEND");
        cmd.add("-t");
        cmd.add(mime);
        cmd.add("--eu");
        cmd.add("android.intent.extra.STREAM");
        cmd.add(uri);
        if (!pkg.isEmpty()) {
            cmd.add("-p");
            cmd.add(pkg);
        }
        execCmdAsync(cmd.toArray(new String[0]));
        waitForWindowTransition(pkg, 90L);
        return "OK\tSHARE " + realPath + " (" + mime + ") -> " + (pkg.isEmpty() ? "chooser" : pkg) + " in " + (System.currentTimeMillis() - t0) + "ms";
    }

    private static String handleShopeeCartFast(String keyword, int minPrice, int maxPrice, boolean returnTermux) throws Exception {
        long t0 = System.currentTimeMillis();
        StringBuilder logs = new StringBuilder(512);
        List<UiItem> items = refreshTree(false, false);
        boolean inSearch = false;
        for (UiItem it : items) {
            if ("com.shopee.id".equals(it.pkg) && it.shortId.contains("inputSearchBar")) {
                inSearch = true;
                break;
            }
        }
        if (!inSearch) {
            if (!"com.shopee.id".equals(lastPackage)) {
                fastOpenTarget("shopee");
            }
            handleCommand("TAP\tid:search_prefill_click || id:inputSearchBar || id:search || Cari\t3000");
            waitForNextUiEvent(90L);
            String b64 = Base64.getEncoder().encodeToString(keyword.getBytes(StandardCharsets.UTF_8));
            handleCommand("SET_B64\t\t" + b64 + "\t2500\t0");
            waitForNextUiEvent(120L);
            handleCommand("NAV\tenter");
            handleCommand("WAIT\tFilter || Terkait || Terlaris || Rp\t4500");
        }
        String uiNow = handleCommand("UI\t0\t");
        if (uiNow.contains("Terlaris")) {
            handleCommand("TAP\tTerlaris\t1000");
            waitForNextUiEvent(220L);
        }

        UiItem bestCard = null;
        int bestPrice = Integer.MAX_VALUE;
        for (int attempt = 0; attempt < 5; attempt++) {
            List<UiItem> curItems = refreshTree(false, attempt > 0);
            for (UiItem it : curItems) {
                int w = it.right - it.left;
                int h = it.bottom - it.top;
                if (!it.clickable || w < 450 || h < 300 || it.top < 260) continue;
                String lbl = (!it.text.isEmpty() ? it.text : "") + " " + (!it.desc.isEmpty() ? it.desc : "") + " " + it.childLabel;
                int rpIdx = lbl.indexOf("Rp");
                if (rpIdx < 0) continue;
                StringBuilder numSb = new StringBuilder();
                for (int k = rpIdx + 2; k < lbl.length(); k++) {
                    char c = lbl.charAt(k);
                    if (c >= '0' && c <= '9') numSb.append(c);
                    else if (c == '.' || c == ' ') continue;
                    else if (numSb.length() > 0) break;
                }
                if (numSb.length() == 0) continue;
                int price = Integer.parseInt(numSb.toString());
                if (price >= minPrice && price <= maxPrice && price < bestPrice) {
                    bestPrice = price;
                    bestCard = it;
                }
            }
            if (bestCard != null) break;
            handleCommand("SCROLL\tdown\t8");
            waitForNextUiEvent(150L);
        }
        if (bestCard == null) {
            return "ERR\tCould not find matching Shopee product card in price range Rp" + minPrice + "-Rp" + maxPrice;
        }
        logs.append("Selected Product: Rp").append(bestPrice).append(" at (").append(bestCard.cx).append(",").append(bestCard.cy).append(")\n");
        fastTapPoint(bestCard.cx, bestCard.cy, 6L);
        handleCommand("WAIT\tBeli Sekarang || Beli Dengan Voucher || Masukkan Keranjang || + Keranjang || Tambah ke Keranjang\t4500");
        String tapCart = handleCommand("TAP\tMasukkan Keranjang || + Keranjang || Tambah ke Keranjang || desc:Keranjang || id:buttonProductAddCart\t1000");
        if (tapCart.startsWith("ERR\t")) {
            tapCart = handleCommand("TAP\t420\t2560");
        }
        logs.append("Tap Cart: ").append(tapCart).append("\n");
        waitForNextUiEvent(180L);
        String uiSheet = handleCommand("UI\t0\t");
        if (uiSheet.contains("buttonOption_unselected") || uiSheet.contains("Variasi") || uiSheet.contains("Ukuran")) {
            handleCommand("TAP\tid:buttonOption_unselected\t1000");
            waitForNextUiEvent(90L);
            handleCommand("TAP\t610\t2557");
            waitForNextUiEvent(150L);
        }
        String uiFinal = handleCommand("UI\t0\t");
        if (returnTermux) {
            logs.append(returnToTermuxFast(System.currentTimeMillis())).append("\n");
        }
        long dt = System.currentTimeMillis() - t0;
        return "OK\tSHOPEE_CART completed in " + dt + "ms\n" + logs.toString().trim() + "\n\n--- FINAL UI ---\n" + uiFinal;
    }

    private static List<String> tokenizeStep(String s) {
        List<String> tokens = new ArrayList<>(8);
        if (s == null) return tokens;
        StringBuilder cur = new StringBuilder();
        char quote = 0;
        int len = s.length();
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                if (c == '\\' && i + 1 < len && s.charAt(i + 1) == quote) {
                    cur.append(s.charAt(++i));
                } else if (c == quote) {
                    quote = 0;
                } else {
                    cur.append(c);
                }
            } else {
                if (c == '\'' || c == '"') {
                    quote = c;
                } else if (Character.isWhitespace(c)) {
                    if (cur.length() > 0) {
                        tokens.add(cur.toString());
                        cur.setLength(0);
                    }
                } else {
                    cur.append(c);
                }
            }
        }
        if (cur.length() > 0) tokens.add(cur.toString());
        return tokens;
    }

    private static String executePipelineStep(String stepStr) throws Exception {
        List<String> tok = tokenizeStep(stepStr);
        if (tok.isEmpty()) return "OK\tEMPTY";
        String sub = tok.get(0).toLowerCase(Locale.ROOT);
        List<String> rest = tok.subList(1, tok.size());

        switch (sub) {
            case "open":
            case "launch":
            case "app":
                return fastOpenTarget(String.join(" ", rest));

            case "tap":
            case "click":
            case "t":
            case "longtap":
            case "longclick": {
                String verb = sub.startsWith("long") ? "LONGTAP" : "TAP";
                long waitMs = 3000L;
                List<String> cleaned = new ArrayList<>();
                for (int i = 0; i < rest.size(); i++) {
                    String a = rest.get(i);
                    if ((a.equals("--wait") || a.equals("-w")) && i + 1 < rest.size()) {
                        waitMs = Long.parseLong(rest.get(++i));
                    } else if (a.equals("--nowait")) {
                        waitMs = 0L;
                    } else {
                        cleaned.add(a);
                    }
                }
                if (cleaned.size() == 2 && cleaned.get(0).matches("-?\\d+") && cleaned.get(1).matches("-?\\d+")) {
                    return handleCommand(verb + "\t" + cleaned.get(0) + "\t" + cleaned.get(1));
                }
                if (cleaned.size() == 1 && cleaned.get(0).contains(",")) {
                    String[] p = cleaned.get(0).split(",", 2);
                    if (p[0].matches("-?\\d+") && p[1].matches("-?\\d+")) {
                        return handleCommand(verb + "\t" + p[0] + "\t" + p[1]);
                    }
                }
                String target = String.join(" ", cleaned).trim();
                if (target.matches("\\d+")) target = "#" + target;
                return handleCommand(verb + "\t" + target + "\t" + waitMs);
            }

            case "set":
            case "input":
            case "write":
            case "append":
            case "type":
            case "search": {
                String appendFlag = sub.equals("append") ? "1" : "0";
                boolean pressEnter = sub.equals("search");
                long waitMs = 2500L;
                String target = "";
                List<String> cleaned = new ArrayList<>();
                for (int i = 0; i < rest.size(); i++) {
                    String a = rest.get(i);
                    if ((a.equals("--wait") || a.equals("-w")) && i + 1 < rest.size()) {
                        waitMs = Long.parseLong(rest.get(++i));
                    } else if ((a.equals("--target") || a.equals("-t")) && i + 1 < rest.size()) {
                        target = rest.get(++i);
                    } else if (a.equals("--enter") || a.equals("--submit") || a.equals("-e")) {
                        pressEnter = true;
                    } else if (a.equals("--append")) {
                        appendFlag = "1";
                    } else {
                        cleaned.add(a);
                    }
                }
                String textVal;
                if (target.isEmpty() && cleaned.size() >= 2 && (
                    cleaned.get(0).startsWith("#") || cleaned.get(0).startsWith("id:") ||
                    cleaned.get(0).startsWith("desc:") || cleaned.get(0).startsWith("text:") ||
                    cleaned.get(0).startsWith("class:"))) {
                    target = cleaned.get(0);
                    textVal = String.join(" ", cleaned.subList(1, cleaned.size()));
                } else {
                    textVal = String.join(" ", cleaned);
                }
                String b64 = Base64.getEncoder().encodeToString(textVal.getBytes(StandardCharsets.UTF_8));
                String res = handleCommand("SET_B64\t" + target + "\t" + b64 + "\t" + waitMs + "\t" + appendFlag);
                if (pressEnter) {
                    String er = handleCommand("NAV\tenter");
                    return res + " + " + er;
                }
                return res;
            }

            case "tap-set":
            case "tapset": {
                if (rest.size() < 2) return "ERR\tUsage: tap-set <target> <text> [--enter]";
                String target = rest.get(0);
                boolean pressEnter = false;
                List<String> txtParts = new ArrayList<>();
                for (int i = 1; i < rest.size(); i++) {
                    String a = rest.get(i);
                    if (a.equals("--enter") || a.equals("-e")) pressEnter = true;
                    else txtParts.add(a);
                }
                String tapRes = handleCommand("TAP\t" + target + "\t2500");
                String b64 = Base64.getEncoder().encodeToString(String.join(" ", txtParts).getBytes(StandardCharsets.UTF_8));
                String setRes = handleCommand("SET_B64\t\t" + b64 + "\t2000\t0");
                if (pressEnter) {
                    return tapRes + " -> " + setRes + " -> " + handleCommand("NAV\tenter");
                }
                return tapRes + " -> " + setRes;
            }

            case "scroll": {
                String dir = !rest.isEmpty() ? rest.get(0) : "down";
                int count = (rest.size() > 1 && rest.get(1).matches("\\d+")) ? Integer.parseInt(rest.get(1)) : 1;
                String last = "";
                for (int i = 0; i < count; i++) {
                    last = handleCommand("SCROLL\t" + dir + "\t8");
                }
                return last;
            }

            case "swipe": {
                if (rest.size() < 4) return "ERR\tUsage: swipe x1 y1 x2 y2 [steps]";
                String st = rest.size() > 4 ? rest.get(4) : "8";
                return handleCommand("SWIPE\t" + rest.get(0) + "\t" + rest.get(1) + "\t" + rest.get(2) + "\t" + rest.get(3) + "\t" + st);
            }

            case "wait":
            case "wait-gone":
            case "wait_gone": {
                String verb = sub.contains("gone") ? "WAIT_GONE" : "WAIT";
                long timeout = 4000L;
                String q;
                if (rest.size() >= 2 && rest.get(rest.size() - 1).matches("\\d+")) {
                    timeout = Long.parseLong(rest.get(rest.size() - 1));
                    q = String.join(" ", rest.subList(0, rest.size() - 1));
                } else {
                    q = String.join(" ", rest);
                }
                return handleCommand(verb + "\t" + q + "\t" + timeout);
            }

            case "back":
            case "home":
            case "recents":
            case "notif":
            case "qs":
            case "wake":
            case "enter":
            case "del":
            case "paste":
            case "lock":
            case "power":
                return handleCommand("NAV\t" + sub);

            case "sleep":
            case "delay": {
                if (!rest.isEmpty() && rest.get(0).matches("\\d+(\\.\\d+)?")) {
                    long ms = Math.round(Double.parseDouble(rest.get(0)) * 1000.0);
                    Thread.sleep(ms);
                    return "OK\tDELAY " + ms + "ms";
                }
                return handleCommand("NAV\tsleep");
            }

            case "nav":
            case "key":
                return handleCommand("NAV\t" + (!rest.isEmpty() ? rest.get(0) : "back"));

            case "ui":
            case "dump":
                return handleCommand("UI\t0\t" + String.join(" ", rest));

            case "live":
            case "frame":
                return handleCommand("LIVE	" + String.join(" ", rest));

            case "stream":
                return handleCommand("STREAM	" + String.join("	", rest));

            case "done":
            case "confirm":
            case "termux":
                return handleCommand("TERMUX");

            case "clean":
            case "purge":
                return handleCommand("CLEAN");

            default:
                return handleCommand(String.join("\t", tok));
        }
    }

    private static String handleCommand(String line) throws Exception {
        long t0 = System.currentTimeMillis();
        String[] parts = line.split("\t", -1);
        String cmd = parts[0].toUpperCase(Locale.ROOT).trim();

        switch (cmd) {
            case "PING": {
                long up = System.currentTimeMillis() - startedAt;
                return "OK\tv4\tconnected=" + connected + "\tuptime_ms=" + up
                    + "\tevents=" + uiEventSeq.get()
                    + "\tautopilot=" + autopilotRunning.get()
                    + "\tbg_joy=" + bgJoyRunning.get()
                    + "\tephemeral_files=" + ephemeralFiles.size();
            }

            case "CLEAN":
            case "PURGE": {
                int del = cleanEphemeralNow(true);
                long dt = System.currentTimeMillis() - t0;
                return "OK\tCLEAN purged=" + del + " files & freed RAM cache in " + dt + "ms";
            }

            case "SHELL":
            case "SH": {
                String b64Cmd = parts.length > 1 ? parts[1].trim() : "";
                if (b64Cmd.isEmpty()) return "ERR\tEmpty shell command";
                String shCmd = new String(Base64.getDecoder().decode(b64Cmd), StandardCharsets.UTF_8);
                ProcessBuilder pb = new ProcessBuilder("/system/bin/sh", "-c", shCmd);
                pb.redirectErrorStream(true);
                Process p = pb.start();
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    char[] cbuf = new char[4096];
                    int n;
                    while ((n = br.read(cbuf)) != -1) {
                        sb.append(cbuf, 0, n);
                    }
                }
                int code = p.waitFor();
                return sb.toString().trim();
            }

            case "HOT_RELOAD": {
                String srcJar = parts.length > 1 && !parts[1].isEmpty() ? parts[1].trim() : "/sdcard/.satset_tmp/satset-daemon.jar";
                File sf = new File(srcJar);
                if (!sf.exists()) return "ERR\tSource jar not found: " + srcJar;
                new File("/data/local/tmp/satset-daemon.jar").delete();
                execCmdOutput(new String[]{"cp", "-f", srcJar, "/data/local/tmp/satset-daemon.jar"});
                execCmdOutput(new String[]{"chmod", "444", "/data/local/tmp/satset-daemon.jar"});
                sf.delete();
                disconnectInternal();
                new Thread(() -> {
                    try {
                        Thread.sleep(30L);
                        Runtime.getRuntime().exec(new String[]{
                            "/system/bin/sh", "-c",
                            "sleep 0.08; ANDROID_DATA=/data/local/tmp CLASSPATH=/system/framework/uiautomator.jar:/data/local/tmp/satset-daemon.jar nohup app_process /system/bin SatsetDaemon >/dev/null 2>&1 </dev/null &"
                        });
                    } catch (Throwable ignored) {}
                    System.exit(0);
                }).start();
                return "OK\tHOT_RELOAD_STARTED";
            }

            case "DISCONNECT": {
                disconnectInternal();
                return "OK\tdisconnected";
            }

            case "QUIT": {
                disconnectInternal();
                new Thread(() -> {
                    try { Thread.sleep(25L); } catch (Throwable ignored) {}
                    System.exit(0);
                }).start();
                return "OK\tquitting";
            }

            case "TERMUX":
            case "DONE": {
                return returnToTermuxFast(t0);
            }

            case "OPEN": {
                String target = parts.length > 1 ? parts[1].trim() : "";
                return fastOpenTarget(target);
            }

            case "SHARE": {
                String fp = parts.length > 1 ? parts[1].trim() : "";
                String app = parts.length > 2 ? parts[2].trim() : "";
                return handleShareFast(fp, app);
            }

            case "SHOPEE_CART": {
                String kw = parts.length > 1 ? parts[1].trim() : "";
                int minP = parts.length > 2 && !parts[2].isEmpty() ? Integer.parseInt(parts[2]) : 5000;
                int maxP = parts.length > 3 && !parts[3].isEmpty() ? Integer.parseInt(parts[3]) : 85000;
                boolean ret = parts.length > 4 && "1".equals(parts[4]);
                return handleShopeeCartFast(kw, minP, maxP, ret);
            }

            case "TAP_SET": {
                String target = parts.length > 1 ? parts[1].trim() : "";
                String b64 = parts.length > 2 ? parts[2].trim() : "";
                boolean pressEnter = parts.length > 3 && "1".equals(parts[3]);
                String tapRes = handleCommand("TAP\t" + target + "\t2500");
                if (tapRes.startsWith("ERR\t")) return tapRes;
                String setRes = handleCommand("SET_B64\t\t" + b64 + "\t2000\t0");
                if (pressEnter) {
                    return tapRes + " -> " + setRes + " -> " + handleCommand("NAV\tenter");
                }
                return tapRes + " -> " + setRes;
            }

            case "DO": {
                // DO \t returnTermux(0|1) \t step1 \t step2 ...
                boolean returnTermux = parts.length > 1 && "1".equals(parts[1]);
                StringBuilder out = new StringBuilder(2048);
                String lastSub = "";
                int stepIdx = 0;

                for (int i = 2; i < parts.length; i++) {
                    String stepStr = parts[i].trim();
                    if (stepStr.isEmpty()) continue;
                    String low = stepStr.toLowerCase(Locale.ROOT);
                    if (low.equals("done") || low.equals("confirm") || low.equals("termux") || low.equals("return-termux") || low.equals("open termux")) {
                        returnTermux = true;
                        continue;
                    }
                    stepIdx++;
                    int sp = low.indexOf(' ');
                    lastSub = (sp > 0) ? low.substring(0, sp) : low;

                    String res = executePipelineStep(stepStr);
                    out.append("[").append(stepIdx).append("] ").append(stepStr).append(" -> ").append(res).append("\n");
                    if (res.startsWith("ERR\t") && !lastSub.equals("wake") && !lastSub.equals("sleep")
                        && !lastSub.equals("scan-color") && !lastSub.equals("tap-color")) {
                        break;
                    }
                }

                // Event-driven micro-settle before capturing target app's final UI (no blind 100ms sleep!)
                if (!lastSub.equals("ui") && !lastSub.equals("dump") && !lastSub.equals("clean")) {
                    if (lastSub.equals("tap") || lastSub.equals("click") || lastSub.equals("open")
                        || lastSub.equals("scroll") || lastSub.equals("swipe") || lastSub.equals("search") || lastSub.equals("enter")) {
                        waitForNextUiEvent(45L);
                    }
                    String uiOut = handleCommand("UI\t0\t");
                    out.append("\n--- CURRENT UI ---\n").append(uiOut);
                }

                if (returnTermux) {
                    String tRes = returnToTermuxFast(System.currentTimeMillis());
                    out.append("\n\n[CONFIRM] ").append(tRes);
                }
                return out.toString().trim();
            }

            case "UI":
            case "DUMP": {
                boolean includeAll = parts.length > 1 && "1".equals(parts[1]);
                String filter = parts.length > 2 ? parts[2].trim() : "";
                List<UiItem> items = refreshTree(includeAll, false);
                if (items.isEmpty()) {
                    items = refreshTree(includeAll, true);
                }
                int[] dims = getScreenDimensions();
                String orient = (dims[0] > dims[1]) ? "LANDSCAPE" : "PORTRAIT";
                long dt = System.currentTimeMillis() - t0;
                StringBuilder sb = new StringBuilder(2048);
                sb.append("PACKAGE: ").append(lastPackage.isEmpty() ? "unknown/game-surface" : lastPackage)
                  .append(" | SCREEN: ").append(dims[0]).append("x").append(dims[1]).append(" (").append(orient).append(")")
                  .append(" | ITEMS: ").append(items.size())
                  .append(" | TIME: ").append(dt).append("ms\n");
                int shown = 0;
                for (UiItem item : items) {
                    if (!filter.isEmpty() && scoreMatch(item, filter) == 0) continue;
                    sb.append(item.formatLine()).append("\n");
                    shown++;
                }
                if (!filter.isEmpty() && shown == 0) {
                    sb.append("(No items matched filter \"").append(filter).append("\")\n");
                }
                return sb.toString().trim();
            }

            case "OPEN_INTENT": {
                String action = parts.length > 1 ? parts[1].trim() : "";
                String uri = parts.length > 2 ? parts[2].trim() : "";
                String pkg = parts.length > 3 ? parts[3].trim() : "";
                String comp = parts.length > 4 ? parts[4].trim() : "";

                List<String> args = new ArrayList<>(12);
                args.add("/system/bin/cmd");
                args.add("activity");
                args.add("start");
                args.add("--activity-single-top");
                if (!action.isEmpty()) { args.add("-a"); args.add(action); }
                if (!uri.isEmpty()) { args.add("-d"); args.add(uri); }
                if (!comp.isEmpty()) { args.add("-n"); args.add(comp); }
                else if (!pkg.isEmpty()) { args.add("-p"); args.add(pkg); }
                execCmdAsync(args.toArray(new String[0]));
                waitForWindowTransition(pkg, 85L);
                long dt = System.currentTimeMillis() - t0;
                return "OK\tOPEN_INTENT (" + (!uri.isEmpty() ? uri : (!comp.isEmpty() ? comp : pkg)) + ") in " + dt + "ms";
            }

            case "TAP":
            case "LONGTAP": {
                ensureConnected();
                boolean isLong = "LONGTAP".equals(cmd);
                if (parts.length >= 3 && parts[1].matches("-?\\d+") && parts[2].matches("-?\\d+")) {
                    int x = Integer.parseInt(parts[1]);
                    int y = Integer.parseInt(parts[2]);
                    boolean ok = fastTapPoint(x, y, isLong ? 520L : 5L);
                    if (!ok) {
                        synchronized (lock) {
                            ok = (Boolean) (isLong ? mLongTapNoSync.invoke(ic, x, y) : mClickNoSync.invoke(ic, x, y));
                        }
                    }
                    cachedTreeEventSeq = -1L; // Invalidate tree cache after tap
                    long dt = System.currentTimeMillis() - t0;
                    return (ok ? "OK" : "ERR") + "\t" + cmd + " (" + x + "," + y + ") in " + dt + "ms";
                }
                String target = parts.length > 1 ? parts[1].trim() : "";
                long waitMs = parts.length > 2 && !parts[2].isEmpty() ? Long.parseLong(parts[2]) : 0L;

                String[] candidates = target.split("\\|\\|");
                UiItem hit = null;
                long startWait = System.currentTimeMillis();
                int pollCount = 0;
                while (true) {
                    if (candidates.length == 1 && candidates[0].trim().startsWith("#") && !lastItems.isEmpty()) {
                        hit = findBestItem(lastItems, candidates[0].trim());
                        if (hit != null) break;
                    }
                    List<UiItem> items = refreshTree(false, pollCount > 0 && (pollCount % 5 == 0));
                    for (String cand : candidates) {
                        hit = findBestItem(items, cand.trim());
                        if (hit != null) break;
                    }
                    if (hit != null) break;
                    long elapsed = System.currentTimeMillis() - startWait;
                    if (elapsed >= waitMs) break;
                    pollCount++;
                    waitForNextUiEvent(Math.min(18L, waitMs - elapsed));
                }

                if (hit == null) {
                    return "ERR\tTarget not found: \"" + target + "\" (waited " + (System.currentTimeMillis() - t0) + "ms)";
                }

                // Direct 5ms MotionEvent injection first; fallback to AccessibilityNodeInfo or InteractionController
                boolean ok = fastTapPoint(hit.cx, hit.cy, isLong ? 520L : 5L);
                if (!ok) {
                    synchronized (lock) {
                        if (hit.node != null) {
                            int action = isLong ? 32 : 16;
                            AccessibilityNodeInfo cur = hit.node;
                            while (cur != null) {
                                if (cur.performAction(action)) {
                                    ok = true;
                                    break;
                                }
                                cur = cur.getParent();
                            }
                        }
                        if (!ok) {
                            ok = (Boolean) (isLong ? mLongTapNoSync.invoke(ic, hit.cx, hit.cy) : mClickNoSync.invoke(ic, hit.cx, hit.cy));
                        }
                    }
                }
                cachedTreeEventSeq = -1L;
                long dt = System.currentTimeMillis() - t0;
                String label = !hit.text.isEmpty() ? hit.text : (!hit.desc.isEmpty() ? hit.desc : (!hit.childLabel.isEmpty() ? hit.childLabel : hit.shortId));
                return "OK\t" + cmd + " (" + hit.cx + "," + hit.cy + ") [#" + hit.index + " \"" + cleanStr(label, 55) + "\"] in " + dt + "ms";
            }

            // =================================================================
            // REAL-TIME GAME COMMANDS (HOLD, DRAG, MASH, JOY, AIM, COMBO, KEY_HOLD)
            // =================================================================

            case "HOLD": {
                ensureConnected();
                float x = Float.parseFloat(parts[1]);
                float y = Float.parseFloat(parts[2]);
                long durMs = parts.length > 3 && !parts[3].isEmpty() ? Long.parseLong(parts[3]) : 500L;
                int slot = acquireFreeActionSlot();
                touchDownSlot(slot, x, y);
                long start = SystemClock.uptimeMillis();
                try {
                    while (SystemClock.uptimeMillis() - start < durMs) {
                        touchMoveSlot(slot, x, y);
                        Thread.sleep(Math.min(14L, Math.max(1L, durMs - (SystemClock.uptimeMillis() - start))));
                    }
                } finally {
                    touchUpSlot(slot);
                }
                long dt = System.currentTimeMillis() - t0;
                return "OK\tHOLD (" + (int) x + "," + (int) y + ") for " + durMs + "ms in " + dt + "ms";
            }

            case "DRAG": {
                ensureConnected();
                float x1 = Float.parseFloat(parts[1]);
                float y1 = Float.parseFloat(parts[2]);
                float x2 = Float.parseFloat(parts[3]);
                float y2 = Float.parseFloat(parts[4]);
                long durMs = parts.length > 5 && !parts[5].isEmpty() ? Long.parseLong(parts[5]) : 180L;
                int slot = acquireFreeActionSlot();
                touchDownSlot(slot, x1, y1);
                long start = SystemClock.uptimeMillis();
                try {
                    while (true) {
                        long el = SystemClock.uptimeMillis() - start;
                        if (el >= durMs) break;
                        float frac = (float) el / (float) Math.max(1L, durMs);
                        touchMoveSlot(slot, x1 + (x2 - x1) * frac, y1 + (y2 - y1) * frac);
                        Thread.sleep(8L); // 120Hz smooth interpolation
                    }
                    touchMoveSlot(slot, x2, y2);
                    Thread.sleep(8L);
                } finally {
                    touchUpSlot(slot);
                }
                long dt = System.currentTimeMillis() - t0;
                return "OK\tDRAG (" + (int) x1 + "," + (int) y1 + ")->(" + (int) x2 + "," + (int) y2 + ") in " + dt + "ms";
            }

            case "MASH": {
                ensureConnected();
                String coordsSpec = parts.length > 1 ? parts[1].trim() : "610,1350";
                int count = parts.length > 2 && !parts[2].isEmpty() ? Integer.parseInt(parts[2]) : 10;
                long intervalMs = parts.length > 3 && !parts[3].isEmpty() ? Long.parseLong(parts[3]) : 25L;
                boolean bg = parts.length > 4 && "1".equals(parts[4]);

                String[] ptTokens = coordsSpec.split("[;\\s]+");
                List<int[]> pts = new ArrayList<>();
                for (String tok : ptTokens) {
                    String[] xy = tok.split(",");
                    if (xy.length == 2) {
                        pts.add(new int[]{Integer.parseInt(xy[0].trim()), Integer.parseInt(xy[1].trim())});
                    }
                }
                if (pts.isEmpty()) return "ERR\tInvalid MASH coordinates: " + coordsSpec;

                Runnable mashTask = () -> {
                    bgMashRunning.set(true);
                    try {
                        for (int i = 0; i < count && bgMashRunning.get(); i++) {
                            int[] pt = pts.get(i % pts.size());
                            fastTapPoint(pt[0], pt[1], 6L);
                            if (i + 1 < count && intervalMs > 0) {
                                Thread.sleep(intervalMs);
                            }
                        }
                    } catch (InterruptedException ignored) {
                    } finally {
                        bgMashRunning.set(false);
                    }
                };

                if (bg) {
                    bgMashRunning.set(false);
                    if (bgMashThread != null) {
                        try { bgMashThread.join(40L); } catch (Throwable ignored) {}
                    }
                    bgMashThread = new Thread(mashTask, "satset-bg-mash");
                    bgMashThread.setDaemon(true);
                    bgMashThread.start();
                    return "OK\tMASH_BG started (" + count + " taps @ " + intervalMs + "ms on " + coordsSpec + ")";
                } else {
                    mashTask.run();
                    long dt = System.currentTimeMillis() - t0;
                    return "OK\tMASH (" + count + " taps @ " + intervalMs + "ms on " + coordsSpec + ") in " + dt + "ms";
                }
            }

            case "JOY": {
                ensureConnected();
                String dir = parts.length > 1 ? parts[1].trim() : "w";
                if (dir.equalsIgnoreCase("stop")) {
                    bgJoyRunning.set(false);
                    touchUpSlot(0);
                    return "OK\tJOY stopped";
                }
                long durMs = parts.length > 2 && !parts[2].isEmpty() ? Long.parseLong(parts[2]) : 800L;
                int[] dims = getScreenDimensions();
                boolean landscape = dims[0] > dims[1];
                float defCx = landscape ? 380f : 300f;
                float defCy = landscape ? (dims[1] - 340f) : (dims[1] - 600f);
                float defR = landscape ? 160f : 150f;

                float cx = parts.length > 3 && !parts[3].isEmpty() && Float.parseFloat(parts[3]) > 0 ? Float.parseFloat(parts[3]) : defCx;
                float cy = parts.length > 4 && !parts[4].isEmpty() && Float.parseFloat(parts[4]) > 0 ? Float.parseFloat(parts[4]) : defCy;
                float radius = parts.length > 5 && !parts[5].isEmpty() && Float.parseFloat(parts[5]) > 0 ? Float.parseFloat(parts[5]) : defR;
                boolean bg = parts.length > 6 && "1".equals(parts[6]);

                bgJoyRunning.set(false);
                if (bgJoyThread != null) {
                    try { bgJoyThread.join(40L); } catch (Throwable ignored) {}
                }
                bgJoyRunning.set(true);

                if (bg) {
                    bgJoyThread = new Thread(() -> runJoystickLoop(dir, durMs, cx, cy, radius, bgJoyRunning), "satset-bg-joy");
                    bgJoyThread.setDaemon(true);
                    bgJoyThread.start();
                    return "OK\tJOY_BG dir=" + dir + " duration=" + durMs + "ms center=(" + (int) cx + "," + (int) cy + ") r=" + (int) radius;
                } else {
                    runJoystickLoop(dir, durMs, cx, cy, radius, bgJoyRunning);
                    long dt = System.currentTimeMillis() - t0;
                    return "OK\tJOY dir=" + dir + " center=(" + (int) cx + "," + (int) cy + ") in " + dt + "ms";
                }
            }

            case "AIM": {
                ensureConnected();
                float x = Float.parseFloat(parts[1]);
                float y = Float.parseFloat(parts[2]);
                String dir = parts.length > 3 ? parts[3].trim() : "ne";
                float dist = parts.length > 4 && !parts[4].isEmpty() ? Float.parseFloat(parts[4]) : 180f;
                long holdMs = parts.length > 5 && !parts[5].isEmpty() ? Long.parseLong(parts[5]) : 60L;

                double rad = parseDirectionRadians(dir);
                float tx = x + (float) (Math.cos(rad) * dist);
                float ty = y + (float) (Math.sin(rad) * dist);

                int slot = acquireFreeActionSlot();
                touchDownSlot(slot, x, y);
                try {
                    int steps = 5;
                    for (int i = 1; i <= steps; i++) {
                        float f = (float) i / steps;
                        touchMoveSlot(slot, x + (tx - x) * f, y + (ty - y) * f);
                        Thread.sleep(8L);
                    }
                    long holdStart = SystemClock.uptimeMillis();
                    while (SystemClock.uptimeMillis() - holdStart < holdMs) {
                        touchMoveSlot(slot, tx, ty);
                        Thread.sleep(10L);
                    }
                } finally {
                    touchUpSlot(slot);
                }
                long dt = System.currentTimeMillis() - t0;
                return "OK\tAIM (" + (int) x + "," + (int) y + ")->(" + (int) tx + "," + (int) ty + ") dir=" + dir + " in " + dt + "ms";
            }

            case "COMBO": {
                ensureConnected();
                long durMs = parts.length > 1 && !parts[1].isEmpty() ? Long.parseLong(parts[1]) : 1200L;
                String joyDir = parts.length > 2 && !parts[2].isEmpty() ? parts[2].trim() : "ne";
                String joyGeom = parts.length > 3 ? parts[3].trim() : "";
                String rightSpec = parts.length > 4 ? parts[4].trim() : "";

                int[] dims = getScreenDimensions();
                boolean landscape = dims[0] > dims[1];
                float jCx = landscape ? 380f : 300f;
                float jCy = landscape ? (dims[1] - 340f) : (dims[1] - 600f);
                float jR = landscape ? 160f : 150f;
                if (!joyGeom.isEmpty()) {
                    String[] jg = joyGeom.split(",");
                    if (jg.length >= 2) {
                        jCx = Float.parseFloat(jg[0]);
                        jCy = Float.parseFloat(jg[1]);
                    }
                    if (jg.length >= 3) jR = Float.parseFloat(jg[2]);
                }

                AtomicBoolean comboJoyFlag = new AtomicBoolean(true);
                final float fCx = jCx, fCy = jCy, fR = jR;
                Thread jThread = null;
                if (!joyDir.equalsIgnoreCase("none") && !joyDir.isEmpty()) {
                    jThread = new Thread(() -> runJoystickLoop(joyDir, durMs, fCx, fCy, fR, comboJoyFlag), "combo-joy");
                    jThread.setDaemon(true);
                    jThread.start();
                }

                int actionsFired = 0;
                long start = SystemClock.uptimeMillis();
                long lastMashAt = 0L;
                int mashX = -1, mashY = -1;
                long mashInterval = 90L;

                List<String[]> timedEvents = new ArrayList<>();
                if (!rightSpec.isEmpty()) {
                    for (String part : rightSpec.split("\\|")) {
                        String p = part.trim();
                        if (p.startsWith("mash:")) {
                            String[] mp = p.substring(5).split(":");
                            String[] xy = mp[0].split(",");
                            if (xy.length == 2) {
                                mashX = Integer.parseInt(xy[0]);
                                mashY = Integer.parseInt(xy[1]);
                            }
                            if (mp.length > 1) mashInterval = Long.parseLong(mp[1]);
                        } else if (!p.isEmpty()) {
                            timedEvents.add(new String[]{p, "0"});
                        }
                    }
                }

                try {
                    while (true) {
                        long el = SystemClock.uptimeMillis() - start;
                        if (el >= durMs) break;

                        for (String[] ev : timedEvents) {
                            if ("1".equals(ev[1])) continue;
                            String spec = ev[0];
                            long atMs = 0L;
                            int atIdx = spec.indexOf('@');
                            if (atIdx > 0) {
                                atMs = Long.parseLong(spec.substring(atIdx + 1));
                                spec = spec.substring(0, atIdx);
                            }
                            if (el >= atMs) {
                                ev[1] = "1";
                                if (spec.startsWith("tap:")) {
                                    String[] xy = spec.substring(4).split(",");
                                    fastTapPoint(Float.parseFloat(xy[0]), Float.parseFloat(xy[1]), 6L);
                                    actionsFired++;
                                } else if (spec.startsWith("aim:")) {
                                    String[] ap = spec.substring(4).split(":");
                                    String[] xy = ap[0].split(",");
                                    float ax = Float.parseFloat(xy[0]);
                                    float ay = Float.parseFloat(xy[1]);
                                    String aDir = ap.length > 1 ? ap[1] : "ne";
                                    float aDist = ap.length > 2 ? Float.parseFloat(ap[2]) : 170f;
                                    double rad = parseDirectionRadians(aDir);
                                    float tx = ax + (float) (Math.cos(rad) * aDist);
                                    float ty = ay + (float) (Math.sin(rad) * aDist);
                                    touchDownSlot(1, ax, ay);
                                    for (int s = 1; s <= 4; s++) {
                                        touchMoveSlot(1, ax + (tx - ax) * (s / 4f), ay + (ty - ay) * (s / 4f));
                                        Thread.sleep(8L);
                                    }
                                    Thread.sleep(28L);
                                    touchUpSlot(1);
                                    actionsFired++;
                                }
                            }
                        }

                        if (mashX >= 0 && (el - lastMashAt) >= mashInterval) {
                            fastTapPoint(mashX, mashY, 6L);
                            lastMashAt = el;
                            actionsFired++;
                        }
                        Thread.sleep(10L);
                    }
                } finally {
                    comboJoyFlag.set(false);
                    if (jThread != null) {
                        try { jThread.join(60L); } catch (Throwable ignored) {}
                    }
                    touchUpSlot(0);
                    touchUpSlot(1);
                }
                long dt = System.currentTimeMillis() - t0;
                return "OK\tCOMBO joy=" + joyDir + " actions=" + actionsFired + " in " + dt + "ms";
            }

            case "KEY_HOLD": {
                ensureConnected();
                int keyCode = Integer.parseInt(parts[1]);
                long durMs = parts.length > 2 && !parts[2].isEmpty() ? Long.parseLong(parts[2]) : 300L;
                int source = parts.length > 3 && !parts[3].isEmpty() ? Integer.parseInt(parts[3]) : SOURCE_KEYBOARD;
                long downTime = SystemClock.uptimeMillis();
                KeyEvent downEv = new KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0, 0, -1, 0, 0, source);
                uiAuto.injectInputEvent(downEv, false);
                int rep = 0;
                while (SystemClock.uptimeMillis() - downTime < durMs) {
                    Thread.sleep(Math.min(35L, Math.max(1L, durMs - (SystemClock.uptimeMillis() - downTime))));
                    rep++;
                    long now = SystemClock.uptimeMillis();
                    KeyEvent repEv = new KeyEvent(downTime, now, KeyEvent.ACTION_DOWN, keyCode, rep, 0, -1, 0, 0, source);
                    uiAuto.injectInputEvent(repEv, false);
                }
                long upTime = SystemClock.uptimeMillis();
                KeyEvent upEv = new KeyEvent(downTime, upTime, KeyEvent.ACTION_UP, keyCode, 0, 0, -1, 0, 0, source);
                uiAuto.injectInputEvent(upEv, false);
                long dt = System.currentTimeMillis() - t0;
                return "OK\tKEY_HOLD code=" + keyCode + " for " + durMs + "ms in " + dt + "ms";
            }

            case "PIXEL": {
                ensureConnected();
                String spec = parts.length > 1 ? parts[1].trim() : "610,1350";
                Bitmap bmp = uiAuto.takeScreenshot();
                if (bmp == null) return "ERR\ttakeScreenshot returned null";
                int w = bmp.getWidth();
                int h = bmp.getHeight();
                cachedScreenW = w;
                cachedScreenH = h;
                lastScreenDimCheck = SystemClock.uptimeMillis();
                StringBuilder sb = new StringBuilder(128);
                sb.append("SCREEN: ").append(w).append("x").append(h);
                for (String tok : spec.split("[;\\s]+")) {
                    String[] xy = tok.split(",");
                    if (xy.length != 2) continue;
                    int x = Math.max(0, Math.min(w - 1, Integer.parseInt(xy[0].trim())));
                    int y = Math.max(0, Math.min(h - 1, Integer.parseInt(xy[1].trim())));
                    int px = bmp.getPixel(x, y);
                    int r = (px >> 16) & 0xFF;
                    int g = (px >> 8) & 0xFF;
                    int b = px & 0xFF;
                    int luma = (r * 299 + g * 587 + b * 114) / 1000;
                    sb.append(String.format(Locale.ROOT, " | (%d,%d)=#%02X%02X%02X rgb(%d,%d,%d) lum=%d%%",
                        x, y, r, g, b, r, g, b, (luma * 100) / 255));
                }
                bmp.recycle();
                long dt = System.currentTimeMillis() - t0;
                return "OK\t" + sb.toString() + " in " + dt + "ms";
            }

            case "SCAN_COLOR": {
                ensureConnected();
                String hex = parts.length > 1 ? parts[1].trim() : "#FF0000";
                int tol = parts.length > 2 && !parts[2].isEmpty() ? Integer.parseInt(parts[2]) : 28;
                String region = parts.length > 3 ? parts[3].trim() : "";
                boolean tapIfFound = parts.length > 4 && "1".equals(parts[4]);

                int targetRgb = parseHexColor(hex);
                Bitmap bmp = uiAuto.takeScreenshot();
                if (bmp == null) return "ERR\ttakeScreenshot returned null";

                int rx1 = 0, ry1 = 0, rx2 = bmp.getWidth(), ry2 = bmp.getHeight();
                if (!region.isEmpty()) {
                    String[] rp = region.split(",");
                    if (rp.length == 4) {
                        rx1 = Integer.parseInt(rp[0].trim());
                        ry1 = Integer.parseInt(rp[1].trim());
                        rx2 = Integer.parseInt(rp[2].trim());
                        ry2 = Integer.parseInt(rp[3].trim());
                    }
                }

                List<ColorCluster> clusters = scanBitmapForColor(bmp, targetRgb, tol, rx1, ry1, rx2, ry2);
                bmp.recycle();

                if (clusters.isEmpty()) {
                    long dt = System.currentTimeMillis() - t0;
                    return "ERR\tCOLOR_NOT_FOUND #" + String.format("%06X", targetRgb) + " (tol=" + tol + ") in " + dt + "ms";
                }

                ColorCluster best = clusters.get(0);
                if (tapIfFound) {
                    fastTapPoint(best.cx, best.cy, 6L);
                }

                long dt = System.currentTimeMillis() - t0;
                StringBuilder sb = new StringBuilder(160);
                sb.append("OK\t").append(tapIfFound ? "TAP_COLOR" : "SCAN_COLOR")
                  .append(" #").append(String.format("%06X", targetRgb))
                  .append(" clusters=").append(clusters.size())
                  .append(" best=(").append(best.cx).append(",").append(best.cy).append(")")
                  .append(" [").append(best.left).append(",").append(best.top).append("-").append(best.right).append(",").append(best.bottom).append("]")
                  .append(" hits=").append(best.count)
                  .append(" in ").append(dt).append("ms");
                int maxShow = Math.min(5, clusters.size());
                for (int i = 1; i < maxShow; i++) {
                    ColorCluster c = clusters.get(i);
                    sb.append(" | #").append(i + 1).append(":(").append(c.cx).append(",").append(c.cy).append(") hits=").append(c.count);
                }
                return sb.toString();
            }

            case "WAIT_COLOR": {
                ensureConnected();
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                int targetRgb = parseHexColor(parts[3]);
                int tol = parts.length > 4 && !parts[4].isEmpty() ? Integer.parseInt(parts[4]) : 28;
                long timeoutMs = parts.length > 5 && !parts[5].isEmpty() ? Long.parseLong(parts[5]) : 5000L;
                boolean wantGone = parts.length > 6 && "1".equals(parts[6]);

                long start = System.currentTimeMillis();
                while (true) {
                    Bitmap bmp = uiAuto.takeScreenshot();
                    if (bmp != null) {
                        int cx = Math.max(0, Math.min(bmp.getWidth() - 1, x));
                        int cy = Math.max(0, Math.min(bmp.getHeight() - 1, y));
                        int px = bmp.getPixel(cx, cy);
                        bmp.recycle();
                        boolean matched = colorMatches(px, targetRgb, tol);
                        if ((!wantGone && matched) || (wantGone && !matched)) {
                            long dt = System.currentTimeMillis() - t0;
                            return String.format(Locale.ROOT, "OK\tWAIT_COLOR (%d,%d)=#%06X in %dms", x, y, (px & 0xFFFFFF), dt);
                        }
                    }
                    long el = System.currentTimeMillis() - start;
                    if (el >= timeoutMs) {
                        return "ERR\tTimeout (" + timeoutMs + "ms) waiting for color at (" + x + "," + y + ")";
                    }
                    Thread.sleep(20L);
                }
            }

            case "AUTOPILOT": {
                ensureConnected();
                String sub = parts.length > 1 ? parts[1].toUpperCase(Locale.ROOT).trim() : "STATUS";
                if (sub.equals("STOP")) {
                    stopAllGameThreads();
                    apStatusMsg = "stopped";
                    return "OK\tAUTOPILOT stopped (frames=" + apFrames.get() + ", attacks=" + apAttacks.get()
                        + ", skills=" + apSkills.get() + ", targets=" + apTargetsFound.get() + ", heals=" + apHeals.get() + ")";
                }
                if (sub.equals("STATUS")) {
                    long runMs = autopilotRunning.get() ? (System.currentTimeMillis() - apStartedAt) : 0L;
                    return "OK\tAUTOPILOT running=" + autopilotRunning.get()
                        + " state=\"" + apStatusMsg + "\" elapsed=" + runMs + "ms"
                        + " frames=" + apFrames.get() + " attacks=" + apAttacks.get()
                        + " skills=" + apSkills.get() + " targets=" + apTargetsFound.get()
                        + " heals=" + apHeals.get();
                }

                // START Decoupled Dual-Thread Autopilot (85Hz Control Thread + Async Vision Thread -> ZERO STUTTER!)
                stopAllGameThreads();
                long durMs = parts.length > 2 && !parts[2].isEmpty() ? Long.parseLong(parts[2]) : 5000L;
                String joyMode = parts.length > 3 && !parts[3].isEmpty() ? parts[3].trim() : "strafe";
                String joyGeom = parts.length > 4 ? parts[4].trim() : "";
                String atkSpec = parts.length > 5 ? parts[5].trim() : "";
                String skillSpec = parts.length > 6 ? parts[6].trim() : "";
                String trackSpec = parts.length > 7 ? parts[7].trim() : "";
                String hpSpec = parts.length > 8 ? parts[8].trim() : "";
                boolean bg = parts.length > 9 && "1".equals(parts[9]);

                int[] dims = getScreenDimensions();
                boolean landscape = dims[0] > dims[1];
                float jCx = landscape ? 380f : 300f;
                float jCy = landscape ? (dims[1] - 340f) : (dims[1] - 600f);
                float jR = landscape ? 160f : 150f;
                if (!joyGeom.isEmpty()) {
                    String[] jg = joyGeom.split(",");
                    if (jg.length >= 2) { jCx = Float.parseFloat(jg[0]); jCy = Float.parseFloat(jg[1]); }
                    if (jg.length >= 3) jR = Float.parseFloat(jg[2]);
                }

                List<int[]> atkPts = new ArrayList<>();
                if (!atkSpec.isEmpty()) {
                    for (String tok : atkSpec.split("[;\\s]+")) {
                        String[] xy = tok.split(",");
                        if (xy.length == 2) atkPts.add(new int[]{Integer.parseInt(xy[0]), Integer.parseInt(xy[1])});
                    }
                } else if (landscape) {
                    atkPts.add(new int[]{dims[0] - 280, dims[1] - 230});
                } else {
                    atkPts.add(new int[]{dims[0] - 200, dims[1] - 400});
                }

                List<int[]> skillPts = new ArrayList<>();
                if (!skillSpec.isEmpty()) {
                    for (String tok : skillSpec.split("[;\\s]+")) {
                        String[] xy = tok.split(",");
                        if (xy.length == 2) skillPts.add(new int[]{Integer.parseInt(xy[0]), Integer.parseInt(xy[1])});
                    }
                }

                final float fCx = jCx, fCy = jCy, fR = jR;
                final float screenCenterX = dims[0] / 2f;
                final float screenCenterY = dims[1] / 2f;

                apFrames.set(0);
                apAttacks.set(0);
                apSkills.set(0);
                apTargetsFound.set(0);
                apHeals.set(0);
                apTrackedRad = Double.NaN;
                apStartedAt = System.currentTimeMillis();
                apStatusMsg = "active(" + joyMode + ")";
                autopilotRunning.set(true);

                // Dedicated Async Vision Thread (Never blocks 85Hz joystick/attack control!)
                if (!trackSpec.isEmpty() || !hpSpec.isEmpty()) {
                    autopilotVisionThread = new Thread(() -> {
                        long start = SystemClock.uptimeMillis();
                        long lastHeal = 0L;
                        while (autopilotRunning.get() && (SystemClock.uptimeMillis() - start) < durMs) {
                            try {
                                Bitmap bmp = uiAuto.takeScreenshot();
                                if (bmp != null) {
                                    long el = SystemClock.uptimeMillis() - start;
                                    if (!hpSpec.isEmpty() && (el - lastHeal) >= 1200L) {
                                        String[] hp = hpSpec.split(",");
                                        if (hp.length >= 5) {
                                            int hx = Integer.parseInt(hp[0]);
                                            int hy = Integer.parseInt(hp[1]);
                                            int hCol = parseHexColor(hp[2]);
                                            int healX = Integer.parseInt(hp[3]);
                                            int healY = Integer.parseInt(hp[4]);
                                            int px = bmp.getPixel(Math.min(bmp.getWidth() - 1, hx), Math.min(bmp.getHeight() - 1, hy));
                                            if (!colorMatches(px, hCol, 38)) {
                                                fastTapPoint(healX, healY, 8L);
                                                apHeals.incrementAndGet();
                                                lastHeal = el;
                                            }
                                        }
                                    }
                                    if (!trackSpec.isEmpty()) {
                                        String[] tp = trackSpec.split(",");
                                        int tCol = parseHexColor(tp[0]);
                                        int tTol = tp.length > 1 ? Integer.parseInt(tp[1]) : 30;
                                        int rx1 = tp.length > 5 ? Integer.parseInt(tp[2]) : 0;
                                        int ry1 = tp.length > 5 ? Integer.parseInt(tp[3]) : 0;
                                        int rx2 = tp.length > 5 ? Integer.parseInt(tp[4]) : bmp.getWidth();
                                        int ry2 = tp.length > 5 ? Integer.parseInt(tp[5]) : bmp.getHeight();
                                        List<ColorCluster> cls = scanBitmapForColor(bmp, tCol, tTol, rx1, ry1, rx2, ry2);
                                        if (!cls.isEmpty()) {
                                            ColorCluster c = cls.get(0);
                                            apTargetsFound.incrementAndGet();
                                            apTrackedRad = Math.atan2(c.cy - screenCenterY, c.cx - screenCenterX);
                                            apStatusMsg = "tracking(" + c.cx + "," + c.cy + ")";
                                        }
                                    }
                                    bmp.recycle();
                                }
                                Thread.sleep(60L);
                            } catch (InterruptedException e) {
                                break;
                            } catch (Throwable ignored) {}
                        }
                    }, "satset-ap-vision");
                    autopilotVisionThread.setDaemon(true);
                    autopilotVisionThread.start();
                }

                // Pure 85Hz Real-Time Control Loop (Zero blocking I/O!)
                Runnable apTask = () -> {
                    boolean useJoy = !joyMode.equalsIgnoreCase("none");
                    if (useJoy) touchDownSlot(0, fCx, fCy);
                    long start = SystemClock.uptimeMillis();
                    long lastAtk = 0L;
                    long lastSkill = 0L;
                    double baseRad = parseDirectionRadians(joyMode);

                    try {
                        while (autopilotRunning.get()) {
                            long now = SystemClock.uptimeMillis();
                            long el = now - start;
                            if (el >= durMs) break;
                            apFrames.incrementAndGet();

                            if (useJoy) {
                                double rad = !Double.isNaN(apTrackedRad) ? apTrackedRad : baseRad;
                                if (Double.isNaN(apTrackedRad)) {
                                    if (joyMode.equalsIgnoreCase("circle") || joyMode.equalsIgnoreCase("spin")) {
                                        rad = (el * 0.0045) % (Math.PI * 2.0);
                                    } else if (joyMode.equalsIgnoreCase("strafe") || joyMode.equalsIgnoreCase("kite")) {
                                        int phase = (int) ((el / 300L) % 4);
                                        rad = (phase == 0) ? Math.toRadians(210)
                                            : (phase == 1) ? Math.toRadians(330)
                                            : (phase == 2) ? Math.toRadians(150)
                                            : Math.toRadians(30);
                                    } else if (joyMode.equalsIgnoreCase("patrol")) {
                                        int phase = (int) ((el / 450L) % 2);
                                        rad = (phase == 0) ? 0.0 : Math.PI;
                                    }
                                }
                                float tx = fCx + (float) (Math.cos(rad) * fR);
                                float ty = fCy + (float) (Math.sin(rad) * fR);
                                touchMoveSlot(0, tx, ty);
                            }

                            if (!atkPts.isEmpty() && (el - lastAtk) >= 75L) {
                                int[] pt = atkPts.get(apAttacks.get() % atkPts.size());
                                fastTapPoint(pt[0], pt[1], 6L);
                                apAttacks.incrementAndGet();
                                lastAtk = el;
                            }

                            if (!skillPts.isEmpty() && (el - lastSkill) >= 500L) {
                                int[] spt = skillPts.get(apSkills.get() % skillPts.size());
                                fastTapPoint(spt[0], spt[1], 8L);
                                apSkills.incrementAndGet();
                                lastSkill = el;
                            }

                            Thread.sleep(12L); // Pure 85Hz tick
                        }
                    } catch (InterruptedException ignored) {
                    } finally {
                        autopilotRunning.set(false);
                        if (useJoy) touchUpSlot(0);
                        touchUpSlot(1);
                        apStatusMsg = "completed";
                    }
                };

                if (bg) {
                    autopilotThread = new Thread(apTask, "satset-autopilot");
                    autopilotThread.setDaemon(true);
                    autopilotThread.start();
                    return "OK\tAUTOPILOT_BG started for " + durMs + "ms (joy=" + joyMode + ")";
                } else {
                    apTask.run();
                    long dt = System.currentTimeMillis() - t0;
                    return "OK\tAUTOPILOT completed in " + dt + "ms (frames=" + apFrames.get()
                        + ", attacks=" + apAttacks.get() + ", skills=" + apSkills.get()
                        + ", targets=" + apTargetsFound.get() + ", heals=" + apHeals.get() + ")";
                }
            }

            case "SET_B64": {
                String target = parts.length > 1 ? parts[1].trim() : "";
                String b64 = parts.length > 2 ? parts[2].trim() : "";
                long waitMs = parts.length > 3 && !parts[3].isEmpty() ? Long.parseLong(parts[3]) : 2000L;
                boolean append = parts.length > 4 && "1".equals(parts[4]);
                String text = new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);

                UiItem hit = null;
                long startWait = System.currentTimeMillis();
                int poll = 0;
                while (true) {
                    List<UiItem> items = refreshTree(false, poll > 0 && (poll % 4 == 0));
                    if (!target.isEmpty()) {
                        hit = findBestItem(items, target);
                    } else {
                        hit = findEditableItem(items);
                    }
                    if (hit != null) break;
                    long elapsed = System.currentTimeMillis() - startWait;
                    if (elapsed >= waitMs) break;
                    poll++;
                    waitForNextUiEvent(Math.min(18L, waitMs - elapsed));
                }

                if (hit == null || hit.node == null) {
                    return "ERR\tNo editable target found" + (target.isEmpty() ? "" : " for \"" + target + "\"") + " after " + (System.currentTimeMillis() - t0) + "ms";
                }

                AccessibilityNodeInfo targetNode = hit.node;
                if (!hit.editable) {
                    fastTapPoint(hit.cx, hit.cy, 5L);
                    waitForNextUiEvent(45L);
                    List<UiItem> items2 = refreshTree(false, true);
                    UiItem ed = findEditableItem(items2);
                    if (ed != null && ed.node != null) {
                        hit = ed;
                        targetNode = ed.node;
                    }
                } else if (!hit.focused) {
                    targetNode.performAction(1); // ACTION_FOCUS (~0.5ms, no 85ms click+sleep!)
                }

                String finalText = text;
                if (append && !hit.text.isEmpty()) {
                    finalText = hit.text + text;
                }

                Bundle bundle = new Bundle();
                bundle.putCharSequence("ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE", finalText);
                boolean ok = targetNode.performAction(0x00200000, bundle);
                if (!ok) {
                    fastTapPoint(hit.cx, hit.cy, 5L);
                    waitForNextUiEvent(30L);
                    List<UiItem> items3 = refreshTree(false, true);
                    UiItem ed = findEditableItem(items3);
                    if (ed != null && ed.node != null) {
                        ok = ed.node.performAction(0x00200000, bundle);
                    }
                }
                cachedTreeEventSeq = -1L;
                long dt = System.currentTimeMillis() - t0;
                if (!ok) {
                    return "FALLBACK_CLIPBOARD\t(" + hit.cx + "," + hit.cy + ")\t" + dt + "ms";
                }
                return "OK\tSET (" + hit.cx + "," + hit.cy + ") [#" + hit.index + "] len=" + finalText.length() + " in " + dt + "ms";
            }

            case "SWIPE": {
                ensureConnected();
                int x1 = Integer.parseInt(parts[1]);
                int y1 = Integer.parseInt(parts[2]);
                int x2 = Integer.parseInt(parts[3]);
                int y2 = Integer.parseInt(parts[4]);
                int steps = parts.length > 5 && !parts[5].isEmpty() ? Integer.parseInt(parts[5]) : 8;
                boolean ok = fastSwipe(x1, y1, x2, y2, steps, 7L);
                cachedTreeEventSeq = -1L;
                long dt = System.currentTimeMillis() - t0;
                return (ok ? "OK" : "ERR") + "\tSWIPE (" + x1 + "," + y1 + ")->(" + x2 + "," + y2 + ") steps=" + steps + " in " + dt + "ms";
            }

            case "SCROLL": {
                ensureConnected();
                String dir = parts.length > 1 ? parts[1].toLowerCase(Locale.ROOT).trim() : "down";
                int steps = parts.length > 2 && !parts[2].isEmpty() ? Integer.parseInt(parts[2]) : 8;
                int x1 = 610, y1 = 2050, x2 = 610, y2 = 600;
                switch (dir) {
                    case "down":
                    case "next":
                        x1 = 610; y1 = 2050; x2 = 610; y2 = 600;
                        break;
                    case "up":
                    case "prev":
                        x1 = 610; y1 = 650; x2 = 610; y2 = 2100;
                        break;
                    case "left":
                        x1 = 1050; y1 = 1350; x2 = 170; y2 = 1350;
                        break;
                    case "right":
                        x1 = 170; y1 = 1350; x2 = 1050; y2 = 1350;
                        break;
                }
                boolean ok = fastSwipe(x1, y1, x2, y2, steps, 7L);
                cachedTreeEventSeq = -1L;
                long dt = System.currentTimeMillis() - t0;
                return (ok ? "OK" : "ERR") + "\tSCROLL " + dir + " in " + dt + "ms";
            }

            case "NAV": {
                ensureConnected();
                String act = parts.length > 1 ? parts[1].toLowerCase(Locale.ROOT).trim() : "back";
                boolean ok = true;
                synchronized (lock) {
                    switch (act) {
                        case "back": ok = uiAuto.performGlobalAction(1); break;
                        case "home": ok = uiAuto.performGlobalAction(2); break;
                        case "recents":
                        case "recent":
                        case "apps": ok = uiAuto.performGlobalAction(3); break;
                        case "notif":
                        case "notifications": ok = uiAuto.performGlobalAction(4); break;
                        case "qs":
                        case "quicksettings": ok = uiAuto.performGlobalAction(5); break;
                        case "power": ok = uiAuto.performGlobalAction(6); break;
                        case "lock": ok = uiAuto.performGlobalAction(8); break;
                        case "wake":
                            mWakeDevice.invoke(ic);
                            execCmdAsync(new String[]{"/system/bin/cmd", "window", "dismiss-keyguard"});
                            ok = true;
                            break;
                        case "sleep": mSleepDevice.invoke(ic); ok = true; break;
                        case "enter":
                        case "search":
                        case "done": ok = (Boolean) mSendKey.invoke(ic, 66, 0); break;
                        case "del":
                        case "backspace": ok = (Boolean) mSendKey.invoke(ic, 67, 0); break;
                        case "paste": ok = (Boolean) mSendKey.invoke(ic, 279, 0); break;
                        default:
                            if (act.matches("\\d+")) {
                                ok = (Boolean) mSendKey.invoke(ic, Integer.parseInt(act), 0);
                            } else {
                                return "ERR\tUnknown NAV action: " + act;
                            }
                    }
                }
                cachedTreeEventSeq = -1L;
                long dt = System.currentTimeMillis() - t0;
                return (ok ? "OK" : "ERR") + "\tNAV " + act + " in " + dt + "ms";
            }

            case "WAIT":
            case "WAIT_GONE": {
                boolean wantGone = "WAIT_GONE".equals(cmd);
                String query = parts.length > 1 ? parts[1].trim() : "";
                long timeoutMs = parts.length > 2 && !parts[2].isEmpty() ? Long.parseLong(parts[2]) : 5000L;
                String[] candidates = query.split("\\|\\|");
                long startWait = System.currentTimeMillis();
                int pollCount = 0;
                while (true) {
                    List<UiItem> items = refreshTree(false, pollCount > 0 && (pollCount % 5 == 0));
                    UiItem hit = null;
                    for (String cand : candidates) {
                        hit = findBestItem(items, cand.trim());
                        if (hit != null) break;
                    }
                    if (!wantGone && hit != null) {
                        long dt = System.currentTimeMillis() - t0;
                        return "OK\tFOUND [#" + hit.index + "] (" + hit.cx + "," + hit.cy + ") in " + dt + "ms";
                    }
                    if (wantGone && hit == null) {
                        long dt = System.currentTimeMillis() - t0;
                        return "OK\tGONE \"" + query + "\" in " + dt + "ms";
                    }
                    long elapsed = System.currentTimeMillis() - startWait;
                    if (elapsed >= timeoutMs) {
                        return "ERR\tTimeout (" + timeoutMs + "ms) waiting for " + (wantGone ? "gone: " : "") + "\"" + query + "\"";
                    }
                    pollCount++;
                    waitForNextUiEvent(Math.min(18L, timeoutMs - elapsed));
                }
            }

            case "STREAM": {
                ensureConnected();
                String sub = parts.length > 1 ? parts[1].toUpperCase(Locale.ROOT).trim() : "STATUS";
                if (sub.equals("START") || sub.equals("ON")) {
                    int interval = parts.length > 2 && !parts[2].isEmpty() ? Integer.parseInt(parts[2]) : 70;
                    float scale = parts.length > 3 && !parts[3].isEmpty() ? Float.parseFloat(parts[3]) : 0.5f;
                    int quality = parts.length > 4 && !parts[4].isEmpty() ? Integer.parseInt(parts[4]) : 60;
                    startStreamer(interval, scale, quality);
                    return "OK	STREAM started (interval=" + streamIntervalMs + "ms, scale=" + streamScale + ", q=" + streamQuality + ")";
                } else if (sub.equals("STOP") || sub.equals("OFF")) {
                    stopStreamer();
                    return "OK	STREAM stopped (total_frames=" + streamFramesCaptured + ")";
                } else {
                    long age = streamLatestTs > 0 ? (System.currentTimeMillis() - streamLatestTs) : -1L;
                    return "OK	STREAM active=" + streamActive.get() + "	frames=" + streamFramesCaptured
                        + "	latest_age_ms=" + age + "	dims=" + streamTargetW + "x" + streamTargetH;
                }
            }

            case "LIVE":
            case "FRAME": {
                // Return latest frame directly from resident RAM circular buffer (0ms wait!)
                ensureConnected();
                if (!streamActive.get() || streamLatestJpeg == null) {
                    startStreamer(70, 0.5f, 60);
                    // Wait up to 180ms for first frame if just started
                    long deadline = SystemClock.uptimeMillis() + 200L;
                    while (streamLatestJpeg == null && SystemClock.uptimeMillis() < deadline) {
                        try { Thread.sleep(15L); } catch (Throwable ignored) {}
                    }
                }

                byte[] frame = streamLatestJpeg;
                if (frame == null) {
                    // Fallback to direct shot
                    return handleCommand("SHOT	0.5	0	60	0	" + (parts.length > 1 ? parts[1] : ""));
                }

                String savePath = parts.length > 1 ? parts[1].trim() : "";
                long frameAge = System.currentTimeMillis() - streamLatestTs;
                long dt = System.currentTimeMillis() - t0;

                if (!savePath.isEmpty() && (savePath.startsWith("/sdcard/") || savePath.startsWith("/storage/") || savePath.startsWith("/data/local/tmp/"))) {
                    try {
                        File outF = new File(savePath);
                        File parent = outF.getParentFile();
                        if (parent != null && !parent.exists()) parent.mkdirs();
                        try (FileOutputStream fos = new FileOutputStream(outF)) {
                            fos.write(frame);
                        }
                        registerEphemeralFile(savePath);
                        return "OK	LIVE frame saved to " + savePath + " (" + streamTargetW + "x" + streamTargetH
                            + " (real:" + streamRealW + "x" + streamRealH + "), " + frame.length + " bytes, age=" + frameAge + "ms) in " + dt + "ms";
                    } catch (Throwable ignored) {}
                }

                String b64 = Base64.getEncoder().encodeToString(frame);
                return "OK_SHOT	" + streamTargetW + "x" + streamTargetH + " (real:" + streamRealW + "x" + streamRealH + ")	"
                    + frame.length + "	" + dt + "ms	" + b64;
            }

            case "SHOT": {
                ensureConnected();
                float scale = parts.length > 1 && !parts[1].isEmpty() ? Float.parseFloat(parts[1]) : 0.5f;
                boolean som = parts.length > 2 && "1".equals(parts[2]);
                int quality = parts.length > 3 && !parts[3].isEmpty() ? Integer.parseInt(parts[3]) : 72;
                boolean grid = parts.length > 4 && "1".equals(parts[4]);
                String savePath = parts.length > 5 ? parts[5].trim() : "";

                // Purge any previous temporary screenshot BEFORE taking a new one so storage never accumulates!
                cleanEphemeralNow(true);
                ensureEphemeralDir();

                List<UiItem> items = som ? refreshTree(false, false) : lastItems;
                Bitmap rawBmp = uiAuto.takeScreenshot();
                if (rawBmp == null) return "ERR\ttakeScreenshot returned null";

                int origW = rawBmp.getWidth();
                int origH = rawBmp.getHeight();
                cachedScreenW = origW;
                cachedScreenH = origH;
                lastScreenDimCheck = SystemClock.uptimeMillis();
                int targetW = Math.max(100, Math.round(origW * scale));
                int targetH = Math.max(100, Math.round(origH * scale));

                Bitmap workBmp = rawBmp;
                if (Math.abs(scale - 1.0f) > 0.01f) {
                    workBmp = Bitmap.createScaledBitmap(rawBmp, targetW, targetH, true);
                    rawBmp.recycle();
                }
                if (som || grid) {
                    Bitmap mutableBmp = workBmp.copy(Bitmap.Config.ARGB_8888, true);
                    workBmp.recycle();
                    workBmp = mutableBmp;
                }

                Canvas canvas = (som || grid) ? new Canvas(workBmp) : null;
                float px = Math.max(2f, Math.round(4f * scale));

                if (grid && canvas != null) {
                    Paint gridPaint = new Paint();
                    gridPaint.setAntiAlias(false);
                    gridPaint.setStyle(Paint.Style.STROKE);
                    gridPaint.setStrokeWidth(Math.max(1f, 2f * scale));
                    gridPaint.setColor(0x9000E5FF);

                    Paint subGridPaint = new Paint();
                    subGridPaint.setAntiAlias(false);
                    subGridPaint.setStyle(Paint.Style.STROKE);
                    subGridPaint.setStrokeWidth(1f);
                    subGridPaint.setColor(0x45FFFFFF);

                    Paint bgPaint = new Paint();
                    bgPaint.setAntiAlias(false);
                    bgPaint.setStyle(Paint.Style.FILL);
                    bgPaint.setColor(0xCC000000);

                    Paint fgPaint = new Paint();
                    fgPaint.setAntiAlias(false);
                    fgPaint.setStyle(Paint.Style.FILL);
                    fgPaint.setColor(0xFF00E5FF);

                    for (int gx = 100; gx < origW; gx += 100) {
                        float sx = gx * scale;
                        canvas.drawLine(sx, 0, sx, targetH, (gx % 200 == 0) ? gridPaint : subGridPaint);
                    }
                    for (int gy = 100; gy < origH; gy += 100) {
                        float sy = gy * scale;
                        canvas.drawLine(0, sy, targetW, sy, (gy % 200 == 0) ? gridPaint : subGridPaint);
                    }
                    for (int gy = 200; gy < origH; gy += 400) {
                        for (int gx = 200; gx < origW; gx += 400) {
                            drawNumberTag(canvas, gx + "," + gy, gx * scale + 2f, gy * scale + 2f, Math.max(1.5f, px * 0.85f), bgPaint, fgPaint, targetW);
                        }
                    }
                }

                if (som && items != null && canvas != null) {
                    Paint boxPaint = new Paint();
                    boxPaint.setAntiAlias(false);
                    boxPaint.setStyle(Paint.Style.STROKE);
                    boxPaint.setStrokeWidth(Math.max(2f, 3f * scale));

                    Paint bgPaint = new Paint();
                    bgPaint.setAntiAlias(false);
                    bgPaint.setStyle(Paint.Style.FILL);

                    Paint fgPaint = new Paint();
                    fgPaint.setAntiAlias(false);
                    fgPaint.setStyle(Paint.Style.FILL);
                    fgPaint.setColor(0xFFFFFFFF);

                    for (UiItem item : items) {
                        if (!item.clickable && !item.editable && !item.scrollable && !item.checkable) continue;
                        int color = item.editable ? 0xFF00E5FF : (item.clickable ? 0xFFFF1744 : 0xFFFFEA00);
                        boxPaint.setColor(color);
                        bgPaint.setColor((color & 0x00FFFFFF) | 0xE0000000);

                        float l = item.left * scale;
                        float t = item.top * scale;
                        float r = item.right * scale;
                        float b = item.bottom * scale;
                        canvas.drawRect(l, t, r, b, boxPaint);
                        drawNumberTag(canvas, String.valueOf(item.index), l, t, px, bgPaint, fgPaint, targetW);
                    }
                }

                byte[] jpegBytes;
                synchronized (visionLock) {
                    sharedBaos.reset();
                    workBmp.compress(Bitmap.CompressFormat.JPEG, quality, sharedBaos);
                    jpegBytes = sharedBaos.toByteArray();
                    sharedBaos.reset();
                }
                workBmp.recycle();

                // If savePath can be written directly by daemon (e.g. /sdcard/...), write directly in RAM->disk without Base64 IPC overhead!
                if (!savePath.isEmpty() && (savePath.startsWith("/sdcard/") || savePath.startsWith("/storage/") || savePath.startsWith("/data/local/tmp/"))) {
                    try {
                        File outF = new File(savePath);
                        File parent = outF.getParentFile();
                        if (parent != null && !parent.exists()) parent.mkdirs();
                        try (FileOutputStream fos = new FileOutputStream(outF)) {
                            fos.write(jpegBytes);
                        }
                        registerEphemeralFile(savePath);
                        long dt = System.currentTimeMillis() - t0;
                        return "OK\tSHOT saved to " + savePath + " (" + targetW + "x" + targetH + " (real:" + origW + "x" + origH + "), "
                            + jpegBytes.length + " bytes, som=" + som + ", grid=" + grid + ", auto-purge=15s/done) in " + dt + "ms";
                    } catch (Throwable ignored) {}
                }

                String b64Jpeg = Base64.getEncoder().encodeToString(jpegBytes);
                long dt = System.currentTimeMillis() - t0;
                return "OK_SHOT\t" + targetW + "x" + targetH + " (real:" + origW + "x" + origH + ")\t" + jpegBytes.length + "\t" + dt + "ms\t" + b64Jpeg;
            }

            default:
                return "ERR\tUnknown command: " + cmd;
        }
    }

    private static void handleClient(Socket client) {
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8))
        ) {
            client.setTcpNoDelay(true);
            client.setSoTimeout(60_000);
            String req;
            while ((req = in.readLine()) != null) {
                if (req.isEmpty()) continue;
                String resp;
                try {
                    resp = handleCommand(req);
                } catch (Throwable t) {
                    resp = "ERR\t" + t.getClass().getSimpleName() + ": " + t.getMessage();
                }
                out.write(resp);
                out.write("\n<<<END>>>\n");
                out.flush();
            }
        } catch (Throwable ignored) {
        } finally {
            try { client.close(); } catch (Throwable ignored) {}
        }
    }

    public static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(new Thread(SatsetDaemon::disconnectInternal));
        startEphemeralReaperThread();

        try (ServerSocket server = new ServerSocket()) {
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress("127.0.0.1", PORT), 64);
            System.out.println("SatsetDaemon v4 (Zero-Latency Engine & Ephemeral Auto-Reaper) listening on 127.0.0.1:" + PORT);

            try { ensureConnected(); } catch (Throwable t) { t.printStackTrace(System.out); }

            while (true) {
                final Socket client = server.accept();
                Thread worker = new Thread(() -> handleClient(client), "satset-worker");
                worker.setDaemon(true);
                worker.start();
            }
        } catch (Throwable t) {
            t.printStackTrace(System.err);
            System.exit(2);
        }
    }
}
