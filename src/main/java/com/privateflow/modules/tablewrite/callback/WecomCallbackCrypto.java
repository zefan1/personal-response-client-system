package com.privateflow.modules.tablewrite.callback;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

final class WecomCallbackCrypto {

  private static final int RANDOM_PREFIX_LENGTH = 16;
  private final WecomSmartSheetCallbackConfig config;

  WecomCallbackCrypto(WecomSmartSheetCallbackConfig config) {
    this.config = config;
  }

  String decrypt(String signature, String timestamp, String nonce, String encrypted) {
    requireConfigured();
    if (empty(signature) || empty(timestamp) || empty(nonce) || empty(encrypted)
        || !MessageDigest.isEqual(signature.getBytes(StandardCharsets.UTF_8),
            signature(timestamp, nonce, encrypted).getBytes(StandardCharsets.UTF_8))) {
      throw new IllegalArgumentException("WeCom callback signature was invalid");
    }
    try {
      byte[] key = Base64.getDecoder().decode(config.encodingAesKey() + "=");
      Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(key, 0, 16));
      byte[] decrypted = unpad(cipher.doFinal(Base64.getDecoder().decode(encrypted)));
      if (decrypted.length < RANDOM_PREFIX_LENGTH + Integer.BYTES) {
        throw new IllegalArgumentException("WeCom callback payload was invalid");
      }
      int length = ByteBuffer.wrap(decrypted, RANDOM_PREFIX_LENGTH, Integer.BYTES).getInt();
      int contentStart = RANDOM_PREFIX_LENGTH + Integer.BYTES;
      int contentEnd = contentStart + length;
      if (length < 0 || contentEnd > decrypted.length) {
        throw new IllegalArgumentException("WeCom callback payload was invalid");
      }
      String corpId = new String(decrypted, contentEnd, decrypted.length - contentEnd, StandardCharsets.UTF_8);
      if (!MessageDigest.isEqual(config.corpId().getBytes(StandardCharsets.UTF_8), corpId.getBytes(StandardCharsets.UTF_8))) {
        throw new IllegalArgumentException("WeCom callback corporate identity was invalid");
      }
      return new String(decrypted, contentStart, length, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalArgumentException("WeCom callback payload could not be decrypted");
    }
  }

  String encryptedValue(String xml) {
    return XmlValues.parse(xml).first("Encrypt");
  }

  String signature(String timestamp, String nonce, String encrypted) {
    String[] values = {config.token(), timestamp, nonce, encrypted};
    Arrays.sort(values);
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-1");
      return HexFormat.of().formatHex(digest.digest(String.join("", values).getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-1 was unavailable", ex);
    }
  }

  private void requireConfigured() {
    if (!config.configured()) {
      throw new IllegalStateException("WeCom callback configuration is incomplete");
    }
  }

  private static byte[] unpad(byte[] value) {
    if (value.length == 0) {
      throw new IllegalArgumentException("WeCom callback payload was invalid");
    }
    int padding = value[value.length - 1] & 0xff;
    if (padding < 1 || padding > 32 || padding > value.length) {
      throw new IllegalArgumentException("WeCom callback payload was invalid");
    }
    for (int index = value.length - padding; index < value.length; index++) {
      if ((value[index] & 0xff) != padding) {
        throw new IllegalArgumentException("WeCom callback payload was invalid");
      }
    }
    return Arrays.copyOf(value, value.length - padding);
  }

  private static boolean empty(String value) {
    return value == null || value.isBlank();
  }
}
