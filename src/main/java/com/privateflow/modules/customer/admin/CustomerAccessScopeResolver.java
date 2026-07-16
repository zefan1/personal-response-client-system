package com.privateflow.modules.customer.admin;

import com.privateflow.modules.api.Role;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.auth.AuthUser;
import com.privateflow.modules.customer.service.CustomerAccessService;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CustomerAccessScopeResolver {

  private final CustomerAccessService customerAccessService;

  public CustomerAccessScopeResolver(CustomerAccessService customerAccessService) {
    this.customerAccessService = customerAccessService;
  }

  public CustomerAccessScope currentScope() {
    AuthUser user = AuthContext.current();
    if (user == null || user.role() == Role.ADMIN) {
      return CustomerAccessScope.all();
    }
    if (user.role() == Role.KEEPER || user.role() == Role.LEADER) {
      return new CustomerAccessScope(
          false,
          List.copyOf(customerAccessService.permittedKeeperPhones(user)),
          true);
    }
    return new CustomerAccessScope(false, List.of(), true);
  }
}
