package com.mycom.myapp.sendapp.delivery.worker.util;

import java.util.Map;

public final class StreamValue {

    private StreamValue() {}

    public static String req(Map<String, String> m, String key) {
        String v = m.get(key);
        if (v == null) throw new IllegalArgumentException("Missing stream field: " + key);
        v = v.trim();
        if (v.isEmpty()) throw new IllegalArgumentException("Blank stream field: " + key);
        return v;
    }

    public static String opt(Map<String, String> m, String key, String def) {
        String v = m.get(key);
        if (v == null) return def;
        v = v.trim();
        return v.isEmpty() ? def : v;
    }

    public static long reqLong(Map<String, String> m, String key) {
        String v = req(m, key);
        try {
            return Long.parseLong(v);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid long for key=" + key + ", value=" + v, e);
        }
    }

    public static int optInt(Map<String, String> m, String key, int def) {
        String v = opt(m, key, null);
        if (v == null) return def;
        try {
            return Integer.parseInt(v);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid int for key=" + key + ", value=" + v, e);
        }
    }
}

