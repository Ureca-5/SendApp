package com.mycom.myapp.sendapp.global.util;

import com.google.crypto.tink.CleartextKeysetHandle;
import com.google.crypto.tink.DeterministicAead;
import com.google.crypto.tink.JsonKeysetReader;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.daead.DeterministicAeadConfig;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Base64;

/**
 * DataGenBatch (persona-based, exact row target, no micro aggregation)
 * 더미데이터 생성기입니다.
 *
 * ✅ CSV 포맷/컬럼/Writer/escape/BOM 규칙 유지
 * ✅ micro_payment_billing_history: 합산 금지 (이벤트 1건 = row 1줄)
 * ✅ users=1,000,000 + bill rows(구독+단건) = 정확히 5,000,000 맞춤(후반 보정 + 마지막 강제)
 * ✅ 초헤비 30명: device 7개(기존 규칙: PHONE 1 + TABLET/WATCH 6), micro 1000~3000 row
 * ✅ user_delivery_settings: users_id 1:1로 100만 row 생성
 * ✅ 요금제 변경: "기존과 다른 요금제"로만 변경 row 생성(디바이스별 월 1회, 확률 매우 낮게)
 *    - PHONE(메인 요금제) 변경: subscribe_billing_history에 동일 device_id로 1줄 추가
 *    - TABLET(기타요금제 CAT=3) 변경: tablet row가 생성된 경우에만 1줄 추가
 *    - subscription_start_date에 "변경일"만 넣고 일할은 배치에서 처리한다는 전제
 */
public class DataGenBatch {

    // ===== 개인정보 암호화(결정적 AEAD) =====
    private static final String ENC_PREFIX_V1 = "v1:";
    private static final String ENV_TINK_KEYSET_B64 = "APP_AES256_KEY_B64";

    private static final String DEFAULT_TINK_KEYSET_B64 =
            "API-KEY-64"; // .env APP_AES256_KEY_B64 연동

    private static final byte[] AD_EMAIL = "users.email".getBytes(StandardCharsets.UTF_8);
    private static final byte[] AD_PHONE = "users.phone".getBytes(StandardCharsets.UTF_8);

    static final class DeterministicCrypto {
        private final DeterministicAead daead;

        private DeterministicCrypto(DeterministicAead daead) {
            this.daead = daead;
        }

        static DeterministicCrypto fromEnvOrDefaultOrThrow() throws Exception {
            String keysetB64 = System.getenv(ENV_TINK_KEYSET_B64);
            if (keysetB64 == null || keysetB64.isBlank()) keysetB64 = DEFAULT_TINK_KEYSET_B64;

            if (keysetB64 == null || keysetB64.isBlank()
                    || "REPLACE_WITH_YOUR_BASE64_TINK_JSON_KEYSET".equals(keysetB64)) {
                throw new IllegalStateException("Missing Tink keyset. Set env " + ENV_TINK_KEYSET_B64 + " or fill DEFAULT_TINK_KEYSET_B64.");
            }

            keysetB64 = keysetB64.trim();
            DeterministicAeadConfig.register();

            String keysetJson = new String(Base64.getDecoder().decode(keysetB64), StandardCharsets.UTF_8);
            KeysetHandle handle = CleartextKeysetHandle.read(JsonKeysetReader.withString(keysetJson));
            DeterministicAead daead = handle.getPrimitive(DeterministicAead.class);
            return new DeterministicCrypto(daead);
        }

        String encryptV1(String plaintext, byte[] associatedData) {
            if (plaintext == null) return "";
            try {
                byte[] ct = daead.encryptDeterministically(plaintext.getBytes(StandardCharsets.UTF_8), associatedData);
                return ENC_PREFIX_V1 + Base64.getEncoder().encodeToString(ct);
            } catch (Exception e) {
                throw new RuntimeException("Deterministic encryption failed", e);
            }
        }
    }

    // ===== mode =====
    enum Mode {
        DEV("dev"), WORST("worst"), CHAOS("chaos");
        final String tag;
        Mode(String tag) { this.tag = tag; }

        static Mode parse(String s) {
            String v = (s == null || s.isBlank()) ? "dev" : s.trim().toLowerCase(Locale.ROOT);
            for (Mode m : values()) if (m.tag.equals(v)) return m;
            throw new IllegalArgumentException("Invalid category: " + s + " (allowed: dev, worst, chaos)");
        }
    }

    // ===== 고정 category id =====
    private static final int CAT_PHONE_PLAN = 1;
    private static final int CAT_ADDON      = 2;
    private static final int CAT_OTHER_PLAN = 3;

    // ===== 목표치 =====
    private static final int TARGET_USERS = 1_000_000;
    private static final long TARGET_BILL_ROWS = 5_000_000L; // subscribe + micro rows (CSV row 기준)

    // 초헤비 유저
    private static final int ULTRA_HEAVY_COUNT = 30;
    private static final int ULTRA_DEVICE_COUNT = 7;           // PHONE 1 + extra 6 (TABLET/WATCH)
    private static final int ULTRA_MICRO_MIN = 1000;
    private static final int ULTRA_MICRO_MAX = 3000;

    // 후반 보정 구간
    private static final int TAIL_CORRECTION_USERS = 30_000;
    private static final int FINAL_FORCE_USERS = 300;

    // ===== 요금제 변경(디바이스별 월 1회) =====
    private static final double PHONE_PLAN_CHANGE_PROB = 0.002;   // 낮게
    private static final double TABLET_PLAN_CHANGE_PROB = 0.001;  // 낮게

    // ===== 출력 포맷 =====
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ===== CSV 입력 인코딩(고정 CSV) =====
    private static final Charset FIXED_CSV_CHARSET = Charset.forName("MS949");

    // ===== 지역 데이터(시 -> 구 -> 동) =====
    private static final City[] CITIES = new City[] {
            City.of("서울특별시",
                    new District("강남구", new String[]{"역삼동","삼성동","대치동","논현동","청담동"}),
                    new District("서초구", new String[]{"서초동","반포동","방배동","양재동"}),
                    new District("송파구", new String[]{"잠실동","문정동","가락동","석촌동"}),
                    new District("마포구", new String[]{"서교동","합정동","연남동","공덕동"}),
                    new District("영등포구", new String[]{"여의도동","당산동","문래동","대림동"})
            ),
            City.of("부산광역시",
                    new District("해운대구", new String[]{"우동","중동","좌동","반여동"}),
                    new District("수영구", new String[]{"광안동","남천동","망미동"}),
                    new District("부산진구", new String[]{"부전동","전포동","양정동"})
            ),
            City.of("인천광역시",
                    new District("남동구", new String[]{"구월동","간석동","논현동"}),
                    new District("연수구", new String[]{"송도동","연수동","청학동"})
            ),
            City.of("대구광역시",
                    new District("수성구", new String[]{"범어동","만촌동","두산동"}),
                    new District("달서구", new String[]{"상인동","월성동","진천동"})
            )
    };

