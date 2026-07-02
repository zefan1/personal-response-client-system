package com.privateflow.modules.analytics;

import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.Role;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.auth.AuthUser;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsService {

  private final AnalyticsRepository repository;

  public AnalyticsService(AnalyticsRepository repository) {
    this.repository = repository;
  }

  public Map<String, Object> overview(int days, String leadType, String caller) {
    return repository.overview(safeDays(days), normalizeLeadType(leadType), scope(caller));
  }

  public Map<String, Object> funnels(String leadType, String caller) {
    requireManager();
    return repository.funnels(normalizeLeadType(leadType), scope(caller));
  }

  public Map<String, Object> staff(int days, String leadType, String caller) {
    requireManager();
    return repository.staff(safeDays(days), normalizeLeadType(leadType), scope(caller));
  }

  public Map<String, Object> sources(int days, String leadType, String caller) {
    requireManager();
    return repository.sources(safeDays(days), normalizeLeadType(leadType), scope(caller));
  }

  public Map<String, Object> stages(String leadType, String caller) {
    requireManager();
    return repository.stages(normalizeLeadType(leadType), scope(caller));
  }

  public Map<String, Object> health(int days, String leadType, String caller) {
    requireManager();
    return repository.health(safeDays(days), normalizeLeadType(leadType), scope(caller));
  }

  public Map<String, Object> lifecycle(String leadType, String caller) {
    requireManager();
    return repository.lifecycle(normalizeLeadType(leadType), scope(caller));
  }

  public Map<String, Object> risks(int days, String leadType, String caller) {
    requireManager();
    return repository.risks(safeDays(days), normalizeLeadType(leadType), scope(caller));
  }

  public Map<String, Object> contentRanking(int days, String leadType, String caller) {
    requireManager();
    return repository.contentRanking(safeDays(days), normalizeLeadType(leadType), scope(caller));
  }

  private AnalyticsScope scope(String caller) {
    AuthUser user = AuthContext.current();
    if (user == null) {
      throw new ApiException(ApiErrorCodes.AUTH_FAILED, "Token required");
    }
    String requestedCaller = blank(caller) ? null : caller.trim();
    if (user.role() == Role.KEEPER) {
      requestedCaller = user.username();
    }
    return new AnalyticsScope(user.role(), user.username(), requestedCaller);
  }

  private void requireManager() {
    AuthUser user = AuthContext.current();
    if (user == null || user.role() == Role.KEEPER) {
      throw new ApiException(ApiErrorCodes.FORBIDDEN, "permission denied");
    }
  }

  private int safeDays(int days) {
    return Math.max(1, Math.min(days, 90));
  }

  private String normalizeLeadType(String leadType) {
    if (blank(leadType) || "ALL".equalsIgnoreCase(leadType.trim())) {
      return null;
    }
    String normalized = leadType.trim().toUpperCase();
    if (!"TUAN_GOU".equals(normalized) && !"XIAN_SUO".equals(normalized) && !"PENDING".equals(normalized)) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "leadType invalid");
    }
    return normalized;
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
