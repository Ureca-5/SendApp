package com.mycom.myapp.sendapp.global.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 생성된 csv 파일 검산기입니다.
 *
 * Usage:
 *   java DataIntegrityCheck <gen_out_dir> <fixed_csv_dir> [--check-total-rows]
 *
 * Example:
 *   java DataIntegrityCheck ./out_csv/gen_202510_n1000000_s72_dev ./src/main/resources/fixed_csv --check-total-rows
 */
public class DataIntegrityCheck {

    private static final int EXPECT_USERS = 1_000_000;

    public static void test(String[] args) throws Exception {
        final String DEFAULT_GEN_OUT_DIR =
                ""; // 산출물 폴더 주소

        // fixed_csv 폴더(예: src/main/resources/fixed_csv)
        final String DEFAULT_FIXED_CSV_DIR =
                ""; //고정파일 저장 폴더 주소

        // 총 row 500만 검증 기본 ON/OFF
        final boolean DEFAULT_CHECK_TOTAL_ROWS = true;

        File genDir;
        File fixedDir;
        boolean checkTotalRows = DEFAULT_CHECK_TOTAL_ROWS;

        if (args.length == 0) {
            genDir = new File(DEFAULT_GEN_OUT_DIR);
            fixedDir = new File(DEFAULT_FIXED_CSV_DIR);
        } else if (args.length == 1) {
            // 폴더 하나만 주면: gen_out_dir로 보고 fixed는 기본값 사용
            genDir = new File(args[0]);
            fixedDir = new File(DEFAULT_FIXED_CSV_DIR);
        } else {
            genDir = new File(args[0]);
            fixedDir = new File(args[1]);
            if (args.length >= 3) {
                checkTotalRows = "--check-total-rows".equalsIgnoreCase(args[2]);
            }
        }
//        if (args.length < 2) {
//            System.out.println("Usage: java DataIntegrityCheck <gen_out_dir> <fixed_csv_dir> [--check-total-rows]");
//            return;
//        }
//
//        File genDir = new File(args[0]);
//        File fixedDir = new File(args[1]);
 //       boolean checkTotalRows = (args.length >= 3) && "--check-total-rows".equalsIgnoreCase(args[2]);

        File usersCsv = new File(genDir, "users.csv");
        File devCsv = new File(genDir, "users_device.csv");
        File subCsv = new File(genDir, "subscribe_billing_history.csv");
        File microCsv = new File(genDir, "micro_payment_billing_history.csv");
        File udsCsv = new File(genDir, "user_delivery_settings.csv");

        File subscribeServiceFixed = new File(fixedDir, "subscribe_service.csv");

        mustExist(usersCsv);
        mustExist(devCsv);
        mustExist(subCsv);
        mustExist(microCsv);
        mustExist(udsCsv);
        mustExist(subscribeServiceFixed);

        // 1) fixed: subscribe_service_id -> category_id
        Int2IntMap svcIdToCat = loadSubscribeServiceCategoryMap(subscribeServiceFixed);

        // 2) users: collect user ids (HashSet<Long> ~ 1,000,000 OK)
        System.out.println("[1/6] Reading users.csv ...");
        Set<Long> userIds = new HashSet<>(EXPECT_USERS * 2);
        long usersCount = scanUsers(usersCsv, userIds);
        assertEquals("users count", EXPECT_USERS, usersCount);

        // 3) user_delivery_settings: validate ranges + FK to users
        System.out.println("[2/6] Reading user_delivery_settings.csv ...");
        long udsCount = scanUserDeliverySettings(udsCsv, userIds);
        assertEquals("user_delivery_settings count", EXPECT_USERS, udsCount);

        // 4) devices: collect device ids + validate FK users
        System.out.println("[3/6] Reading users_device.csv ...");
        Set<Long> deviceIds = new HashSet<>(EXPECT_USERS * 2); // 대략
        long deviceCount = scanDevices(devCsv, userIds, deviceIds);
        if (deviceCount <= 0) throw new IllegalStateException("deviceCount is 0?");

        // 5) subscribe billing: validate FK users/device + validate plan-change rule per (u,d,yyyymm,cat in {1,3})
        System.out.println("[4/6] Reading subscribe_billing_history.csv ...");
        long subCount = scanSubscribeBilling(subCsv, userIds, deviceIds, svcIdToCat);

        // 6) micro billing: validate FK users
        System.out.println("[5/6] Reading micro_payment_billing_history.csv ...");
        long microCount = scanMicroBilling(microCsv, userIds);

        if (checkTotalRows) {
            System.out.println("[6/6] Checking total rows ...");
            long total = subCount + microCount;
            if (total != 5_000_000L) {
                throw new IllegalStateException("Total bill rows mismatch. sub=" + subCount + ", micro=" + microCount + ", total=" + total);
            }
        }

        System.out.println("\n✅ PASS");
        System.out.println("users=" + usersCount);
        System.out.println("user_delivery_settings=" + udsCount);
        System.out.println("devices=" + deviceCount);
        System.out.println("subscribe_billing_history=" + subCount);
        System.out.println("micro_payment_billing_history=" + microCount);
    }

