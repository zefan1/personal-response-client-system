package com.privateflow.modules.customer.infra;

import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.LeadTypes;
import com.privateflow.modules.customer.ScanFilter;
import com.privateflow.modules.tags.LegacyCustomerTagSynchronizer;
import com.privateflow.modules.tags.TagExchangeResult;
import com.privateflow.modules.tags.TagExchangeSourceType;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class CustomerRepository {

  private static final CustomerRowMapper ROW_MAPPER = new CustomerRowMapper();
  private final JdbcTemplate jdbcTemplate;
  private final LegacyCustomerTagSynchronizer tagSynchronizer;

  public CustomerRepository(JdbcTemplate jdbcTemplate, LegacyCustomerTagSynchronizer tagSynchronizer) {
    this.jdbcTemplate = jdbcTemplate;
    this.tagSynchronizer = tagSynchronizer;
  }

  public Optional<Customer> findByPhone(String phone) {
    List<Customer> customers = jdbcTemplate.query("SELECT * FROM customers WHERE phone = ? LIMIT 1", ROW_MAPPER, phone);
    return customers.stream().findFirst();
  }

  public Optional<Customer> findById(long id) {
    List<Customer> customers = jdbcTemplate.query(
        "SELECT * FROM customers WHERE id = ? LIMIT 1",
        ROW_MAPPER,
        id);
    return customers.stream().findFirst();
  }

  public List<Customer> searchByNickname(String nickname, int limit) {
    return jdbcTemplate.query(
        "SELECT * FROM customers WHERE phone NOT LIKE '%*%' AND nickname LIKE CONCAT('%', ?, '%') ORDER BY last_followup_at DESC LIMIT ?",
        ROW_MAPPER,
        nickname,
        limit);
  }

  public List<Customer> searchByKeyword(String keyword, int limit) {
    String trimmed = keyword == null ? "" : keyword.trim();
    String digits = trimmed.replaceAll("[^\\d]", "");
    List<Object> args = new ArrayList<>();
    StringBuilder sql = new StringBuilder("""
        SELECT * FROM customers
        WHERE phone NOT LIKE '%*%'
          AND (
             nickname LIKE CONCAT('%', ?, '%')
           OR source_channel LIKE CONCAT('%', ?, '%')
           OR intended_store LIKE CONCAT('%', ?, '%')
           OR intended_project LIKE CONCAT('%', ?, '%')
           OR followup_notes LIKE CONCAT('%', ?, '%')
        """);
    args.add(trimmed);
    args.add(trimmed);
    args.add(trimmed);
    args.add(trimmed);
    args.add(trimmed);
    if (!digits.isBlank()) {
      sql.append(" OR phone LIKE CONCAT('%', ?) ");
      args.add(digits);
    }
    sql.append(") ORDER BY last_followup_at DESC LIMIT ? ");
    args.add(limit);
    return jdbcTemplate.query(sql.toString(), ROW_MAPPER, args.toArray());
  }

  public List<Customer> scanActiveCustomers(ScanFilter filter, int defaultLimit) {
    StringBuilder sql = new StringBuilder("SELECT * FROM customers WHERE 1=1");
    List<Object> args = new ArrayList<>();
    if (Boolean.TRUE.equals(filter.assignedKeeperNotNull())) {
      sql.append(" AND assigned_keeper IS NOT NULL");
    }
    if (filter.lastFollowupBeforeHours() != null) {
      sql.append(" AND (last_followup_at IS NULL OR last_followup_at < ?)");
      args.add(Timestamp.valueOf(LocalDateTime.now().minusHours(filter.lastFollowupBeforeHours())));
    }
    if (Boolean.TRUE.equals(filter.nextFollowupBeforeNow())) {
      sql.append(" AND next_followup_at < ?");
      args.add(Timestamp.valueOf(LocalDateTime.now()));
    }
    if (filter.noMessageDays() != null) {
      sql.append(" AND (last_followup_at IS NULL OR last_followup_at < ?)");
      args.add(Timestamp.valueOf(LocalDateTime.now().minusDays(filter.noMessageDays())));
    }
    sql.append(" ORDER BY COALESCE(next_followup_at, last_followup_at, created_at) ASC LIMIT ?");
    args.add(filter.limit() == null ? defaultLimit : filter.limit());
    return jdbcTemplate.query(sql.toString(), ROW_MAPPER, args.toArray());
  }

  @Transactional
  public boolean upsert(Customer customer) {
    return upsert(customer, null, null, null);
  }

  @Transactional
  public boolean upsert(
      Customer customer,
      TagExchangeResult exchangeResult,
      TagExchangeSourceType sourceType,
      String sourceRecordId) {
    String leadType = LeadTypes.normalize(customer.getLeadType());
    int updated = jdbcTemplate.update("""
        INSERT INTO customers (
          phone, nickname, source_channel, lead_type, personality_type, assigned_keeper,
          intended_store, intended_project, purchased_project, postpartum_months, parity,
          delivery_method, breastfeeding, lochia_period, pregnancy_weight, current_weight,
          body_concerns, diastasis_recti, urine_leakage, pubic_lumbago, prev_repair_exp,
          postpartum_check, exercise_habits, intent_level, worries, customer_stage,
          last_followup_at, followup_notes, next_followup_at, next_followup_dir,
          appointment_date, appointment_store, appointment_item, arrived, source_table,
          source_row_id, synced_at
        ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        ON DUPLICATE KEY UPDATE
          nickname=VALUES(nickname), source_channel=VALUES(source_channel), lead_type=VALUES(lead_type),
          personality_type=VALUES(personality_type), assigned_keeper=VALUES(assigned_keeper),
          intended_store=VALUES(intended_store), intended_project=VALUES(intended_project),
          purchased_project=VALUES(purchased_project), postpartum_months=VALUES(postpartum_months),
          parity=VALUES(parity), delivery_method=VALUES(delivery_method), breastfeeding=VALUES(breastfeeding),
          lochia_period=VALUES(lochia_period), pregnancy_weight=VALUES(pregnancy_weight),
          current_weight=VALUES(current_weight), body_concerns=VALUES(body_concerns),
          diastasis_recti=VALUES(diastasis_recti), urine_leakage=VALUES(urine_leakage),
          pubic_lumbago=VALUES(pubic_lumbago), prev_repair_exp=VALUES(prev_repair_exp),
          postpartum_check=VALUES(postpartum_check), exercise_habits=VALUES(exercise_habits),
          intent_level=VALUES(intent_level), worries=VALUES(worries), customer_stage=VALUES(customer_stage),
          last_followup_at=VALUES(last_followup_at), followup_notes=VALUES(followup_notes),
          next_followup_at=VALUES(next_followup_at), next_followup_dir=VALUES(next_followup_dir),
          appointment_date=VALUES(appointment_date), appointment_store=VALUES(appointment_store),
          appointment_item=VALUES(appointment_item), arrived=VALUES(arrived), source_table=VALUES(source_table),
          source_row_id=VALUES(source_row_id), synced_at=VALUES(synced_at), version=version+1
        """,
        customer.getPhone(),
        customer.getNickname(),
        customer.getSourceChannel(),
        leadType,
        customer.getPersonalityType(),
        customer.getAssignedKeeper(),
        customer.getIntendedStore(),
        customer.getIntendedProject(),
        customer.getPurchasedProject(),
        customer.getPostpartumMonths(),
        customer.getParity(),
        customer.getDeliveryMethod(),
        customer.getBreastfeeding(),
        customer.getLochiaPeriod(),
        customer.getPregnancyWeight(),
        customer.getCurrentWeight(),
        customer.getBodyConcerns(),
        customer.getDiastasisRecti(),
        customer.getUrineLeakage(),
        customer.getPubicLumbago(),
        customer.getPrevRepairExp(),
        customer.getPostpartumCheck(),
        customer.getExerciseHabits(),
        customer.getIntentLevel(),
        customer.getWorries(),
        customer.getCustomerStage(),
        customer.getLastFollowupAt() == null ? null : Timestamp.valueOf(customer.getLastFollowupAt()),
        customer.getFollowupNotes(),
        customer.getNextFollowupAt() == null ? null : Timestamp.valueOf(customer.getNextFollowupAt()),
        customer.getNextFollowupDir(),
        customer.getAppointmentDate() == null ? null : Date.valueOf(customer.getAppointmentDate()),
        customer.getAppointmentStore(),
        customer.getAppointmentItem(),
        customer.getArrived(),
        customer.getSourceTable(),
        customer.getSourceRowId(),
        customer.getSyncedAt() == null ? null : Timestamp.valueOf(customer.getSyncedAt()));
    if (updated > 0) {
      if (exchangeResult == null) {
        Map<String, Object> legacyFields = new LinkedHashMap<>();
        legacyFields.put("personalityType", customer.getPersonalityType());
        legacyFields.put("bodyConcerns", customer.getBodyConcerns());
        legacyFields.put("worries", customer.getWorries());
        legacyFields.put("intentLevel", customer.getIntentLevel());
        tagSynchronizer.synchronize(customer.getPhone(), legacyFields);
      } else {
        tagSynchronizer.synchronize(
            customer.getPhone(),
            exchangeResult.acceptedFields(),
            sourceType,
            sourceRecordId);
      }
    }
    return updated > 0;
  }

  public int warmupBatch(long lastId, int limit, CustomerBatchConsumer consumer) {
    List<Customer> batch = jdbcTemplate.query(
        "SELECT * FROM customers WHERE id > ? ORDER BY id ASC LIMIT ?",
        ROW_MAPPER,
        lastId,
        limit);
    batch.forEach(consumer::accept);
    return batch.size();
  }

  @FunctionalInterface
  public interface CustomerBatchConsumer {
    void accept(Customer customer);
  }
}