    // ===== 이름 데이터 =====
    private static final String[] LAST_NAMES = {
            "김","이","박","최","정","강","조","윤","장","임",
            "한","오","서","신","권","황","안","송","류","홍"
    };
    private static final String[] FIRST_NAMES_200 = buildFirstNames200();
    private static String[] buildFirstNames200() {
        return new String[]{
                "민준","서준","도윤","예준","시우","하준","주원","지호","지후","준우",
                "현우","도현","건우","우진","선우","서진","민재","윤우","은우","정우",
                "승우","준서","유준","동현","지훈","시윤","태윤","민성","준혁","규민",
                "지환","승민","재윤","수현","민규","시현","재원","태민","민우","지민",
                "하율","서율","지우","서연","서윤","지윤","지아","하윤","지유","채원",
                "윤서","유나","지민","수아","예은","서현","예린","수민","민서","지현",
                "지원","서아","지은","다은","채은","소율","예나","시은","하은","나은",
                "수빈","소연","유진","은채","다연","가은","유림","다현","아린","하린",
                "세아","보민","서영","서희","나연","수진","윤아","은서","채영","지영",
                "민지","혜원","정민","성민","준호","성훈","태현","상현","정현","재현",
                "동훈","성준","기현","영훈","진우","진호","진혁","현준","현수","현석",
                "준영","지성","태우","태호","상우","상민","재민","재호","재훈","광민",
                "나영","가영","소영","지혜","은지","수지","혜진","지현","윤정","유정",
                "민정","수정","혜정","예정","다정","하정","미정","연정","서정","주정",
                "건희","준희","민희","지희","은희","영희","상희","도희","하희","가희",
                "세훈","민훈","지훈","성훈","정훈","재훈","기훈","승훈","태훈","영훈",
                "예성","도성","준성","민성","지성","태성","현성","우성","강성","윤성",
                "예진","수진","지진","다진","하진","서진","민진","윤진","은진","채진",
                "예림","수림","지림","다림","하림","서림","민림","윤림","은림","채림",
                "예원","수원","지원","다원","하원","서원","민원","윤원","은원","채원",
                "예지","수지","지지","다지","하지","서지","민지","윤지","은지","채지",
                "도영","준영","민영","지영","서영","윤영","은영","채영","하영","가영",
                "도훈","준훈","민훈","지훈","서훈","윤훈","은훈","채훈","하훈","가훈"
        };
    }

    // ===== 페르소나 =====
    enum PlanTier { PLAN_L, PLAN_M, PLAN_H, PLAN_SIG }
    enum AddonType { OTT, SAFE, INS, GAME, MUSIC, ETC }
    enum MicroType { DATA, DEVICE, MICRO }

    static final class Persona {
        final String id;
        final String name;
        final double weight; // pick용 (P01~P16+P99 합 1.0)
        final PlanTier planTier;

        final double addonLambda;
        final int addonCap;
        final boolean addonFixedOtt;
        final boolean addonFixedSafe;
        final boolean addonFixedIns;
        final double addonChurn;

        final double singleLambda;
        final int singleCap;
        final EnumMap<MicroType, Double> microMix;

        final double discountMinPct;
        final double discountMaxPct;

        final double otherPlanProb; // tablet 존재 시 CAT=3 생성확률

        Persona(
                String id, String name, double weight, PlanTier planTier,
                double addonLambda, int addonCap,
                boolean addonFixedOtt, boolean addonFixedSafe, boolean addonFixedIns,
                double addonChurn,
                double singleLambda, int singleCap,
                EnumMap<MicroType, Double> microMix,
                double discountMinPct, double discountMaxPct,
                double otherPlanProb
        ) {
            this.id = id;
            this.name = name;
            this.weight = weight;
            this.planTier = planTier;
            this.addonLambda = addonLambda;
            this.addonCap = addonCap;
            this.addonFixedOtt = addonFixedOtt;
            this.addonFixedSafe = addonFixedSafe;
            this.addonFixedIns = addonFixedIns;
            this.addonChurn = addonChurn;
            this.singleLambda = singleLambda;
            this.singleCap = singleCap;
            this.microMix = microMix;
            this.discountMinPct = discountMinPct;
            this.discountMaxPct = discountMaxPct;
            this.otherPlanProb = otherPlanProb;
        }
    }
    private static EnumMap<MicroType, Double> mix(double micro, double data, double device) {

    	EnumMap<MicroType, Double> m = new EnumMap<>(MicroType.class);

    	m.put(MicroType.MICRO, micro);

    	m.put(MicroType.DATA, data);

    	m.put(MicroType.DEVICE, device);

    	return m;

    	}
    private static List<Persona> buildPersonas() {
        List<Persona> ps = new ArrayList<>();


        ps.add(new Persona("P01","10대 게임과금형",0.06,PlanTier.PLAN_L, 0.3,2,false,false,false,0.0, 3.8,25, mix(0.75,0.20,0.05), 0.00,0.03, 0.55));
        ps.add(new Persona("P02","10대 데이터초과형",0.04,PlanTier.PLAN_L, 0.1,1,false,false,false,0.0, 3.0,20, mix(0.20,0.80,0.00), 0.00,0.02, 0.60));
        ps.add(new Persona("P03","20대 OTT/정기구독형",0.07,PlanTier.PLAN_M, 1.2,3,true,false,false,0.0, 1.6,12, mix(0.80,0.15,0.05), 0.03,0.08, 0.50));
        ps.add(new Persona("P04","20대 알뜰/저활동형",0.06,PlanTier.PLAN_L, 0.2,1,false,false,false,0.0, 0.2,2,  mix(0.70,0.25,0.05), 0.05,0.15, 0.25));
        ps.add(new Persona("P05","20대 부가변동형",0.03,PlanTier.PLAN_M, 0.9,3,false,false,false,0.35, 0.6,6, mix(0.70,0.20,0.10), 0.00,0.05, 0.55));
        ps.add(new Persona("P06","30대 헤비데이터/콘텐츠형",0.06,PlanTier.PLAN_H, 2.2,5,true,false,false,0.0, 7.0,55, mix(0.35,0.55,0.10), 0.00,0.05, 0.70));
        ps.add(new Persona("P07","30대 직장인 베이스형",0.10,PlanTier.PLAN_M, 0.5,2,false,false,false,0.0, 0.8,6, mix(0.70,0.25,0.05), 0.00,0.05, 0.50));
        ps.add(new Persona("P08","30대 쇼핑/구독형",0.05,PlanTier.PLAN_M, 1.0,3,true,true,false,0.0, 1.8,15, mix(0.70,0.20,0.10), 0.02,0.07, 0.55));
        ps.add(new Persona("P09","30대 단말구매/할부형",0.06,PlanTier.PLAN_M, 0.5,2,false,false,true,0.0, 0.3,2, mix(0.10,0.05,0.85), 0.00,0.03, 0.65));
        ps.add(new Persona("P10","40대 가족대표/안심형",0.10,PlanTier.PLAN_SIG,1.2,3,false,true,false,0.0, 0.7,6, mix(0.40,0.20,0.40), 0.00,0.05, 0.75));
        ps.add(new Persona("P11","40대 보호서비스 선호형",0.07,PlanTier.PLAN_M, 1.0,2,false,false,true,0.0, 0.2,2, mix(0.60,0.25,0.15), 0.03,0.08, 0.55));
        ps.add(new Persona("P12","40대 균형형",0.06,PlanTier.PLAN_M, 0.8,3,false,false,false,0.0, 1.0,8, mix(0.65,0.25,0.10), 0.00,0.06, 0.55));
        ps.add(new Persona("P13","50대 고소득/프리미엄형",0.05,PlanTier.PLAN_SIG,0.8,2,false,false,true,0.0, 1.2,10, mix(0.20,0.45,0.35), 0.02,0.06, 0.65));
        ps.add(new Persona("P14","50대 초과데이터형",0.04,PlanTier.PLAN_L, 0.1,1,false,false,false,0.0, 2.8,22, mix(0.10,0.85,0.05), 0.00,0.03, 0.35));
        ps.add(new Persona("P15","50대 라이트 사용자",0.04,PlanTier.PLAN_L, 0.2,1,false,false,false,0.0, 0.2,2,  mix(0.70,0.25,0.05), 0.03,0.10, 0.20));
        ps.add(new Persona("P16","60대 실버 베이스형",0.06,PlanTier.PLAN_L, 0.1,1,false,false,false,0.0, 0.1,1,  mix(0.80,0.20,0.00), 0.10,0.20, 0.10));

        ps.add(new Persona("P99","볼륨 부스터",0.05,PlanTier.PLAN_H, 2.0,3,false,false,false,0.0, 6.0,120, mix(0.55,0.35,0.10), 0.00,0.05, 0.80));

        double sum = 0;
        for (Persona p : ps) sum += p.weight;
        if (Math.abs(sum - 1.0) > 1e-9) throw new IllegalStateException("Persona weight sum must be 1.0, got=" + sum);
        return ps;
    }

