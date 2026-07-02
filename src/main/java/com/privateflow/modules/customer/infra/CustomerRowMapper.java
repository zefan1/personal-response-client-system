package com.privateflow.modules.customer.infra;

import com.privateflow.modules.customer.Customer;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.RowMapper;

public class CustomerRowMapper implements RowMapper<Customer> {

  @Override
  public Customer mapRow(ResultSet rs, int rowNum) throws SQLException {
    Customer customer = new Customer();
    customer.setId(rs.getLong("id"));
    customer.setPhone(rs.getString("phone"));
    customer.setNickname(rs.getString("nickname"));
    customer.setSourceChannel(rs.getString("source_channel"));
    customer.setLeadType(rs.getString("lead_type"));
    customer.setPersonalityType(rs.getString("personality_type"));
    customer.setAssignedKeeper(rs.getString("assigned_keeper"));
    customer.setIntendedStore(rs.getString("intended_store"));
    customer.setIntendedProject(rs.getString("intended_project"));
    customer.setPurchasedProject(rs.getString("purchased_project"));
    customer.setPostpartumMonths(rs.getBigDecimal("postpartum_months"));
    customer.setParity(rs.getString("parity"));
    customer.setDeliveryMethod(rs.getString("delivery_method"));
    customer.setBreastfeeding(rs.getString("breastfeeding"));
    customer.setLochiaPeriod(rs.getString("lochia_period"));
    customer.setPregnancyWeight(rs.getBigDecimal("pregnancy_weight"));
    customer.setCurrentWeight(rs.getBigDecimal("current_weight"));
    customer.setBodyConcerns(rs.getString("body_concerns"));
    customer.setDiastasisRecti(rs.getString("diastasis_recti"));
    customer.setUrineLeakage(rs.getString("urine_leakage"));
    customer.setPubicLumbago(rs.getString("pubic_lumbago"));
    customer.setPrevRepairExp(rs.getString("prev_repair_exp"));
    customer.setPostpartumCheck(rs.getString("postpartum_check"));
    customer.setExerciseHabits(rs.getString("exercise_habits"));
    customer.setIntentLevel(rs.getString("intent_level"));
    customer.setWorries(rs.getString("worries"));
    customer.setCustomerStage(rs.getString("customer_stage"));
    customer.setLastFollowupAt(rs.getTimestamp("last_followup_at") == null ? null : rs.getTimestamp("last_followup_at").toLocalDateTime());
    customer.setFollowupNotes(rs.getString("followup_notes"));
    customer.setNextFollowupAt(rs.getTimestamp("next_followup_at") == null ? null : rs.getTimestamp("next_followup_at").toLocalDateTime());
    customer.setNextFollowupDir(rs.getString("next_followup_dir"));
    customer.setAppointmentDate(rs.getDate("appointment_date") == null ? null : rs.getDate("appointment_date").toLocalDate());
    customer.setAppointmentStore(rs.getString("appointment_store"));
    customer.setAppointmentItem(rs.getString("appointment_item"));
    customer.setArrived(rs.getString("arrived"));
    customer.setSourceTable(rs.getString("source_table"));
    customer.setSourceRowId(rs.getString("source_row_id"));
    customer.setSyncedAt(rs.getTimestamp("synced_at") == null ? null : rs.getTimestamp("synced_at").toLocalDateTime());
    customer.setVersion(rs.getInt("version"));
    customer.setCreatedAt(rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime());
    customer.setUpdatedAt(rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toLocalDateTime());
    return customer;
  }
}
