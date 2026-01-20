package com.mycom.myapp.sendapp.global.crypto;

import com.google.crypto.tink.DeterministicAead;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * "EncryptedString -> (필요시 복호화) -> 마스킹" 단일 관문.
 *
 * 강제 규칙:
 * - 외부 응답/로그에는 masked*만 사용
 * - plain*은 내부 발송(SMS/Email)에서만 제한적으로 사용
 */
public final class ContactProtector {

    private static final String PREFIX = "v1:";
    private static final byte[] AD_EMAIL = "users.email".getBytes(StandardCharsets.UTF_8);
    private static final byte[] AD_PHONE = "users.phone".getBytes(StandardCharsets.UTF_8);

    private final DeterministicAead daead;

    public ContactProtector(DeterministicAead daead) {
        this.daead = daead;
    }
    
    public String maskedName(String plainName) {
    	return Masker.maskName(plainName);
    }
    
    public String maskedEmail(EncryptedString emailEnc) throws Exception {
        String plain = decryptV1(emailEnc, AD_EMAIL);
        return Masker.maskEmail(plain);
    }

    public String maskedPhone(EncryptedString phoneEnc) throws Exception {
        String plain = decryptV1(phoneEnc, AD_PHONE);
        return Masker.maskPhone010(plain);
    }

    /**
     * 내부 시스템에서 실제 발송이 필요할 때만 사용.
     * 외부 DTO/로그에 절대 내보내지 말 것.
     */
    public String plainEmail(EncryptedString emailEnc) throws Exception {
        return decryptV1(emailEnc, AD_EMAIL);
    }

    /**
     * 내부 시스템에서 실제 발송이 필요할 때만 사용.
     * 외부 DTO/로그에 절대 내보내지 말 것.
     */
    public String plainPhone(EncryptedString phoneEnc) throws Exception {
        return decryptV1(phoneEnc, AD_PHONE);
    }

    private String decryptV1(EncryptedString enc, byte[] ad) throws Exception {
        if (enc == null || enc.isEmpty()) return null;
        String packed = enc.packed();
        if (!packed.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Invalid format: missing 'v1:'");
        }
        byte[] ct = Base64.getDecoder().decode(packed.substring(PREFIX.length()));
        byte[] pt = daead.decryptDeterministically(ct, ad);
        return new String(pt, StandardCharsets.UTF_8);
    }
}
