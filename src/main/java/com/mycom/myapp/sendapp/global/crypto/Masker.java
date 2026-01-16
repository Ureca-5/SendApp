package com.mycom.myapp.sendapp.global.crypto;

/**
 * 외부 노출용 마스킹 유틸.
 *
 * 원칙:
 * - 외부 응답/로그에는 평문 금지, 마스킹만 허용.
 */
public final class Masker {

    private Masker() {}

    public static String maskPhone010(String phone11) {
        if (phone11 == null || phone11.length() != 11) return "";
        // 01012345678 -> 010****5678
        return phone11.substring(0, 3) + "****" + phone11.substring(7);
    }

    public static String maskEmail(String email) {
        if (email == null) return "";
        int at = email.indexOf('@');
        if (at <= 0) return "";

        String local = email.substring(0, at);
        String domain = email.substring(at);

        // local-part 최소 2글자까지 노출, 나머지 ***
        String head = local.length() <= 2 ? local : local.substring(0, 2);
        return head + "***" + domain;
    }
}
