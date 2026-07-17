package com.privateflow.modules.customer.admin;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CustomerCsvWriter {

  private static final String HEADER = "客户ID,手机号,客户昵称,来源渠道,线索类型,分配管家,意向门店,意向项目,客户阶段,意向等级,最近跟进时间,下次跟进时间,预约日期,预约门店,预约项目,是否到店,来源表,更新时间,当前标签\r\n";

  public byte[] write(List<CustomerAdminListItem> items) {
    StringBuilder csv = new StringBuilder("\uFEFF").append(HEADER);
    if (items != null) {
      for (CustomerAdminListItem item : items) {
        if (item == null) {
          continue;
        }
        appendRow(csv, Arrays.asList(
            item.id(), item.phone(), item.nickname(), item.sourceChannel(), item.leadType(),
            item.assignedKeeper(), item.intendedStore(), item.intendedProject(), item.customerStage(),
            item.intentLevel(), item.lastFollowupAt(), item.nextFollowupAt(), item.appointmentDate(),
            item.appointmentStore(), item.appointmentItem(), item.arrived(), item.sourceTable(),
            item.updatedAt(), tagText(item.tags())));
      }
    }
    return csv.toString().getBytes(StandardCharsets.UTF_8);
  }

  private String tagText(List<CustomerTagSummary> tags) {
    if (tags == null || tags.isEmpty()) {
      return "";
    }
    return tags.stream()
        .map(tag -> String.valueOf(tag.categoryName()) + ":" + tag.displayName() + "[" + tag.valueCode() + "]")
        .reduce((left, right) -> left + "；" + right)
        .orElse("");
  }

  private void appendRow(StringBuilder csv, List<?> values) {
    for (int index = 0; index < values.size(); index++) {
      if (index > 0) {
        csv.append(',');
      }
      csv.append(csvCell(values.get(index)));
    }
    csv.append("\r\n");
  }

  private String csvCell(Object value) {
    String raw = value == null ? "" : String.valueOf(value);
    if (!raw.isEmpty() && "=+-@".indexOf(raw.charAt(0)) >= 0) {
      raw = "'" + raw;
    }
    return '"' + raw.replace("\"", "\"\"") + '"';
  }
}
