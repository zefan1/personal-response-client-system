package com.privateflow.modules.customer.admin;

import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import org.springframework.stereotype.Service;

@Service
public class CustomerAdminSearchService {

  private final CustomerAdminSearchRepository repository;

  public CustomerAdminSearchService(CustomerAdminSearchRepository repository) {
    this.repository = repository;
  }

  public CustomerAdminSearchPage search(String keyword, int page, int size) {
    String normalizedKeyword = keyword == null ? "" : keyword.trim();
    if (normalizedKeyword.length() > 100) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "搜索关键词不能超过 100 个字符");
    }
    if (page < 1) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "页码必须大于等于 1");
    }
    if (size < 1 || size > 50) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "每页数量必须在 1-50 之间");
    }
    return repository.search(normalizedKeyword, page, size);
  }
}