    // =========================
    // Core Scanners (streaming)
    // =========================

    private static long scanUsers(File f, Set<Long> userIds) throws IOException {
        try (BufferedReader br = openUtf8BomAware(f)) {
            String line = br.readLine(); // header
            if (line == null) throw new IllegalStateException("Empty users.csv");
            long cnt = 0;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] r = splitCsvLineSimple(line);
                // users_id is col 0
                long userId = parseLong(r, 0, "users.users_id");
                if (!userIds.add(userId)) {
                    throw new IllegalStateException("Duplicate users_id: " + userId);
                }
                cnt++;
            }
            return cnt;
        }
    }

    private static long scanUserDeliverySettings(File f, Set<Long> userIds) throws IOException {
        Set<Long> seen = new HashSet<>(EXPECT_USERS * 2);
        try (BufferedReader br = openUtf8BomAware(f)) {
            String line = br.readLine(); // header
            if (line == null) throw new IllegalStateException("Empty user_delivery_settings.csv");
            long cnt = 0;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] r = splitCsvLineSimple(line);

                long userId = parseLong(r, 0, "uds.user_id");
                int hour = parseInt(r, 1, "uds.preferred_hour");
                int day = parseInt(r, 2, "uds.preferred_day");

                if (!seen.add(userId)) throw new IllegalStateException("Duplicate user_delivery_settings.user_id: " + userId);
                if (!userIds.contains(userId)) throw new IllegalStateException("user_delivery_settings.user_id not found in users: " + userId);

                if (hour < 0 || hour > 23) throw new IllegalStateException("preferred_hour out of range: user_id=" + userId + " hour=" + hour);
                if (day < 1 || day > 31) throw new IllegalStateException("preferred_day out of range: user_id=" + userId + " day=" + day);

                cnt++;
            }
            return cnt;
        }
    }

    private static long scanDevices(File f, Set<Long> userIds, Set<Long> deviceIds) throws IOException {
        try (BufferedReader br = openUtf8BomAware(f)) {
            String line = br.readLine(); // header
            if (line == null) throw new IllegalStateException("Empty users_device.csv");
            long cnt = 0;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] r = splitCsvLineSimple(line);

                long deviceId = parseLong(r, 0, "device.device_id");
                long userId = parseLong(r, 1, "device.users_id");

                if (!deviceIds.add(deviceId)) throw new IllegalStateException("Duplicate device_id: " + deviceId);
                if (!userIds.contains(userId)) throw new IllegalStateException("users_device.users_id not found in users: " + userId);

                cnt++;
            }
            return cnt;
        }
    }

    private static long scanSubscribeBilling(File f, Set<Long> userIds, Set<Long> deviceIds, Int2IntMap svcToCat) throws IOException {
        // (userId, deviceId, yyyymm, cat) -> count ; cat only 1 or 3
        // 메모리: 5M rows에서 전부 키 저장하면 터짐.
        // 해결: cat in {1,3}만 추적 + "2개 초과"만 잡으면 됨. 즉, count가 2 넘어가는 순간 즉시 실패.
        Long4KeyCounter counter = new Long4KeyCounter(2_000_000);

        try (BufferedReader br = openUtf8BomAware(f)) {
            String line = br.readLine(); // header
            if (line == null) throw new IllegalStateException("Empty subscribe_billing_history.csv");
            long cnt = 0;

            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] r = splitCsvLineSimple(line);

                long userId = parseLong(r, 1, "sub.users_id");
                long deviceId = parseLong(r, 2, "sub.device_id");
                int subscribeServiceId = parseInt(r, 3, "sub.subscribe_service_id");
                int yyyymm = parseInt(r, 9, "sub.billing_yyyymm");

                if (!userIds.contains(userId)) throw new IllegalStateException("subscribe_billing_history.users_id not in users: " + userId);
                if (!deviceIds.contains(deviceId)) throw new IllegalStateException("subscribe_billing_history.device_id not in users_device: " + deviceId);

                int cat = svcToCat.getOrDefault(subscribeServiceId, -1);
                if (cat == 1 || cat == 3) {
                    // 월 1회 변경 => 최대 2줄
                    int c = counter.increment(userId, deviceId, yyyymm, cat);
                    if (c > 2) {
                        throw new IllegalStateException("Plan change rule violated (cat=" + cat + "): user=" + userId + ", device=" + deviceId + ", yyyymm=" + yyyymm + ", count=" + c);
                    }
                }

                cnt++;
                if ((cnt % 1_000_000) == 0) {
                    System.out.println("  ... subscribe rows scanned=" + cnt);
                }
            }
            return cnt;
        }
    }

    private static long scanMicroBilling(File f, Set<Long> userIds) throws IOException {
        try (BufferedReader br = openUtf8BomAware(f)) {
            String line = br.readLine(); // header
            if (line == null) throw new IllegalStateException("Empty micro_payment_billing_history.csv");
            long cnt = 0;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] r = splitCsvLineSimple(line);

                long userId = parseLong(r, 1, "micro.users_id");
                if (!userIds.contains(userId)) throw new IllegalStateException("micro_payment_billing_history.users_id not in users: " + userId);

                cnt++;
                if ((cnt % 1_000_000) == 0) {
                    System.out.println("  ... micro rows scanned=" + cnt);
                }
            }
            return cnt;
        }
    }

    // =========================
    // fixed csv loader (MS949)
    // =========================
    private static Int2IntMap loadSubscribeServiceCategoryMap(File f) throws IOException {
        // subscribe_service.csv: id,name,subscribe_category_id,fee
        Int2IntMap map = new Int2IntMap(4096);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "MS949"))) {
            String line = br.readLine(); // header
            if (line == null) throw new IllegalStateException("Empty fixed subscribe_service.csv");
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] r = line.split(",", -1);
                if (r.length < 4) continue;
                int id = Integer.parseInt(r[0].trim());
                int cat = Integer.parseInt(r[2].trim());
                map.put(id, cat);
            }
        }
        return map;
    }

    // =========================
    // CSV helpers (simple, for your generated format)
    // =========================
    private static BufferedReader openUtf8BomAware(File f) throws IOException {
        InputStream is = new FileInputStream(f);
        PushbackInputStream pb = new PushbackInputStream(is, 3);
        byte[] bom = new byte[3];
        int n = pb.read(bom, 0, 3);
        if (n == 3) {
            if (!(bom[0] == (byte)0xEF && bom[1] == (byte)0xBB && bom[2] == (byte)0xBF)) {
                pb.unread(bom, 0, 3);
            }
        } else if (n > 0) {
            pb.unread(bom, 0, n);
        }
        return new BufferedReader(new InputStreamReader(pb, StandardCharsets.UTF_8), 1 << 20);
    }

    /**
     * Your generator uses: comma-separated + quotes only when needed; values can contain commas/quotes.
     * This simple splitter supports quoted CSV minimally.
     */
    private static String[] splitCsvLineSimple(String line) {
        List<String> out = new ArrayList<>(16);
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inQuote) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuote = false;
                    }
                } else {
                    cur.append(ch);
                }
            } else {
                if (ch == ',') {
                    out.add(cur.toString());
                    cur.setLength(0);
                } else if (ch == '"') {
                    inQuote = true;
                } else {
                    cur.append(ch);
                }
            }
        }
        out.add(cur.toString());
        return out.toArray(new String[0]);
    }

    private static long parseLong(String[] r, int idx, String field) {
        if (idx >= r.length) throw new IllegalStateException("Missing column for " + field + " idx=" + idx);
        String s = r[idx].trim();
        if (s.isEmpty()) throw new IllegalStateException("Empty value for " + field);
        return Long.parseLong(s);
    }

    private static int parseInt(String[] r, int idx, String field) {
        if (idx >= r.length) throw new IllegalStateException("Missing column for " + field + " idx=" + idx);
        String s = r[idx].trim();
        if (s.isEmpty()) throw new IllegalStateException("Empty value for " + field);
        return Integer.parseInt(s);
    }

    private static void mustExist(File f) {
        if (!f.exists()) throw new IllegalArgumentException("File not found: " + f.getAbsolutePath());
    }

    private static void assertEquals(String label, long expected, long actual) {
        if (expected != actual) {
            throw new IllegalStateException(label + " mismatch. expected=" + expected + ", actual=" + actual);
        }
    }

    // =========================
    // Lightweight int->int map (avoid boxing)
    // =========================
    static class Int2IntMap {
        private int[] keys;
        private int[] vals;
        private boolean[] used;
        private int size;

        Int2IntMap(int cap) {
            int n = 1;
            while (n < cap * 2) n <<= 1;
            keys = new int[n];
            vals = new int[n];
            used = new boolean[n];
        }

        void put(int k, int v) {
            if (size * 2 >= keys.length) rehash();
            int mask = keys.length - 1;
            int i = mix(k) & mask;
            while (used[i]) {
                if (keys[i] == k) { vals[i] = v; return; }
                i = (i + 1) & mask;
            }
            used[i] = true;
            keys[i] = k;
            vals[i] = v;
            size++;
        }

        int getOrDefault(int k, int def) {
            int mask = keys.length - 1;
            int i = mix(k) & mask;
            while (used[i]) {
                if (keys[i] == k) return vals[i];
                i = (i + 1) & mask;
            }
            return def;
        }

        private void rehash() {
            int[] oldK = keys;
            int[] oldV = vals;
            boolean[] oldU = used;

            keys = new int[oldK.length << 1];
            vals = new int[oldV.length << 1];
            used = new boolean[oldU.length << 1];
            size = 0;

            for (int i = 0; i < oldK.length; i++) {
                if (oldU[i]) put(oldK[i], oldV[i]);
            }
        }

        private int mix(int x) {
            x ^= (x >>> 16);
            x *= 0x7feb352d;
            x ^= (x >>> 15);
            x *= 0x846ca68b;
            x ^= (x >>> 16);
            return x;
        }
    }

    // =========================
    // Counter for 4-long key (userId, deviceId, yyyymm, cat) -> count
    // We only need to detect count > 2.
    // =========================
    static class Long4KeyCounter {
        private long[] k1, k2, k3, k4;
        private byte[] c;
        private boolean[] used;
        private int size;

        Long4KeyCounter(int cap) {
            int n = 1;
            while (n < cap * 2) n <<= 1;
            k1 = new long[n];
            k2 = new long[n];
            k3 = new long[n];
            k4 = new long[n];
            c = new byte[n];
            used = new boolean[n];
        }

        int increment(long a, long b, long d, long e) {
            if (size * 2 >= k1.length) rehash();
            int mask = k1.length - 1;
            int i = (int)(mix4(a, b, d, e) & mask);
            while (used[i]) {
                if (k1[i] == a && k2[i] == b && k3[i] == d && k4[i] == e) {
                    int nv = (c[i] & 0xFF) + 1;
                    c[i] = (byte)Math.min(255, nv);
                    return nv;
                }
                i = (i + 1) & mask;
            }
            used[i] = true;
            k1[i] = a; k2[i] = b; k3[i] = d; k4[i] = e;
            c[i] = 1;
            size++;
            return 1;
        }

        private void rehash() {
            long[] ok1 = k1, ok2 = k2, ok3 = k3, ok4 = k4;
            byte[] oc = c;
            boolean[] ou = used;

            int n = ok1.length << 1;
            k1 = new long[n]; k2 = new long[n]; k3 = new long[n]; k4 = new long[n];
            c = new byte[n];
            used = new boolean[n];
            size = 0;

            for (int i = 0; i < ok1.length; i++) {
                if (!ou[i]) continue;
                long a = ok1[i], b = ok2[i], d = ok3[i], e = ok4[i];
                int cnt = oc[i] & 0xFF;
                // reinsert with existing count
                int mask = k1.length - 1;
                int j = (int)(mix4(a, b, d, e) & mask);
                while (used[j]) j = (j + 1) & mask;
                used[j] = true;
                k1[j]=a; k2[j]=b; k3[j]=d; k4[j]=e;
                c[j]=(byte)cnt;
                size++;
            }
        }

        private long mix4(long a, long b, long c, long d) {
            long x = a * 0x9E3779B97F4A7C15L;
            x ^= b * 0xC2B2AE3D27D4EB4FL;
            x ^= c * 0x165667B19E3779F9L;
            x ^= d * 0x27D4EB2F165667C5L;
            x ^= (x >>> 33);
            x *= 0xff51afd7ed558ccdL;
            x ^= (x >>> 33);
            x *= 0xc4ceb9fe1a85ec53L;
            x ^= (x >>> 33);
            return x;
        }
    }
}