    static Persona pickPersona(Random rng, List<Persona> ps) {
        double r = rng.nextDouble();
        double acc = 0;
        for (Persona p : ps) {
            acc += p.weight;
            if (r < acc) return p;
        }
        return ps.get(ps.size() - 1);
    }

    // ===== 서비스 모델 =====
    static class SubscribeService {
        int subscribeServiceId;
        String name;
        int subscribeCategoryId;
        long fee;
        SubscribeService(int id, String name, int cat, long fee) {
            this.subscribeServiceId = id;
            this.name = name;
            this.subscribeCategoryId = cat;
            this.fee = fee;
        }
    }

    static class MicroPaymentService {
        int id;
        String serviceTypeName;
        MicroPaymentService(int id, String name) {
            this.id = id;
            this.serviceTypeName = name;
        }
    }

    // ===== device row =====
    static class UserDeviceRow {
        long deviceId;
        long usersId;
        String deviceType;
        String nickname;
        UserDeviceRow(long deviceId, long usersId, String deviceType, String nickname) {
            this.deviceId = deviceId;
            this.usersId = usersId;
            this.deviceType = deviceType;
            this.nickname = nickname;
        }
    }

    // ===== Address Model =====
    static class City {
        String cityName;
        District[] districts;
        static City of(String cityName, District... ds) {
            City c = new City();
            c.cityName = cityName;
            c.districts = ds;
            return c;
        }
    }
    static class District {
        String name;
        String[] dongs;
        District(String name, String[] dongs) {
            this.name = name;
            this.dongs = dongs;
        }
    }

    // ===== CSV Writer (UTF-8 BOM) =====
    static class CsvWriter implements Closeable {
        private final BufferedWriter bw;

