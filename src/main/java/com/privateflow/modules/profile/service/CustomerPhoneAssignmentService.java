package com.privateflow.modules.profile.service;

import com.privateflow.common.events.CustomerTableSyncRequestedEvent;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.customer.service.CustomerAccessService;
import com.privateflow.modules.match.util.PhoneUtils;
import com.privateflow.modules.profile.CustomerPhoneAssignmentRequest;
import com.privateflow.modules.profile.CustomerPhoneAssignmentResult;
import com.privateflow.modules.profile.ProfileErrorCodes;
import com.privateflow.modules.profile.ProfileUpdateException;
import com.privateflow.modules.profile.infra.AuditLogRepository;
import com.privateflow.modules.profile.infra.ProfileWriter;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class CustomerPhoneAssignmentService {

  private final CustomerQueryService customerQueryService;
  private final CustomerAccessService customerAccessService;
  private final ProfileWriter profileWriter;
  private final AuditLogRepository auditLogRepository;
  private final ApplicationEventPublisher eventPublisher;

  public CustomerPhoneAssignmentService(
      CustomerQueryService customerQueryService,
      CustomerAccessService customerAccessService,
      ProfileWriter profileWriter,
      AuditLogRepository auditLogRepository,
      ApplicationEventPublisher eventPublisher) {
    this.customerQueryService = customerQueryService;
    this.customerAccessService = customerAccessService;
    this.profileWriter = profileWriter;
    this.auditLogRepository = auditLogRepository;
    this.eventPublisher = eventPublisher;
  }

  public CustomerPhoneAssignmentResult assign(long customerId, CustomerPhoneAssignmentRequest request) {
    if (customerId <= 0 || request == null || request.version() == null) {
      throw badRequest("customer id and version are required");
    }
    String phone = PhoneUtils.clean(request.phone());
    if (!PhoneUtils.isValid(phone)) {
      throw badRequest("phone format is invalid");
    }
    Customer customer = customerQueryService.getById(customerId);
    if (customer == null) {
      throw badRequest("customer does not exist");
    }
    if (!customerAccessService.canAccess(customer)) {
      throw badRequest("customer is outside of your access scope");
    }
    if (customer.getPhone() != null && !customer.getPhone().isBlank()) {
      throw badRequest("customer already has a phone number");
    }
    if (!request.version().equals(customer.getVersion())) {
      throw new ProfileUpdateException(ProfileErrorCodes.VERSION_CONFLICT, "customer profile is stale");
    }
    Customer existing = customerQueryService.getByPhone(phone);
    if (existing != null && !customerIdEquals(existing, customerId)) {
      throw badRequest("phone already belongs to another customer");
    }
    int version = profileWriter.writeByCustomerId(customerId, Map.of("phone", phone), request.version(), false);
    auditLogRepository.log("ASSIGN_CUSTOMER_PHONE", request.operator(), "customer", Long.toString(customerId),
        "manual phone assignment");
    eventPublisher.publishEvent(new CustomerTableSyncRequestedEvent(customerId));
    return new CustomerPhoneAssignmentResult(customerId, version);
  }

  private boolean customerIdEquals(Customer customer, long customerId) {
    return customer.getId() != null && customer.getId() == customerId;
  }

  private ProfileUpdateException badRequest(String message) {
    return new ProfileUpdateException(ProfileErrorCodes.BAD_REQUEST, message);
  }
}
