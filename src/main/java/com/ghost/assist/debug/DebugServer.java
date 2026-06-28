package com.ghost.assist.debug;

import android.util.Log;

import com.ghost.assist.core.AppConfig;
import com.ghost.assist.core.Bridge;
import com.ghost.assist.core.GuardRuntime;
import com.ghost.assist.core.InterceptCounter;
import com.ghost.assist.core.StateMachine;
import com.ghost.assist.core.NativeBridge;
import com.ghost.assist.debug.DebugTelemetry;
import com.ghost.assist.net.EnvelopeStore;
import com.ghost.assist.net.GuardActivation;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimal HTTP debug server on port 8080.
 * Serves debug HTML page + JSON API endpoints for polling state/counters.
 */
public class DebugServer {

    private static final String TAG = "NCL";
    private static ServerSocket sServerSocket;
    private static Thread sThread;
    private static volatile boolean sRunning = false;
    private static volatile String sModulePath = null;

    public static void setModulePath(String path) { sModulePath = path; }

    public static void start() {
        if (sRunning) return;
        sRunning = true;
        sThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    int port = AppConfig.getInstance().getServerPort();
                    sServerSocket = new ServerSocket(port);
                    Log.i(TAG, "[HTTP] listening on port " + port);
                    while (sRunning) {
                        try {
                            Socket client = sServerSocket.accept();
                            handleClient(client);
                        } catch (IOException e) {
                            if (sRunning) Log.e(TAG, "[HTTP] accept error: " + e.getMessage());
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "[HTTP] server error: " + e.getMessage());
                }
            }
        }, "debug-http");
        sThread.setDaemon(true);
        sThread.start();
    }

    public static void stop() {
        sRunning = false;
        try {
            if (sServerSocket != null) sServerSocket.close();
        } catch (IOException ignored) {}
        sThread = null;
    }

    private static void handleClient(Socket client) {
        try {
            client.setSoTimeout(5000);
            InputStream in = client.getInputStream();
            OutputStream out = client.getOutputStream();

            // Parse request line
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String requestLine = reader.readLine();
            if (requestLine == null) { client.close(); return; }

            String[] parts = requestLine.split(" ");
            if (parts.length < 2) { client.close(); return; }
            String method = parts[0];
            String path = parts[1];

            // Read headers (skip for now)
            String header;
            int contentLength = 0;
            while ((header = reader.readLine()) != null && !header.isEmpty()) {
                if (header.toLowerCase().startsWith("content-length:")) {
                    contentLength = Integer.parseInt(header.substring(15).trim());
                }
            }

            // Read body if POST
            String body = "";
            if ("POST".equals(method) && contentLength > 0) {
                char[] buf = new char[contentLength];
                int read = reader.read(buf, 0, contentLength);
                if (read > 0) body = new String(buf, 0, read);
            }

            // Route
            byte[] response;
            if ("/api/state".equals(path)) {
                response = apiState();
            } else if ("/api/native".equals(path)) {
                response = apiNative();
            } else if ("/api/counters".equals(path)) {
                response = apiCounters();
            } else if ("/api/events".equals(path)) {
                response = apiEvents();
            } else if ("/api/feature".equals(path)) {
                response = "POST".equals(method) ? apiSetFeature(body) : apiGetFeature();
            } else if ("/api/notify_policy".equals(path)) {
                response = "POST".equals(method) ? apiSetNotifyPolicy(body) : apiGetNotifyPolicy();
            } else if ("/api/trigger".equals(path) && "POST".equals(method)) {
                response = apiTrigger(body);
            } else if ("/api/mode".equals(path) && "POST".equals(method)) {
                response = apiSetMode(body);
            } else if ("/api/password".equals(path) && "POST".equals(method)) {
                response = apiSetPassword(body);
            } else if ("/api/config".equals(path)) {
                response = apiConfig();
            } else if ("/api/contact".equals(path) && "POST".equals(method)) {
                response = apiResolveContact(body);
            } else if ("/api/hidden".equals(path)) {
                response = "GET".equals(method) ? apiHiddenList() : apiHidden(body);
            } else if ("/api/feed-wxids".equals(path)) {
                response = apiFeedWxids();
            } else if ("/api/conv-wxids".equals(path)) {
                response = apiConvWxids();
            } else if ("/api/activate".equals(path) && "POST".equals(method)) {
                response = apiActivate(body);
            } else if ("/api/forcefunnel".equals(path) && "POST".equals(method)) {
                response = apiForceFunnel();
            } else if ("/api/forcecardrevoke".equals(path) && "POST".equals(method)) {
                response = apiForceCardRevoke(body);
            } else if ("/api/mywxid".equals(path)) {
                response = "GET".equals(method) ? apiGetMyWxid() : apiSetMyWxid(body);
            } else if ("/api/dumps".equals(path)) {
                response = apiDumps();
            } else if ("/api/rawfeed".equals(path)) {
                response = apiRawFeed();
            } else if (path.startsWith("/api/telemetry")) {
                response = apiTelemetry(path);
            } else {
                response = serveDebugPage();
            }

            out.write(response);
            out.flush();
            client.close();
        } catch (Exception e) {
            Log.e(TAG, "[HTTP] handle error: " + e.getMessage());
        }
    }

    // --- API handlers ---

    private static byte[] apiState() {
        StateMachine sm = StateMachine.getInstance();
        String json = "{" +
            "\"state\":\"" + sm.getStateName() + "\"," +
            "\"code\":" + sm.getStateCode() + "," +
            "\"mode\":" + sm.isActive() + "}";
        return jsonResponse(json);
    }

    /**
     * Native / protection-layer status snapshot for the dashboard.
     * STATUS ONLY — never expose keys, raw config, lease tokens or decrypt material here.
     * DEV builds only (the whole DebugServer must be gated off in release).
     * leaseValid / decryptOk are Phase 1/2 placeholders (null until wired).
     */
    private static byte[] apiNative() {
        boolean avail = NativeBridge.isAvailable();
        String summary = GuardRuntime.getActiveRegistrySummary();
        boolean configReady = GuardRuntime.isConfigReady();
        boolean recipeOk = !GuardRuntime.getRecipe("conv.list", "adapter_class").isEmpty()
                && !GuardRuntime.getRecipe("search.gateway", "gateway").isEmpty();
        boolean registryAfterSeed = configReady && recipeOk;
        String auth = EnvelopeStore.isAuthorizedNow() ? "AUTH_OK" : "AUTH_NO_LICENSE";
        String heartbeat = EnvelopeStore.hasCachedEnvelope() ? "synced" : "none";
        String risk = com.ghost.assist.core.RiskState.currentLevel() == com.ghost.assist.core.RiskState.Level.CLEAN
                ? "正常"
                : com.ghost.assist.core.RiskState.currentLevel().label;
        String json = "{"
            + "\"soLoaded\":" + avail + ","
            + "\"role\":\"" + roleName(NativeBridge.getProcessRole()) + "\","
            + "\"authState\":\"" + authName(NativeBridge.getAuthState()) + "\","
            + "\"authorized\":" + NativeBridge.isAuthorized() + ","
            + "\"nativeRisk\":\"" + riskName(NativeBridge.getRiskState()) + "\","
            + "\"configVersion\":" + NativeBridge.getConfigVersion() + ","
            + "\"hidden\":" + NativeBridge.isHidden() + ","
            + "\"leaseValid\":null,"      // Phase 1: heartbeat lease
            + "\"graceRemainHours\":-1,"  // Phase 1: from GRACE_* ladder
            + "\"decryptOk\":null,"       // Phase 1: SO decrypt_config()
            + "\"tampered\":false,"       // Phase 2: honeypot tripwire flag
            // ── P1F Java 防护层（两闸 + 风险等级）：复用本端点，不新建（PROTECTION_MAP §10.1 不碎拆）──
            + "\"jrisk\":\"" + com.ghost.assist.core.RiskState.currentLevel().label + "\","
            + "\"funnel\":" + com.ghost.assist.core.RiskState.shouldFunnel() + ","
            + "\"kill\":" + com.ghost.assist.core.AppConfig.getInstance().isKillSwitch() + ","
            // ── S3a status-only snapshot. Never expose token/k/S_rel/W or raw envelope. ──
            + "\"release_id\":\"" + AppConfig.GUARD_RELEASE_ID + "\","
            + "\"auth\":\"" + auth + "\","
            + "\"heartbeat\":\"" + heartbeat + "\","
            + "\"risk\":\"" + risk + "\","
            + "\"registryRequiresServerSeed\":true,"
            + "\"registryAfterSeed\":\"" + (registryAfterSeed ? "ok" : "fail") + "\","
            + "\"recipeOk\":" + recipeOk + ","
            + "\"schema\":\"r8071_v1\","
            + "\"entries\":" + registryEntryCount(summary)
            + "}";
        return jsonResponse(json);
    }

    private static int registryEntryCount(String summary) {
        if (summary == null) return 0;
        int idx = summary.indexOf("entries=");
        if (idx < 0) return 0;
        idx += "entries=".length();
        int end = idx;
        while (end < summary.length() && Character.isDigit(summary.charAt(end))) end++;
        if (end == idx) return 0;
        try {
            return Integer.parseInt(summary.substring(idx, end));
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static String authName(int s) {
        switch (s) {
            case NativeBridge.AUTH_OK:               return "OK";
            case NativeBridge.AUTH_EXPIRED:          return "EXPIRED";
            case NativeBridge.AUTH_TAMPERED:         return "TAMPERED";
            case NativeBridge.AUTH_NO_LICENSE:       return "NO_LICENSE";
            case NativeBridge.AUTH_ACCOUNT_MISMATCH: return "ACCOUNT_MISMATCH";
            case NativeBridge.AUTH_DEVICE_MISMATCH:  return "DEVICE_MISMATCH";
            default:                                 return "UNKNOWN";
        }
    }

    private static String riskName(int s) {
        switch (s) {
            case NativeBridge.RISK_PACKAGE_MISMATCH: return "PACKAGE_MISMATCH";
            case NativeBridge.RISK_CONFIG_TAMPERED:  return "CONFIG_TAMPERED";
            case NativeBridge.RISK_GRACE_EXPIRED:    return "GRACE_EXPIRED";
            case NativeBridge.RISK_PIRATE:           return "PIRATE";
            default:                                 return "NONE";
        }
    }

    private static String roleName(int r) {
        switch (r) {
            case NativeBridge.ROLE_MAIN:    return "MAIN";
            case NativeBridge.ROLE_PUSH:    return "PUSH";
            case NativeBridge.ROLE_BLOCKED: return "BLOCKED";
            default:                        return "UNKNOWN";
        }
    }

    private static byte[] apiCounters() {
        return jsonResponse(InterceptCounter.getInstance().toJsonSnapshot());
    }

    private static byte[] apiEvents() {
        InterceptCounter ic = InterceptCounter.getInstance();
        StringBuilder sb = new StringBuilder("[");
        int i = 0;
        for (InterceptCounter.Event e : ic.getRecentEvents()) {
            if (i > 0) sb.append(",");
            sb.append("{\"time\":").append(e.timestamp)
              .append(",\"type\":\"").append(e.type)
              .append("\",\"detail\":\"").append(e.detail).append("\"}");
            i++;
            if (i >= 50) break;
        }
        sb.append("]");
        return jsonResponse(sb.toString());
    }

    private static byte[] apiGetFeature() {
        boolean on = Bridge.getInstance().isFeatureEnabled();
        return jsonResponse("{\"enabled\":" + on + "}");
    }

    private static byte[] apiSetFeature(String body) {
        String val = extractJsonField(body, "enabled");
        boolean on = !"false".equals(val);
        Bridge.getInstance().setFeatureEnabled(on);
        android.util.Log.i("NCL", "[DBG] feature=" + (on ? "ON" : "OFF"));
        return jsonResponse("{\"enabled\":" + on + ",\"ok\":true}");
    }

    private static byte[] apiGetNotifyPolicy() {
        String p = Bridge.getInstance().getNotifyPolicy().name();
        return jsonResponse("{\"policy\":\"" + p + "\"}");
    }

    private static byte[] apiSetNotifyPolicy(String body) {
        String val = extractJsonField(body, "policy");
        Bridge.NotifyPolicy policy = Bridge.NotifyPolicy.fromString(val);
        Bridge.getInstance().setNotifyPolicy(policy);
        android.util.Log.i("NCL", "[DBG] notify_policy=" + policy.name());
        return jsonResponse("{\"policy\":\"" + policy.name() + "\",\"ok\":true}");
    }

    private static byte[] apiTrigger(String body) {
        // body: {"action":"toggle|enter|show|unlock","password":"1111"}
        String action = extractJsonField(body, "action");
        String password = extractJsonField(body, "password");
        StateMachine sm = StateMachine.getInstance();

        switch (action) {
            case "toggle":
                sm.toggle();
                break;
            case "enter":
                sm.enterHidden();
                break;
            case "show":
                sm.exitHidden(false);
                break;
            case "unlock":
                sm.beginUnlock();
                sm.attemptUnlock(password != null ? password : "");
                break;
            default:
                return jsonResponse("{\"error\":\"unknown action\"}", 400);
        }

        return apiState();
    }

    private static byte[] apiSetMode(String body) {
        String mode = extractJsonField(body, "mode");
        try {
            AppConfig.Mode m = AppConfig.Mode.valueOf(mode);
            AppConfig.getInstance().setMode(m);
        } catch (Exception e) {
            return jsonResponse("{\"error\":\"invalid mode\"}", 400);
        }
        return apiConfig();
    }

    private static byte[] apiSetPassword(String body) {
        if (!StateMachine.getInstance().isVipAuthorized()) {
            return jsonResponse("{\"error\":\"not authorized\"}", 403);
        }
        String password = extractJsonField(body, "password");
        if (password == null) password = "";
        password = password.trim();
        if (password.length() < 4 || password.length() > 32) {
            return jsonResponse("{\"error\":\"invalid password\"}", 400);
        }
        StateMachine.getInstance().setPassword(password);
        android.util.Log.i("NCL", "[DBG] password updated");
        return jsonResponse("{\"ok\":true}");
    }

    private static byte[] apiConfig() {
        AppConfig cfg = AppConfig.getInstance();
        String json = "{" +
            "\"mode\":\"" + cfg.getMode().name() + "\"," +
            "\"localDev\":" + cfg.isLocalDevMode() + "," +
            "\"overlay\":" + cfg.isOverlayEnabled() + "," +
            "\"port\":" + cfg.getServerPort() + "}";
        return jsonResponse(json);
    }

    private static byte[] apiResolveContact(String body) {
        String wxid = extractJsonField(body, "wxid");
        if (wxid == null || wxid.isEmpty()) {
            return jsonResponse("{\"error\":\"no wxid\"}", 400);
        }
        return jsonResponse(ContactResolver.resolveJson(wxid));
    }

    private static byte[] apiTelemetry(String path) {
        if (path.contains("/events")) {
            String channel = "";
            int q = path.indexOf('?');
            if (q >= 0) {
                String qs = path.substring(q + 1);
                for (String part : qs.split("&")) {
                    if (part.startsWith("channel=")) channel = part.substring(8);
                }
            }
            return jsonResponse("{\"events\":" + DebugTelemetry.getInstance().eventsJson(channel, 100) + "}");
        }
        return jsonResponse(DebugTelemetry.getInstance().toJsonSnapshot());
    }

    private static byte[] apiRawFeed() {
        java.util.List<String> lines = Bridge.getInstance().getRawFeed();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append(",");
            String esc = lines.get(i).replace("\\","\\\\").replace("\"","\\\"");
            sb.append("\"").append(esc).append("\"");
        }
        sb.append("]");
        return jsonResponse("{\"lines\":" + sb + ",\"count\":" + lines.size() + "}");
    }

    private static byte[] apiDumps() {
        java.util.List<String> dumps = Bridge.getInstance().getItemDumps();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < dumps.size(); i++) {
            if (i > 0) sb.append(",");
            String escaped = dumps.get(i)
                .replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "");
            sb.append("\"").append(escaped).append("\"");
        }
        sb.append("]");
        return jsonResponse("{\"dumps\":" + sb + "}");
    }

    private static byte[] apiGetMyWxid() {
        String w = Bridge.getInstance().getMyWxid();
        return jsonResponse("{\"wxid\":\"" + w + "\"}");
    }

    private static byte[] apiSetMyWxid(String body) {
        String wxid = extractJsonField(body, "wxid");
        if (wxid == null) wxid = "";
        Bridge.getInstance().setMyWxid(wxid);
        return apiGetMyWxid();
    }

    /**
     * S2 真锁激活入口（DEV 联调用）。卡密 → token → 起心跳。
     * ungated：激活是建立授权的入口（同 bind_account），不能要授权。
     * 注意：DebugServer 仅 DEV/HONEY 起；release 激活入口走 SettingsEntry（另排）。
     * 在 debug-http 后台线程同步跑（含网络），回 ok/fail 给调试页。
     */
    private static byte[] apiActivate(String body) {
        String cardKey = extractJsonField(body, "card_key");
        if (cardKey == null || cardKey.trim().isEmpty()) {
            return jsonResponse("{\"error\":\"no card_key\"}", 400);
        }
        GuardActivation.Result r = GuardActivation.activate(cardKey);
        String msg = r.message == null ? "" : r.message.replace("\\", "\\\\").replace("\"", "\\\"");
        return jsonResponse("{\"ok\":" + r.ok + ",\"msg\":\"" + msg + "\"}", r.ok ? 200 : 400);
    }

    /**
     * 段1 装机验证入口（DEBUG-only）：强制 RiskState 进 funnel 态。
     * 设态后切到微信前台 → Activity.onResume → RiskPromptController → FunnelPrompt 弹窗。
     * release 包 BuildConfig.DEBUG=false → debugForceFunnel 空操作（不会被滥用）。
     */
    private static byte[] apiForceFunnel() {
        com.ghost.assist.core.RiskState.debugForceFunnel();
        String lvl = com.ghost.assist.core.RiskState.currentLevel().label;
        return jsonResponse("{\"ok\":true,\"level\":\"" + lvl
                + "\",\"funnel\":" + com.ghost.assist.core.RiskState.shouldFunnel() + "}");
    }

    /**
     * #6 装机验证入口（DEBUG-only）：置/清封停删卡态（rf），验「撤闸立刻散」(A2+隐私一起死)。
     * body {"on":true} 置撤闸 / {"on":false} 复位。release BuildConfig.DEBUG=false → 空操作。
     * 置后回前台/冷启 → isAntiBanReady=false（A2 不装）+ isAuthorizedNow=false（isActive 断、隐私放行）。
     */
    private static byte[] apiForceCardRevoke(String body) {
        boolean on = !"false".equalsIgnoreCase(extractJsonField(body, "on"));
        com.ghost.assist.net.EnvelopeStore.debugSetCardRevoked(on);
        return jsonResponse("{\"ok\":true,\"cardRevoked\":"
                + com.ghost.assist.net.EnvelopeStore.isCardRevoked() + "}");
    }

    private static byte[] apiFeedWxids() {
        java.util.List<String[]> list = Bridge.getInstance().getFeedWxids();
        java.util.Collections.reverse(list); // newest first
        // 只透出好友 wxid_（gh_ 公众号保留在内存供 DIAG，不展示）
        java.util.List<String[]> friends = new java.util.ArrayList<>();
        for (String[] p : list) { if (p[0].startsWith("wxid_")) friends.add(p); }
        list = friends;
        java.util.Set<String> hidden = Bridge.getInstance().getWxids();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            String wxid = list.get(i)[0];
            String nick = list.get(i)[1];
            // escape nickname to avoid JSON breakage
            String nickEsc = nick.replace("\\", "\\\\").replace("\"", "\\\"");
            sb.append("{\"wxid\":\"").append(wxid)
              .append("\",\"nick\":\"").append(nickEsc)
              .append("\",\"hidden\":").append(hidden.contains(wxid)).append("}");
        }
        sb.append("]");
        return jsonResponse("{\"list\":" + sb + "}");
    }

    private static byte[] apiConvWxids() {
        java.util.List<String[]> list = Bridge.getInstance().getConvWxids();
        java.util.Collections.reverse(list);
        java.util.Set<String> hidden = Bridge.getInstance().allHiddenIds();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            String wxid = list.get(i)[0];
            String nick = list.get(i)[1] != null ? list.get(i)[1] : "";
            String nickEsc = nick.replace("\\", "\\\\").replace("\"", "\\\"");
            sb.append("{\"wxid\":\"").append(wxid)
              .append("\",\"nick\":\"").append(nickEsc)
              .append("\",\"hidden\":").append(hidden.contains(wxid)).append("}");
        }
        sb.append("]");
        return jsonResponse("{\"list\":" + sb + "}");
    }

    private static byte[] apiHiddenList() {
        Bridge bridge = Bridge.getInstance();
        java.util.Set<String> wxids = bridge.getWxids();
        java.util.Set<String> groups = bridge.getGroupIds();
        StringBuilder sb = new StringBuilder("[");
        int i = 0;
        for (String wxid : wxids) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(wxid).append("\"");
            i++;
        }
        for (String g : groups) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(g).append("\"");
            i++;
        }
        sb.append("]");
        return jsonResponse("{\"list\":" + sb
            + ",\"count\":" + (wxids.size() + groups.size())
            + ",\"wxids\":" + wxids.size()
            + ",\"groups\":" + groups.size()
            + "}");
    }

    private static byte[] apiHidden(String body) {
        String action = extractJsonField(body, "action");
        String wxid = extractJsonField(body, "wxid");
        if (wxid == null || wxid.isEmpty()) {
            return jsonResponse("{\"error\":\"no wxid\"}", 400);
        }
        Bridge bridge = Bridge.getInstance();
        boolean isGroup = Bridge.isGroupId(wxid);
        if ("remove".equals(action)) {
            if (isGroup) bridge.removeGroupId(wxid); else bridge.removeWxid(wxid);
        } else {
            if (isGroup) bridge.addGroupId(wxid); else bridge.addWxid(wxid);
        }
        return apiHiddenList();
    }

    // --- Serve debug HTML page ---
    private static byte[] serveDebugPage() {
        // LSPosed ClassLoader does not support getResourceAsStream("assets/...")
        // Reliable path: read directly from module APK via ZipFile
        if (sModulePath != null) {
            try {
                java.util.zip.ZipFile zip = new java.util.zip.ZipFile(sModulePath);
                java.util.zip.ZipEntry entry = zip.getEntry("assets/debug/index.html");
                if (entry != null) {
                    InputStream is = zip.getInputStream(entry);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int read;
                    while ((read = is.read(buf)) != -1) baos.write(buf, 0, read);
                    is.close();
                    zip.close();
                    return htmlResponse(new String(baos.toByteArray(), StandardCharsets.UTF_8));
                }
                zip.close();
            } catch (Exception e) {
                Log.e(TAG, "[HTTP] zip read failed: " + e.getMessage());
            }
        }
        // Fallback: classloader (works in unit tests / non-LSPosed)
        try {
            InputStream is = DebugServer.class.getClassLoader()
                .getResourceAsStream("assets/debug/index.html");
            if (is != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int read;
                while ((read = is.read(buf)) != -1) baos.write(buf, 0, read);
                is.close();
                return htmlResponse(new String(baos.toByteArray(), StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {}
        return htmlResponse("<h1>Debug page not found (modulePath=" + sModulePath + ")</h1>", 404);
    }

    // --- Helpers ---
    private static byte[] jsonResponse(String json) { return jsonResponse(json, 200); }

    private static byte[] jsonResponse(String json, int code) {
        return response(json, "application/json; charset=utf-8", code);
    }

    private static byte[] htmlResponse(String html) { return htmlResponse(html, 200); }

    private static byte[] htmlResponse(String html, int code) {
        return response(html, "text/html; charset=utf-8", code);
    }

    private static byte[] response(String body, String contentType, int code) {
        String status = code == 200 ? "OK" : (code == 400 ? "Bad Request" : "Error");
        String header = "HTTP/1.1 " + code + " " + status + "\r\n" +
            "Content-Type: " + contentType + "\r\n" +
            "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n" +
            "Access-Control-Allow-Origin: *\r\n" +
            "Connection: close\r\n" +
            "\r\n";
        byte[] headerBytes = header.getBytes(StandardCharsets.US_ASCII);
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        byte[] full = new byte[headerBytes.length + bodyBytes.length];
        System.arraycopy(headerBytes, 0, full, 0, headerBytes.length);
        System.arraycopy(bodyBytes, 0, full, headerBytes.length, bodyBytes.length);
        return full;
    }

    private static String extractJsonField(String json, String field) {
        if (json == null || field == null) return "";
        String key = "\"" + field + "\":";
        int start = json.indexOf(key);
        if (start < 0) return "";
        start += key.length();
        while (start < json.length() && json.charAt(start) == ' ') start++;
        if (start >= json.length()) return "";
        char first = json.charAt(start);
        if (first == '"') {
            int end = json.indexOf('"', start + 1);
            if (end < 0) return "";
            return json.substring(start + 1, end);
        } else {
            int end = start;
            while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') end++;
            return json.substring(start, end).trim();
        }
    }
}
