package com.ghost.assist.debug;

import com.ghost.assist.core.Bridge;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * Structured debug telemetry for the HTTP console (V2).
 * Ring buffers + per-channel metrics: seen / parsed / matched / blocked / removed / dropped.
 */
public final class DebugTelemetry {

    public static final class Metrics {
        public long seen;
        public long parsed;
        public long matched;
        public long blocked;
        public long removed;
        public long uiHidden;
        public long dropped;
    }

    public static final class Event {
        public final long ts;
        public final String channel;
        public final String kind;
        public final Map<String, String> fields;

        Event(String channel, String kind, Map<String, String> fields) {
            this.ts = System.currentTimeMillis();
            this.channel = channel;
            this.kind = kind;
            this.fields = fields != null ? fields : new HashMap<>();
        }
    }

    private static final DebugTelemetry sInstance = new DebugTelemetry();

    private static final int EVENT_MAX = 800;
    private static final int EVENT_MAX_PER_CHANNEL = 200;
    private static final long MIN_EMIT_MS = 30L;

    private final ArrayDeque<Event> mEvents = new ArrayDeque<>();
    private final Map<String, Metrics> mMetrics = new HashMap<>();
    private final Map<String, Long> mLastEmit = new HashMap<>();

    // Moments snapshot for panel
    private volatile String mPage = "Unknown";
    private volatile String mActivity = "";
    private volatile String mTab = "";
    private volatile int mVisibleFeedEstimate = 0;
    private final LinkedHashMap<String, String> mVisibleWxids = new LinkedHashMap<>();
    private static final int VISIBLE_WXID_MAX = 40;

    public static DebugTelemetry getInstance() { return sInstance; }

    public static boolean isNoiseClass(String cn) {
        if (cn == null || cn.isEmpty()) return true;
        if (cn.startsWith("java.") || cn.startsWith("android.") || cn.startsWith("androidx.")
                || cn.startsWith("kotlin.")) return true;
        String low = cn.toLowerCase(Locale.US);
        if (low.contains("weakreference") || low.contains("textview")
                || low.contains("appbrand") || low.contains("autofill")
                || low.contains("music") || low.contains("banner")
                || low.contains("glide") || low.contains("emoji")) return true;
        if (cn.startsWith("wq.")) return true;
        return false;
    }

    /** Classify addAll / hook target into channel; null = noise */
    public static String channelForClass(String cn) {
        if (cn == null) return null;
        if (isNoiseClass(cn)) return "noise";
        if (cn.contains("SnsMsgUI") || cn.equals("jw1.d")) return "badge";
        if (cn.contains("na4.b") || cn.contains("la4.p") || cn.contains("k24.b")) return "moments";
        if (cn.contains("e56") || cn.contains("di0") || cn.contains("i84.y")) return "like_comment";
        if (cn.contains("kc5.") || cn.contains("f45.") || cn.contains("mvvmlist")) return "conv";
        if (cn.toLowerCase(Locale.US).contains("address") || cn.contains("contact")) return "contact";
        return null;
    }

    public synchronized void setPageContext(String activity, String page, String tab) {
        if (activity != null) mActivity = activity;
        if (page != null) mPage = page;
        if (tab != null) mTab = tab;
    }

    public synchronized void emit(String channel, String kind, Map<String, String> fields) {
        if (channel == null) channel = "misc";
        String key = channel + ":" + kind;
        long now = System.currentTimeMillis();
        Long last = mLastEmit.get(key);
        if (last != null && now - last < MIN_EMIT_MS) {
            metric(channel).dropped++;
            return;
        }
        mLastEmit.put(key, now);

        Event e = new Event(channel, kind, fields);
        mEvents.addFirst(e);
        while (mEvents.size() > EVENT_MAX) mEvents.removeLast();

        trimChannel(channel);
        metric(channel).seen++;

        String wxid = fields != null ? fields.get("wxid") : null;
        if (wxid != null && wxid.length() > 5) {
            metric(channel).parsed++;
            if (Bridge.getInstance().getWxids().contains(wxid)) {
                metric(channel).matched++;
            }
        }
    }

    public synchronized void incBlocked(String channel) {
        metric(channel).blocked++;
        Map<String, String> f = new HashMap<>();
        f.put("page", mPage);
        emit(channel, "blocked", f);
    }

    /** 仅增加 blocked 计数，不额外 emit（配合 emit() 使用，避免 seen 重复计数）*/
    public synchronized void addBlocked(String channel) {
        metric(channel).blocked++;
    }

    public synchronized void incRemoved(String channel, int n) {
        if (n <= 0) return;
        metric(channel).removed += n;
        Map<String, String> f = new HashMap<>();
        f.put("count", String.valueOf(n));
        f.put("page", mPage);
        emit(channel, "removed", f);
    }

    /** 仅增加 removed 计数，不额外 emit */
    public synchronized void addRemoved(String channel, int n) {
        if (n > 0) metric(channel).removed += n;
    }

