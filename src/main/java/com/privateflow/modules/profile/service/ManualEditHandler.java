package com.privateflow.modules.profile.service;

import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.customer.service.CustomerAccessService;
import com.privateflow.modules.profile.ManualProfileUpdateRequest;
import com.privateflow.modules.profile.ManualProfileUpdateResult;
import com.privateflow.modules.profile.ProfileErrorCodes;
import com.privateflow.modules.profile.ProfileUpdateException;
import com.privateflow.modules.profile.infra.AuditLogRepository;
import com.privateflow.modules.profile.infra.ProfileWriter;
import org.springframework.stereotype.Service;

@Service
public class ManualEditHandler {

  private final CustomerQueryService customerQueryService;
  private final ProfileWriter profileWriter;
  private final AuditLogRepository auditLogRepository;
  private final CustomerAccessService customerAccessService;

  public ManualEditHandler(
      CustomerQueryService customerQueryService,
      ProfileWriter profileWriter,
      AuditLogRepository auditLogRepository,
      CustomerAccessService customerAccessService) {
    this.customerQueryService = customerQueryService;
    this.profileWriter = profileWriter;
    this.auditLogRepository = auditLogRepository;
    this.customerAccessService = customerAccessService;
  }

  public ManualProfileUpdateResult update(String phone, ManualProfileUpdateRequest request) {
    if (request == null || request.version() == null) {
      throw new ProfileUpdateException(ProfileErrorCodes.BAD_REQUEST, "version 必填");
    }
    Customer customer = customerQueryService.getByPhone(phone);
    if (customer == null) {
      throw new ProfileUpdateException(ProfileErrorCodes.BAD_REQUEST, "客户不存在");
    }
    if (!customerAccessService.canAccess(customer)) {
      throw new ProfileUpdateException(ProfileErrorCodes.BAD_REQUEST, "该客户不在你的负责范围内");
    }
    if (!request.version().equals(customer.getVersion())) {
      throw new ProfileUpdateException(ProfileErrorCodes.VERSION_CONFLICT, "档案已被更新，请刷新后重试");
    }
    int version = profileWriter.write(phone, request.fields(), request.version(), true);
    auditLogRepository.log("UPDATE_PROFILE", request.operator(), "customer", phone, "manual profile update");
    return new ManualProfileUpdateResult(version);
  }
}
