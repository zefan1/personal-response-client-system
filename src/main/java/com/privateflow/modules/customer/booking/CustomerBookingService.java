package com.privateflow.modules.customer.booking;

import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.profile.infra.ProfileWriter;
import com.privateflow.modules.arrival.ArrivalHandoverService;
import com.privateflow.modules.customer.history.CustomerFieldHistoryContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerBookingService {

  private final CustomerQueryService customers;
  private final ProfileWriter writer;
  private final ArrivalHandoverService arrivalHandover;

  public CustomerBookingService(CustomerQueryService customers, ProfileWriter writer, ArrivalHandoverService arrivalHandover) {
    this.customers = customers;
    this.writer = writer;
    this.arrivalHandover = arrivalHandover;
  }

  @Transactional
  public BookingConfirmResult confirm(String phone, BookingConfirmRequest request) {
    Customer customer = requireCustomer(phone);
    if (request == null || blank(request.appointmentDate()) || blank(request.appointmentStore())) {
      throw new IllegalArgumentException("预约日期和门店不能为空");
    }
    var taskDecision = arrivalHandover.createOrRefresh(
        customer, request.appointmentDate(), request.appointmentTime(), request.appointmentStore(), request.appointmentItem());
    if (taskDecision.completedDuplicate()) {
      return new BookingConfirmResult("已预约", template(request));
    }
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("appointmentDate", request.appointmentDate());
    fields.put("appointmentTime", request.appointmentTime());
    fields.put("appointmentStore", request.appointmentStore());
    fields.put("appointmentItem", request.appointmentItem());
    fields.put("appointmentStatus", "待补充");
    writer.write(phone, fields, customer.getVersion(), true);
    return new BookingConfirmResult("待补充", template(request));
  }

  /**
   * Persists a complete appointment found in a conversation and creates one
   * human-completion task per project. The current customer snapshot keeps all
   * projects so a later profile refresh does not hide part of the booking.
   */
  @Transactional
  public BookingConfirmResult confirmRecognized(
      Customer customer,
      String customerName,
      String appointmentDate,
      String appointmentTime,
      String appointmentStore,
      List<String> appointmentItems) {
    if (customer == null || customer.getId() == null
        || blank(appointmentDate) || blank(appointmentStore)
        || appointmentItems == null || appointmentItems.stream().noneMatch(item -> !blank(item))) {
      throw new IllegalArgumentException("预约信息不完整");
    }
    List<String> items = appointmentItems.stream()
        .filter(item -> !blank(item))
        .map(String::trim)
        .distinct()
        .toList();
    boolean allCompleted = true;
    for (String item : items) {
      var decision = arrivalHandover.createOrRefresh(
          customer, appointmentDate, appointmentTime, appointmentStore, item);
      allCompleted = allCompleted && decision.completedDuplicate();
    }

    String combinedItems = String.join("、", items);
    Map<String, Object> fields = new LinkedHashMap<>();
    if (!blank(customerName) && !same(customerName, customer.getCustomerName())) {
      fields.put("customerName", customerName.trim());
    }
    if (!appointmentDate.equals(value(customer.getAppointmentDate()))) {
      fields.put("appointmentDate", appointmentDate);
    }
    if (!same(appointmentTime, customer.getAppointmentTime())) {
      fields.put("appointmentTime", appointmentTime);
    }
    if (!same(appointmentStore, customer.getAppointmentStore())) {
      fields.put("appointmentStore", appointmentStore);
    }
    if (!same(combinedItems, customer.getAppointmentItem())) {
      fields.put("appointmentItem", combinedItems);
    }
    String status = allCompleted ? "已预约" : "待补充";
    if (!same(status, customer.getAppointmentStatus())) {
      fields.put("appointmentStatus", status);
    }
    if (!fields.isEmpty()) {
      if (blank(customer.getPhone())) {
        writer.writeByCustomerId(
            customer.getId(), fields, null, true,
            CustomerFieldHistoryContext.of("会话识别", "聊天预约信息", "SYSTEM"));
      } else {
        writer.write(
            customer.getPhone(), fields, null, true,
            CustomerFieldHistoryContext.of("会话识别", "聊天预约信息", "SYSTEM"));
      }
    }
    return new BookingConfirmResult(status, template(new BookingConfirmRequest(
        appointmentDate, appointmentTime, appointmentStore, combinedItems)));
  }

  public void completeAfterSuggestion(String phone) {
    Customer customer = requireCustomer(phone);
    if (customer.getAppointmentDate() == null || blank(customer.getAppointmentStore())) {
      return;
    }
    arrivalHandover.createOrRefresh(customer, customer.getAppointmentDate().toString(), customer.getAppointmentTime(), customer.getAppointmentStore(), customer.getAppointmentItem());
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
  private boolean same(String left, String right) { return safe(left).equals(safe(right)); }
  private String value(java.time.LocalDate value) { return value == null ? "" : value.toString(); }
}
