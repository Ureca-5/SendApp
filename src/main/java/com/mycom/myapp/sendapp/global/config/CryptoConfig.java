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

@Configuration
public class CryptoConfig {

    @Value("${APP_AES256_KEY_B64:}")
    private String keysetB64;

    @Bean
    public DeterministicAead deterministicAead() throws Exception {
        if (keysetB64 == null || keysetB64.isBlank()) {
            throw new IllegalStateException("Missing keyset: set APP_AES256_KEY_B64 in .env");
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
