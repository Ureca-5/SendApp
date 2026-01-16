package com.mycom.myapp.sendapp.global.crypto;

import java.util.Objects;

/**
 * DB/CSV에 저장된 암호문("v1:...") 래퍼.
 *
 * 목적:
 * - String 평문/암호문이 섞여서 전파되는 걸 타입으로 차단
 * - 로그/디버그 출력(toString)에서 평문이 새는 사고 방지
 */
public final class EncryptedString {

    private static final String PREFIX = "v1:";

    private final String packed; // e.g. v1:Base64...

    private EncryptedString(String packed) {
        this.packed = packed;
    }

    public static EncryptedString of(String packed) {
        if (packed == null || packed.isBlank()) {
            return new EncryptedString(null);
        }
        String trimmed = packed.trim();
        if (!trimmed.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Invalid packed string (missing v1:)");
        }
        return new EncryptedString(trimmed);
    }

    public String packed() {
        return packed;
    }

    public boolean isEmpty() {
        return packed == null;
    }

    @Override
    public String toString() {
        // 절대 평문/암호문 전체를 노출하지 않는다.
        return packed == null ? "(null)" : "(encrypted:v1)";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EncryptedString that)) return false;
        return Objects.equals(packed, that.packed);
    }

    @Override
    public int hashCode() {
        return Objects.hash(packed);
    }
}
