package com.privateflow.modules.customer.admin;

import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.infra.CustomerRepository;
import com.privateflow.modules.tags.TagExchangeResult;
import com.privateflow.modules.tags.TagExchangeService;
import com.privateflow.modules.tags.TagExchangeSourceType;
import java.beans.PropertyDescriptor;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.web.multipart.MultipartFile;

public class CustomerCsvImportProcessor {

  private static final int IMPORT_MAX_ROWS = 5000;

  private final CustomerRepository customerRepository;
  private final TagExchangeService exchangeService;

  public CustomerCsvImportProcessor(CustomerRepository customerRepository, TagExchangeService exchangeService) {
    this.customerRepository = customerRepository;
    this.exchangeService = exchangeService;
  }

  public CsvImportResult importCsv(MultipartFile file) {
    int total = 0;
    int created = 0;
    int updated = 0;
    int skipped = 0;
    int unmatchedCount = 0;
    List<Integer> unmatchedRows = new ArrayList<>();
    List<CsvImportResult.RowError> errors = new ArrayList<>();
    Set<String> seenPhones = new LinkedHashSet<>();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
      String headerLine = reader.readLine();
      if (headerLine == null) {
        return new CsvImportResult(0, 0, 0, 0, List.of(new CsvImportResult.RowError(1, "empty csv")));
      }
      List<String> headers = parseLine(headerLine);
      int phoneIndex = headers.indexOf("phone");
      if (phoneIndex < 0) {
        throw new ApiException(ApiErrorCodes.BAD_REQUEST, "csv must contain phone column");
      }
      String line;
      while ((line = reader.readLine()) != null) {
        total++;
        if (total > IMPORT_MAX_ROWS) {
          throw new ApiException(ApiErrorCodes.BAD_REQUEST, "single import max rows is 5000");
        }
        List<String> values = parseLine(line);
        String phone = phoneIndex < values.size() ? values.get(phoneIndex).trim() : "";
        if (!phone.matches("\\d{11}")) {
          skipped++;
          errors.add(new CsvImportResult.RowError(total + 1, "phone invalid"));
          continue;
        }
        if (!seenPhones.add(phone)) {
          skipped++;
          errors.add(new CsvImportResult.RowError(total + 1, "duplicate phone in same file"));
          continue;
        }
        Customer customer = customerRepository.findByPhone(phone).orElseGet(Customer::new);
        boolean exists = customer.getPhone() != null;
        customer.setPhone(phone);
        customer.setSourceTable(customer.getSourceTable() == null ? "CSV_IMPORT" : customer.getSourceTable());
        customer.setSyncedAt(LocalDateTime.now());
        Map<String, Object> rawFields = new LinkedHashMap<>();
        for (int index = 0; index < headers.size(); index++) {
          String header = headers.get(index);
          if (header.equals("phone") || index >= values.size() || values.get(index).isBlank()) {
            continue;
          }
          rawFields.put(header, values.get(index));
        }
        TagExchangeResult exchange = exchangeService == null
            ? new TagExchangeResult(rawFields, List.of(), List.of())
            : exchangeService.prepareInbound(
                TagExchangeSourceType.CSV_IMPORT,
                String.valueOf(total + 1),
                rawFields);
        applyFields(customer, exchange.acceptedFields());
        if (!exchange.unmatched().isEmpty()) {
          unmatchedCount += exchange.unmatched().size();
          unmatchedRows.add(total + 1);
        }
        customerRepository.upsert(
            customer,
            exchange,
            TagExchangeSourceType.CSV_IMPORT,
            String.valueOf(total + 1));
        if (exists) {
          updated++;
        } else {
          created++;
        }
      }
    } catch (ApiException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ApiException(ApiErrorCodes.INTERNAL_ERROR, "csv import failed");
    }
    return new CsvImportResult(total, created, updated, skipped, errors, unmatchedCount, unmatchedRows);
  }

  private void applyFields(Customer customer, Map<String, Object> fields) {
    for (Map.Entry<String, Object> entry : fields.entrySet()) {
      if ("phone".equals(entry.getKey())) {
        continue;
      }
      if ("nickname".equals(entry.getKey()) && isBlank(customer.getNickname())) {
        setCustomerField(customer, entry.getKey(), entry.getValue());
        continue;
      }
      if (!"nickname".equals(entry.getKey())) {
        setCustomerField(customer, entry.getKey(), entry.getValue());
      }
    }
  }

  private void setCustomerField(Customer customer, String field, Object raw) {
    try {
      PropertyDescriptor descriptor = new PropertyDescriptor(field, Customer.class);
      Method setter = descriptor.getWriteMethod();
      if (setter != null) {
        setter.invoke(customer, convertCustomerField(descriptor.getPropertyType(), raw));
      }
    } catch (Exception ex) {
    }
  }

  private Object convertCustomerField(Class<?> type, Object raw) {
    String value = String.valueOf(raw).trim();
    if (String.class.equals(type)) {
      return value;
    }
    if (BigDecimal.class.equals(type)) {
      return new BigDecimal(value);
    }
    if (LocalDate.class.equals(type)) {
      return LocalDate.parse(value);
    }
    if (LocalDateTime.class.equals(type)) {
      return LocalDateTime.parse(value);
    }
    return value;
  }

  private List<String> parseLine(String line) {
    return List.of(line.split(",", -1)).stream().map(String::trim).toList();
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
