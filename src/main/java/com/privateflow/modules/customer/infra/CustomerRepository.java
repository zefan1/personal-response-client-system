package com.privateflow.modules.customer.infra;

import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.LeadTypes;
import com.privateflow.modules.customer.ScanFilter;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerRepository {

  private static final CustomerRowMapper ROW_MAPPER = new CustomerRowMapper();
  private final JdbcTemplate jdbcTemplate;

  public CustomerRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Optional<Customer> findByPhone(String phone) {
    List<Customer> customers = jdbcTemplate.query("SELECT * FROM customers WHERE phone = ? LIMIT 1", ROW_MAPPER, phone);
    return customers.stream().findFirst();
  }

  public List<Customer> searchByNickname(String nickname, int limit) {
    return jdbcTemplate.query(
        "SELECT * FROM customers WHERE nickname LIKE CONCAT('%', ?, '%') ORDER BY last_followup_at DESC LIMIT ?",
        ROW_MAPPER,
        nickname,
        limit);
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

  public boolean upsert(Customer customer) {
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
