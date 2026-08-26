package com.privateflow.modules.arrival;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ArrivalHandoverTaskRepository {
  private final JdbcTemplate jdbc;
  public ArrivalHandoverTaskRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public Optional<ArrivalHandoverTask> findMatching(String phone, LocalDate date, String time, String store, String item) {
    return jdbc.query("""
        SELECT * FROM arrival_handover_tasks WHERE phone=? AND appointment_date=?
        AND COALESCE(appointment_time,'')=? AND appointment_store=? AND COALESCE(appointment_item,'')=?
        ORDER BY id DESC LIMIT 1
        """, (rs, row) -> map(rs), phone, Date.valueOf(date), value(time), value(store), value(item)).stream().findFirst();
  }

  public Optional<ArrivalHandoverTask> findManualMatching(String phone, LocalDate date, String time, String store, String item) {
    return jdbc.query("""
        SELECT * FROM arrival_handover_tasks WHERE phone=?
        AND COALESCE(appointment_date, '1000-01-01') = COALESCE(?, '1000-01-01')
        AND COALESCE(appointment_time,'')=? AND COALESCE(appointment_store,'')=? AND COALESCE(appointment_item,'')=?
        ORDER BY id DESC LIMIT 1
        """, (rs, row) -> map(rs), phone, date == null ? null : Date.valueOf(date), value(time), value(store), value(item)).stream().findFirst();
  }
  public long create(ArrivalHandoverTask task) {
    jdbc.update("""
        INSERT INTO arrival_handover_tasks (customer_id,phone,assigned_keeper,appointment_date,appointment_time,appointment_store,appointment_item,next_sync_at)
        VALUES (?,?,?,?,?,?,?,NOW())
        """, task.getCustomerId(), task.getPhone(), task.getAssignedKeeper(), Date.valueOf(task.getAppointmentDate()), task.getAppointmentTime(), task.getAppointmentStore(), task.getAppointmentItem());
    Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    return id == null ? 0L : id;
  }
  public void refreshOpenTask(long id, String keeper) {
    jdbc.update("UPDATE arrival_handover_tasks SET assigned_keeper=?, updated_at=NOW() WHERE id=? AND task_status='PENDING'", keeper, id);
  }
  public Optional<ArrivalHandoverTask> find(long id) { return jdbc.query("SELECT * FROM arrival_handover_tasks WHERE id=?", (rs,row)->map(rs), id).stream().findFirst(); }
  public List<ArrivalHandoverTask> listPending() { return jdbc.query("SELECT * FROM arrival_handover_tasks WHERE task_status='PENDING' ORDER BY created_at DESC,id DESC", (rs,row)->map(rs)); }
  public List<ArrivalHandoverTask> dueSync(int limit) { return jdbc.query("""
      SELECT * FROM arrival_handover_tasks WHERE task_status='COMPLETED' AND sync_status IN ('PENDING','FAILED')
      AND (next_sync_at IS NULL OR next_sync_at<=NOW()) ORDER BY next_sync_at ASC,id ASC LIMIT ?
      """, (rs,row)->map(rs), limit); }
  public void complete(long id, ArrivalHandoverCompleteRequest request, String reports, String operator) {
    jdbc.update("""
      UPDATE arrival_handover_tasks SET visit_type=?, voucher_redeemed=?, experience_project=?, project_type=?,
      historical_experience_count=?, customer_report=?, task_status='COMPLETED', sync_status='PENDING',
      completed_by=?, completed_at=NOW(), next_sync_at=NOW(), sync_error=NULL, updated_at=NOW()
      WHERE id=? AND task_status='PENDING'
      """, request.visitType(), request.voucherRedeemed(), request.experienceProject(), request.projectType(),
        request.historicalExperienceCount(), reports, operator, id);
  }
  public long createManual(ArrivalHandoverTask task, String operator) {
    jdbc.update("""
        INSERT INTO arrival_handover_tasks
        (customer_id,phone,assigned_keeper,appointment_date,appointment_time,appointment_store,appointment_item,
         visit_type,voucher_redeemed,experience_project,project_type,historical_experience_count,customer_report,
         task_status,sync_status,completed_by,completed_at,next_sync_at)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,'COMPLETED','PENDING',?,NOW(),NOW())
        """, task.getCustomerId(), task.getPhone(), task.getAssignedKeeper(),
        task.getAppointmentDate() == null ? null : Date.valueOf(task.getAppointmentDate()), task.getAppointmentTime(),
        task.getAppointmentStore(), task.getAppointmentItem(), task.getVisitType(), task.getVoucherRedeemed(),
        task.getExperienceProject(), task.getProjectType(), task.getHistoricalExperienceCount(), task.getCustomerReport(), operator);
    Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    return id == null ? 0L : id;
  }
  public void updateManual(long id, ArrivalHandoverTask task, String operator) {
    jdbc.update("""
        UPDATE arrival_handover_tasks SET assigned_keeper=?, appointment_date=?, appointment_time=?, appointment_store=?, appointment_item=?,
        visit_type=?, voucher_redeemed=?, experience_project=?, project_type=?, historical_experience_count=?, customer_report=?,
        task_status='COMPLETED', sync_status='PENDING', completed_by=?, completed_at=NOW(), next_sync_at=NOW(), sync_error=NULL, updated_at=NOW()
        WHERE id=?
        """, task.getAssignedKeeper(), task.getAppointmentDate() == null ? null : Date.valueOf(task.getAppointmentDate()),
        task.getAppointmentTime(), task.getAppointmentStore(), task.getAppointmentItem(), task.getVisitType(), task.getVoucherRedeemed(),
        task.getExperienceProject(), task.getProjectType(), task.getHistoricalExperienceCount(), task.getCustomerReport(), operator, id);
  }
  public void updateManualReports(long id, String reports) {
    jdbc.update("""
        UPDATE arrival_handover_tasks SET customer_report=?, sync_status='PENDING', next_sync_at=NOW(),
        sync_error=NULL, updated_at=NOW() WHERE id=? AND task_status='COMPLETED'
        """, reports, id);
  }
  public void markReminded(long id) { jdbc.update("UPDATE arrival_handover_tasks SET reminded_at=NOW(), updated_at=NOW() WHERE id=? AND task_status='PENDING'", id); }
  public void markSynced(long id, String rowId) { jdbc.update("UPDATE arrival_handover_tasks SET sync_status='SYNCED',wecom_row_id=?,sync_error=NULL,next_sync_at=NULL,updated_at=NOW() WHERE id=?", rowId,id); }
  public void markSyncFailed(long id, int retryCount, LocalDateTime next, String error) { jdbc.update("UPDATE arrival_handover_tasks SET sync_status='FAILED',sync_retry_count=?,next_sync_at=?,sync_error=?,updated_at=NOW() WHERE id=?", retryCount,Timestamp.valueOf(next),trim(error),id); }
  private ArrivalHandoverTask map(java.sql.ResultSet rs) throws java.sql.SQLException {
    ArrivalHandoverTask t=new ArrivalHandoverTask(); t.setId(rs.getLong("id")); t.setCustomerId(rs.getLong("customer_id")); t.setPhone(rs.getString("phone")); t.setAssignedKeeper(rs.getString("assigned_keeper"));
    Date d=rs.getDate("appointment_date");t.setAppointmentDate(d==null?null:d.toLocalDate());t.setAppointmentTime(rs.getString("appointment_time"));t.setAppointmentStore(rs.getString("appointment_store"));t.setAppointmentItem(rs.getString("appointment_item"));t.setVisitType(rs.getString("visit_type"));t.setVoucherRedeemed(rs.getString("voucher_redeemed"));t.setExperienceProject(rs.getString("experience_project"));t.setProjectType(rs.getString("project_type"));t.setHistoricalExperienceCount(rs.getString("historical_experience_count"));t.setCustomerReport(rs.getString("customer_report"));t.setTaskStatus(rs.getString("task_status"));t.setSyncStatus(rs.getString("sync_status"));t.setWecomRowId(rs.getString("wecom_row_id"));t.setSyncRetryCount(rs.getInt("sync_retry_count"));
    Timestamp v=rs.getTimestamp("next_sync_at");t.setNextSyncAt(v==null?null:v.toLocalDateTime());t.setSyncError(rs.getString("sync_error"));v=rs.getTimestamp("reminded_at");t.setRemindedAt(v==null?null:v.toLocalDateTime());t.setCompletedBy(rs.getString("completed_by"));v=rs.getTimestamp("completed_at");t.setCompletedAt(v==null?null:v.toLocalDateTime());v=rs.getTimestamp("created_at");t.setCreatedAt(v==null?null:v.toLocalDateTime());v=rs.getTimestamp("updated_at");t.setUpdatedAt(v==null?null:v.toLocalDateTime());return t;
  }
  private static String value(String s){return s==null?"":s.trim();} private static String trim(String s){if(s==null)return null;return s.length()>500?s.substring(0,500):s;}
}