    public synchronized void noteFeedWxid(String wxid, String nick) {
        if (wxid == null || !wxid.startsWith("wxid_")) return;
        mVisibleWxids.remove(wxid);
        mVisibleWxids.put(wxid, nick != null ? nick : "");
        while (mVisibleWxids.size() > VISIBLE_WXID_MAX) {
            mVisibleWxids.remove(mVisibleWxids.entrySet().iterator().next().getKey());
        }
        Map<String, String> f = new HashMap<>();
        f.put("wxid", wxid);
        f.put("page", "Moments");
        emit("moments", "feed_seen", f);
    }

    public synchronized void setVisibleFeedEstimate(int n) {
        mVisibleFeedEstimate = n;
    }

    private void trimChannel(String channel) {
        int count = 0;
        for (Event e : mEvents) {
            if (channel.equals(e.channel)) count++;
        }
        if (count <= EVENT_MAX_PER_CHANNEL) return;
        for (int i = mEvents.size() - 1; i >= 0 && count > EVENT_MAX_PER_CHANNEL; i--) {
            Event e = mEvents.peekLast();
            if (e != null && channel.equals(e.channel)) {
                mEvents.remove(e);
                count--;
                metric(channel).dropped++;
            }
        }
    }

    private Metrics metric(String channel) {
        Metrics m = mMetrics.get(channel);
        if (m == null) {
            m = new Metrics();
            mMetrics.put(channel, m);
        }
        return m;
    }

    public synchronized List<Event> getEvents(String channelFilter, int limit) {
        List<Event> out = new ArrayList<>();
        int n = 0;
        for (Event e : mEvents) {
            if (channelFilter != null && !channelFilter.isEmpty() && !channelFilter.equals(e.channel)) continue;
            out.add(e);
            n++;
            if (n >= limit) break;
        }
        return out;
    }

    public synchronized String toJsonSnapshot() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"page\":{");
        sb.append("\"activity\":\"").append(esc(mActivity)).append("\",");
        sb.append("\"page\":\"").append(esc(mPage)).append("\",");
        sb.append("\"tab\":\"").append(esc(mTab)).append("\",");
        sb.append("\"visible_feed_count\":").append(mVisibleFeedEstimate).append(",");
        sb.append("\"visible_wxids\":").append(wxidsJson()).append(",");
        sb.append("\"ts\":\"").append(sdf.format(new Date())).append("\"");
        sb.append("},");

        sb.append("\"metrics\":").append(metricsJson()).append(",");
        sb.append("\"events\":").append(eventsJson(null, 80));
        sb.append("}");
        return sb.toString();
    }

    private String wxidsJson() {
        StringBuilder sb = new StringBuilder("[");
        int i = 0;
        Set<String> hidden = Bridge.getInstance().getWxids();
        for (Map.Entry<String, String> e : mVisibleWxids.entrySet()) {
            if (i++ > 0) sb.append(",");
            sb.append("{\"wxid\":\"").append(esc(e.getKey())).append("\",");
            sb.append("\"nick\":\"").append(esc(e.getValue())).append("\",");
            sb.append("\"hidden\":").append(hidden.contains(e.getKey())).append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private String metricsJson() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Metrics> e : mMetrics.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            Metrics m = e.getValue();
            sb.append("\"").append(esc(e.getKey())).append("\":{");
            sb.append("\"seen\":").append(m.seen).append(",");
            sb.append("\"parsed\":").append(m.parsed).append(",");
            sb.append("\"matched\":").append(m.matched).append(",");
            sb.append("\"blocked\":").append(m.blocked).append(",");
            sb.append("\"removed\":").append(m.removed).append(",");
            sb.append("\"uiHidden\":").append(m.uiHidden).append(",");
            sb.append("\"dropped\":").append(m.dropped);
            sb.append("}");
        }
        sb.append("}");
        return sb.toString();
    }

    public synchronized String eventsJson(String channelFilter, int limit) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
        StringBuilder sb = new StringBuilder("[");
        int i = 0;
        for (Event e : mEvents) {
            if (channelFilter != null && !channelFilter.isEmpty() && !channelFilter.equals(e.channel)) continue;
            if (i++ > 0) sb.append(",");
            sb.append("{\"ts\":\"").append(sdf.format(new Date(e.ts))).append("\",");
            sb.append("\"channel\":\"").append(esc(e.channel)).append("\",");
            sb.append("\"kind\":\"").append(esc(e.kind)).append("\",");
            sb.append("\"fields\":").append(fieldsJson(e.fields)).append("}");
            if (i >= limit) break;
        }
        sb.append("]");
        return sb.toString();
    }

    private static String fieldsJson(Map<String, String> fields) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : fields.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(esc(e.getKey())).append("\":\"")
              .append(esc(e.getValue())).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "");
    }

    public static Map<String, String> fields(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (kv[i] != null) m.put(kv[i], kv[i + 1] != null ? kv[i + 1] : "");
        }
        return m;
    }
}
