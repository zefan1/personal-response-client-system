package com.privateflow.modules.customer.service;

import com.privateflow.modules.api.Role;
import com.privateflow.modules.api.auth.Account;
import com.privateflow.modules.api.auth.AccountRepository;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.auth.AuthUser;
import com.privateflow.modules.customer.Customer;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class CustomerAccessService {

  private final AccountRepository accountRepository;

  public CustomerAccessService(AccountRepository accountRepository) {
    this.accountRepository = accountRepository;
  }

  public boolean canAccess(Customer customer) {
    if (customer == null) {
      return false;
    }
    AuthUser user = AuthContext.current();
    if (user == null || user.role() == Role.ADMIN) {
      return true;
    }
    String assignedKeeper = normalize(customer.getAssignedKeeper());
    if (assignedKeeper.isBlank()) {
      return false;
    }
    if (user.role() == Role.KEEPER) {
      return matchesUserIdentity(assignedKeeper, user);
    }
    if (user.role() == Role.LEADER) {
      return permittedKeeperPhones(user).contains(assignedKeeper);
    }
    return false;
  }

  public Set<String> permittedKeeperPhones(AuthUser user) {
    Set<String> phones = new HashSet<>();
    if (user == null) {
      return phones;
    }
    String username = normalize(user.username());
    if (!username.isBlank()) {
      phones.add(username);
    }
    String displayName = normalize(user.displayName());
    if (!displayName.isBlank()) {
      phones.add(displayName);
    }
    if (user.role() != Role.LEADER) {
      return phones;
    }
    accountRepository.findByPhone(user.username())
        .map(Account::id)
        .ifPresent(leaderId -> accountRepository.findEnabledKeepersByLeaderId(leaderId).forEach(account -> {
          String keeperPhone = normalize(account.username());
          String keeperName = normalize(account.displayName());
          if (!keeperPhone.isBlank()) {
            phones.add(keeperPhone);
          }
          if (!keeperName.isBlank()) {
            phones.add(keeperName);
          }
        }));
    return phones;
  }

  private boolean matchesUserIdentity(String assignedKeeper, AuthUser user) {
    if (assignedKeeper.equals(normalize(user.username()))
        || assignedKeeper.equals(normalize(user.displayName()))) {
      return true;
    }
    return accountRepository.resolveEnabledUsername(assignedKeeper)
        .map(username -> username.equals(normalize(user.username())))
        .orElse(false);
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim();
  }
}
