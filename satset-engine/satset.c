/*
 * SATSET v4 — Sub-Millisecond Native ARM64 C Client for SatsetDaemon v4 (127.0.0.1:19888).
 * Eliminates ~45ms Python interpreter startup overhead on every invocation.
 */
#define _GNU_SOURCE
#include <arpa/inet.h>
#include <ctype.h>
#include <errno.h>
#include <fcntl.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <sys/types.h>
#include <unistd.h>

#define DAEMON_HOST "127.0.0.1"
#define DAEMON_PORT 19888
#define DEFAULT_SHOT_PATH "/sdcard/.satset_tmp/shot.jpg"

static const char B64_TABLE[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

static char *b64_encode(const unsigned char *src, size_t len) {
    size_t out_len = 4 * ((len + 2) / 3);
    char *out = (char *)malloc(out_len + 1);
    if (!out) return NULL;
    size_t i = 0, j = 0;
    while (i < len) {
        unsigned int a = i < len ? src[i++] : 0;
        unsigned int b = i < len ? src[i++] : 0;
        unsigned int c = i < len ? src[i++] : 0;
        unsigned int triple = (a << 16) | (b << 8) | c;
        out[j++] = B64_TABLE[(triple >> 18) & 0x3F];
        out[j++] = B64_TABLE[(triple >> 12) & 0x3F];
        out[j++] = B64_TABLE[(triple >> 6) & 0x3F];
        out[j++] = B64_TABLE[triple & 0x3F];
    }
    int mod = len % 3;
    if (mod == 1) {
        out[out_len - 1] = '=';
        out[out_len - 2] = '=';
    } else if (mod == 2) {
        out[out_len - 1] = '=';
    }
    out[out_len] = '\0';
    return out;
}

static unsigned char *b64_decode(const char *src, size_t len, size_t *out_len) {
    static int dtable[256];
    static int init = 0;
    if (!init) {
        for (int i = 0; i < 256; i++) dtable[i] = -1;
        for (int i = 0; i < 64; i++) dtable[(unsigned char)B64_TABLE[i]] = i;
        init = 1;
    }
    unsigned char *out = (unsigned char *)malloc(len);
    if (!out) return NULL;
    size_t j = 0;
    unsigned int val = 0;
    int valb = -8;
    for (size_t i = 0; i < len; i++) {
        int c = dtable[(unsigned char)src[i]];
        if (c == -1) continue;
        val = (val << 6) + c;
        valb += 6;
        if (valb >= 0) {
            out[j++] = (unsigned char)((val >> valb) & 0xFF);
            valb -= 8;
        }
    }
    *out_len = j;
    return out;
}

static int is_int_str(const char *s) {
    if (!s || !*s) return 0;
    if (*s == '-') s++;
    if (!*s) return 0;
    while (*s) {
        if (!isdigit((unsigned char)*s)) return 0;
        s++;
    }
    return 1;
}

static int connect_daemon(int timeout_ms) {
    int fd = socket(AF_INET, SOCK_STREAM, 0);
    if (fd < 0) return -1;
    int one = 1;
    setsockopt(fd, IPPROTO_TCP, TCP_NODELAY, &one, sizeof(one));

    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_port = htons(DAEMON_PORT);
    inet_pton(AF_INET, DAEMON_HOST, &addr.sin_addr);

    struct timeval tv;
    tv.tv_sec = timeout_ms / 1000;
    tv.tv_usec = (timeout_ms % 1000) * 1000;
    setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof(tv));
    tv.tv_sec = 60;
    tv.tv_usec = 0;
    setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));

    if (connect(fd, (struct sockaddr *)&addr, sizeof(addr)) < 0) {
        close(fd);
        return -1;
    }
    return fd;
}

static char *send_request_fd(int fd, const char *payload) {
    size_t plen = strlen(payload);
    char *send_buf = (char *)malloc(plen + 2);
    if (!send_buf) return NULL;
    memcpy(send_buf, payload, plen);
    send_buf[plen] = '\n';
    send_buf[plen + 1] = '\0';

    ssize_t sent = 0;
    size_t total_to_send = plen + 1;
    while ((size_t)sent < total_to_send) {
        ssize_t n = write(fd, send_buf + sent, total_to_send - sent);
        if (n <= 0) {
            free(send_buf);
            return NULL;
        }
        sent += n;
    }
    free(send_buf);

    size_t cap = 65536;
    size_t len = 0;
    char *buf = (char *)malloc(cap);
    if (!buf) return NULL;

    const char *marker = "\n<<<END>>>\n";
    const size_t mlen = 11;

    while (1) {
        if (len + 16384 >= cap) {
            cap *= 2;
            char *nb = (char *)realloc(buf, cap);
            if (!nb) {
                free(buf);
                return NULL;
            }
            buf = nb;
        }
        ssize_t n = read(fd, buf + len, cap - len - 1);
        if (n <= 0) break;
        len += (size_t)n;
        buf[len] = '\0';

        size_t search_offset = len > (size_t)n + mlen ? len - (size_t)n - mlen : 0;
        char *found = strstr(buf + search_offset, marker);
        if (found) {
            *found = '\0';
            break;
        }
    }
    return buf;
}