        CsvWriter(File file, List<String> header) throws IOException {
            OutputStream os = new FileOutputStream(file);
            os.write(0xEF); os.write(0xBB); os.write(0xBF);
            this.bw = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8), 1 << 20);
            row(header);
        }

        void row(List<String> cols) throws IOException {
            for (int i = 0; i < cols.size(); i++) {
                if (i > 0) bw.write(",");
                bw.write(escape(cols.get(i)));
            }
            bw.write("\n");
        }

        private String escape(String v) {
            if (v == null) return "";
            boolean needQuote = v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r");
            String s = v.replace("\"", "\"\"");
            return needQuote ? ("\"" + s + "\"") : s;
        }

        @Override public void close() throws IOException {
            bw.flush();
            bw.close();
        }
    }

    // ===== PlanTier 분할 =====
    static Map<PlanTier, List<SubscribeService>> splitPlansByFee(List<SubscribeService> plans) {
        List<SubscribeService> sorted = new ArrayList<>(plans);
        sorted.sort(Comparator.comparingLong(s -> s.fee));

        int n = sorted.size();
        int iL = (int)Math.floor(n * 0.50);
        int iM = (int)Math.floor(n * 0.80);
        int iH = (int)Math.floor(n * 0.95);

        Map<PlanTier, List<SubscribeService>> m = new EnumMap<>(PlanTier.class);
        m.put(PlanTier.PLAN_L, new ArrayList<>(sorted.subList(0, Math.max(1, iL))));
        m.put(PlanTier.PLAN_M, new ArrayList<>(sorted.subList(Math.max(1, iL), Math.max(iM, iL + 1))));
        m.put(PlanTier.PLAN_H, new ArrayList<>(sorted.subList(Math.max(iM, iL + 1), Math.max(iH, iM + 1))));
        m.put(PlanTier.PLAN_SIG, new ArrayList<>(sorted.subList(Math.max(iH, iM + 1), n)));

        for (PlanTier t : PlanTier.values()) {
            if (m.get(t) == null || m.get(t).isEmpty()) m.put(t, new ArrayList<>(sorted));
        }
        return m;
    }

    // ===== Addon 풀 구성 =====
    static Map<AddonType, List<SubscribeService>> buildAddonPool(List<SubscribeService> addons) {
        Map<AddonType, List<SubscribeService>> pool = new EnumMap<>(AddonType.class);
        for (AddonType t : AddonType.values()) pool.put(t, new ArrayList<>());

        for (SubscribeService a : addons) {
            AddonType t = classifyAddon(a.name);
            pool.get(t).add(a);
        }

        for (AddonType t : AddonType.values()) {
            if (pool.get(t).isEmpty()) pool.get(t).addAll(addons);
        }
        return pool;
    }

    static AddonType classifyAddon(String name) {
        if (name == null) return AddonType.ETC;
        String s = name;
        if (s.contains("디즈니") || s.contains("넷플릭스") || s.contains("유튜브") || s.contains("OTT")) return AddonType.OTT;
        if (s.contains("안심") || s.contains("세이프") || s.contains("보호")) return AddonType.SAFE;
        if (s.contains("보험") || s.contains("INS")) return AddonType.INS;
        if (s.contains("게임")) return AddonType.GAME;
        if (s.contains("지니뮤직") || s.contains("뮤직") || s.contains("MUSIC")) return AddonType.MUSIC;
        return AddonType.ETC;
    }

    // ===== addon 배타 그룹 =====
    static String deriveAddonExclusiveGroup(String serviceName) {
        if (serviceName == null) return null;
        if (serviceName.contains("디즈니+")) return "OTT_DISNEY";
        if (serviceName.startsWith("지니뮤직")) return "MUSIC_GENIE";
        return null;
    }

    // =========================
    // 시간 계산
    // =========================
    static LocalDateTime monthEndCreatedAt(int yyyymm) {
        int year = yyyymm / 100;
        int month = yyyymm % 100;
        YearMonth ym = YearMonth.of(year, month);
        LocalDate last = ym.atEndOfMonth();
        return LocalDateTime.of(last, LocalTime.of(23, 59, 59));
    }

    static LocalDate firstDayOfMonth(int yyyymm) {
        int year = yyyymm / 100;
        int month = yyyymm % 100;
        return LocalDate.of(year, month, 1);
    }

    static LocalDate expiredAtPlusYears(int yyyymm, int years) {
        int year = yyyymm / 100;
        int month = yyyymm % 100;
        LocalDate end = YearMonth.of(year, month).atEndOfMonth();
        return end.plusYears(years);
    }

    static LocalDate pickChangeDayInMonth(int yyyymm, Random rng) {
        int year = yyyymm / 100;
        int month = yyyymm % 100;
        YearMonth ym = YearMonth.of(year, month);
        int last = ym.lengthOfMonth();
        if (last <= 2) return LocalDate.of(year, month, 1);
        int day = 2 + rng.nextInt(Math.max(1, last - 2)); // 2..(last-1)
        if (day >= last) day = last - 1;
        return LocalDate.of(year, month, day);
    }

    // =========================
    // 고정 CSV 로더
    // =========================
    static List<SubscribeService> loadSubscribeServices(File f) throws IOException {
        List<SubscribeService> out = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), FIXED_CSV_CHARSET))) {
            String line;
            boolean first = true;
            while ((line = br.readLine()) != null) {
                if (first) { first = false; continue; }
                if (line.isBlank()) continue;
                String[] r = line.split(",", -1);
                if (r.length < 4) continue;
                int id = Integer.parseInt(r[0].trim());
                String name = r[1].trim();
                int cat = Integer.parseInt(r[2].trim());
                long fee = Long.parseLong(r[3].trim());
                out.add(new SubscribeService(id, name, cat, fee));
            }
        }
        return out;
    }

    static List<MicroPaymentService> loadMicroPaymentServices(File f) throws IOException {
        List<MicroPaymentService> out = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), FIXED_CSV_CHARSET))) {
            String line;
            boolean first = true;
            while ((line = br.readLine()) != null) {
                if (first) { first = false; continue; }
                if (line.isBlank()) continue;
                String[] r = line.split(",", -1);
                if (r.length < 2) continue;
                int id = Integer.parseInt(r[0].trim());
                String name = r[1].trim();
                out.add(new MicroPaymentService(id, name));
            }
        }
        return out;
    }

    static List<SubscribeService> filterByCategory(List<SubscribeService> all, int catId) {
        List<SubscribeService> out = new ArrayList<>();
        for (SubscribeService s : all) if (s.subscribeCategoryId == catId) out.add(s);
        return out;
    }

    // =========================
    // 할인/금액
    // =========================
    static long clampDiscount(long discount, long origin) {
        if (discount < 0) return 0;
        if (discount > origin) return origin;
        return discount;
    }

    static long discountAmount(long origin, double minPct, double maxPct, Random rng) {
        if (origin <= 0) return 0;
        if (maxPct <= 0) return 0;
        double lo = Math.max(0.0, minPct);
        double hi = Math.max(lo, maxPct);
        double pct = lo + (hi - lo) * rng.nextDouble();
        long d = (long)Math.floor(origin * pct);
        return clampDiscount(d, origin);
    }

    // =========================
    // 포아송 샘플러(캡 적용)
    // =========================
    static int samplePoisson(Random rng, double lambda, int cap) {
        if (cap <= 0) return 0;
        if (lambda <= 0) return 0;

        int k;
        if (lambda < 30.0) {
            double L = Math.exp(-lambda);
            int c = 0;
            double p = 1.0;
            do {
                c++;
                p *= rng.nextDouble();
            } while (p > L && c < 10_000);
            k = c - 1;
        } else {
            double mean = lambda;
            double std = Math.sqrt(lambda);
            double z = rng.nextGaussian();
            k = (int)Math.round(mean + std * z);
        }

        if (k < 0) k = 0;
        if (k > cap) k = cap;
        return k;
    }

    // =========================
    // micro 타입 선택/서비스 풀
    // =========================
    static MicroType pickMicroType(Random rng, EnumMap<MicroType, Double> mix) {
        double m = mix.getOrDefault(MicroType.MICRO, 0.0);
        double d = mix.getOrDefault(MicroType.DATA, 0.0);
        double v = mix.getOrDefault(MicroType.DEVICE, 0.0);
        double sum = m + d + v;
        if (sum <= 0) return MicroType.MICRO;
        double r = rng.nextDouble() * sum;
        if (r < m) return MicroType.MICRO;
        r -= m;
        if (r < d) return MicroType.DATA;
        return MicroType.DEVICE;
    }

    static MicroType classifyMicroService(String name) {
        if (name == null) return MicroType.MICRO;
        if (name.contains("초과 데이터")) return MicroType.DATA;
        if (name.contains("할부")) return MicroType.DEVICE;
        return MicroType.MICRO;
    }

    static long generateOriginByTypeName(String serviceTypeName, Random rng) {
        if (serviceTypeName == null) return step(skewLow(rng, 1000, 50000), 10);

        if (serviceTypeName.contains("초과 데이터")) return step(skewLow(rng, 1000, 80000), 10);
        if (serviceTypeName.contains("할부"))       return step(25000 + rng.nextInt(120000), 10);
        if (serviceTypeName.contains("게임"))       return step(skewLow(rng, 1100, 110000), 10);
        if (serviceTypeName.contains("웹툰") || serviceTypeName.contains("웹소설"))
            return step(skewLow(rng, 500, 30000), 10);
        if (serviceTypeName.contains("스트리밍") || serviceTypeName.contains("정기"))
            return step(7900 + rng.nextInt(15000), 10);
        if (serviceTypeName.contains("편의점"))     return step(skewLow(rng, 1000, 30000), 10);
        if (serviceTypeName.contains("배달") || serviceTypeName.contains("주문"))
            return step(skewLow(rng, 7000, 80000), 10);

        return step(skewLow(rng, 1000, 50000), 10);
    }

    static long skewLow(Random rng, long min, long max) {
        double x = rng.nextDouble();
        double y = Math.pow(x, 3);
        long v = (long)(min + (max - min) * y);
        if (v < min) v = min;
        if (v > max) v = max;
        return v;
    }

    static long step(long v, long step) {
        return (v / step) * step;
    }

    // =========================
    // users: 이름/주소/폰/이메일/결제정보
    // =========================
    static String randomKoreanName(Random rng) {
        String last = LAST_NAMES[rng.nextInt(LAST_NAMES.length)];
        String first = FIRST_NAMES_200[rng.nextInt(FIRST_NAMES_200.length)];
        return last + first;
    }

    static String randomKoreanAddress(Random rng) {
        City c = CITIES[rng.nextInt(CITIES.length)];
        District d = c.districts[rng.nextInt(c.districts.length)];
        String dong = d.dongs[rng.nextInt(d.dongs.length)];
        int bun = 1 + rng.nextInt(200);
        int ho = 1 + rng.nextInt(50);
        return c.cityName + " " + d.name + " " + dong + " " + bun + "-" + ho;
    }

    static String phone010ById(long userId) {
        if (userId < 0 || userId > 99_999_999L) throw new IllegalArgumentException("userId must be 0..99,999,999");
        return "010" + String.format("%08d", userId);
    }

    static String makeEmail(long userId) {
        String hex = Long.toHexString(userId).toLowerCase(Locale.ROOT);
        return "user" + hex + "@example.com";
    }

    static String makePaymentInfo(String paymentMethod, Random rng) {
        if ("CARD".equals(paymentMethod)) {
            int last4 = 1000 + rng.nextInt(9000);
            return "CARD_TOKEN_****-****-****-" + last4;
        }
        int bank = 100 + rng.nextInt(900);
        int token = 100000 + rng.nextInt(900000);
        return "BANK_TOKEN_" + bank + "_" + token;
    }

    static LocalDateTime randomJoinedAtBeforeMonth(Random rng, int yyyymm) {
        LocalDate first = firstDayOfMonth(yyyymm);
        int daysBack = 30 + rng.nextInt(871);
        LocalDateTime dt = first.atStartOfDay().minusDays(daysBack);
        return dt.plusSeconds(rng.nextInt(24 * 3600));
    }

    static int pickFrom(int[] arr, Random rng) {
        return arr[rng.nextInt(arr.length)];
    }

    static <T> T pickFromList(List<T> list, Random rng) {
        return list.get(rng.nextInt(list.size()));
    }

    // =========================
    // device 규칙(기존 로직 유지)
    // =========================
    static int sampleTotalDevices(Random rng, Mode mode) {
        if (mode == Mode.WORST) return 1 + rng.nextInt(15);
        if (mode == Mode.CHAOS) return 1 + rng.nextInt(6);
        return 1 + rng.nextInt(3);
    }

    static String pickExtraDeviceType(Random rng, Mode mode) {
        double r = rng.nextDouble();
        if (mode == Mode.WORST) return (r < 0.70) ? "TABLET" : "WATCH";
        return (r < 0.65) ? "TABLET" : "WATCH";
    }

    // =========================
    // 요금제 변경: 반드시 다른 요금제로 선택
    // =========================
    static SubscribeService pickDifferentPlan(List<SubscribeService> candidates, int currentServiceId, Random rng) {
        if (candidates == null || candidates.isEmpty()) return null;
        if (candidates.size() == 1) {
            return (candidates.get(0).subscribeServiceId == currentServiceId) ? null : candidates.get(0);
        }
        for (int i = 0; i < 12; i++) {
            SubscribeService p = candidates.get(rng.nextInt(candidates.size()));
            if (p.subscribeServiceId != currentServiceId) return p;
        }
        for (SubscribeService p : candidates) {
            if (p.subscribeServiceId != currentServiceId) return p;
        }
        return null;
    }

    // =========================
    // 생성 결과 핸들러
    // =========================
    static final class GenResult {
        long nextDeviceIdSeq;
        long nextSubBillIdSeq;
        long subscribeRows;
        long phoneDeviceId;
        boolean hasTablet;
        long tabletDeviceId;           // 없으면 -1
        Integer tabletPlanServiceId;   // CAT=3 row가 생성된 경우에만 값 존재
        SubscribeService tabletPlan;   // 필요 시 사용
    }

    // users + user_delivery_settings + devices + subscribe_billing_history 생성
    static GenResult generateUserAndSubscribe(
            CsvWriter usersW,
            CsvWriter settingsW,
            CsvWriter devW,
            CsvWriter subW,
            DeterministicCrypto crypto,
            Random rng,
            Mode mode,
            long userId,
            long deviceIdSeq,
            long subBillIdSeq,
            int yyyymm,
            LocalDate subscriptionStartDate,
            LocalDateTime createdAtForMonth,
            LocalDate expiredAt,
            Map<PlanTier, List<SubscribeService>> plansByTier,
            List<SubscribeService> otherPlans,
            Map<AddonType, List<SubscribeService>> addonPool,
            Persona persona,
            int forceDeviceCount // -1이면 기존 규칙 적용, 양수면 강제
    ) throws IOException {

        GenResult out = new GenResult();
        out.tabletDeviceId = -1L;

        // ===== users =====
        String name = randomKoreanName(rng);
        String address = randomKoreanAddress(rng);

        String phonePlain = phone010ById(userId);
        String emailPlain = makeEmail(userId);

        String phoneEnc = crypto.encryptV1(phonePlain, AD_PHONE);
        String emailEnc = crypto.encryptV1(emailPlain, AD_EMAIL);

        LocalDateTime joinedAt = randomJoinedAtBeforeMonth(rng, yyyymm);

        int billingDay = pickFrom(new int[]{5,10,15,20,25}, rng);
        String paymentMethod = rng.nextBoolean() ? "CARD" : "BANK";
        String paymentInfo = makePaymentInfo(paymentMethod, rng);

        usersW.row(List.of(
                String.valueOf(userId),
                emailEnc,
                name,
                address,
                phoneEnc,
                TS.format(joinedAt),
                "0",
                "",
                paymentMethod,
                paymentInfo,
                String.valueOf(billingDay)
        ));

        // ===== user_delivery_settings (1:1) =====
        int preferredHour = rng.nextInt(24);      // 0~23
        int preferredDay = 1 + rng.nextInt(28);  // 1~31
        settingsW.row(List.of(
                String.valueOf(userId),
                String.valueOf(preferredHour),
                String.valueOf(preferredDay)
        ));

        // ===== devices =====
        List<UserDeviceRow> devices = new ArrayList<>();
        long phoneDeviceId = deviceIdSeq++;
        devices.add(new UserDeviceRow(phoneDeviceId, userId, "PHONE", "내 폰"));

        int totalDevices = (forceDeviceCount > 0) ? forceDeviceCount : sampleTotalDevices(rng, mode);

        boolean hasTablet = false;
        long firstTabletDeviceId = -1L;

        for (int d = 1; d < totalDevices; d++) {
            String type = pickExtraDeviceType(rng, mode);
            if ("TABLET".equals(type) && !hasTablet) {
                hasTablet = true;
            }
            String nick = type.equals("TABLET") ? ("내 아이패드" + d) : ("내 워치" + d);
            long did = deviceIdSeq++;
            if ("TABLET".equals(type) && firstTabletDeviceId < 0) firstTabletDeviceId = did;
            devices.add(new UserDeviceRow(did, userId, type, nick));
        }

        for (UserDeviceRow r : devices) {
            devW.row(List.of(
                    String.valueOf(r.deviceId),
                    String.valueOf(r.usersId),
                    r.nickname,
                    r.deviceType,
                    TS.format(joinedAt)
            ));
        }

        // ===== subscribe_billing_history =====
        long subscribeRows = 0;

        // (A) 메인 요금제 1건(항상)
        SubscribeService mainPlan = pickFromList(plansByTier.get(persona.planTier), rng);
        long mainOrigin = mainPlan.fee;
        long mainDiscount = discountAmount(mainOrigin, persona.discountMinPct, persona.discountMaxPct, rng);
        long mainTotal = mainOrigin - mainDiscount;

        subW.row(List.of(
                String.valueOf(subBillIdSeq++),
                String.valueOf(userId),
                String.valueOf(phoneDeviceId),
                String.valueOf(mainPlan.subscribeServiceId),
                mainPlan.name,
                subscriptionStartDate.toString(),
                String.valueOf(mainOrigin),
                String.valueOf(mainDiscount),
                String.valueOf(mainTotal),
                String.valueOf(yyyymm),
                TS.format(createdAtForMonth),
                expiredAt.toString()
        ));
        subscribeRows += 1;

        // (A-2) PHONE 요금제 변경(월 1회, 확률 낮게, 반드시 다른 요금제)
        if (rng.nextDouble() < PHONE_PLAN_CHANGE_PROB) {
            List<SubscribeService> tierPlans = plansByTier.get(persona.planTier);
            SubscribeService changed = pickDifferentPlan(tierPlans, mainPlan.subscribeServiceId, rng);

            if (changed == null) {
                List<SubscribeService> allPhone = new ArrayList<>();
                for (List<SubscribeService> v : plansByTier.values()) allPhone.addAll(v);
                changed = pickDifferentPlan(allPhone, mainPlan.subscribeServiceId, rng);
            }

            if (changed != null) {
                LocalDate changeDate = pickChangeDayInMonth(yyyymm, rng);
                long cOrigin = changed.fee;
                long cDiscount = discountAmount(cOrigin, persona.discountMinPct, persona.discountMaxPct, rng);
                long cTotal = cOrigin - cDiscount;

                subW.row(List.of(
                        String.valueOf(subBillIdSeq++),
                        String.valueOf(userId),
                        String.valueOf(phoneDeviceId),
                        String.valueOf(changed.subscribeServiceId),
                        changed.name,
                        changeDate.toString(),          // 변경일
                        String.valueOf(cOrigin),
                        String.valueOf(cDiscount),
                        String.valueOf(cTotal),
                        String.valueOf(yyyymm),
                        TS.format(createdAtForMonth),
                        expiredAt.toString()
                ));
                subscribeRows += 1;
            }
        }

        // (B) 기타요금제(CAT=3): tablet 있을 때만 + persona.otherPlanProb
        //     + tablet 요금제 변경(월 1회, 확률 낮게, 반드시 다른 요금제)
        Integer tabletPlanServiceId = null;
        SubscribeService tabletPlan = null;
        long tabletDeviceId = -1L;

        if (hasTablet && !otherPlans.isEmpty() && rng.nextDouble() < persona.otherPlanProb) {
            tabletDeviceId = (firstTabletDeviceId > 0) ? firstTabletDeviceId : phoneDeviceId;

            SubscribeService other = pickFromList(otherPlans, rng);
            long oOrigin = other.fee;
            long oDiscount = discountAmount(oOrigin, persona.discountMinPct, persona.discountMaxPct, rng);
            long oTotal = oOrigin - oDiscount;

            subW.row(List.of(
                    String.valueOf(subBillIdSeq++),
                    String.valueOf(userId),
                    String.valueOf(tabletDeviceId),
                    String.valueOf(other.subscribeServiceId),
                    other.name,
                    subscriptionStartDate.toString(),
                    String.valueOf(oOrigin),
                    String.valueOf(oDiscount),
                    String.valueOf(oTotal),
                    String.valueOf(yyyymm),
                    TS.format(createdAtForMonth),
                    expiredAt.toString()
            ));
            subscribeRows += 1;

            tabletPlanServiceId = other.subscribeServiceId;
            tabletPlan = other;

            // (B-2) TABLET 요금제 변경: tablet row가 생성된 경우에만, 반드시 다른 요금제
            if (rng.nextDouble() < TABLET_PLAN_CHANGE_PROB) {
                SubscribeService changedOther = pickDifferentPlan(otherPlans, other.subscribeServiceId, rng);
                if (changedOther != null) {
                    LocalDate changeDate = pickChangeDayInMonth(yyyymm, rng);
                    long cOrigin = changedOther.fee;
                    long cDiscount = discountAmount(cOrigin, persona.discountMinPct, persona.discountMaxPct, rng);
                    long cTotal = cOrigin - cDiscount;

                    subW.row(List.of(
                            String.valueOf(subBillIdSeq++),
                            String.valueOf(userId),
                            String.valueOf(tabletDeviceId),
                            String.valueOf(changedOther.subscribeServiceId),
                            changedOther.name,
                            changeDate.toString(),
                            String.valueOf(cOrigin),
                            String.valueOf(cDiscount),
                            String.valueOf(cTotal),
                            String.valueOf(yyyymm),
                            TS.format(createdAtForMonth),
                            expiredAt.toString()
                    ));
                    subscribeRows += 1;
                }
            }
        }

        // (C) 부가서비스(CAT=2): 포아송 + cap, fixed 우선 반영, 배타그룹 유지
        int addonCount = samplePoisson(rng, persona.addonLambda, persona.addonCap);

        List<SubscribeService> addonCandidates = new ArrayList<>();
        if (persona.addonFixedOtt)  addonCandidates.addAll(addonPool.get(AddonType.OTT));
        if (persona.addonFixedSafe) addonCandidates.addAll(addonPool.get(AddonType.SAFE));
        if (persona.addonFixedIns)  addonCandidates.addAll(addonPool.get(AddonType.INS));

        Set<String> exclusive = new HashSet<>();
        Set<Integer> usedServiceId = new HashSet<>();
        int filled = 0;

        for (SubscribeService a : addonCandidates) {
            if (filled >= addonCount) break;
            if (usedServiceId.contains(a.subscribeServiceId)) continue;
            String g = deriveAddonExclusiveGroup(a.name);
            if (g != null && exclusive.contains(g)) continue;

            usedServiceId.add(a.subscribeServiceId);
            if (g != null) exclusive.add(g);

            long aOrigin = a.fee;
            long aDiscount = discountAmount(aOrigin, persona.discountMinPct, persona.discountMaxPct, rng);
            long aTotal = aOrigin - aDiscount;

            subW.row(List.of(
                    String.valueOf(subBillIdSeq++),
                    String.valueOf(userId),
                    String.valueOf(phoneDeviceId),
                    String.valueOf(a.subscribeServiceId),
                    a.name,
                    subscriptionStartDate.toString(),
                    String.valueOf(aOrigin),
                    String.valueOf(aDiscount),
                    String.valueOf(aTotal),
                    String.valueOf(yyyymm),
                    TS.format(createdAtForMonth),
                    expiredAt.toString()
            ));
            subscribeRows += 1;
            filled++;
        }

        int remaining = addonCount - filled;
        if (remaining > 0 && !addonPool.isEmpty()) {
            for (int i = 0; i < remaining; i++) {
                SubscribeService a = pickAddonByPersona(rng, addonPool, persona);
                if (a == null) break;

                if (usedServiceId.contains(a.subscribeServiceId)) continue;
                String g = deriveAddonExclusiveGroup(a.name);
                if (g != null && exclusive.contains(g)) continue;

                usedServiceId.add(a.subscribeServiceId);
                if (g != null) exclusive.add(g);

                long aOrigin = a.fee;
                long aDiscount = discountAmount(aOrigin, persona.discountMinPct, persona.discountMaxPct, rng);
                long aTotal = aOrigin - aDiscount;

                subW.row(List.of(
                        String.valueOf(subBillIdSeq++),
                        String.valueOf(userId),
                        String.valueOf(phoneDeviceId),
                        String.valueOf(a.subscribeServiceId),
                        a.name,
                        subscriptionStartDate.toString(),
                        String.valueOf(aOrigin),
                        String.valueOf(aDiscount),
                        String.valueOf(aTotal),
                        String.valueOf(yyyymm),
                        TS.format(createdAtForMonth),
                        expiredAt.toString()
                ));
                subscribeRows += 1;
            }
        }

        out.nextDeviceIdSeq = deviceIdSeq;
        out.nextSubBillIdSeq = subBillIdSeq;
        out.subscribeRows = subscribeRows;
        out.phoneDeviceId = phoneDeviceId;
        out.hasTablet = hasTablet;
        out.tabletDeviceId = tabletDeviceId;
        out.tabletPlanServiceId = tabletPlanServiceId;
        out.tabletPlan = tabletPlan;
        return out;
    }

    static SubscribeService pickAddonByPersona(Random rng, Map<AddonType, List<SubscribeService>> addonPool, Persona p) {
        double r = rng.nextDouble();
        AddonType t;

        if (p.addonFixedSafe && r < 0.45) t = AddonType.SAFE;
        else if (p.addonFixedOtt && r < 0.75) t = AddonType.OTT;
        else if (p.addonFixedIns && r < 0.90) t = AddonType.INS;
        else t = AddonType.ETC;

        List<SubscribeService> list = addonPool.getOrDefault(t, Collections.emptyList());
        if (list.isEmpty()) list = addonPool.getOrDefault(AddonType.ETC, Collections.emptyList());
        if (list.isEmpty()) return null;
        return pickFromList(list, rng);
    }

    // =========================
    // micro pool
    // =========================
    static Map<MicroType, List<MicroPaymentService>> buildMicroPool(List<MicroPaymentService> microServices) {
        Map<MicroType, List<MicroPaymentService>> pool = new EnumMap<>(MicroType.class);
        for (MicroType t : MicroType.values()) pool.put(t, new ArrayList<>());
        for (MicroPaymentService s : microServices) {
            pool.get(classifyMicroService(s.serviceTypeName)).add(s);
        }
        for (MicroType t : MicroType.values()) {
            if (pool.get(t).isEmpty()) pool.get(t).addAll(microServices);
        }
        return pool;
    }

    static MicroPaymentService pickMicroServiceByType(Random rng, Map<MicroType, List<MicroPaymentService>> pool, MicroType type) {
        List<MicroPaymentService> list = pool.getOrDefault(type, Collections.emptyList());
        if (list.isEmpty()) list = pool.getOrDefault(MicroType.MICRO, Collections.emptyList());
        if (list.isEmpty()) {
            List<MicroPaymentService> all = new ArrayList<>();
            for (List<MicroPaymentService> v : pool.values()) all.addAll(v);
            if (all.isEmpty()) return null;
            return pickFromList(all, rng);
        }
        return pickFromList(list, rng);
    }

    // =========================
    // micro row writer (합산 없음)
    // =========================
    static long writeMicroRows(
            CsvWriter microW,
            Map<MicroType, List<MicroPaymentService>> microPool,
            Random rng,
            Set<Long> installmentOnceUser,
            long userId,
            int yyyymm,
            LocalDateTime createdAtForMonth,
            LocalDate expiredAt,
            int microCount,
            EnumMap<MicroType, Double> mix,
            double discountMinPct,
            double discountMaxPct,
            long microBillIdSeqStart
    ) throws IOException {

        long seq = microBillIdSeqStart;
        long wrote = 0;

        int attempts = 0;
        int safety = Math.max(50, microCount * 8);

        while (wrote < microCount && attempts < safety) {
            attempts++;

            MicroType targetType = pickMicroType(rng, mix);

            MicroPaymentService svc = pickMicroServiceByType(rng, microPool, targetType);
            if (svc == null) svc = pickMicroServiceByType(rng, microPool, MicroType.MICRO);
            if (svc == null) break;

            // 할부 1회 제한
            if (svc.serviceTypeName != null && svc.serviceTypeName.contains("할부")) {
                if (installmentOnceUser.contains(userId)) continue;
                installmentOnceUser.add(userId);
            }

            long origin = generateOriginByTypeName(svc.serviceTypeName, rng);
            long discount = discountAmount(origin, discountMinPct, discountMaxPct, rng);
            long total = origin - discount;

            microW.row(List.of(
                    String.valueOf(seq++),
                    String.valueOf(userId),
                    String.valueOf(svc.id),
                    svc.serviceTypeName,
                    String.valueOf(origin),
                    String.valueOf(discount),
                    String.valueOf(total),
                    String.valueOf(yyyymm),
                    TS.format(createdAtForMonth),
                    expiredAt.toString()
            ));
            wrote++;
        }

        while (wrote < microCount) {
            MicroPaymentService svc = pickMicroServiceByType(rng, microPool, MicroType.MICRO);
            if (svc == null) svc = pickMicroServiceByType(rng, microPool, MicroType.DATA);
            if (svc == null) svc = pickMicroServiceByType(rng, microPool, MicroType.DEVICE);
            if (svc == null) break;

            if (svc.serviceTypeName != null && svc.serviceTypeName.contains("할부")) {
                if (installmentOnceUser.contains(userId)) {
                    svc = pickMicroServiceByType(rng, microPool, MicroType.MICRO);
                    if (svc == null) break;
                } else {
                    installmentOnceUser.add(userId);
                }
            }

            long origin = generateOriginByTypeName(svc.serviceTypeName, rng);
            long discount = discountAmount(origin, discountMinPct, discountMaxPct, rng);
            long total = origin - discount;

            microW.row(List.of(
                    String.valueOf(seq++),
                    String.valueOf(userId),
                    String.valueOf(svc.id),
                    svc.serviceTypeName,
                    String.valueOf(origin),
                    String.valueOf(discount),
                    String.valueOf(total),
                    String.valueOf(yyyymm),
                    TS.format(createdAtForMonth),
                    expiredAt.toString()
            ));
            wrote++;
        }

        return wrote;
    }

    // =========================
    // main
    // =========================
    public static void main(String[] args) throws Exception {

        // args 미리 지정(폴더만 바꿔서 바로 돌리기)
        if (args.length == 0) {
            args = new String[] {
                    "src/main/resources/fixed_csv", // fixedCsvDir (subscribe_service.csv, micro_payment_service.csv)
                    "./out_csv",                   // outRootDir
                    "202512",                      // yyyymm
                    String.valueOf(TARGET_USERS),  // userCount (고정)
                    "26",                          // seed
                    "dev"                          // category(dev|worst|chaos)
            };
        }

        if (args.length < 5) {
            System.out.println("Usage: java DataGenBatch <fixedCsvDir> <outRootDir> <yyyymm> <userCount> <seed> [category(dev|worst|chaos)]");
            return;
        }

        File fixedDir = new File(args[0]);
        File outRoot = new File(args[1]);
        int yyyymm = Integer.parseInt(args[2]);
        int userCount = Integer.parseInt(args[3]);
        long seed = Long.parseLong(args[4]);
        Mode mode = Mode.parse((args.length >= 6) ? args[5] : "dev");

        if (userCount != TARGET_USERS) {
            throw new IllegalArgumentException("This build expects userCount=" + TARGET_USERS + " (got " + userCount + ")");
        }

        if (!fixedDir.exists()) throw new IllegalArgumentException("fixedCsvDir not found: " + fixedDir.getAbsolutePath());
        if (!outRoot.exists() && !outRoot.mkdirs()) throw new IOException("Cannot create outRootDir: " + outRoot.getAbsolutePath());

        String dirName = "gen_" + yyyymm + "_n" + userCount + "_s" + seed + "_" + mode.tag;
        File outDir = new File(outRoot, dirName);
        if (!outDir.exists() && !outDir.mkdirs()) throw new IOException("Cannot create outDir: " + outDir.getAbsolutePath());

        Random rng = new Random(seed);
        DeterministicCrypto crypto = DeterministicCrypto.fromEnvOrDefaultOrThrow();

        List<SubscribeService> subscribeServices = loadSubscribeServices(new File(fixedDir, "subscribe_service.csv"));
        List<MicroPaymentService> microServices = loadMicroPaymentServices(new File(fixedDir, "micro_payment_service.csv"));

        List<SubscribeService> phonePlans = filterByCategory(subscribeServices, CAT_PHONE_PLAN);
        List<SubscribeService> addons     = filterByCategory(subscribeServices, CAT_ADDON);
        List<SubscribeService> otherPlans = filterByCategory(subscribeServices, CAT_OTHER_PLAN);

        if (phonePlans.isEmpty()) throw new IllegalStateException("phonePlans empty (subscribe_category_id=1 required).");
        if (microServices.isEmpty()) throw new IllegalStateException("micro_payment_service empty.");

        List<Persona> personas = buildPersonas();
        Map<PlanTier, List<SubscribeService>> plansByTier = splitPlansByFee(phonePlans);
        Map<AddonType, List<SubscribeService>> addonPool = buildAddonPool(addons);
        Map<MicroType, List<MicroPaymentService>> microPool = buildMicroPool(microServices);

        LocalDateTime createdAtForMonth = monthEndCreatedAt(yyyymm);
        LocalDate subscriptionStartDate = firstDayOfMonth(yyyymm);
        LocalDate expiredAt = expiredAtPlusYears(yyyymm, 3);

        // 할부(installment) 유저당 1회 제한
        Set<Long> installmentOnceUser = new HashSet<>(userCount / 10);

        long billRowsSoFar = 0;
        long subscribeRowsSoFar = 0;
        long microRowsSoFar = 0;
        long subscribeRowsAvgDen = 0;

        try (CsvWriter usersW = new CsvWriter(new File(outDir, "users.csv"),
                List.of("users_id","email","name","address","phone","joined_at",
                        "is_withdrawn","updated_at","payment_method","payment_info","billing_day"
                ));
             CsvWriter settingsW = new CsvWriter(new File(outDir, "user_delivery_settings.csv"),
                     List.of("user_id","preferred_hour","preferred_day")
             );
             CsvWriter devW = new CsvWriter(new File(outDir, "users_device.csv"),
                     List.of("device_id","users_id","nickname","device_type","created_at")
             );
             CsvWriter subW = new CsvWriter(new File(outDir, "subscribe_billing_history.csv"),
                     List.of("subscribe_billing_history_id","users_id","device_id","subscribe_service_id",
                             "service_name","subscription_start_date","origin_amount","discount_amount","total_amount",
                             "billing_yyyymm","created_at","expired_at"
                     ));
             CsvWriter microW = new CsvWriter(new File(outDir, "micro_payment_billing_history.csv"),
                     List.of("micro_payment_billing_history_id","users_id","micro_payment_service_id","service_name",
                             "origin_amount","discount_amount","total_amount","billing_yyyymm","created_at","expired_at"
                     ))
        ) {

            long userIdSeq = 10001L;
            long deviceIdSeq = 50001L;
            long subBillIdSeq = 90001L;
            long microBillIdSeq = 95001L;

            int normalUsers = userCount - ULTRA_HEAVY_COUNT;
            if (normalUsers <= 0) throw new IllegalStateException("userCount too small for ultra heavy.");

            // ========== 1) 초헤비 30명 선생성 ==========
            Persona ultraPersona = new Persona(
                    "P98","초헤비",0.0,PlanTier.PLAN_H,
                    2.0,3,false,false,false,0.0,
                    9999.0,9999, new EnumMap<>(MicroType.class) {{
                        put(MicroType.MICRO, 0.70);
                        put(MicroType.DATA, 0.30);
                        put(MicroType.DEVICE, 0.00);
                    }},
                    0.00,0.03, 0.80
            );

            for (int i = 0; i < ULTRA_HEAVY_COUNT; i++) {
                long userId = userIdSeq++;

                GenResult g = generateUserAndSubscribe(
                        usersW, settingsW, devW, subW,
                        crypto, rng, mode,
                        userId, deviceIdSeq, subBillIdSeq,
                        yyyymm, subscriptionStartDate, createdAtForMonth, expiredAt,
                        plansByTier, otherPlans, addonPool,
                        ultraPersona,
                        ULTRA_DEVICE_COUNT
                );

                deviceIdSeq = g.nextDeviceIdSeq;
                subBillIdSeq = g.nextSubBillIdSeq;

                billRowsSoFar += g.subscribeRows;
                subscribeRowsSoFar += g.subscribeRows;
                subscribeRowsAvgDen += 1;

                int microCount = ULTRA_MICRO_MIN + rng.nextInt(ULTRA_MICRO_MAX - ULTRA_MICRO_MIN + 1);
                long wrote = writeMicroRows(
                        microW, microPool,
                        rng, installmentOnceUser,
                        userId, yyyymm, createdAtForMonth, expiredAt,
                        microCount, ultraPersona.microMix,
                        ultraPersona.discountMinPct, ultraPersona.discountMaxPct,
                        microBillIdSeq
                );
                microBillIdSeq += wrote;

                billRowsSoFar += wrote;
                microRowsSoFar += wrote;

                if (billRowsSoFar > TARGET_BILL_ROWS) {
                    throw new IllegalStateException("Overshoot after ultra heavy. billRowsSoFar=" + billRowsSoFar + " > target=" + TARGET_BILL_ROWS);
                }
            }

            // ========== 2) 일반 유저 생성(후반 보정 포함) ==========
            int correctionStart = Math.max(0, normalUsers - TAIL_CORRECTION_USERS);
            int forceStart = Math.max(0, normalUsers - FINAL_FORCE_USERS);

            for (int idx = 0; idx < normalUsers; idx++) {
                long userId = userIdSeq++;

                boolean inCorrection = (idx >= correctionStart);
                boolean inForce = (idx >= forceStart);

                Persona p = pickPersona(rng, personas);

                // 후반에서는 P99 유입을 조금 늘림(조절 여지 확보)
                if (inCorrection && !inForce && rng.nextDouble() < 0.25) {
                    for (Persona px : personas) {
                        if ("P99".equals(px.id)) { p = px; break; }
                    }
                }

                GenResult g = generateUserAndSubscribe(
                        usersW, settingsW, devW, subW,
                        crypto, rng, mode,
                        userId, deviceIdSeq, subBillIdSeq,
                        yyyymm, subscriptionStartDate, createdAtForMonth, expiredAt,
                        plansByTier, otherPlans, addonPool,
                        p,
                        -1
                );

                deviceIdSeq = g.nextDeviceIdSeq;
                subBillIdSeq = g.nextSubBillIdSeq;

                billRowsSoFar += g.subscribeRows;
                subscribeRowsSoFar += g.subscribeRows;
                subscribeRowsAvgDen += 1;

                if (billRowsSoFar > TARGET_BILL_ROWS) {
                    throw new IllegalStateException(
                            "Overshoot by subscribe rows. billRowsSoFar=" + billRowsSoFar + " > target=" + TARGET_BILL_ROWS
                                    + " (try lowering addon lambdas/caps or lowering plan change probs)"
                    );
                }

                int usersLeft = (normalUsers - idx); // 현재 포함
                long rowsLeft = TARGET_BILL_ROWS - billRowsSoFar;

                // 미래 유저의 "최소 subscribe"는 1(메인 요금제)씩 무조건.
                // (너는 정확히 맞추기가 목표라 이 최소치만으로도 안전장치 역할은 충분)
                long minFutureSubscribe = (usersLeft - 1L) * 1L;

                long maxMicroAllowedNow = rowsLeft - minFutureSubscribe;
                if (maxMicroAllowedNow < 0) maxMicroAllowedNow = 0;

                int microCount;

                if (!inCorrection) {
                    microCount = samplePoisson(rng, p.singleLambda, p.singleCap);
                    if (microCount > maxMicroAllowedNow) microCount = (int)maxMicroAllowedNow;
                } else if (!inForce) {
                    double subAvg = (subscribeRowsAvgDen == 0) ? 2.0 : (subscribeRowsSoFar / (double)subscribeRowsAvgDen);
                    double desiredTotalPerUser = rowsLeft / (double) usersLeft;
                    double desiredMicro = desiredTotalPerUser - subAvg;
                    if (desiredMicro < 0) desiredMicro = 0;

                    microCount = (int)Math.round(desiredMicro);

                    if (rng.nextDouble() < 0.20) microCount += (rng.nextBoolean() ? 1 : -1);
                    if (microCount < 0) microCount = 0;
                    if (microCount > p.singleCap) microCount = p.singleCap;
                    if (microCount > maxMicroAllowedNow) microCount = (int)maxMicroAllowedNow;
                } else {
                    // 마지막 강제 보정: "정확히 맞추기" 우선
                    if (usersLeft == 1) {
                        microCount = (int)Math.min(Integer.MAX_VALUE, rowsLeft);
                    } else {
                        long microNeedNow = rowsLeft - minFutureSubscribe;
                        if (microNeedNow < 0) microNeedNow = 0;

                        microCount = (int)Math.floor(microNeedNow / (double)usersLeft);
                        if (microCount < 0) microCount = 0;
                        if (microCount > maxMicroAllowedNow) microCount = (int)maxMicroAllowedNow;
                        // cap 무시는 "필요하면 마지막 유저에서" 자동으로 해결됨
                    }
                }

                long wrote = writeMicroRows(
                        microW, microPool,
                        rng, installmentOnceUser,
                        userId, yyyymm, createdAtForMonth, expiredAt,
                        microCount, p.microMix,
                        p.discountMinPct, p.discountMaxPct,
                        microBillIdSeq
                );
                microBillIdSeq += wrote;

                billRowsSoFar += wrote;
                microRowsSoFar += wrote;

                if (billRowsSoFar > TARGET_BILL_ROWS) {
                    throw new IllegalStateException("Overshoot by micro rows. billRowsSoFar=" + billRowsSoFar + " > target=" + TARGET_BILL_ROWS);
                }

                if ((idx + 1) % 100_000 == 0) {
                    System.out.println("[PROGRESS] users(normal)=" + (idx + 1) +
                            " billRowsSoFar=" + billRowsSoFar +
                            " subscribe=" + subscribeRowsSoFar +
                            " micro=" + microRowsSoFar);
                }
            }

            if (billRowsSoFar != TARGET_BILL_ROWS) {
                throw new IllegalStateException("Final bill rows mismatch: " + billRowsSoFar + " (target " + TARGET_BILL_ROWS + ")");
            }

            System.out.println("DONE: " + outDir.getAbsolutePath());
            System.out.println("SUMMARY: users=" + userCount + ", billRows=" + billRowsSoFar +
                    " (subscribe=" + subscribeRowsSoFar + ", micro=" + microRowsSoFar + "), ultra=" + ULTRA_HEAVY_COUNT);
        }
    }
}
