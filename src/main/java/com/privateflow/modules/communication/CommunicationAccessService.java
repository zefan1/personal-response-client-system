package com.privateflow.modules.communication;

import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.customer.service.CustomerAccessService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommunicationAccessService {

  private final CommunicationArchiveRepository repository;
  private final CustomerQueryService customerQueryService;
  private final CustomerAccessService customerAccessService;

  public CommunicationAccessService(
      CommunicationArchiveRepository repository,
      CustomerQueryService customerQueryService,
      CustomerAccessService customerAccessService) {
    this.repository = repository;
    this.customerQueryService = customerQueryService;
    this.customerAccessService = customerAccessService;
  }

  public CommunicationMessagePage listMessages(
      String phone,
      String platform,
      LocalDate from,
      LocalDate to,
      String keyword,
      Long beforeId,
      int requestedLimit) {
    Customer customer = requireAccessibleCustomer(phone);
    int limit = Math.max(1, Math.min(requestedLimit, 100));
    List<ArchivedCommunicationMessage> rows = repository.searchCustomerMessages(
        customer.getId(),
        platform,
        from == null ? null : from.atStartOfDay(),
        to == null ? null : to.plusDays(1).atStartOfDay(),
        keyword,
        beforeId,
        limit + 1);
    boolean hasMore = rows.size() > limit;
    List<ArchivedCommunicationMessage> page = hasMore
        ? List.copyOf(rows.subList(0, limit))
        : List.copyOf(rows);
    Long nextBeforeId = hasMore && !page.isEmpty() ? page.get(page.size() - 1).id() : null;
    return new CommunicationMessagePage(page, nextBeforeId);
  }

  public CommunicationMessagePage listMessages(
      long customerId,
      String platform,
      LocalDate from,
      LocalDate to,
      String keyword,
      Long beforeId,
      int requestedLimit) {
    Customer customer = requireAccessibleCustomer(customerId);
    return listMessagesForCustomer(customer, platform, from, to, keyword, beforeId, requestedLimit);
  }

  public void correct(long messageId, String correctedText) {
    if (blank(correctedText)) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "corrected text is required");
    }
    ArchivedCommunicationMessage message = repository.findMessage(messageId)
        .orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "message not found"));
    if (message.customerId() == null) {
      if (!AuthContext.username().equals(message.username())) {
        throw new ApiException(ApiErrorCodes.FORBIDDEN, "no access to message");
      }
    } else {
      String phone = repository.findCustomerPhone(message.customerId())
          .orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "customer not found"));
      requireAccessibleCustomer(phone);
    }
    repository.correctMessage(
        messageId, correctedText.trim(), AuthContext.username(), LocalDateTime.now());
  }

  public List<CommunicationSummaryVersion> listSummaryVersions(String phone) {
    Customer customer = requireAccessibleCustomer(phone);
    return repository.findSummaryVersions(customer.getId());
  }

  public List<CommunicationSummaryVersion> listSummaryVersions(long customerId) {
    return repository.findSummaryVersions(requireAccessibleCustomer(customerId).getId());
  }

  private CommunicationMessagePage listMessagesForCustomer(
      Customer customer,
      String platform,
      LocalDate from,
      LocalDate to,
      String keyword,
      Long beforeId,
      int requestedLimit) {
    int limit = Math.max(1, Math.min(requestedLimit, 100));
    List<ArchivedCommunicationMessage> rows = repository.searchCustomerMessages(
        customer.getId(),
        platform,
        from == null ? null : from.atStartOfDay(),
        to == null ? null : to.plusDays(1).atStartOfDay(),
        keyword,
        beforeId,
        limit + 1);
    boolean hasMore = rows.size() > limit;
    List<ArchivedCommunicationMessage> page = hasMore
        ? List.copyOf(rows.subList(0, limit))
        : List.copyOf(rows);
    Long nextBeforeId = hasMore && !page.isEmpty() ? page.get(page.size() - 1).id() : null;
    return new CommunicationMessagePage(page, nextBeforeId);
  }

  private Customer requireAccessibleCustomer(String phone) {
    if (blank(phone)) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "phone is required");
    }
    Customer customer = customerQueryService.getByPhone(phone.trim());
    if (customer == null || customer.getId() == null) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "customer not found");
    }
    if (!customerAccessService.canAccess(customer)) {
      throw new ApiException(ApiErrorCodes.FORBIDDEN, "no access to customer");
    }
    return customer;
  }

  private Customer requireAccessibleCustomer(long customerId) {
    if (customerId <= 0) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "customerId is required");
    }
    Customer customer = customerQueryService.getById(customerId);
    if (customer == null || customer.getId() == null) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "customer not found");
    }
    if (!customerAccessService.canAccess(customer)) {
      throw new ApiException(ApiErrorCodes.FORBIDDEN, "no access to customer");
    }
    return customer;
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
