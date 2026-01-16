package com.mycom.myapp.sendapp.global.config;

import com.google.crypto.tink.CleartextKeysetHandle;
import com.google.crypto.tink.DeterministicAead;
import com.google.crypto.tink.JsonKeysetReader;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.daead.DeterministicAeadConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Base64;

@Configuration
public class CryptoConfig {

    // 네 .env 키 이름
    @Value("${APP_AES256_KEY_B64:}")
    private String appAes256KeyB64;

    // (선택) 혹시 나중에 표준 변수명으로 바꾸면 같이 먹게
    @Value("${TINK_KEYSET_B64:}")
    private String tinkKeysetB64;

    // (선택) yml에 직접 넣는 케이스
    @Value("${tink.keyset-b64:}")
    private String tinkKeysetB64Prop;

    @Bean
    public DeterministicAead deterministicAead() throws Exception {
        DeterministicAeadConfig.register();

        // 우선순위: yml > TINK_KEYSET_B64 > APP_AES256_KEY_B64
        String keysetB64 = firstNonBlank(tinkKeysetB64Prop, tinkKeysetB64, appAes256KeyB64);
        if (isBlank(keysetB64)) {
            throw new IllegalStateException(
                    "Missing keyset: set APP_AES256_KEY_B64 in .env (or TINK_KEYSET_B64 / tink.keyset-b64)."
            );
        }

        String keysetJson;
        try {
            keysetJson = new String(Base64.getDecoder().decode(keysetB64.trim()));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Keyset is not valid Base64. Check APP_AES256_KEY_B64 value.", e);
        }

        // Base64 decode 결과는 Tink keyset JSON이어야 정상
        String t = keysetJson.trim();
        if (!(t.startsWith("{") && t.endsWith("}"))) {
            throw new IllegalStateException(
                    "Decoded keyset is not JSON. APP_AES256_KEY_B64 must be Base64(JSON Tink keyset), not raw AES key."
            );
        }

        KeysetHandle handle = CleartextKeysetHandle.read(JsonKeysetReader.withString(keysetJson));
        return handle.getPrimitive(DeterministicAead.class);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (!isBlank(v)) return v;
        }
        return null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