static int start_or_restart_daemon(int force_restart) {
    const char *home = getenv("HOME");
    char jar_local[512];
    snprintf(jar_local, sizeof(jar_local), "%s/.local/share/satset/satset-daemon.jar", home ? home : "/data/data/com.termux/files/home");

    if (force_restart) {
        /* Try zero-ADB HOT_RELOAD first if daemon is already alive! */
        int fd = connect_daemon(200);
        if (fd >= 0) {
            mkdir("/sdcard/.satset_tmp", 0777);
            char cp_cmd[1024];
            snprintf(cp_cmd, sizeof(cp_cmd), "cp -f '%s' /sdcard/.satset_tmp/satset-daemon.jar 2>/dev/null", jar_local);
            if (system(cp_cmd) == 0 && access("/sdcard/.satset_tmp/satset-daemon.jar", R_OK) == 0) {
                char *resp = send_request_fd(fd, "HOT_RELOAD\t/sdcard/.satset_tmp/satset-daemon.jar");
                close(fd);
                if (resp && strncmp(resp, "OK", 2) == 0) {
                    free(resp);
                    for (int i = 0; i < 60; i++) {
                        usleep(40000);
                        int nfd = connect_daemon(150);
                        if (nfd >= 0) {
                            char *p = send_request_fd(nfd, "PING");
                            close(nfd);
                            if (p && strncmp(p, "OK", 2) == 0) {
                                free(p);
                                return 1;
                            }
                            free(p);
                        }
                    }
                }
                free(resp);
            }
            /* Fallback if old daemon didn't support HOT_RELOAD */
            int qfd = connect_daemon(150);
            if (qfd >= 0) {
                char *qr = send_request_fd(qfd, "QUIT");
                free(qr);
                close(qfd);
                usleep(100000);
            }
        }
    }

    char cmd[2048];
    snprintf(cmd, sizeof(cmd),
        "adb push '%s' /data/local/tmp/satset-daemon.jar >/dev/null 2>&1; "
        "adb shell 'chmod 444 /data/local/tmp/satset-daemon.jar 2>/dev/null; "
        "rm -f /data/local/tmp/satset.log 2>/dev/null; "
        "ANDROID_DATA=/data/local/tmp CLASSPATH=/system/framework/uiautomator.jar:/data/local/tmp/satset-daemon.jar "
        "nohup app_process /system/bin SatsetDaemon >/dev/null 2>&1 </dev/null &' >/dev/null 2>&1",
        jar_local);
    system(cmd);

    for (int i = 0; i < 60; i++) {
        int fd = connect_daemon(150);
        if (fd >= 0) {
            char *resp = send_request_fd(fd, "PING");
            close(fd);
            if (resp && strncmp(resp, "OK", 2) == 0) {
                free(resp);
                return 1;
            }
            free(resp);
        }
        usleep(40000);
    }
    return 0;
}

static char *daemon_request(const char *payload) {
    int fd = connect_daemon(300);
    if (fd < 0) {
        if (!start_or_restart_daemon(0)) {
            return strdup("ERR\tFailed to connect or start SatsetDaemon v4 on 127.0.0.1:19888");
        }
        fd = connect_daemon(500);
        if (fd < 0) {
            return strdup("ERR\tFailed to connect to SatsetDaemon v4 on 127.0.0.1:19888");
        }
    }
    char *resp = send_request_fd(fd, payload);
    close(fd);
    if (!resp) {
        if (!start_or_restart_daemon(0)) {
            return strdup("ERR\tLost connection to SatsetDaemon v4");
        }
        fd = connect_daemon(500);
        if (fd < 0) return strdup("ERR\tFailed to reconnect to SatsetDaemon v4");
        resp = send_request_fd(fd, payload);
        close(fd);
    }
    return resp ? resp : strdup("ERR\tEmpty response from SatsetDaemon v4");
}

static void join_args(char *dst, size_t dst_size, int argc, char **argv, int start_idx, const char *sep) {
    dst[0] = '\0';
    size_t pos = 0;
    size_t sep_len = strlen(sep);
    for (int i = start_idx; i < argc; i++) {
        if (i > start_idx && pos + sep_len < dst_size - 1) {
            memcpy(dst + pos, sep, sep_len);
            pos += sep_len;
        }
        size_t alen = strlen(argv[i]);
        if (pos + alen >= dst_size - 1) alen = dst_size - 1 - pos;
        memcpy(dst + pos, argv[i], alen);
        pos += alen;
        dst[pos] = '\0';
    }
}

static int map_game_keycode(const char *kname, int *out_code, int *out_src) {
    struct { const char *name; int code; int src; } table[] = {
        {"w", 51, 0x101}, {"a", 29, 0x101}, {"s", 47, 0x101}, {"d", 32, 0x101},
        {"e", 33, 0x101}, {"f", 34, 0x101}, {"q", 45, 0x101}, {"r", 46, 0x101},
        {"c", 31, 0x101}, {"x", 52, 0x101}, {"z", 54, 0x101}, {"space", 62, 0x101},
        {"shift", 59, 0x101}, {"ctrl", 113, 0x101}, {"tab", 61, 0x101}, {"esc", 111, 0x101},
        {"enter", 66, 0x101}, {"up", 19, 0x101}, {"down", 20, 0x101}, {"left", 21, 0x101},
        {"right", 22, 0x101}, {"btn_a", 96, 0x401}, {"btn_b", 97, 0x401}, {"btn_x", 99, 0x401},
        {"btn_y", 100, 0x401}, {"l1", 102, 0x401}, {"r1", 103, 0x401}, {"l2", 104, 0x401},
        {"r2", 105, 0x401}, {"start", 108, 0x401}, {"select", 109, 0x401},
        {NULL, 0, 0}
    };
    for (int i = 0; table[i].name != NULL; i++) {
        if (strcasecmp(kname, table[i].name) == 0) {
            *out_code = table[i].code;
            *out_src = table[i].src;
            return 1;
        }
    }
    if (is_int_str(kname)) {
        *out_code = atoi(kname);
        *out_src = 0x101;
        return 1;
    }
    return 0;
}

static int parse_xy_args(int argc, char **argv, int idx, char *x_out, size_t x_sz, char *y_out, size_t y_sz) {
    if (idx >= argc) return 0;
    if (idx + 1 < argc && is_int_str(argv[idx]) && is_int_str(argv[idx + 1])) {
        snprintf(x_out, x_sz, "%s", argv[idx]);
        snprintf(y_out, y_sz, "%s", argv[idx + 1]);
        return 2;
    }
    const char *comma = strchr(argv[idx], ',');
    if (comma) {
        size_t xlen = (size_t)(comma - argv[idx]);
        if (xlen > 0 && xlen < x_sz) {
            memcpy(x_out, argv[idx], xlen);
            x_out[xlen] = '\0';
            snprintf(y_out, y_sz, "%s", comma + 1);
            if (is_int_str(x_out) && is_int_str(y_out)) return 1;
        }
    }
    return 0;
}

