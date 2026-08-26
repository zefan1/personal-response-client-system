package com.privateflow.modules.customer.admin;

import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.infra.CustomerRepository;
import com.privateflow.modules.customer.history.CustomerFieldHistoryContext;
import com.privateflow.modules.profile.infra.ProfileWriter;
import com.privateflow.modules.tablewrite.client.WecomSmartSheetField;
import com.privateflow.modules.tablewrite.client.WecomSmartSheetFieldCatalog;
import com.privateflow.modules.tablewrite.config.AuxiliarySmartSheetTarget;
import com.privateflow.modules.tablewrite.config.WecomSmartSheetConfig;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class IntentProjectMappingService {

  private static final Logger log = LoggerFactory.getLogger(IntentProjectMappingService.class);
  private static final String FIELD_TITLE = "意向项目";
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

  private final IntentProjectMappingRepository repository;
  private final WecomSmartSheetFieldCatalog fieldCatalog;
  private final WecomSmartSheetConfig smartSheetConfig;
  private final CustomerRepository customerRepository;
  private final ProfileWriter profileWriter;

  @Autowired
  public IntentProjectMappingService(
      IntentProjectMappingRepository repository,
      WecomSmartSheetFieldCatalog fieldCatalog,
      WecomSmartSheetConfig smartSheetConfig,
      CustomerRepository customerRepository,
      ProfileWriter profileWriter) {
    this.repository = repository;
    this.fieldCatalog = fieldCatalog;
    this.smartSheetConfig = smartSheetConfig;
    this.customerRepository = customerRepository;
    this.profileWriter = profileWriter;
  }

  public Map<String, Object> current(boolean refresh) {
    if (refresh) {
      synchronizeOptions(false);
    }
    List<IntentProjectMappingRule> rules = repository.list();
    return Map.of("fieldName", FIELD_TITLE, "rules", rules, "total", rules.size());
  }

  public Map<String, Object> refreshOptions() {
    return synchronizeOptions(true);
  }

  private Map<String, Object> synchronizeOptions(boolean forceRemoteRead) {
    if (forceRemoteRead) {
      // The catalog normally caches field metadata for five minutes. An explicit
      // administrator refresh must see just-added WeCom options immediately.
      fieldCatalog.invalidate();
    }
    WecomSmartSheetField field = fieldCatalog.visibleFields(primaryTarget(), READ_TIMEOUT).get(FIELD_TITLE);
    if (field == null) {
      throw new IllegalStateException("企业微信客户主表中没有找到“意向项目”字段");
    }
    if (!"FIELD_TYPE_SINGLE_SELECT".equals(field.type()) && !"FIELD_TYPE_SELECT".equals(field.type())) {
      throw new IllegalStateException("企业微信“意向项目”不是选项字段");
    }
    boolean initial = !repository.exists();
    List<String> ids = new ArrayList<>();
    field.optionIdsByText().forEach((text, id) -> {
      ids.add(id);
      repository.observe(id, text, initial);
    });
    repository.markMissing(ids);
    return current(false);
  }

  public IntentProjectMappingRule save(String optionId, IntentProjectMappingSaveRequest request) {
    if (optionId == null || optionId.isBlank() || request == null) {
      throw new IllegalArgumentException("意向项目规则参数不完整");
    }
    List<String> keywords = request.keywords() == null ? List.of() : request.keywords().stream()
        .filter(value -> value != null && !value.isBlank())
        .map(String::trim)
        .distinct()
        .toList();
    if (keywords.stream().anyMatch(value -> value.length() > 100)) {
      throw new IllegalArgumentException("单个关键词不能超过100个字符");
    }
    return repository.save(optionId.trim(), keywords, request.priority() == null ? 0 : request.priority(),
        request.enabled() == null || request.enabled());
  }

  public Map<String, Object> recompute(boolean onlyEmpty) {
    List<Customer> customers = customerRepository.findCustomersForIntentProject(onlyEmpty);
    int scanned = 0;
    int matched = 0;
    int databaseUpdated = 0;
    int projectionQueued = 0;
    List<Map<String, Object>> errors = new ArrayList<>();
    for (Customer customer : customers) {
      scanned++;
      Optional<Customer> latestCustomer = customerRepository.findByPhone(customer.getPhone());
      if (latestCustomer.isEmpty()) {
        continue;
      }
      Customer latest = latestCustomer.get();
      // A concurrent manual or chat-recognition result always wins over keyword mapping.
      if (latest.getIntendedProject() != null && !latest.getIntendedProject().isBlank()) {
        continue;
      }
      Optional<String> candidate = match(latest.getPurchasedProject());
      if (candidate.isEmpty()) {
        continue;
      }
      matched++;
      if (candidate.get().equals(latest.getIntendedProject())) {
        continue;
      }
      try {
        profileWriter.write(latest.getPhone(), Map.of("intendedProject", candidate.get()), latest.getVersion(), true,
            CustomerFieldHistoryContext.of("已购项目关键词映射", "已购项目", "SYSTEM"));
        databaseUpdated++;
        // ProfileUpdatedEvent is handled asynchronously by TableWriteOrchestrator. It performs
        // the WeCom projection or queues a retry without keeping this administrative request open.
        projectionQueued++;
      } catch (RuntimeException ex) {
        errors.add(error(latest, "唯一事实数据库更新", ex));
        log.warn("intent project mapping database update failed, phone={}", latest.getPhone(), ex);
      }
    }
    return Map.of("onlyEmpty", onlyEmpty, "scanned", scanned, "matched", matched,
        // Keep updated for older callers while exposing the asynchronous projection hand-off.
        "updated", databaseUpdated, "databaseUpdated", databaseUpdated,
        "projectionQueued", projectionQueued, "errors", errors);
  }

  public Optional<String> applyPurchaseMapping(Customer customer) {
    if (customer == null || customer.getPhone() == null || customer.getPhone().isBlank()) {
      return Optional.empty();
    }
    Customer latest = customerRepository.findByPhone(customer.getPhone()).orElse(customer);
    Optional<String> candidate = match(latest.getPurchasedProject());
    if (candidate.isEmpty() || (latest.getIntendedProject() != null && !latest.getIntendedProject().isBlank())) {
      return candidate;
    }
    try {
      profileWriter.write(latest.getPhone(), Map.of("intendedProject", candidate.get()), latest.getVersion(), true,
          CustomerFieldHistoryContext.of("已购项目关键词映射", "已购项目", "SYSTEM"));
    } catch (RuntimeException ex) {
      log.warn("purchase project mapping failed, phone={}", latest.getPhone(), ex);
    }
    return candidate;
  }

  public Optional<String> match(String purchasedProject) {
    if (purchasedProject == null || purchasedProject.isBlank()) {
      return Optional.empty();
    }
    String source = purchasedProject.trim().toLowerCase(Locale.ROOT);
    return repository.list().stream()
        .filter(rule -> "ACTIVE".equals(rule.status()) && !rule.keywords().isEmpty())
        .flatMap(rule -> rule.keywords().stream()
            .filter(keyword -> source.contains(keyword.toLowerCase(Locale.ROOT)))
            .map(keyword -> new Match(rule, keyword)))
        .sorted(Comparator.comparingInt((Match value) -> value.rule().priority()).reversed()
            .thenComparing(Comparator.comparingInt((Match value) -> value.keyword().length()).reversed())
            .thenComparing(value -> value.rule().optionText()))
        .map(value -> value.rule().optionText())
        .findFirst();
  }

  private AuxiliarySmartSheetTarget primaryTarget() {
    return new AuxiliarySmartSheetTarget("PRIMARY", smartSheetConfig.documentId(), smartSheetConfig.sheetId(),
        smartSheetConfig.viewId(), smartSheetConfig.uniqueFieldTitle(), "");
  }

  private Map<String, Object> error(Customer customer, String stage, RuntimeException failure) {
    return Map.of(
        "phone", customer.getPhone(),
        "stage", stage,
        "message", failure.getMessage() == null ? stage + "失败" : failure.getMessage());
  }

  private record Match(IntentProjectMappingRule rule, String keyword) {
  }
}
