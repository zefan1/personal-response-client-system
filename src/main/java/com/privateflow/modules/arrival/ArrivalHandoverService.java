package com.privateflow.modules.arrival;

import com.privateflow.modules.api.Role;
import com.privateflow.modules.api.auth.AccountRepository;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.auth.AuthUser;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.customer.history.CustomerFieldHistoryContext;
import com.privateflow.modules.customer.service.CustomerAccessService;
import com.privateflow.modules.profile.infra.ProfileFieldRegistry;
import com.privateflow.modules.profile.infra.ProfileWriter;
import com.privateflow.modules.tablewrite.client.AuxiliarySmartSheetWriter;
import com.privateflow.modules.tablewrite.client.WecomSmartSheetField;
import com.privateflow.modules.tablewrite.client.WecomSmartSheetFieldCatalog;
import com.privateflow.modules.tablewrite.config.AuxiliarySmartSheetTarget;
import com.privateflow.modules.tablewrite.config.AuxiliarySmartSheetTargets;
import com.privateflow.modules.tablewrite.config.TableConfigProvider;
import com.privateflow.modules.tablewrite.infra.TableFieldMappingResolver;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ArrivalHandoverService {
  private static final List<String> MANUAL_FIELD_TITLES = List.of("类型", "是否核券", "体验项目", "项目类型", "历史体验次数", "客户报告");
  private final ArrivalHandoverTaskRepository tasks; private final CustomerQueryService customers; private final CustomerAccessService access;
  private final AccountRepository accounts; private final ProfileWriter writer; private final AuxiliarySmartSheetWriter sheetWriter;
  private final AuxiliarySmartSheetTargets targets; private final TableConfigProvider config; private final TableFieldMappingResolver mappings;
  private final WecomSmartSheetFieldCatalog fields; private final ArrivalReportStorage reports; private final ProfileFieldRegistry profileFields;
  public ArrivalHandoverService(ArrivalHandoverTaskRepository tasks, CustomerQueryService customers, CustomerAccessService access, AccountRepository accounts, ProfileWriter writer, AuxiliarySmartSheetWriter sheetWriter, AuxiliarySmartSheetTargets targets, TableConfigProvider config, TableFieldMappingResolver mappings, WecomSmartSheetFieldCatalog fields, ArrivalReportStorage reports, ProfileFieldRegistry profileFields) {
    this.tasks=tasks;this.customers=customers;this.access=access;this.accounts=accounts;this.writer=writer;this.sheetWriter=sheetWriter;this.targets=targets;this.config=config;this.mappings=mappings;this.fields=fields;this.reports=reports;this.profileFields=profileFields;
  }
  @Transactional
  public ArrivalHandoverTaskDecision createOrRefresh(Customer customer, String date, String time, String store, String item) {
    if (customer == null || customer.getId() == null || blank(date) || blank(store)) return new ArrivalHandoverTaskDecision(0, false);
    LocalDate bookingDate = LocalDate.parse(date.trim().substring(0, 10));
    return tasks.findMatching(customer.getPhone(), bookingDate, time, store, item)
        .map(existing -> decisionFor(existing, customer))
        .orElseGet(() -> createTask(customer, bookingDate, time, store, item));
  }
  public List<ArrivalHandoverTaskView> pendingTasks() { return tasks.listPending().stream().map(this::view).filter(v -> v != null).toList(); }
  public Map<String, List<String>> options() {
    AuxiliarySmartSheetTarget target = arrivalTarget(); Map<String, WecomSmartSheetField> catalog = fields.visibleFields(target, timeout()); Map<String,List<String>> result=new LinkedHashMap<>();
    for (String title: MANUAL_FIELD_TITLES) { WecomSmartSheetField field=catalog.get(title); result.put(title, field == null ? List.of() : List.copyOf(field.optionIdsByText().keySet())); }
    return result;
  }
  public ArrivalReportAttachment upload(long taskId, MultipartFile file) { requireCompleter(requireTask(taskId)); return reports.store(taskId,file); }
  @Transactional
  public void complete(long id, ArrivalHandoverCompleteRequest request, List<ArrivalReportAttachment> reportFiles) {
    ArrivalHandoverTask task=requireTask(id); requireCompleter(task); validate(request); String reportJson=reports.encode(reportFiles); tasks.complete(id,request,reportJson,AuthContext.username());
    Customer customer=customers.getById(task.getCustomerId()); if(customer!=null) { Map<String,Object> values=new LinkedHashMap<>(); values.put("arrivalHandoverRecord",request.visitType());values.put("voucherRedeemed",request.voucherRedeemed());values.put("arrivalExperienceProject",request.experienceProject());values.put("arrivalProjectType",request.projectType());values.put("historicalExperienceCount",request.historicalExperienceCount());values.put("customerReport",reportJson);values.put("appointmentStatus","已预约"); writer.write(customer.getPhone(),values,null,true,CustomerFieldHistoryContext.of("到店衔接补充","到店事项",AuthContext.username())); }
  }
  public ArrivalHandoverCompletionResult completeAndSync(long id, ArrivalHandoverCompleteRequest request) {
    complete(id,request,request == null ? List.of() : request.reports());
    ArrivalHandoverTask completed = requireTask(id);
    syncOne(completed);
    ArrivalHandoverTask latest = requireTask(id);
    return new ArrivalHandoverCompletionResult(
        true,
        "SYNCED".equals(latest.getSyncStatus()),
        latest.getWecomRowId(),
        latest.getSyncError());
  }
  public void remind(long id) { ArrivalHandoverTask task=requireTask(id); Customer customer=customers.getById(task.getCustomerId()); if(customer==null || !access.canAccess(customer)) throw denied(); tasks.markReminded(id); }
  public ArrivalHandoverTask requireTask(long id) { return tasks.find(id).orElseThrow(() -> new IllegalArgumentException("到店事项不存在")); }
  @Scheduled(fixedDelay = 60000L)
  public void retryDueSyncs() { for(ArrivalHandoverTask task:tasks.dueSync(100)) syncOne(task); }
  public void syncOne(ArrivalHandoverTask task) {
    try { AuxiliarySmartSheetTarget target=arrivalTarget(); String source="ARRIVAL:"+target.sheetId(); Map<String,Object> outgoing=new LinkedHashMap<>(); Customer c=customers.getById(task.getCustomerId()); if(c!=null){Map<String,Object> facts=new LinkedHashMap<>(profileFields.toProfileMap(c));facts.put("phone",task.getPhone());facts.put("appointmentDate",task.getAppointmentDate());facts.put("appointmentDateTime",appointmentDateTime(task));facts.put("appointmentTime",task.getAppointmentTime());facts.put("appointmentStore",task.getAppointmentStore());facts.put("appointmentItem",task.getAppointmentItem());facts.put("assignedKeeper",task.getAssignedKeeper());outgoing.putAll(mappings.toSourceFields(source,facts));} Map<String,WecomSmartSheetField> catalog=fields.visibleFields(target,timeout()); List<ArrivalReportAttachment> attachments=reports.decode(task.getCustomerReport());if(!attachments.isEmpty()){ WecomSmartSheetField reportField=catalog.get("客户报告"); if(reportField!=null && reportField.writable()) put(outgoing,catalog,"客户报告",attachments.stream().map(ArrivalReportAttachment::fileName).reduce((a,b)->a+"\n"+b).orElse("")); } else { put(outgoing,catalog,"客户报告",task.getCustomerReport()); } String row=task.getWecomRowId(); boolean newRow=blank(row) || !sheetWriter.recordExists(target,row,timeout()); if(newRow){row=sheetWriter.addRecord(target,outgoing,timeout());}else{sheetWriter.updateRecord(target,row,outgoing,timeout());} tasks.markSynced(task.getId(),row); if(c!=null && !row.equals(trim(c.getArrivalSourceRowId())))writer.write(c.getPhone(),Map.of("arrivalSourceRowId",row),null,true,CustomerFieldHistoryContext.of("企业微信到店表","到店表行", "SYSTEM")); }
    catch(Exception ex){int retry=task.getSyncRetryCount()+1;tasks.markSyncFailed(task.getId(),retry,LocalDateTime.now().plusMinutes(Math.min(60,Math.max(1,retry)*5L)),ex.getMessage());}
  }
  private ArrivalHandoverTaskView view(ArrivalHandoverTask task) { Customer customer=customers.getById(task.getCustomerId());if(customer==null||!access.canAccess(customer))return null;return new ArrivalHandoverTaskView(task.getId(),task.getPhone(),customer.getNickname(),task.getAssignedKeeper(),task.getAppointmentDate(),task.getAppointmentTime(),task.getAppointmentStore(),task.getAppointmentItem(),isAssignee(task),true,task.getRemindedAt()); }
  private void requireCompleter(ArrivalHandoverTask task){if(!isAssignee(task))throw denied();}
  private ArrivalHandoverTaskDecision createTask(Customer customer, LocalDate date, String time, String store, String item) {
    ArrivalHandoverTask task = new ArrivalHandoverTask(); task.setCustomerId(customer.getId()); task.setPhone(customer.getPhone()); task.setAssignedKeeper(customer.getAssignedKeeper()); task.setAppointmentDate(date); task.setAppointmentTime(trim(time)); task.setAppointmentStore(trim(store)); task.setAppointmentItem(trim(item));
    try { return new ArrivalHandoverTaskDecision(tasks.create(task), false); }
    catch (DuplicateKeyException ex) { return tasks.findMatching(customer.getPhone(), date, time, store, item).map(existing -> decisionFor(existing, customer)).orElseThrow(() -> ex); }
  }
  private ArrivalHandoverTaskDecision decisionFor(ArrivalHandoverTask task, Customer customer) {
    boolean completed = "COMPLETED".equals(task.getTaskStatus());
    if (!completed) tasks.refreshOpenTask(task.getId(), customer.getAssignedKeeper());
    return new ArrivalHandoverTaskDecision(task.getId(), completed);
  }
  private boolean isAssignee(ArrivalHandoverTask task){AuthUser user=AuthContext.current();if(user==null)return true;String keeper=trim(task.getAssignedKeeper());if(keeper.isBlank())return user.role()==Role.ADMIN;return keeper.equals(trim(user.username()))||keeper.equals(trim(user.displayName()))||accounts.resolveEnabledUsername(keeper).map(name->name.equals(trim(user.username()))).orElse(false);}
  private void validate(ArrivalHandoverCompleteRequest r){if(r==null||blank(r.visitType())||blank(r.voucherRedeemed())||blank(r.experienceProject())||blank(r.projectType())||blank(r.historicalExperienceCount()))throw new IllegalArgumentException("请完整填写到店人工信息");}
  private AuxiliarySmartSheetTarget arrivalTarget(){return targets.arrival().orElseThrow(()->new IllegalStateException("到店表尚未配置"));} private Duration timeout(){return Duration.ofMillis(config.get().writeTimeoutMs());} private void put(Map<String,Object> out,Map<String,WecomSmartSheetField> catalog,String title,String value){if(value!=null&&catalog.containsKey(title))out.put(title,value);} private RuntimeException denied(){return new IllegalArgumentException("只能填写自己负责的到店事项");} private static boolean blank(String v){return v==null||v.isBlank();} private static String trim(String v){return v==null?"":v.trim();}
  private static LocalDateTime appointmentDateTime(ArrivalHandoverTask task) {
    if (task == null || task.getAppointmentDate() == null) return null;
    String raw = trim(task.getAppointmentTime());
    if (raw.isBlank()) return task.getAppointmentDate().atStartOfDay();
    String normalized = raw.replace("时", ":").replace("点", ":").replace("分", "");
    for (DateTimeFormatter formatter : List.of(DateTimeFormatter.ofPattern("H:mm"), DateTimeFormatter.ofPattern("HH:mm"))) {
      try { return LocalDateTime.of(task.getAppointmentDate(), LocalTime.parse(normalized, formatter)); }
      catch (DateTimeParseException ignored) { }
    }
    return task.getAppointmentDate().atStartOfDay();
  }
}
