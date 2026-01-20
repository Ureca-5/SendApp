package com.mycom.myapp.sendapp.global.crypto;

import java.util.Arrays;

/**
 * 외부 노출용 마스킹 유틸.
 *
 * 원칙:
 * - 외부 응답/로그에는 평문 금지, 마스킹만 허용.
 */
public final class Masker {

    private Masker() {}
    
    public static String maskName(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }

        int length = name.length();

        // 1. 이름이 1글자인 경우 ("*" 처리)
        if (length <= 1) {
            return "*";
        }

        // 2. 이름이 2글자인 경우 (김철 -> 김*)
        if (length == 2) {
            return name.charAt(0) + "*";
        }

        // 3. 이름이 3글자 이상인 경우 (홍길동 -> 홍*동, 남궁민수 -> 남**수)
        char[] stars = new char[length - 2];
        Arrays.fill(stars, '*');
        
        return name.charAt(0) + new String(stars) + name.charAt(length - 1);
    }
    
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
