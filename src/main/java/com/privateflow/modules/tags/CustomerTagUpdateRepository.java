package com.privateflow.modules.tags;

import com.privateflow.modules.profile.infra.ProfileFieldRegistry;
import com.privateflow.modules.skill.TagAnalysisAction;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class CustomerTagUpdateRepository {

  private final JdbcTemplate jdbcTemplate;
  private final CustomerTagFoundationRepository foundationRepository;
  private final ProfileFieldRegistry fieldRegistry;

  @Autowired
  public CustomerTagUpdateRepository(
      JdbcTemplate jdbcTemplate,
      CustomerTagFoundationRepository foundationRepository,
      ProfileFieldRegistry fieldRegistry) {
    this.jdbcTemplate = jdbcTemplate;
    this.foundationRepository = foundationRepository;
    this.fieldRegistry = fieldRegistry;
  }

  CustomerTagUpdateRepository(
      JdbcTemplate jdbcTemplate,
      CustomerTagFoundationRepository foundationRepository) {
    this(jdbcTemplate, foundationRepository, new ProfileFieldRegistry());
  }

  public List<CustomerTagAssignment> findActiveAssignments(long customerId, long categoryId) {
    return foundationRepository.findCurrentAssignments(customerId).stream()
        .filter(assignment -> assignment.categoryId() == categoryId)
        .toList();
  }

  public Optional<CustomerTagCategoryLock> findCategoryLock(long customerId, long categoryId) {
    return foundationRepository.findCategoryLocks(customerId).stream()
        .filter(lock -> lock.categoryId() == categoryId)
        .findFirst();
  }

  public Optional<LocalDateTime> findLastAutomaticUpdateAt(long customerId, long categoryId) {
    return jdbcTemplate.query("""
        SELECT MAX(created_at) AS last_updated_at
        FROM customer_tag_assignments
        WHERE customer_id = ? AND category_id = ? AND source_type = 'SYSTEM_INFERENCE'
        """, (rs, rowNum) -> {
          java.sql.Timestamp value = rs.getTimestamp("last_updated_at");
          return value == null ? null : value.toLocalDateTime();
        }, customerId, categoryId).stream().filter(java.util.Objects::nonNull).findFirst();
  }

  @Transactional
  public CustomerTagUpdateResult applyAutomatic(AutomaticCustomerTagUpdatePlan plan) {
    List<AutomaticMutation> mutations = plan.decisions().stream()
        .filter(AutomaticCustomerTagDecisionPlan::accepted)
        .map(this::mutation)
        .filter(AutomaticMutation::changesAssignments)
        .toList();
    boolean shouldUpdate = !mutations.isEmpty();
    int nextVersion = plan.expectedCustomerVersion();
    boolean versionConflict = false;
    if (shouldUpdate) {
      int updated = updateCustomer(plan, mutations);
      if (updated == 1) {
        nextVersion++;
      } else {
        versionConflict = true;
        shouldUpdate = false;
        nextVersion = currentCustomerVersion(plan.customerId());
      }
    }

    long analysisRunId = insertAnalysisRun(plan, versionConflict, shouldUpdate);
    Map<CategoryValueKey, Long> resultIds = insertAnalysisResults(
        analysisRunId, plan, versionConflict);
    if (shouldUpdate) {
      for (AutomaticMutation mutation : mutations) {
        persistMutation(plan, mutation, nextVersion, resultIds);
      }
    }
    insertAudit(plan, versionConflict, shouldUpdate);

    List<CustomerTagDecisionResult> results = new ArrayList<>();
    for (AutomaticCustomerTagDecisionPlan decision : plan.decisions()) {
      boolean decisionUpdated = shouldUpdate
          && mutations.stream().anyMatch(mutation -> mutation.decision() == decision);
      String reason = versionConflict && decision.accepted()
          ? "客户数据版本已被其他操作更新"
          : decisionUpdated
              ? decision.action() == TagAnalysisAction.ADD ? "自动新增完成" : "自动替换完成"
              : decision.reason();
      results.add(new CustomerTagDecisionResult(
          decision.category() == null ? 0 : decision.category().id(),
          decision.category() == null
              ? decision.analysisDecision() == null ? "" : decision.analysisDecision().categoryCode()
              : decision.category().categoryKey(),
          decision.action().name(),
          decisionUpdated,
          reason));
    }
    return new CustomerTagUpdateResult(nextVersion, shouldUpdate, results);
  }

  @Transactional
  public CustomerTagUpdateResult applyManual(ManualCustomerTagUpdatePlan plan) {
    int updated = updateManualCustomer(plan);
    if (updated != 1) {
      int currentVersion = currentCustomerVersion(plan.customerId());
      insertManualAudit(plan, false, "客户数据版本已被其他操作更新");
      return new CustomerTagUpdateResult(
          currentVersion,
          false,
          List.of(new CustomerTagDecisionResult(
              plan.category().id(),
              plan.category().categoryKey(),
              "REPLACE",
              false,
              "客户数据版本已被其他操作更新")));
    }
    int nextVersion = plan.expectedCustomerVersion() + 1;
    String invalidatedReason = plan.desiredValues().isEmpty()
        ? "MANUAL_REMOVED"
        : "MANUAL_REPLACED";
    jdbcTemplate.update("""
        UPDATE customer_tag_assignments
        SET is_active = 0, invalidated_reason = ?, invalidated_at = ?, updated_at = ?
        WHERE customer_id = ? AND category_id = ? AND is_active = 1
        """,
        invalidatedReason,
        plan.evaluatedAt(),
        plan.evaluatedAt(),
        plan.customerId(),
        plan.category().id());
    Long supersedes = plan.previousAssignments().stream()
        .map(CustomerTagAssignment::id)
        .findFirst()
        .orElse(null);
    for (TagValue value : plan.desiredValues()) {
      jdbcTemplate.update("""
          INSERT INTO customer_tag_assignments (
            customer_id, category_id, tag_value_id, selection_mode, is_active,
            source_type, evidence_text, evidence_message_count, operator_account,
            is_manual_locked, locked_by, locked_at, supersedes_assignment_id,
            customer_version, created_at, updated_at
          ) VALUES (?, ?, ?, ?, 1, 'MANUAL', ?, 0, ?, ?, ?, ?, ?, ?, ?, ?)
          """,
          plan.customerId(),
          plan.category().id(),
          value.id(),
          plan.category().selectionMode().name(),
          plan.reason(),
          normalizedOperator(plan.operator()),
          plan.lockAfterUpdate() ? 1 : 0,
          plan.lockAfterUpdate() ? normalizedOperator(plan.operator()) : null,
          plan.lockAfterUpdate() ? plan.evaluatedAt() : null,
          supersedes,
          nextVersion,
          plan.evaluatedAt(),
          plan.evaluatedAt());
    }
    if (plan.lockAfterUpdate()) {
      lockCategory(plan);
    }
    insertManualAudit(plan, true, plan.desiredValues().isEmpty() ? "人工移除标签并锁定分类" : "人工修改标签并锁定分类");
    return new CustomerTagUpdateResult(
        nextVersion,
        true,
        List.of(new CustomerTagDecisionResult(
            plan.category().id(),
            plan.category().categoryKey(),
            plan.desiredValues().isEmpty() ? "REMOVE" : "REPLACE",
            true,
            plan.desiredValues().isEmpty() ? "人工标签移除完成" : "人工标签修改完成")));
  }

  @Transactional
  public CustomerTagUpdateResult applyLock(CustomerTagLockUpdatePlan plan) {
    int updated = jdbcTemplate.update("""
        UPDATE customers
        SET version = version + 1, updated_at = NOW()
        WHERE id = ? AND version = ?
        """, plan.customerId(), plan.expectedCustomerVersion());
    if (updated != 1) {
      int currentVersion = currentCustomerVersion(plan.customerId());
      insertLockAudit(plan, false, "客户数据版本已被其他操作更新");
      return new CustomerTagUpdateResult(
          currentVersion,
          false,
          List.of(new CustomerTagDecisionResult(
              plan.category().id(),
              plan.category().categoryKey(),
              plan.locked() ? "LOCK" : "UNLOCK",
              false,
              "客户数据版本已被其他操作更新")));
    }
    Integer count = jdbcTemplate.queryForObject("""
        SELECT COUNT(*) FROM customer_tag_category_locks
        WHERE customer_id = ? AND category_id = ?
        """, Integer.class, plan.customerId(), plan.category().id());
    if (count != null && count > 0) {
      if (plan.locked()) {
        jdbcTemplate.update("""
            UPDATE customer_tag_category_locks
            SET is_locked = 1, locked_by = ?, lock_reason = ?, locked_at = ?,
                unlocked_by = NULL, unlocked_at = NULL, version = version + 1, updated_at = ?
            WHERE customer_id = ? AND category_id = ?
            """,
            normalizedOperator(plan.operator()),
            plan.reason(),
            plan.evaluatedAt(),
            plan.evaluatedAt(),
            plan.customerId(),
            plan.category().id());
      } else {
        jdbcTemplate.update("""
            UPDATE customer_tag_category_locks
            SET is_locked = 0, lock_reason = ?, unlocked_by = ?, unlocked_at = ?,
                version = version + 1, updated_at = ?
            WHERE customer_id = ? AND category_id = ?
            """,
            plan.reason(),
            normalizedOperator(plan.operator()),
            plan.evaluatedAt(),
            plan.evaluatedAt(),
            plan.customerId(),
            plan.category().id());
      }
    } else {
      jdbcTemplate.update("""
          INSERT INTO customer_tag_category_locks (
            customer_id, category_id, is_locked, locked_by, lock_reason,
            locked_at, unlocked_by, unlocked_at, version, created_at, updated_at
          ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
          """,
          plan.customerId(),
          plan.category().id(),
          plan.locked() ? 1 : 0,
          normalizedOperator(plan.operator()),
          plan.reason(),
          plan.evaluatedAt(),
          plan.locked() ? null : normalizedOperator(plan.operator()),
          plan.locked() ? null : plan.evaluatedAt(),
          plan.evaluatedAt(),
          plan.evaluatedAt());
    }
    jdbcTemplate.update("""
        UPDATE customer_tag_assignments
        SET is_manual_locked = ?, locked_by = ?, locked_at = ?, updated_at = ?
        WHERE customer_id = ? AND category_id = ? AND is_active = 1
        """,
        plan.locked() ? 1 : 0,
        plan.locked() ? normalizedOperator(plan.operator()) : null,
        plan.locked() ? plan.evaluatedAt() : null,
        plan.evaluatedAt(),
        plan.customerId(),
        plan.category().id());
    insertLockAudit(plan, true, plan.locked() ? "分类已锁定" : "分类已解除锁定");
    return new CustomerTagUpdateResult(
        plan.expectedCustomerVersion() + 1,
        true,
        List.of(new CustomerTagDecisionResult(
            plan.category().id(),
            plan.category().categoryKey(),
            plan.locked() ? "LOCK" : "UNLOCK",
            true,
            plan.locked() ? "分类已锁定" : "分类已解除锁定")));
  }

  private void insertLockAudit(
      CustomerTagLockUpdatePlan plan,
      boolean success,
      String detail) {
    jdbcTemplate.update("""
        INSERT INTO audit_logs (action, operator, target_type, target_id, detail)
        VALUES (?, ?, 'CUSTOMER', ?, ?)
        """,
        plan.locked() ? "MANUAL_LOCK_CUSTOMER_TAGS" : "MANUAL_UNLOCK_CUSTOMER_TAGS",
        normalizedOperator(plan.operator()),
        String.valueOf(plan.customerId()),
        (success ? "成功：" : "拒绝：") + detail + "；分类=" + plan.category().categoryKey());
  }

  private int updateManualCustomer(ManualCustomerTagUpdatePlan plan) {
    String boundField = plan.category().boundField();
    if (boundField == null || !fieldRegistry.supports(boundField)) {
      return jdbcTemplate.update("""
          UPDATE customers
          SET version = version + 1, updated_at = NOW()
          WHERE id = ? AND version = ?
          """, plan.customerId(), plan.expectedCustomerVersion());
    }
    String rawValue = plan.desiredValues().isEmpty()
        ? null
        : plan.desiredValues().stream()
            .map(TagValue::tagValue)
            .reduce((left, right) -> left + "," + right)
            .orElse(null);
    ProfileFieldRegistry.FieldSpec spec = fieldRegistry.spec(boundField);
    return jdbcTemplate.update(
        "UPDATE customers SET " + spec.columnName()
            + " = ?, version = version + 1, updated_at = NOW() WHERE id = ? AND version = ?",
        fieldRegistry.normalizeValue(boundField, rawValue),
        plan.customerId(),
        plan.expectedCustomerVersion());
  }

  private void lockCategory(ManualCustomerTagUpdatePlan plan) {
    Integer count = jdbcTemplate.queryForObject("""
        SELECT COUNT(*) FROM customer_tag_category_locks
        WHERE customer_id = ? AND category_id = ?
        """, Integer.class, plan.customerId(), plan.category().id());
    if (count != null && count > 0) {
      jdbcTemplate.update("""
          UPDATE customer_tag_category_locks
          SET is_locked = 1, locked_by = ?, lock_reason = ?, locked_at = ?,
              unlocked_by = NULL, unlocked_at = NULL, version = version + 1, updated_at = ?
          WHERE customer_id = ? AND category_id = ?
          """,
          normalizedOperator(plan.operator()),
          plan.reason(),
          plan.evaluatedAt(),
          plan.evaluatedAt(),
          plan.customerId(),
          plan.category().id());
      return;
    }
    jdbcTemplate.update("""
        INSERT INTO customer_tag_category_locks (
          customer_id, category_id, is_locked, locked_by, lock_reason,
          locked_at, version, created_at, updated_at
        ) VALUES (?, ?, 1, ?, ?, ?, 0, ?, ?)
        """,
        plan.customerId(),
        plan.category().id(),
        normalizedOperator(plan.operator()),
        plan.reason(),
        plan.evaluatedAt(),
        plan.evaluatedAt(),
        plan.evaluatedAt());
  }

  private void insertManualAudit(
      ManualCustomerTagUpdatePlan plan,
      boolean success,
      String detail) {
    jdbcTemplate.update("""
        INSERT INTO audit_logs (action, operator, target_type, target_id, detail)
        VALUES ('MANUAL_UPDATE_CUSTOMER_TAGS', ?, 'CUSTOMER', ?, ?)
        """,
        normalizedOperator(plan.operator()),
        String.valueOf(plan.customerId()),
        (success ? "成功：" : "拒绝：") + detail + "；分类=" + plan.category().categoryKey());
  }

  private AutomaticMutation mutation(AutomaticCustomerTagDecisionPlan decision) {
    Set<Long> currentIds = new LinkedHashSet<>();
    decision.previousAssignments().forEach(assignment -> currentIds.add(assignment.tagValueId()));
    List<TagValue> valuesToInsert;
    List<TagValue> resultingValues;
    if (decision.action() == TagAnalysisAction.ADD) {
      valuesToInsert = decision.values().stream()
          .filter(value -> !currentIds.contains(value.id()))
          .toList();
      LinkedHashMap<Long, TagValue> combined = new LinkedHashMap<>();
      decision.category().values().stream()
          .filter(value -> currentIds.contains(value.id()))
          .forEach(value -> combined.put(value.id(), value));
      decision.values().forEach(value -> combined.put(value.id(), value));
      resultingValues = List.copyOf(combined.values());
    } else {
      Set<Long> desiredIds = new LinkedHashSet<>();
      decision.values().forEach(value -> desiredIds.add(value.id()));
      valuesToInsert = currentIds.equals(desiredIds) ? List.of() : decision.values();
      resultingValues = decision.values();
    }
    return new AutomaticMutation(decision, valuesToInsert, resultingValues);
  }

  private int updateCustomer(
      AutomaticCustomerTagUpdatePlan plan,
      List<AutomaticMutation> mutations) {
    Map<String, Object> legacyFields = new LinkedHashMap<>();
    for (AutomaticMutation mutation : mutations) {
      String boundField = mutation.decision().category().boundField();
      if (boundField == null || !fieldRegistry.supports(boundField)) {
        continue;
      }
      String value = mutation.resultingValues().stream()
          .map(TagValue::tagValue)
          .reduce((left, right) -> left + "," + right)
          .orElse("");
      legacyFields.put(boundField, value);
    }
    StringBuilder sql = new StringBuilder("UPDATE customers SET ");
    List<Object> args = new ArrayList<>();
    for (Map.Entry<String, Object> entry : legacyFields.entrySet()) {
      ProfileFieldRegistry.FieldSpec spec = fieldRegistry.spec(entry.getKey());
      sql.append(spec.columnName()).append(" = ?, ");
      args.add(fieldRegistry.normalizeValue(entry.getKey(), entry.getValue()));
    }
    sql.append("version = version + 1, updated_at = NOW() WHERE id = ? AND version = ?");
    args.add(plan.customerId());
    args.add(plan.expectedCustomerVersion());
    return jdbcTemplate.update(sql.toString(), args.toArray());
  }

  private long insertAnalysisRun(
      AutomaticCustomerTagUpdatePlan plan,
      boolean versionConflict,
      boolean updated) {
    String status = versionConflict ? "REJECTED" : updated ? "COMPLETED" : "NO_CHANGE";
    KeyHolder keys = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement statement = connection.prepareStatement("""
          INSERT INTO tag_analysis_runs (
            analysis_key, customer_id, source_type, status, effective_message_count,
            customer_version, caller, error_message, started_at, finished_at
          ) VALUES (?, ?, 'PROFILE_ANALYSIS', ?, ?, ?, ?, ?, ?, ?)
          """, Statement.RETURN_GENERATED_KEYS);
      statement.setString(1, plan.analysisKey());
      statement.setLong(2, plan.customerId());
      statement.setString(3, status);
      statement.setInt(4, plan.effectiveMessageCount());
      statement.setInt(5, plan.expectedCustomerVersion());
      statement.setString(6, normalizedOperator(plan.operator()));
      statement.setString(7, versionConflict ? "客户数据版本已被其他操作更新" : null);
      statement.setObject(8, plan.evaluatedAt());
      statement.setObject(9, plan.evaluatedAt());
      return statement;
    }, keys);
    return requiredKey(keys);
  }

  private Map<CategoryValueKey, Long> insertAnalysisResults(
      long runId,
      AutomaticCustomerTagUpdatePlan plan,
      boolean versionConflict) {
    Map<CategoryValueKey, Long> resultIds = new LinkedHashMap<>();
    for (AutomaticCustomerTagDecisionPlan decision : plan.decisions()) {
      if (decision.category() == null) {
        continue;
      }
      List<TagValue> values = decision.values().isEmpty() ? java.util.Collections.singletonList(null) : decision.values();
      for (TagValue value : values) {
        boolean accepted = decision.accepted() && !versionConflict;
        String reason = versionConflict && decision.accepted()
            ? "客户数据版本已被其他操作更新"
            : decision.reason();
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
          PreparedStatement statement = connection.prepareStatement("""
              INSERT INTO tag_analysis_results (
                analysis_run_id, category_id, tag_value_id, result_type, requested_action,
                confidence, evidence_text, validation_status, validation_reason
              ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
              """, Statement.RETURN_GENERATED_KEYS);
          statement.setLong(1, runId);
          statement.setLong(2, decision.category().id());
          if (value == null) {
            statement.setObject(3, null);
          } else {
            statement.setLong(3, value.id());
          }
          statement.setString(4, decision.analysisDecision() == null
              ? "UNABLE_TO_DETERMINE"
              : decision.analysisDecision().resultType().name());
          statement.setString(5, decision.action().name());
          statement.setBigDecimal(6, decision.analysisDecision() == null
              ? null
              : decision.analysisDecision().confidence());
          statement.setString(7, decision.analysisDecision() == null
              ? null
              : decision.analysisDecision().evidence());
          statement.setString(8, accepted ? "ACCEPTED" : "REJECTED");
          statement.setString(9, reason);
          return statement;
        }, keys);
        if (value != null) {
          resultIds.put(new CategoryValueKey(decision.category().id(), value.id()), requiredKey(keys));
        }
      }
    }
    return resultIds;
  }

  private void persistMutation(
      AutomaticCustomerTagUpdatePlan plan,
      AutomaticMutation mutation,
      int customerVersion,
      Map<CategoryValueKey, Long> resultIds) {
    AutomaticCustomerTagDecisionPlan decision = mutation.decision();
    Long supersedes = decision.previousAssignments().stream()
        .map(CustomerTagAssignment::id)
        .findFirst()
        .orElse(null);
    if (decision.action() == TagAnalysisAction.REPLACE) {
      jdbcTemplate.update("""
          UPDATE customer_tag_assignments
          SET is_active = 0, invalidated_reason = 'AUTO_REPLACED',
              invalidated_at = ?, updated_at = ?
          WHERE customer_id = ? AND category_id = ? AND is_active = 1
          """,
          plan.evaluatedAt(),
          plan.evaluatedAt(),
          plan.customerId(),
          decision.category().id());
    }
    for (TagValue value : mutation.valuesToInsert()) {
      jdbcTemplate.update("""
          INSERT INTO customer_tag_assignments (
            customer_id, category_id, tag_value_id, selection_mode, is_active,
            source_type, confidence, evidence_text, evidence_message_count,
            analysis_result_id, operator_account, is_manual_locked,
            supersedes_assignment_id, customer_version, created_at, updated_at
          ) VALUES (?, ?, ?, ?, 1, 'SYSTEM_INFERENCE', ?, ?, ?, ?, ?, 0, ?, ?, ?, ?)
          """,
          plan.customerId(),
          decision.category().id(),
          value.id(),
          decision.category().selectionMode().name(),
          decision.analysisDecision().confidence(),
          decision.analysisDecision().evidence(),
          plan.effectiveMessageCount(),
          resultIds.get(new CategoryValueKey(decision.category().id(), value.id())),
          normalizedOperator(plan.operator()),
          supersedes,
          customerVersion,
          plan.evaluatedAt(),
          plan.evaluatedAt());
    }
  }

  private void insertAudit(
      AutomaticCustomerTagUpdatePlan plan,
      boolean versionConflict,
      boolean updated) {
    String detail = versionConflict
        ? "自动标签更新被拒绝：客户数据版本已被其他操作更新"
        : updated
            ? "自动标签更新完成，分析编号：" + plan.analysisKey()
            : "自动标签未修改，分析编号：" + plan.analysisKey();
    jdbcTemplate.update("""
        INSERT INTO audit_logs (action, operator, target_type, target_id, detail)
        VALUES ('AUTO_UPDATE_CUSTOMER_TAGS', ?, 'CUSTOMER', ?, ?)
        """,
        normalizedOperator(plan.operator()),
        String.valueOf(plan.customerId()),
        detail);
  }

  private int currentCustomerVersion(long customerId) {
    Integer version = jdbcTemplate.queryForObject(
        "SELECT version FROM customers WHERE id = ?",
        Integer.class,
        customerId);
    return version == null ? 0 : version;
  }

  private long requiredKey(KeyHolder keys) {
    Map<String, Object> generated = keys.getKeys();
    if (generated == null || generated.isEmpty()) {
      throw new IllegalStateException("保存标签分析记录后未返回编号");
    }
    Object raw = generated.entrySet().stream()
        .filter(entry -> "id".equalsIgnoreCase(entry.getKey()))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElseGet(() -> generated.values().iterator().next());
    if (!(raw instanceof Number key)) {
      throw new IllegalStateException("保存标签分析记录后返回了非法编号：" + raw);
    }
    return key.longValue();
  }

  private String normalizedOperator(String operator) {
    return operator == null || operator.isBlank() ? "SYSTEM" : operator.trim();
  }

  private record CategoryValueKey(long categoryId, long valueId) {
  }

  private record AutomaticMutation(
      AutomaticCustomerTagDecisionPlan decision,
      List<TagValue> valuesToInsert,
      List<TagValue> resultingValues
  ) {
    private boolean changesAssignments() {
      return !valuesToInsert.isEmpty();
    }
  }
}
