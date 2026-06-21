package com.ghost.assist.debug;

import android.util.Log;

import java.lang.reflect.Method;

/**
 * Resolve WeChat contact info (nickname, avatar) from a wxid.
 * Uses reflection on com.tencent.mm.storage.ContactStorage to query local DB.
 *
 * §5.8: no sensitive strings in class names or identifiers.
 *
 * EVIDENCE: L4 — all class/method/field names below are sourced from Catfish 8.0.70.
 * None have been verified against 8.0.71 (current base). Treat every reflection call as "may fail
 * gracefully". Upgrade to L2 only after live verification on target device.
 * See FAILURE_LOG.md before modifying.
 */
public class ContactResolver {

    private static final String TAG = "NCL";
    private static final String CONTACT_STORAGE_CLASS = "com.tencent.mm.storage.ContactStorage";
    private static final String CONTACT_CLASS = "com.tencent.mm.storage.e4"; // may vary by version

    /**
     * Attempt to resolve wxid → {nickname, avatar_path}.
     * Returns JSON (even on failure) for the debug HTTP API.
     */
    public static String resolveJson(String wxid) {
        if (wxid == null || wxid.isEmpty()) {
            return "{\"error\":\"empty wxid\"}";
        }

        try {
            // Attempt 1: reflect get contact storage + query by wxid
            // In 8.0.71 (assumed, L4 — unverified), storage is accessible via com.tencent.mm.kernel.h
            Class<?> kernelClass = Class.forName("com.tencent.mm.kernel.h");
            Method getStorageMethod = kernelClass.getMethod("getStorage", Class.class);

            Class<?> contactStorageClass = Class.forName(CONTACT_STORAGE_CLASS);
            Object contactStorage = getStorageMethod.invoke(null, contactStorageClass);

            if (contactStorage != null) {
                // Try to get contact by wxid field
                Method getMethod = contactStorageClass.getMethod("get", String.class);
                Object contact = getMethod.invoke(contactStorage, wxid);

                if (contact != null) {
                    // Extract nickname field (field_nickname or "h1")
                    String nickname = getFieldString(contact, "field_nickname");
                    if (nickname == null) nickname = getFieldString(contact, "h1");
                    if (nickname == null) {
                        // Try calling getNickname() method
                        try {
                            Method nickMethod = contact.getClass().getMethod("getNickname");
                            Object nick = nickMethod.invoke(contact);
                            if (nick != null) nickname = nick.toString();
                        } catch (Exception ignored) {}
                    }

                    // Extract avatar (field_avatar or "b") — returns path on disk
                    String avatar = getFieldString(contact, "field_avatar");
                    if (avatar == null) avatar = getFieldString(contact, "b");

                    Log.i(TAG, "[CR] resolved " + wxid + " → " + nickname);
                    return "{\"wxid\":\"" + escape(wxid) + "\"," +
                           "\"nickname\":\"" + escape(nickname != null ? nickname : wxid) + "\"," +
                           "\"avatar\":\"" + escape(avatar != null ? avatar : "") + "\"," +
                           "\"found\":true}";
                }
            }
        } catch (ClassNotFoundException e) {
            // 8.0.71 may have different class names — graceful fallback
            Log.w(TAG, "[CR] ContactStorage class not found (may be different in 8.0.71): " + e.getMessage());
        } catch (Exception e) {
            Log.w(TAG, "[CR] resolution error for " + wxid + ": " + e.getMessage());
        }

        // Not found or error — return placeholder
        return "{\"wxid\":\"" + escape(wxid) + "\"," +
               "\"nickname\":\"" + escape(wxid) + "\"," +
               "\"avatar\":\"\"," +
               "\"found\":false," +
               "\"note\":\"Contact not found or API changed in this version\"}";
    }

    private static String getFieldString(Object obj, String fieldName) {
        try {
            java.lang.reflect.Field f = obj.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            Object val = f.get(obj);
            return val != null ? val.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
