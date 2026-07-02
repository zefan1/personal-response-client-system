package com.privateflow.modules.api.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.Role;
import com.privateflow.modules.api.config.SystemConfigProvider;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
  private final SystemConfigProvider configProvider;
  private final ObjectMapper objectMapper;

  public JwtService(SystemConfigProvider configProvider, ObjectMapper objectMapper) {
    this.configProvider = configProvider;
    this.objectMapper = objectMapper;
  }

  public String issue(AuthUser user) {
    try {
      long now = Instant.now().getEpochSecond();
      // Compatibility note: module H originally used jwtExpireHours; module 44 prefers seconds.
      long exp = now + configProvider.get().jwtAccessTokenTtlS();
      Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("phone", user.username());
      payload.put("username", user.username());
      payload.put("displayName", user.displayName());
      payload.put("role", user.role().name());
      payload.put("leaderId", user.leaderId());
      payload.put("iat", now);
      payload.put("exp", exp);
      String unsigned = encode(header) + "." + encode(payload);
      return unsigned + "." + sign(unsigned);
    } catch (Exception ex) {
      throw new ApiException(ApiErrorCodes.INTERNAL_ERROR, "failed to issue token");
    }
  }

  public AuthUser verify(String token) {
    try {
      String[] parts = token == null ? new String[0] : token.split("\\.");
      if (parts.length != 3) {
        throw new ApiException(ApiErrorCodes.AUTH_FAILED, "Token invalid");
      }
      String unsigned = parts[0] + "." + parts[1];
      if (!constantEquals(sign(unsigned), parts[2])) {
        throw new ApiException(ApiErrorCodes.AUTH_FAILED, "Token invalid");
      }
      Map<String, Object> payload = objectMapper.readValue(URL_DECODER.decode(parts[1]), new TypeReference<>() {});
      long exp = ((Number) payload.get("exp")).longValue();
      if (Instant.now().getEpochSecond() >= exp) {
        throw new ApiException(ApiErrorCodes.AUTH_FAILED, "Token expired");
      }
      Object leaderId = payload.get("leaderId");
      Object phone = payload.get("phone") == null ? payload.get("username") : payload.get("phone");
      return new AuthUser(
          phone.toString(),
          payload.get("displayName").toString(),
          Role.valueOf(payload.get("role").toString()),
          leaderId == null ? null : Long.valueOf(leaderId.toString()));
    } catch (ApiException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ApiException(ApiErrorCodes.AUTH_FAILED, "Token invalid");
    }
  }

  private String encode(Object value) throws Exception {
    return URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
  }

  private String sign(String unsigned) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(configProvider.get().jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return URL_ENCODER.encodeToString(mac.doFinal(unsigned.getBytes(StandardCharsets.UTF_8)));
  }

  private boolean constantEquals(String left, String right) {
    return left != null && right != null && MessageDigestCompat.equals(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
  }

  private static final class MessageDigestCompat {
    static boolean equals(byte[] left, byte[] right) {
      if (left.length != right.length) {
        return false;
      }
      int result = 0;
      for (int i = 0; i < left.length; i++) {
        result |= left[i] ^ right[i];
      }
      return result == 0;
    }
  }
}
