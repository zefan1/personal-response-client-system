package com.privateflow.modules.customer.booking;

import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.profile.infra.ProfileWriter;
import com.privateflow.modules.tablewrite.client.AuxiliarySmartSheetWriter;
import com.privateflow.modules.tablewrite.config.AuxiliarySmartSheetTarget;
import com.privateflow.modules.tablewrite.config.AuxiliarySmartSheetTargets;
import com.privateflow.modules.tablewrite.config.TableConfigProvider;
import com.privateflow.modules.tablewrite.infra.TableFieldMappingResolver;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CustomerBookingService {

  private final CustomerQueryService customers;
  private final ProfileWriter writer;
  private final AuxiliarySmartSheetWriter smartSheetWriter;
  private final AuxiliarySmartSheetTargets smartSheetTargets;
  private final TableConfigProvider configProvider;
  private final TableFieldMappingResolver mappingResolver;

  public CustomerBookingService(CustomerQueryService customers, ProfileWriter writer,
      AuxiliarySmartSheetWriter smartSheetWriter, AuxiliarySmartSheetTargets targets,
      TableConfigProvider configProvider, TableFieldMappingResolver mappingResolver) {
    this.customers = customers;
    this.writer = writer;
    this.smartSheetWriter = smartSheetWriter;
    this.smartSheetTargets = targets;
    this.configProvider = configProvider;
    this.mappingResolver = mappingResolver;
  }

  public BookingConfirmResult confirm(String phone, BookingConfirmRequest request) {
    Customer customer = requireCustomer(phone);
    if (request == null || blank(request.appointmentDate()) || blank(request.appointmentStore())) {
      throw new IllegalArgumentException("预约日期和门店不能为空");
    }
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("appointmentDate", request.appointmentDate());
    fields.put("appointmentTime", request.appointmentTime());
    fields.put("appointmentStore", request.appointmentStore());
    fields.put("appointmentItem", request.appointmentItem());
    fields.put("appointmentStatus", "待确认");
    int bookedVersion = writer.write(phone, fields, customer.getVersion(), true);
    complete(customer, request.appointmentDate(), request.appointmentStore(), request.appointmentItem(),
        bookedVersion);
    return new BookingConfirmResult("已预约", template(request));
  }

  public void completeAfterSuggestion(String phone) {
    Customer customer = requireCustomer(phone);
    if (!"待确认".equals(customer.getAppointmentStatus())) {
      return;
    }
    complete(customer, customer.getAppointmentDate(), customer.getAppointmentStore(),
        customer.getAppointmentItem(), customer.getVersion());
  }

  private void complete(
      Customer customer,
      Object appointmentDate,
      String appointmentStore,
      String appointmentItem,
      int expectedVersion) {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("nickname", customer.getNickname());
    fields.put("phone", customer.getPhone());
    fields.put("appointmentDate", appointmentDate);
    fields.put("appointmentStore", appointmentStore);
    fields.put("intendedProject", appointmentItem);
    fields.put("assignedKeeper", customer.getAssignedKeeper());
    AuxiliarySmartSheetTarget arrivalTarget = smartSheetTargets.arrival()
        .orElseThrow(() -> new IllegalStateException("到店表尚未配置"));
    String sourceTable = "ARRIVAL:" + arrivalTarget.sheetId();
    String rowId = smartSheetWriter.upsert(arrivalTarget,
        mappingResolver.toSourceFields(sourceTable, fields),
        mappingResolver.sourceFieldFor(sourceTable, "phone"),
        Duration.ofMillis(configProvider.get().writeTimeoutMs()));
    writer.write(customer.getPhone(), Map.of("appointmentStatus", "已预约", "arrivalSourceRowId", rowId),
        expectedVersion, true);
  }

  private Customer requireCustomer(String phone) {
    Customer customer = customers.getByPhone(phone);
    if (customer == null) throw new IllegalArgumentException("客户不存在");
    return customer;
  }

  private String template(BookingConfirmRequest request) {
    return "请填写预约信息：\n姓名：\n手机号：\n预约日期：" + request.appointmentDate()
        + "\n预约时间：" + safe(request.appointmentTime()) + "\n门店：" + request.appointmentStore()
        + "\n项目：" + safe(request.appointmentItem());
  }

  private boolean blank(String value) { return value == null || value.isBlank(); }
  private String safe(String value) { return value == null ? "" : value; }
}
