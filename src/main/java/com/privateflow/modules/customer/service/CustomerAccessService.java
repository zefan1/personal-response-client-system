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
      return assignedKeeper.equals(normalize(user.username()));
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
    if (user.role() != Role.LEADER) {
      return phones;
    }
    accountRepository.findByPhone(user.username())
        .map(Account::id)
        .ifPresent(leaderId -> phones.addAll(accountRepository.findEnabledKeeperPhonesByLeaderId(leaderId).stream()
            .map(this::normalize)
            .filter(value -> !value.isBlank())
            .toList()));
    return phones;
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim();
  }
}
