package com.privateflow.modules.profile.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.common.events.ManualProfileUpdatedEvent;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.history.CustomerFieldHistoryContext;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.customer.service.CustomerAccessService;
import com.privateflow.modules.profile.ManualProfileUpdateRequest;
import com.privateflow.modules.profile.ManualProfileUpdateResult;
import com.privateflow.modules.profile.ProfileErrorCodes;
import com.privateflow.modules.profile.ProfileUpdateException;
import com.privateflow.modules.profile.infra.AuditLogRepository;
import com.privateflow.modules.profile.infra.ProfileWriter;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import java.util.Map;

@Service
public class ManualEditHandler {

  private final CustomerQueryService customerQueryService;
  private final ProfileWriter profileWriter;
  private final AuditLogRepository auditLogRepository;
  private final CustomerAccessService customerAccessService;
  private final ApplicationEventPublisher eventPublisher;
  private final ObjectMapper objectMapper;

  public ManualEditHandler(
      CustomerQueryService customerQueryService,
      ProfileWriter profileWriter,
      AuditLogRepository auditLogRepository,
      CustomerAccessService customerAccessService,
      ApplicationEventPublisher eventPublisher,
      ObjectMapper objectMapper) {
    this.customerQueryService = customerQueryService;
    this.profileWriter = profileWriter;
    this.auditLogRepository = auditLogRepository;
    this.customerAccessService = customerAccessService;
    this.eventPublisher = eventPublisher;
    this.objectMapper = objectMapper;
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
    int version = profileWriter.write(
        phone,
        request.fields(),
        request.version(),
        true,
        CustomerFieldHistoryContext.of("人工编辑", "后台客户档案", request.operator()));
    auditLogRepository.log(
        "UPDATE_PROFILE", request.operator(), "customer", phone, auditDetail(request, version));
    if (eventPublisher != null) {
      eventPublisher.publishEvent(new ManualProfileUpdatedEvent(phone, request.fields(), request.operator()));
    }
    return new ManualProfileUpdateResult(version);
  }

  private String auditDetail(ManualProfileUpdateRequest request, int version) {
    Map<String, Object> detail = new java.util.LinkedHashMap<>();
    detail.put("fields", request.fields() == null ? Map.of() : request.fields().keySet());
    detail.put("version", version);
    detail.put("syncMode", "AUTOMATIC");
    try {
      return objectMapper.writeValueAsString(detail);
    } catch (JsonProcessingException ex) {
      return "manual profile update; automatic table projection requested";
    }
  }
}
