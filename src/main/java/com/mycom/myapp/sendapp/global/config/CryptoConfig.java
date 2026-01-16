package com.mycom.myapp.sendapp.global.config;

import com.google.crypto.tink.CleartextKeysetHandle;
import com.google.crypto.tink.DeterministicAead;
import com.google.crypto.tink.JsonKeysetReader;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.daead.DeterministicAeadConfig;
import com.mycom.myapp.sendapp.global.crypto.ContactProtector;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Tink DeterministicAead 및 ContactProtector Bean 등록.
 *
 * 설정 우선순위:
 * 1) application.yml/properties: tink.keyset-b64
 * 2) environment: TINK_KEYSET_B64
 */
@Configuration
public class CryptoConfig {

    @Bean
    public DeterministicAead deterministicAead(@Value("${tink.keyset-b64:}") String keysetB64FromProp) throws Exception {
        String keysetB64 = (keysetB64FromProp != null && !keysetB64FromProp.isBlank())
                ? keysetB64FromProp
                : System.getenv("TINK_KEYSET_B64");

        if (keysetB64 == null || keysetB64.isBlank()) {
            throw new IllegalStateException("Missing keyset: set tink.keyset-b64 or env TINK_KEYSET_B64");
        }

        DeterministicAeadConfig.register();

        String keysetJson = new String(Base64.getDecoder().decode(keysetB64.trim()), StandardCharsets.UTF_8);
        KeysetHandle handle = CleartextKeysetHandle.read(JsonKeysetReader.withString(keysetJson));
        return handle.getPrimitive(DeterministicAead.class);
    }

    @Bean
    public ContactProtector contactProtector(DeterministicAead daead) {
        return new ContactProtector(daead);
    }
}
