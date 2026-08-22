package com.privateflow.modules.api.chat;

import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.Role;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.auth.AuthUser;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PendingSendConfirmationAdminService {

  private final SendConfirmationRepository repository;

  public PendingSendConfirmationAdminService(SendConfirmationRepository repository) {
    this.repository = repository;
  }

  public Map<String, Object> summary(int days, String operator) {
    requireAdmin();
    return repository.summary(Math.max(1, Math.min(days, 90)), operator);
  }

  private void requireAdmin() {
    AuthUser user = AuthContext.current();
    if (user == null || user.role() != Role.ADMIN) {
      throw new ApiException(ApiErrorCodes.FORBIDDEN, "permission denied");
    }
  }
}
