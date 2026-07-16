package com.privateflow.modules.customer.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.privateflow.modules.api.Role;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.auth.AuthUser;
import com.privateflow.modules.customer.service.CustomerAccessService;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CustomerAccessScopeResolverTest {

  @AfterEach
  void clearAuthContext() {
    AuthContext.clear();
  }

  @Test
  void keeperGetsRestrictedAssignedKeeperScope() {
    AuthUser user = new AuthUser("keeper-1", "管家一", Role.KEEPER, null);
    AuthContext.set(user);
    CustomerAccessService accessService = mock(CustomerAccessService.class);
    when(accessService.permittedKeeperPhones(user)).thenReturn(Set.of("keeper-1"));
    CustomerAccessScopeResolver resolver = new CustomerAccessScopeResolver(accessService);

    CustomerAccessScope scope = resolver.currentScope();

    assertThat(scope.unrestricted()).isFalse();
    assertThat(scope.permittedKeepers()).containsExactly("keeper-1");
    assertThat(scope.excludeUnassigned()).isTrue();
  }
}
