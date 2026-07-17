package com.privateflow.modules.customer.admin;

import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CustomerAdminSearchService {

  private static final int MAX_EXPORT_ROWS = 10_000;
  private final CustomerAdminSearchRepository repository;
  private final CustomerFilterValidator filterValidator;
  private final CustomerAccessScopeResolver accessScopeResolver;
  private final CustomerCsvWriter csvWriter;

  public CustomerAdminSearchService(
      CustomerAdminSearchRepository repository,
      CustomerFilterValidator filterValidator,
      CustomerAccessScopeResolver accessScopeResolver,
      CustomerCsvWriter csvWriter) {
    this.repository = repository;
    this.filterValidator = filterValidator;
    this.accessScopeResolver = accessScopeResolver;
    this.csvWriter = csvWriter;
  }

  public CustomerAdminSearchPage search(CustomerSearchRequest request) {
    return searchValidated(request == null ? null : request.toFilter());
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
    return searchValidated(new CustomerFilter(
        normalizedKeyword, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
        null, null, List.of(),
        TagGroupLogic.AND, CustomerSortField.UPDATED_AT, SortDirection.DESC, page, size));
  }

  public byte[] export(CustomerSearchRequest request) {
    CustomerFilter filter = filterValidator.validate(request == null ? null : request.toFilter());
    CustomerAccessScope scope = accessScopeResolver.currentScope();
    long total = repository.count(filter, scope);
    if (total > MAX_EXPORT_ROWS) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "导出客户数量超过 10000 条，请缩小筛选范围");
    }
    return csvWriter.write(repository.exportRows(filter, scope, (int) total));
  }

  private CustomerAdminSearchPage searchValidated(CustomerFilter rawFilter) {
    CustomerFilter filter = filterValidator.validate(rawFilter);
    return repository.search(filter, accessScopeResolver.currentScope());
  }
}
