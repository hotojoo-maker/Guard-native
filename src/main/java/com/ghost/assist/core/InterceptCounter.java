package com.ghost.assist.core;

import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;

/**
 * Interception counter for F04 (conversation), F05 (moments), F07 (contacts).
 * Plus a recent event queue for the debug overlay/HTTP console.
 */
public class InterceptCounter {

    private static final String TAG = "NCL";
    private static final String KEY_F04 = "ic04";
    private static final String KEY_F05 = "ic05";
    private static final String KEY_F07 = "ic07";
    private static final String KEY_TOTAL = "ictt";
    private static final int MAX_EVENTS = 50;

    public static class Event {
        public final long timestamp;
        public final String type;   // F04 / F05 / F07
        public final String detail; // wxid or brief desc
        public Event(String type, String detail) {
            this.timestamp = System.currentTimeMillis();
            this.type = type;
            this.detail = detail;
        }
    }

    private static final InterceptCounter sInstance = new InterceptCounter();
    private final LinkedList<Event> mEvents = new LinkedList<>();

    public static InterceptCounter getInstance() { return sInstance; }

    public void init() {
        Log.i(TAG, "[IC] restored F04=" + getF04() + " F05=" + getF05() + " F07=" + getF07());
    }

    // --- Increment ---
    public synchronized void incF04(String detail) { inc("F04", KEY_F04, detail); }
    public synchronized void incF05(String detail) { inc("F05", KEY_F05, detail); }
    public synchronized void incF07(String detail) { inc("F07", KEY_F07, detail); }

    private void inc(String type, String key, String detail) {
        Bridge bridge = Bridge.getInstance();
        bridge.putInt(key, bridge.getInt(key, 0) + 1);
        bridge.putInt(KEY_TOTAL, bridge.getInt(KEY_TOTAL, 0) + 1);

        synchronized (mEvents) {
            mEvents.addFirst(new Event(type, detail));
            while (mEvents.size() > MAX_EVENTS) mEvents.removeLast();
        }
    }

    // --- Getters ---
    public int getF04() { return Bridge.getInstance().getInt(KEY_F04, 0); }
    public int getF05() { return Bridge.getInstance().getInt(KEY_F05, 0); }
    public int getF07() { return Bridge.getInstance().getInt(KEY_F07, 0); }
    public int getTotal() { return Bridge.getInstance().getInt(KEY_TOTAL, 0); }

    public LinkedList<Event> getRecentEvents() {
        synchronized (mEvents) {
            return new LinkedList<>(mEvents);
        }
    }

    /** Reset all counters */
    public synchronized void resetAll() {
        Bridge.getInstance().putInt(KEY_F04, 0);
        Bridge.getInstance().putInt(KEY_F05, 0);
        Bridge.getInstance().putInt(KEY_F07, 0);
        Bridge.getInstance().putInt(KEY_TOTAL, 0);
        synchronized (mEvents) { mEvents.clear(); }
    }

    /** Get summary string for notification / debug */
    public String getSummary() {
        return "F04=" + getF04() + " F05=" + getF05() + " F07=" + getF07();
    }

    /** Build a JSON-like snapshot for the debug server */
    public String toJsonSnapshot() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"total\":").append(getTotal()).append(",");
        sb.append("\"f04\":").append(getF04()).append(",");
        sb.append("\"f05\":").append(getF05()).append(",");
        sb.append("\"f07\":").append(getF07()).append(",");
        sb.append("\"events\":[");
        LinkedList<Event> events = getRecentEvents();
        int i = 0;
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.US);
        for (Event e : events) {
            if (i > 0) sb.append(",");
            sb.append("{\"t\":\"").append(sdf.format(new Date(e.timestamp))).append("\",");
            sb.append("\"type\":\"").append(e.type).append("\",");
            sb.append("\"d\":\"").append(escapeJson(e.detail)).append("\"}");
            i++;
            if (i >= 20) break;
        }
        sb.append("]");
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
