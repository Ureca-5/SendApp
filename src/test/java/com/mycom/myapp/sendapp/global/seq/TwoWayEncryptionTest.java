package com.mycom.myapp.sendapp.global.seq;

import com.google.crypto.tink.CleartextKeysetHandle;
import com.google.crypto.tink.DeterministicAead;
import com.google.crypto.tink.JsonKeysetReader;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.daead.DeterministicAeadConfig;
import com.mycom.myapp.sendapp.global.crypto.ContactProtector;
import com.mycom.myapp.sendapp.global.crypto.EncryptedString;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class TwoWayEncryptionTest {

  private static final String KEYSET_B64 = ""; //test key 활용

  private static final byte[] AD_EMAIL = "users.email".getBytes(StandardCharsets.UTF_8);
  private static final byte[] AD_PHONE = "users.phone".getBytes(StandardCharsets.UTF_8);

  @Test
  void roundTrip_phone_masked_only() throws Exception {
    DeterministicAead daead = daeadFromKeysetB64(KEYSET_B64);
    ContactProtector protector = new ContactProtector(daead);

    String phonePlain = "01000010001";

    // encrypt -> v1:pack
    byte[] ct = daead.encryptDeterministically(phonePlain.getBytes(StandardCharsets.UTF_8), AD_PHONE);
    String packed = "v1:" + Base64.getEncoder().encodeToString(ct);

    String masked = protector.maskedPhone(EncryptedString.of(packed));
    assertEquals("010****0001", masked);     // 평문 출력 없이 성공 검증
  }

  @Test
  void roundTrip_email_masked_only() throws Exception {
    DeterministicAead daead = daeadFromKeysetB64(KEYSET_B64);
    ContactProtector protector = new ContactProtector(daead);

    String emailPlain = "abced@test.com";

    byte[] ct = daead.encryptDeterministically(emailPlain.getBytes(StandardCharsets.UTF_8), AD_EMAIL);
    String packed = "v1:" + Base64.getEncoder().encodeToString(ct);

    String masked = protector.maskedEmail(EncryptedString.of(packed));
    assertEquals("ab***@test.com", masked);
  }

  private static DeterministicAead daeadFromKeysetB64(String keysetB64) throws Exception {
    if (keysetB64 == null || keysetB64.isBlank()) {
      throw new IllegalStateException("Set -Dtink.keyset-b64.test=... (base64 JSON keyset)");
    }
    DeterministicAeadConfig.register();
    String keysetJson = new String(Base64.getDecoder().decode(keysetB64.trim()), StandardCharsets.UTF_8);
    KeysetHandle handle = CleartextKeysetHandle.read(JsonKeysetReader.withString(keysetJson));
    return handle.getPrimitive(DeterministicAead.class);
  }
}
