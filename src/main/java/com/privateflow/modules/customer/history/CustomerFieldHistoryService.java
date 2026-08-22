package com.privateflow.modules.customer.history;

import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.infra.CustomerRowMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CustomerFieldHistoryService {

  private final JdbcTemplate jdbcTemplate;
  private final CustomerFieldHistoryRepository repository;
  private final CustomerRowMapper rowMapper = new CustomerRowMapper();

  public CustomerFieldHistoryService(
      JdbcTemplate jdbcTemplate,
      CustomerFieldHistoryRepository repository) {
    this.jdbcTemplate = jdbcTemplate;
    this.repository = repository;
  }

  public CustomerSnapshot snapshotByPhone(String phone) {
    if (phone == null || phone.isBlank()) {
      return null;
    }
    return jdbcTemplate.query("SELECT * FROM customers WHERE phone = ? LIMIT 1", rowMapper, phone)
        .stream().findFirst().map(this::snapshot).orElse(null);
  }

  public CustomerSnapshot snapshotById(long customerId) {
    if (customerId <= 0) {
      return null;
    }
    return jdbcTemplate.query("SELECT * FROM customers WHERE id = ? LIMIT 1", rowMapper, customerId)
        .stream().findFirst().map(this::snapshot).orElse(null);
  }

  public void recordProfileWrite(
      CustomerSnapshot before,
      String phone,
      Collection<String> fields,
      CustomerFieldHistoryContext context) {
    CustomerSnapshot after = snapshotByPhone(phone);
    recordChanges(before, after, fields, context);
  }

  public void recordProfileWriteById(
      CustomerSnapshot before,
      long customerId,
      Collection<String> fields,
      CustomerFieldHistoryContext context) {
    CustomerSnapshot after = snapshotById(customerId);
    recordChanges(before, after, fields, context);
  }

  public void recordExternalSync(
      Customer beforeCustomer,
      String phone,
      String source,
      Map<String, String> sourceFields) {
    CustomerSnapshot before = beforeCustomer == null ? null : snapshot(beforeCustomer);
    CustomerSnapshot after = snapshotByPhone(phone);
    recordChanges(before, after, sourceFields == null ? Map.<String, String>of().keySet() : sourceFields.keySet(),
        CustomerFieldHistoryContext.external(source, sourceFields, "SYSTEM"));
  }

  public java.util.List<CustomerFieldHistoryEntry> history(long customerId, String fieldName) {
    return repository.list(customerId, fieldName);
  }

  public CustomerFieldHistoryEntry latest(long customerId, String fieldName) {
    return repository.latest(customerId, fieldName);
  }

  private void recordChanges(
      CustomerSnapshot before,
      CustomerSnapshot after,
      Collection<String> fields,
      CustomerFieldHistoryContext context) {
    if (after == null || after.customerId() <= 0 || fields == null) {
      return;
    }
    for (String field : fields) {
      if (field == null || field.isBlank()) {
        continue;
      }
      String previous = stringify(before == null ? null : before.fields().get(field));
      String current = stringify(after.fields().get(field));
      if (!Objects.equals(previous, current)) {
        repository.append(after.customerId(), field, current, context);
      }
    }
  }

  private CustomerSnapshot snapshot(Customer customer) {
    Map<String, Object> values = new LinkedHashMap<>();
    try {
      for (java.beans.PropertyDescriptor descriptor : java.beans.Introspector.getBeanInfo(Customer.class).getPropertyDescriptors()) {
        if (!"class".equals(descriptor.getName()) && descriptor.getReadMethod() != null) {
          try {
            values.put(descriptor.getName(), descriptor.getReadMethod().invoke(customer));
          } catch (ReflectiveOperationException ignored) {
            // A field that cannot be read is not a recordable field.
          }
        }
      }
    } catch (java.beans.IntrospectionException ignored) {
      return new CustomerSnapshot(customer.getId() == null ? 0 : customer.getId(), values);
    }
    return new CustomerSnapshot(customer.getId() == null ? 0 : customer.getId(), values);
  }

  private String stringify(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof BigDecimal decimal) {
      return decimal.toPlainString();
    }
    if (value instanceof LocalDate || value instanceof LocalDateTime) {
      return value.toString();
    }
    return String.valueOf(value);
  }

  public record CustomerSnapshot(long customerId, Map<String, Object> fields) {
    public CustomerSnapshot {
      fields = fields == null
          ? Map.of()
          : Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }
  }
}
