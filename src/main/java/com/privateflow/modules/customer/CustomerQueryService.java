package com.privateflow.modules.customer;

import java.util.List;

public interface CustomerQueryService {
  Customer getByPhone(String phone);

  List<Customer> searchByNickname(String nickname);

  List<Customer> searchByNickname(String nickname, int limit);

  List<Customer> scanActiveCustomers(ScanFilter filter);

  void refreshCache(String phone);
}