int main(int argc, char **argv) {
    if (argc < 2 || strcmp(argv[1], "-h") == 0 || strcmp(argv[1], "--help") == 0 || strcmp(argv[1], "help") == 0) {
        printf(
            "SATSET v4 — Ultra-Low-Latency Native C CLI + Event-Driven Daemon & Auto-Reaper\n"
            "Usage: satset <command> [args...]\n"
            "  Core UI:    do | ui | tap | longtap | set | tap-set | search | scroll | swipe | wait | open | share | shot | done | clean\n"
            "  Real-Time:  joy | hold | drag | mash | aim | combo | key-hold | game-shot | pixel | scan-color | tap-color | autopilot\n"
            "  Confirm:    done (or end `satset do ... \"done\"` to capture final app UI, purge temp files, and return to Termux)\n"
        );
        return 0;
    }

    const char *sub = argv[1];

    /* 1. Single-Roundtrip In-Daemon DO Pipeline */
    if (strcasecmp(sub, "do") == 0 || strcasecmp(sub, "chain") == 0 || strcasecmp(sub, "run") == 0 || strcasecmp(sub, "batch") == 0) {
        int return_termux = 0;
        size_t cap = 16384;
        char *payload = (char *)malloc(cap);
        strcpy(payload, "DO\t0");
        size_t pos = 4;

        for (int i = 2; i < argc; i++) {
            const char *s = argv[i];
            if (strcasecmp(s, "--done") == 0 || strcasecmp(s, "--confirm") == 0 || strcasecmp(s, "--termux") == 0
                || strcasecmp(s, "done") == 0 || strcasecmp(s, "confirm") == 0 || strcasecmp(s, "termux") == 0
                || strcasecmp(s, "return-termux") == 0 || strcasecmp(s, "open termux") == 0) {
                return_termux = 1;
                continue;
            }
            size_t slen = strlen(s);
            if (pos + slen + 4 >= cap) {
                cap = (pos + slen + 4) * 2;
                payload = (char *)realloc(payload, cap);
            }
            payload[pos++] = '\t';
            memcpy(payload + pos, s, slen);
            pos += slen;
            payload[pos] = '\0';
        }
        if (return_termux) payload[3] = '1';

        char *resp = daemon_request(payload);
        puts(resp);
        free(resp);
        free(payload);
        return 0;
    }

    /* 2. Daemon Lifecycle & Instant Termux Return / Cleanup */
    if (strcasecmp(sub, "status") == 0 || strcasecmp(sub, "ping") == 0) {
        char *r = daemon_request("PING");
        puts(r);
        free(r);
        return 0;
    }
    if (strcasecmp(sub, "start") == 0) {
        int ok = start_or_restart_daemon(0);
        puts(ok ? "OK\tSatsetDaemon v4 running" : "ERR\tFailed to start SatsetDaemon v4");
        return ok ? 0 : 1;
    }
    if (strcasecmp(sub, "restart") == 0 || strcasecmp(sub, "reload") == 0) {
        int ok = start_or_restart_daemon(1);
        puts(ok ? "OK\tSatsetDaemon v4 hot-reloaded" : "ERR\tFailed to restart SatsetDaemon v4");
        return ok ? 0 : 1;
    }
    if (strcasecmp(sub, "stop") == 0) {
        int fd = connect_daemon(200);
        if (fd >= 0) {
            char *r = send_request_fd(fd, "QUIT");
            free(r);
            close(fd);
        }
        puts("OK\tstopped");
        return 0;
    }
    if (strcasecmp(sub, "done") == 0 || strcasecmp(sub, "confirm") == 0 || strcasecmp(sub, "termux") == 0
        || strcasecmp(sub, "return-termux") == 0 || strcasecmp(sub, "finish") == 0) {
        char *r = daemon_request("TERMUX");
        puts(r);
        free(r);
        return 0;
    }
    if (strcasecmp(sub, "clean") == 0 || strcasecmp(sub, "purge") == 0) {
        char *r = daemon_request("CLEAN");
        puts(r);
        free(r);
        return 0;
    }
    if (strcasecmp(sub, "sh") == 0 || strcasecmp(sub, "shell") == 0 || strcasecmp(sub, "exec") == 0) {
        if (argc < 3) {
            puts("ERR\tUsage: satset sh \"<command>\"");
            return 1;
        }
        char cmd_buf[16384];
        join_args(cmd_buf, sizeof(cmd_buf), argc, argv, 2, " ");
        char *b64 = b64_encode((const unsigned char *)cmd_buf, strlen(cmd_buf));
        size_t req_len = strlen(b64) + 16;
        char *req = (char *)malloc(req_len);
        snprintf(req, req_len, "SHELL\t%s", b64);
        char *r = daemon_request(req);
        puts(r);
        free(r);
        free(req);
        free(b64);
        return 0;
    }

    /* 3. UI Hierarchy Inspection */
    if (strcasecmp(sub, "ui") == 0 || strcasecmp(sub, "dump") == 0 || strcasecmp(sub, "ls") == 0 || strcasecmp(sub, "find") == 0) {
        int inc_all = 0;
        char filt[2048];
        filt[0] = '\0';
        for (int i = 2; i < argc; i++) {
            if (strcmp(argv[i], "--all") == 0 || strcmp(argv[i], "-a") == 0) {
                inc_all = 1;
            } else {
                if (filt[0] != '\0') strncat(filt, " ", sizeof(filt) - strlen(filt) - 1);
                strncat(filt, argv[i], sizeof(filt) - strlen(filt) - 1);
            }
        }
        char req[2200];
        snprintf(req, sizeof(req), "UI\t%d\t%s", inc_all, filt);
        char *r = daemon_request(req);
        puts(r);
        free(r);
        return 0;
    }

    /* 4. Tap / Long-Tap */
    if (strcasecmp(sub, "tap") == 0 || strcasecmp(sub, "click") == 0 || strcasecmp(sub, "t") == 0
        || strcasecmp(sub, "longtap") == 0 || strcasecmp(sub, "longclick") == 0) {
        const char *verb = (strncasecmp(sub, "long", 4) == 0) ? "LONGTAP" : "TAP";
        int wait_ms = 2000;
        char target[2048];
        target[0] = '\0';
        char *cleaned[64];
        int c_count = 0;

        for (int i = 2; i < argc; i++) {
            if ((strcmp(argv[i], "--wait") == 0 || strcmp(argv[i], "-w") == 0) && i + 1 < argc) {
                wait_ms = atoi(argv[++i]);
            } else if (strcmp(argv[i], "--nowait") == 0) {
                wait_ms = 0;
            } else if (c_count < 64) {
                cleaned[c_count++] = argv[i];
            }
        }

        char req[2400];
        if (c_count == 2 && is_int_str(cleaned[0]) && is_int_str(cleaned[1])) {
            snprintf(req, sizeof(req), "%s\t%s\t%s", verb, cleaned[0], cleaned[1]);
        } else if (c_count == 1 && strchr(cleaned[0], ',') != NULL) {
            char x[64], y[64];
            if (parse_xy_args(1, cleaned, 0, x, sizeof(x), y, sizeof(y)) == 1) {
                snprintf(req, sizeof(req), "%s\t%s\t%s", verb, x, y);
            } else {
                snprintf(req, sizeof(req), "%s\t%s\t%d", verb, cleaned[0], wait_ms);
            }
        } else {
            for (int i = 0; i < c_count; i++) {
                if (i > 0) strncat(target, " ", sizeof(target) - strlen(target) - 1);
                strncat(target, cleaned[i], sizeof(target) - strlen(target) - 1);
            }
            if (is_int_str(target)) {
                snprintf(req, sizeof(req), "%s\t#%s\t%d", verb, target, wait_ms);
            } else {
                snprintf(req, sizeof(req), "%s\t%s\t%d", verb, target, wait_ms);
            }
        }
        char *r = daemon_request(req);
        puts(r);
        free(r);
        return 0;
    }

    /* 5. Atomic Tap + Set (tap-set) */
    if (strcasecmp(sub, "tap-set") == 0 || strcasecmp(sub, "tapset") == 0) {
        if (argc < 4) {
            puts("ERR\tUsage: satset tap-set <target> <text> [--enter]");
            return 1;
        }
        const char *target = argv[2];
        int press_enter = 0;
        char text_buf[8192];
        text_buf[0] = '\0';
        for (int i = 3; i < argc; i++) {
            if (strcmp(argv[i], "--enter") == 0 || strcmp(argv[i], "-e") == 0) {
                press_enter = 1;
            } else {
                if (text_buf[0] != '\0') strncat(text_buf, " ", sizeof(text_buf) - strlen(text_buf) - 1);
                strncat(text_buf, argv[i], sizeof(text_buf) - strlen(text_buf) - 1);
            }
        }
        char *b64 = b64_encode((const unsigned char *)text_buf, strlen(text_buf));
        size_t req_sz = strlen(target) + strlen(b64) + 64;
        char *req = (char *)malloc(req_sz);
        snprintf(req, req_sz, "TAP_SET\t%s\t%s\t%d", target, b64, press_enter);
        char *r = daemon_request(req);
        puts(r);
        free(r);
        free(req);
        free(b64);
        return 0;
    }

    /* 6. Instant Text Injection (set / search / type / append) */
    if (strcasecmp(sub, "set") == 0 || strcasecmp(sub, "input") == 0 || strcasecmp(sub, "write") == 0
        || strcasecmp(sub, "append") == 0 || strcasecmp(sub, "type") == 0 || strcasecmp(sub, "search") == 0) {
        int append_flag = (strcasecmp(sub, "append") == 0) ? 1 : 0;
        int press_enter = (strcasecmp(sub, "search") == 0) ? 1 : 0;
        int wait_ms = 2500;
        const char *target = "";
        char *cleaned[128];
        int c_count = 0;

        for (int i = 2; i < argc; i++) {
            if ((strcmp(argv[i], "--wait") == 0 || strcmp(argv[i], "-w") == 0) && i + 1 < argc) {
                wait_ms = atoi(argv[++i]);
            } else if ((strcmp(argv[i], "--target") == 0 || strcmp(argv[i], "-t") == 0) && i + 1 < argc) {
                target = argv[++i];
            } else if (strcmp(argv[i], "--enter") == 0 || strcmp(argv[i], "--submit") == 0 || strcmp(argv[i], "-e") == 0) {
                press_enter = 1;
            } else if (strcmp(argv[i], "--append") == 0) {
                append_flag = 1;
            } else if (c_count < 128) {
                cleaned[c_count++] = argv[i];
            }
        }

        int start_txt = 0;
        if (target[0] == '\0' && c_count >= 2 && (
            strncmp(cleaned[0], "#", 1) == 0 || strncmp(cleaned[0], "id:", 3) == 0 ||
            strncmp(cleaned[0], "desc:", 5) == 0 || strncmp(cleaned[0], "text:", 5) == 0 ||
            strncmp(cleaned[0], "class:", 6) == 0)) {
            target = cleaned[0];
            start_txt = 1;
        }

        char text_val[16384];
        text_val[0] = '\0';
        for (int i = start_txt; i < c_count; i++) {
            if (i > start_txt) strncat(text_val, " ", sizeof(text_val) - strlen(text_val) - 1);
            strncat(text_val, cleaned[i], sizeof(text_val) - strlen(text_val) - 1);
        }

        char *b64 = b64_encode((const unsigned char *)text_val, strlen(text_val));
        size_t req_sz = strlen(target) + strlen(b64) + 64;
        char *req = (char *)malloc(req_sz);
        snprintf(req, req_sz, "SET_B64\t%s\t%s\t%d\t%d", target, b64, wait_ms, append_flag);
        char *resp = daemon_request(req);
        free(req);
        free(b64);

        if (press_enter) {
            char *er = daemon_request("NAV\tenter");
            printf("%s + %s\n", resp, er);
            free(er);
        } else {
            puts(resp);
        }
        free(resp);
        return 0;
    }

    /* 7. Open / Launch / Share / Shopee-Cart */
    if (strcasecmp(sub, "open") == 0 || strcasecmp(sub, "launch") == 0 || strcasecmp(sub, "app") == 0) {
        if (argc < 3) {
            puts("ERR\tUsage: satset open <app_or_url>");
            return 1;
        }
        char target[2048];
        join_args(target, sizeof(target), argc, argv, 2, " ");
        char req[2200];
        snprintf(req, sizeof(req), "OPEN\t%s", target);
        char *r = daemon_request(req);
        puts(r);
        free(r);
        return 0;
    }

    if (strcasecmp(sub, "share") == 0) {
        if (argc < 3) {
            puts("ERR\tUsage: satset share <file_path> [app]");
            return 1;
        }
        const char *fp = argv[2];
        const char *app = argc > 3 ? argv[3] : "";
        char req[2200];
        snprintf(req, sizeof(req), "SHARE\t%s\t%s", fp, app);
        char *r = daemon_request(req);
        puts(r);
        free(r);
        return 0;
    }

    if (strcasecmp(sub, "shopee-cart") == 0) {
        int max_p = 85000, min_p = 5000, ret_t = 0;
        char kw[1024];
        kw[0] = '\0';
        for (int i = 2; i < argc; i++) {
            if (strcmp(argv[i], "--max") == 0 && i + 1 < argc) max_p = atoi(argv[++i]);
            else if (strcmp(argv[i], "--min") == 0 && i + 1 < argc) min_p = atoi(argv[++i]);
            else if (strcmp(argv[i], "--done") == 0 || strcmp(argv[i], "--confirm") == 0 || strcmp(argv[i], "--termux") == 0) ret_t = 1;
            else {
                if (kw[0] != '\0') strncat(kw, " ", sizeof(kw) - strlen(kw) - 1);
                strncat(kw, argv[i], sizeof(kw) - strlen(kw) - 1);
            }
        }
        char req[1400];
        snprintf(req, sizeof(req), "SHOPEE_CART\t%s\t%d\t%d\t%d", kw, min_p, max_p, ret_t);
        char *r = daemon_request(req);
        puts(r);
        free(r);
        return 0;
    }

    /* 8. Scroll / Swipe / Nav / Wait */
    if (strcasecmp(sub, "scroll") == 0) {
        const char *dir = argc > 2 ? argv[2] : "down";
        int count = (argc > 3 && is_int_str(argv[3])) ? atoi(argv[3]) : 1;
        char req[128];
        snprintf(req, sizeof(req), "SCROLL\t%s\t8", dir);
        for (int i = 0; i < count; i++) {
            char *r = daemon_request(req);
            puts(r);
            free(r);
        }
        return 0;
    }

    if (strcasecmp(sub, "swipe") == 0) {
        if (argc < 6) {
            puts("ERR\tUsage: satset swipe <x1> <y1> <x2> <y2> [steps]");
            return 1;
        }
        const char *steps = argc > 6 ? argv[6] : "8";
        char req[256];
        snprintf(req, sizeof(req), "SWIPE\t%s\t%s\t%s\t%s\t%s", argv[2], argv[3], argv[4], argv[5], steps);
        char *r = daemon_request(req);
        puts(r);
        free(r);
        return 0;
    }

    if (strcasecmp(sub, "nav") == 0 || strcasecmp(sub, "key") == 0) {
        const char *act = argc > 2 ? argv[2] : "back";
        char req[128];
        snprintf(req, sizeof(req), "NAV\t%s", act);
        char *r = daemon_request(req);
        puts(r);
        free(r);
        return 0;
    }

    if (strcasecmp(sub, "back") == 0 || strcasecmp(sub, "home") == 0 || strcasecmp(sub, "recents") == 0
        || strcasecmp(sub, "notif") == 0 || strcasecmp(sub, "qs") == 0 || strcasecmp(sub, "wake") == 0
        || strcasecmp(sub, "sleep") == 0 || strcasecmp(sub, "enter") == 0 || strcasecmp(sub, "del") == 0
        || strcasecmp(sub, "paste") == 0 || strcasecmp(sub, "lock") == 0 || strcasecmp(sub, "power") == 0) {
        if (strcasecmp(sub, "sleep") == 0 && argc > 2 && isdigit((unsigned char)argv[2][0])) {
            double sec = atof(argv[2]);
            usleep((useconds_t)(sec * 1000000.0));
            printf("OK\tSLEEP %.2fs\n", sec);
            return 0;
        }
        char req[128];
        snprintf(req, sizeof(req), "NAV\t%s", sub);
        char *r = daemon_request(req);
        puts(r);
        free(r);
        return 0;
    }

    if (strcasecmp(sub, "delay") == 0) {
        double sec = argc > 2 ? atof(argv[2]) : 0.2;
        usleep((useconds_t)(sec * 1000000.0));
        printf("OK\tDELAY %.2fs\n", sec);
        return 0;
    }

    if (strcasecmp(sub, "wait") == 0 || strcasecmp(sub, "wait-gone") == 0 || strcasecmp(sub, "wait_gone") == 0) {
        const char *verb = (strstr(sub, "gone") != NULL) ? "WAIT_GONE" : "WAIT";
        int timeout_ms = 5000;
        int end_idx = argc;
        if (argc >= 4 && is_int_str(argv[argc - 1])) {
            timeout_ms = atoi(argv[argc - 1]);
            end_idx = argc - 1;
        }
        char q[2048];
        join_args(q, sizeof(q), end_idx, argv, 2, " ");
        char req[2200];
        snprintf(req, sizeof(req), "%s\t%s\t%d", verb, q, timeout_ms);
        char *r = daemon_request(req);
        puts(r);
        free(r);
        return 0;
    }

    /* 9. Real-Time Gaming Commands (joy, hold, drag, mash, aim, combo, key-hold, pixel, scan-color, tap-color, wait-color, autopilot) */
    if (strcasecmp(sub, "joy") == 0 || strcasecmp(sub, "joystick") == 0 || strcasecmp(sub, "move") == 0) {
        if (argc < 3) {
            puts("ERR\tUsage: satset joy <w|a|s|d|ne|nw|se|sw|circle|strafe|stop|angle> [duration_ms] [--at cx,cy] [--radius r] [--bg]");
            return 1;
        }
        const char *dir = argv[2];
        if (strcasecmp(dir, "stop") == 0) {
            char *r = daemon_request("JOY\tstop");
            puts(r);
            free(r);
            return 0;
        }
        const char *dur_ms = "800";
        char cx[32] = "0", cy[32] = "0", radius[32] = "0";
        int bg = 0;
        for (int i = 3; i < argc; i++) {
            if (strcmp(argv[i], "--at") == 0 && i + 1 < argc) {
                char *xy = strdup(argv[++i]);
                char *c1 = strchr(xy, ',');
                if (c1) {
                    *c1 = '\0';
                    snprintf(cx, sizeof(cx), "%s", xy);
                    char *c2 = strchr(c1 + 1, ',');
                    if (c2) {
                        *c2 = '\0';
                        snprintf(cy, sizeof(cy), "%s", c1 + 1);
                        snprintf(radius, sizeof(radius), "%s", c2 + 1);
                    } else {
                        snprintf(cy, sizeof(cy), "%s", c1 + 1);
                    }
                }
                free(xy);
            } else if ((strcmp(argv[i], "--radius") == 0 || strcmp(argv[i], "-r") == 0) && i + 1 < argc) {
                snprintf(radius, sizeof(radius), "%s", argv[++i]);
            } else if (strcmp(argv[i], "--bg") == 0 || strcmp(argv[i], "--async") == 0) {
                bg = 1;
            } else if (is_int_str(argv[i])) {
                dur_ms = argv[i];
            }
        }
        char req[256];
        snprintf(req, sizeof(req), "JOY\t%s\t%s\t%s\t%s\t%s\t%d", dir, dur_ms, cx, cy, radius, bg);
        char *r = daemon_request(req);
        puts(r);
        free(r);
        return 0;
    }

    if (strcasecmp(sub, "hold") == 0) {
        char x[32], y[32];
        int cons = parse_xy_args(argc, argv, 2, x, sizeof(x), y, sizeof(y));
        if (!cons) {
            puts("ERR\tUsage: satset hold <x> <y> [duration_ms]");
            return 1;
        }
        const char *dur = (argc > 2 + cons) ? argv[2 + cons] : "600";
        char req[128];
        snprintf(req, sizeof(req), "HOLD\t%s\t%s\t%s", x, y, dur);
        char *r = daemon_request(req);
        puts(r);
        free(r);
        return 0;
    }

    if (strcasecmp(sub, "drag") == 0) {
        if (argc < 4) {
            puts("ERR\tUsage: satset drag <x1> <y1> <x2> <y2> [duration_ms]");
            return 1;
        }
        char x1[32], y1[32], x2[32], y2[32];
        const char *dur = "180";
        if (argc >= 6 && is_int_str(argv[2]) && is_int_str(argv[3])) {
            snprintf(x1, sizeof(x1), "%s", argv[2]);
            snprintf(y1, sizeof(y1), "%s", argv[3]);
            snprintf(x2, sizeof(x2), "%s", argv[4]);
            snprintf(y2, sizeof(y2), "%s", argv[5]);
            if (argc > 6) dur = argv[6];
        } else {
            parse_xy_args(argc, argv, 2, x1, sizeof(x1), y1, sizeof(y1));
            parse_xy_args(argc, argv, 3, x2, sizeof(x2), y2, sizeof(y2));
            if (argc > 4) dur = argv[4];
        }
        char req[256];
        snprintf(req, sizeof(req), "DRAG\t%s\t%s\t%s\t%s\t%s", x1, y1, x2, y2, dur);
        char *r = daemon_request(req);
        puts(r);
        free(r);
        return 0;
    }

    if (strcasecmp(sub, "mash") == 0 || strcasecmp(sub, "burst") == 0 || strcasecmp(sub, "rapid") == 0) {
        if (argc < 3) {
            puts("ERR\tUsage: satset mash <x,y | x y | 'x1,y1;x2,y2'> [count] [interval_ms] [--bg]");
            return 1;
        }
        int bg = 0;
        char *cleaned[16];
        int cc = 0;
        for (int i = 2; i < argc; i++) {
            if (strcmp(argv[i], "--bg") == 0 || strcmp(argv[i], "--async") == 0) bg = 1;
            else if (cc < 16) cleaned[cc++] = argv[i];
        }
        char coords[512];
        const char *count = "10";
        const char *interval = "25";
        if (cc >= 2 && is_int_str(cleaned[0]) && is_int_str(cleaned[1]) && (cc == 2 || is_int_str(cleaned[2]))) {
            snprintf(coords, sizeof(coords), "%s,%s", cleaned[0], cleaned[1]);
            if (cc > 2) count = cleaned[2];
            if (cc > 3) interval = cleaned[3];
        } else {
            snprintf(coords, sizeof(coords), "%s", cleaned[0]);
            if (cc > 1) count = cleaned[1];
            if (cc > 2) interval = cleaned[2];
        }
        char req[640];
        snprintf(req, sizeof(req), "MASH\t%s\t%s\t%s\t%d", coords, count, interval, bg);
        char *r = daemon_request(req);
        puts(r);
        free(r);
        return 0;
    }

    if (strcasecmp(sub, "aim") == 0 || strcasecmp(sub, "skill") == 0 || strcasecmp(sub, "flick") == 0) {
        char x[32], y[32];
        int cons = parse_xy_args(argc, argv, 2, x, sizeof(x), y, sizeof(y));
        if (!cons || argc <= 2 + cons) {
            puts("ERR\tUsage: satset aim <x> <y> <dir_or_angle> [dist_px] [hold_ms]");
            return 1;
        }
        const char *dir = argv[2 + cons];
        const char *dist = (argc > 3 + cons) ? argv[3 + cons] : "180";
        const char *hold = (argc > 4 + cons) ? argv[4 + cons] : "60";
        char req[256];
        snprintf(req, sizeof(req), "AIM\t%s\t%s\t%s\t%s\t%s", x, y, dir, dist, hold);
        char *r = daemon_request(req);
        puts(r);
        free(r);
        return 0;
    }

    if (strcasecmp(sub, "combo") == 0 || strcasecmp(sub, "multitouch") == 0) {
        if (argc < 5) {
            puts("ERR\tUsage: satset combo <duration_ms> <joy_dir> \"mash:x,y:100|aim:x,y:ne:170@400|tap:x,y@800\" [--joy cx,cy,r]");
            return 1;
        }
        const char *dur = argv[2];
        const char *jdir = argv[3];
        const char *rspec = argv[4];
        const char *jgeom = (argc >= 7 && strcmp(argv[5], "--joy") == 0) ? argv[6] : "";
        char req[2048];
        snprintf(req, sizeof(req), "COMBO\t%s\t%s\t%s\t%s", dur, jdir, jgeom, rspec);
        char *r = daemon_request(req);
        puts(r);
        free(r);
        return 0;
    }

    if (strcasecmp(sub, "key-hold") == 0 || strcasecmp(sub, "keyhold") == 0 || strcasecmp(sub, "gamepad") == 0) {
        if (argc < 3) {
            puts("ERR\tUsage: satset key-hold <w|a|s|d|space|btn_a|btn_b|keycode> [duration_ms]");
            return 1;
        }
        int code = 0, src = 0x101;
        if (!map_game_keycode(argv[2], &code, &src)) {
            printf("ERR\tUnknown key '%s'\n", argv[2]);
            return 1;
        }
        const char *dur = argc > 3 ? argv[3] : "350";
        char req[128];
        snprintf(req, sizeof(req), "KEY_HOLD\t%d\t%s\t%d", code, dur, src);
        char *r = daemon_request(req);
        puts(r);
        free(r);
        return 0;
    }

    if (strcasecmp(sub, "pixel") == 0 || strcasecmp(sub, "pixels") == 0 || strcasecmp(sub, "color") == 0) {
        if (argc < 3) {
            puts("ERR\tUsage: satset pixel <x1,y1> [x2,y2 ...]");
            return 1;
        }
        char spec[1024];
        if (argc == 4 && is_int_str(argv[2]) && is_int_str(argv[3])) {
            snprintf(spec, sizeof(spec), "%s,%s", argv[2], argv[3]);
        } else {
            join_args(spec, sizeof(spec), argc, argv, 2, ";");
        }
        char req[1152];
        snprintf(req, sizeof(req), "PIXEL\t%s", spec);
        char *r = daemon_request(req);
        puts(r);
        free(r);
        return 0;
    }

    if (strcasecmp(sub, "scan-color") == 0 || strcasecmp(sub, "find-color") == 0 || strcasecmp(sub, "tap-color") == 0) {
        if (argc < 3) {
            printf("ERR\tUsage: satset %s <#RRGGBB> [--tol 28] [--region x1,y1,x2,y2]\n", sub);
            return 1;
        }
        int tap_flag = (strcasecmp(sub, "tap-color") == 0) ? 1 : 0;
        const char *hex = argv[2];
        const char *tol = "28";
        const char *region = "";
        for (int i = 3; i < argc; i++) {
            if (strcmp(argv[i], "--tol") == 0 && i + 1 < argc) tol = argv[++i];
            else if (strcmp(argv[i], "--region") == 0 && i + 1 < argc) region = argv[++i];
        }
        char req[512];
        snprintf(req, sizeof(req), "SCAN_COLOR\t%s\t%s\t%s\t%d", hex, tol, region, tap_flag);
        char *r = daemon_request(req);
        puts(r);
        free(r);
        return 0;
    }

    if (strcasecmp(sub, "wait-color") == 0 || strcasecmp(sub, "wait_color") == 0) {
        char x[32], y[32];
        int cons = parse_xy_args(argc, argv, 2, x, sizeof(x), y, sizeof(y));
        if (!cons || argc <= 2 + cons) {
            puts("ERR\tUsage: satset wait-color <x,y> <#RRGGBB> [timeout_ms] [--tol 28] [--gone]");
            return 1;
        }
        const char *hex = argv[2 + cons];
        const char *timeout = "5000";
        const char *tol = "28";
        int gone = 0;
        for (int i = 3 + cons; i < argc; i++) {
            if (strcmp(argv[i], "--tol") == 0 && i + 1 < argc) tol = argv[++i];
            else if (strcmp(argv[i], "--gone") == 0) gone = 1;
            else if (is_int_str(argv[i])) timeout = argv[i];
        }
        char req[256];
        snprintf(req, sizeof(req), "WAIT_COLOR\t%s\t%s\t%s\t%s\t%s\t%d", x, y, hex, tol, timeout, gone);
        char *r = daemon_request(req);
        puts(r);
        free(r);
        return 0;
    }

    if (strcasecmp(sub, "autopilot") == 0 || strcasecmp(sub, "reflex") == 0 || strcasecmp(sub, "bot") == 0) {
        const char *action = argc > 2 ? argv[2] : "STATUS";
        if (strcasecmp(action, "STOP") == 0 || strcasecmp(action, "STATUS") == 0) {
            char req[64];
            snprintf(req, sizeof(req), "AUTOPILOT\t%s", action);
            char *r = daemon_request(req);
            puts(r);
            free(r);
            return 0;
        }
        const char *dur = "5000", *jmode = "strafe", *jgeom = "", *atk = "", *skills = "", *track = "", *hp = "";
        int bg = 0;
        int start_i = (strcasecmp(action, "START") == 0) ? 3 : 2;
        for (int i = start_i; i < argc; i++) {
            if ((strcmp(argv[i], "--duration") == 0 || strcmp(argv[i], "-d") == 0) && i + 1 < argc) dur = argv[++i];
            else if (strcmp(argv[i], "--joy") == 0 && i + 1 < argc) jmode = argv[++i];
            else if (strcmp(argv[i], "--joy-at") == 0 && i + 1 < argc) jgeom = argv[++i];
            else if (strcmp(argv[i], "--atk") == 0 && i + 1 < argc) atk = argv[++i];
            else if (strcmp(argv[i], "--skills") == 0 && i + 1 < argc) skills = argv[++i];
            else if (strcmp(argv[i], "--track") == 0 && i + 1 < argc) track = argv[++i];
            else if (strcmp(argv[i], "--hp") == 0 && i + 1 < argc) hp = argv[++i];
            else if (strcmp(argv[i], "--bg") == 0 || strcmp(argv[i], "--async") == 0) bg = 1;
            else if (is_int_str(argv[i])) dur = argv[i];
        }
        char req[1024];
        snprintf(req, sizeof(req), "AUTOPILOT\tSTART\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%d",
            dur, jmode, jgeom, atk, skills, track, hp, bg);
        char *r = daemon_request(req);
        puts(r);
        free(r);
        return 0;
    }

    /* 9b. Real-Time Screen Streamer & Circular RAM Buffer (Live Vision / Instant Frame) */
    if (strcasecmp(sub, "live") == 0 || strcasecmp(sub, "frame") == 0 || strcasecmp(sub, "peek") == 0) {
        const char *out_path = DEFAULT_SHOT_PATH;
        for (int i = 2; i < argc; i++) {
            if (argv[i][0] != '-') out_path = argv[i];
        }
        char req[1024];
        snprintf(req, sizeof(req), "LIVE\t%s", out_path);
        char *resp = daemon_request(req);
        if (strncmp(resp, "OK_SHOT\t", 8) == 0) {
            char *p1 = resp + 8;
            char *p2 = strchr(p1, '\t');
            char *p3 = p2 ? strchr(p2 + 1, '\t') : NULL;
            char *p4 = p3 ? strchr(p3 + 1, '\t') : NULL;
            if (p2 && p3 && p4) {
                *p2 = '\0'; *p3 = '\0'; *p4 = '\0';
                const char *dims = p1;
                const char *size_b = p2 + 1;
                const char *dt_str = p3 + 1;
                const char *b64_data = p4 + 1;
                size_t raw_len = 0;
                unsigned char *raw = b64_decode(b64_data, strlen(b64_data), &raw_len);
                if (raw) {
                    FILE *fp = fopen(out_path, "wb");
                    if (fp) {
                        fwrite(raw, 1, raw_len, fp);
                        fclose(fp);
                    }
                    free(raw);
                }
                printf("OK\tLIVE frame saved to %s (%s, %s bytes, resident RAM) in %s\n",
                    out_path, dims, size_b, dt_str);
                free(resp);
                return 0;
            }
        }
        puts(resp);
        free(resp);
        return 0;
    }

    if (strcasecmp(sub, "stream") == 0) {
        const char *action = (argc > 2) ? argv[2] : "status";
        const char *interval = (argc > 3) ? argv[3] : "70";
        const char *scale = (argc > 4) ? argv[4] : "0.5";
        const char *quality = (argc > 5) ? argv[5] : "60";

        char req[1024];
        snprintf(req, sizeof(req), "STREAM\t%s\t%s\t%s\t%s", action, interval, scale, quality);
        char *resp = daemon_request(req);
        puts(resp);
        free(resp);
        return 0;
    }

    /* 10. Visual Screenshot & Tactical Game Grid (Auto-Purged Ephemeral Storage) */
    if (strcasecmp(sub, "shot") == 0 || strcasecmp(sub, "screenshot") == 0 || strcasecmp(sub, "som") == 0
        || strcasecmp(sub, "game-shot") == 0 || strcasecmp(sub, "gameshot") == 0) {
        int som = (strcasecmp(sub, "som") == 0 || strcasecmp(sub, "game-shot") == 0 || strcasecmp(sub, "gameshot") == 0) ? 1 : 0;
        int grid = (strcasecmp(sub, "game-shot") == 0 || strcasecmp(sub, "gameshot") == 0) ? 1 : 0;
        const char *scale = "0.5";
        const char *quality = "72";
        const char *out_path = DEFAULT_SHOT_PATH;

        for (int i = 2; i < argc; i++) {
            if (strcmp(argv[i], "--som") == 0 || strcmp(argv[i], "-s") == 0) som = 1;
            else if (strcmp(argv[i], "--grid") == 0 || strcmp(argv[i], "-g") == 0) grid = 1;
            else if (strcmp(argv[i], "--full") == 0) scale = "1.0";
            else if (strcmp(argv[i], "--scale") == 0 && i + 1 < argc) scale = argv[++i];
            else if (strcmp(argv[i], "--quality") == 0 && i + 1 < argc) quality = argv[++i];
            else out_path = argv[i];
        }

        char req[1024];
        snprintf(req, sizeof(req), "SHOT\t%s\t%d\t%s\t%d\t%s", scale, som, quality, grid, out_path);
        char *resp = daemon_request(req);
        if (strncmp(resp, "OK_SHOT\t", 8) == 0) {
            /* Fallback when out_path is outside /sdcard (e.g. inside Termux home) */
            char *p1 = resp + 8;
            char *p2 = strchr(p1, '\t');
            char *p3 = p2 ? strchr(p2 + 1, '\t') : NULL;
            char *p4 = p3 ? strchr(p3 + 1, '\t') : NULL;
            if (p2 && p3 && p4) {
                *p2 = '\0'; *p3 = '\0'; *p4 = '\0';
                const char *dims = p1;
                const char *size_b = p2 + 1;
                const char *dt_str = p3 + 1;
                const char *b64_data = p4 + 1;
                size_t raw_len = 0;
                unsigned char *raw = b64_decode(b64_data, strlen(b64_data), &raw_len);
                if (raw) {
                    FILE *fp = fopen(out_path, "wb");
                    if (fp) {
                        fwrite(raw, 1, raw_len, fp);
                        fclose(fp);
                    }
                    free(raw);
                }
                printf("OK\tSHOT saved to %s (%s, %s bytes, som=%s, grid=%s) in %s\n",
                    out_path, dims, size_b, som ? "true" : "false", grid ? "true" : "false", dt_str);
                free(resp);
                return 0;
            }
        }
        puts(resp);
        free(resp);
        return 0;
    }

    printf("ERR\tUnknown command '%s'. Run `satset help`.\n", sub);
    return 1;
}
