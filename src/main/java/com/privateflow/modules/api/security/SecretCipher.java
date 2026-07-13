package com.privateflow.modules.api.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SecretCipher {

  private static final String PREFIX = "{aes-gcm}";
  private static final String LEGACY_PLAIN_PREFIX = "{plain}";
  private static final int GCM_TAG_BITS = 128;
  private static final int IV_BYTES = 12;
  private final SecretKeySpec key;
  private final SecureRandom random = new SecureRandom();

  public SecretCipher(
      @Value("${system.secret-encryption-key:${SYSTEM_SECRET_ENCRYPTION_KEY:${system.jwt-secret}}}") String secret) {
    this.key = deriveKey(secret);
  }

  public String encrypt(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    try {
      byte[] iv = new byte[IV_BYTES];
      random.nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
      byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
      ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
      buffer.put(iv);
      buffer.put(encrypted);
      return PREFIX + Base64.getEncoder().encodeToString(buffer.array());
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException("secret encryption failed", ex);
    }
  }

  public String decrypt(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    if (value.startsWith(LEGACY_PLAIN_PREFIX)) {
      return value.substring(LEGACY_PLAIN_PREFIX.length());
    }
    if (!value.startsWith(PREFIX)) {
      return value;
    }
    try {
      byte[] payload = Base64.getDecoder().decode(value.substring(PREFIX.length()));
      if (payload.length <= IV_BYTES) {
        throw new IllegalArgumentException("encrypted secret payload is too short");
      }
      byte[] iv = Arrays.copyOfRange(payload, 0, IV_BYTES);
      byte[] encrypted = Arrays.copyOfRange(payload, IV_BYTES, payload.length);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
      return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    } catch (RuntimeException | GeneralSecurityException ex) {
      throw new IllegalStateException("secret decryption failed", ex);
    }
  }

  private static SecretKeySpec deriveKey(String secret) {
    try {
      String seed = secret == null || secret.isBlank() ? "private-domain-assistant-local-secret" : secret;
      byte[] hash = MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8));
      return new SecretKeySpec(hash, "AES");
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException("secret key derivation failed", ex);
    }
  }
}
