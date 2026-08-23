package com.privateflow.modules.customer.infra;

import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.config.CustomerCacheProperties;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class CustomerCacheManager {

  private static final Logger log = LoggerFactory.getLogger(CustomerCacheManager.class);
  private static final String INDEX_KEY = "customer:index";
  private final StringRedisTemplate redisTemplate;
  private final CustomerRepository customerRepository;
  private final CustomerCacheProperties properties;

  public CustomerCacheManager(
      StringRedisTemplate redisTemplate,
      CustomerRepository customerRepository,
      CustomerCacheProperties properties) {
    this.redisTemplate = redisTemplate;
    this.customerRepository = customerRepository;
    this.properties = properties;
  }

  @PostConstruct
  public void warmUp() {
    try {
      Long existing = redisTemplate.opsForSet().size(INDEX_KEY);
      if (existing != null && existing > 0) {
        log.info("customer cache warmup skipped, existing index size={}", existing);
        return;
      }
      final long[] lastId = {0L};
      int loaded;
      do {
        loaded = customerRepository.warmupBatch(lastId[0], properties.getLoadBatchSize(), customer -> {
          write(customer);
          lastId[0] = customer.getId();
        });
      } while (loaded == properties.getLoadBatchSize());
      log.info("customer cache warmup completed");
    } catch (RuntimeException ex) {
      log.warn("customer cache warmup skipped because redis/mysql is unavailable: {}", ex.getMessage());
    }
  }

  public Optional<Customer> read(String phone) {
    try {
      Map<Object, Object> raw = redisTemplate.opsForHash().entries(key(phone));
      if (raw.isEmpty()) {
        return Optional.empty();
      }
      redisTemplate.expire(key(phone), Duration.ofSeconds(properties.getTtlSeconds()));
      return Optional.of(fromHash(raw));
    } catch (RuntimeException ex) {
      log.warn("redis read failed, fallback mysql will be used, phone={}", phone);
      return Optional.empty();
    }
  }

  public void write(Customer customer) {
    if (customer == null || customer.getPhone() == null || customer.getPhone().isBlank()) {
      return;
    }
    try {
      redisTemplate.opsForHash().putAll(key(customer.getPhone()), toHash(customer));
      redisTemplate.expire(key(customer.getPhone()), Duration.ofSeconds(properties.getTtlSeconds()));
      redisTemplate.opsForSet().add(INDEX_KEY, customer.getPhone());
    } catch (RuntimeException ex) {
      log.warn("redis write failed, mysql remains source of truth, phone={}", customer.getPhone());
    }
  }

  public void evict(String phone) {
    try {
      redisTemplate.delete(key(phone));
      redisTemplate.opsForSet().remove(INDEX_KEY, phone);
    } catch (RuntimeException ex) {
      log.warn("redis evict failed, phone={}", phone);
    }
  }

  public Optional<String> tryLock(String phone) {
    String token = UUID.randomUUID().toString();
    try {
      Boolean ok = redisTemplate.opsForValue().setIfAbsent(lockKey(phone), token, Duration.ofSeconds(properties.getLockTtlS()));
      return Boolean.TRUE.equals(ok) ? Optional.of(token) : Optional.empty();
    } catch (DataAccessException ex) {
      return Optional.empty();
    }
  }

  public void unlock(String phone, String token) {
    try {
      String current = redisTemplate.opsForValue().get(lockKey(phone));
      if (token.equals(current)) {
        redisTemplate.delete(lockKey(phone));
      }
    } catch (DataAccessException ex) {
      log.warn("redis unlock failed, phone={}", phone);
    }
  }

  private static String key(String phone) {
    return "customer:" + phone;
  }

  private static String lockKey(String phone) {
    return "customer:lock:" + phone;
  }

  private Map<String, String> toHash(Customer c) {
    Map<String, String> map = new HashMap<>();
    put(map, "id", c.getId());
    put(map, "phone", c.getPhone());
    put(map, "nickname", c.getNickname());
    put(map, "customerName", c.getCustomerName());
    put(map, "wechatId", c.getWechatId());
    put(map, "sourceChannel", c.getSourceChannel());
    put(map, "leadType", c.getLeadType());
    put(map, "leadCaptureType", c.getLeadCaptureType());
    put(map, "leadCaptureMethod", c.getLeadCaptureMethod());
    put(map, "platformLeadAt", c.getPlatformLeadAt());
    put(map, "advertisingType", c.getAdvertisingType());
    put(map, "globalAdvertisementId", c.getGlobalAdvertisementId());
    put(map, "standardAdvertisementId", c.getStandardAdvertisementId());
    put(map, "contentId", c.getContentId());
    put(map, "videoId", c.getVideoId());
    put(map, "orderNumber", c.getOrderNumber());
    put(map, "conversionTrace", c.getConversionTrace());
    put(map, "personalityType", c.getPersonalityType());
    put(map, "assignedKeeper", c.getAssignedKeeper());
    put(map, "assignedAt", c.getAssignedAt());
    put(map, "leadInitialProcessedAt", c.getLeadInitialProcessedAt());
    put(map, "leadInitialProcessedBy", c.getLeadInitialProcessedBy());
    put(map, "leadRetainedUntil", c.getLeadRetainedUntil());
    put(map, "leadInvalid", c.isLeadInvalid());
    put(map, "previousAssignedKeeper", c.getPreviousAssignedKeeper());
    put(map, "previousPlatformLeadAt", c.getPreviousPlatformLeadAt());
    put(map, "assignmentMonth", c.getAssignmentMonth());
    put(map, "intendedStore", c.getIntendedStore());
    put(map, "intendedProject", c.getIntendedProject());
    put(map, "purchasedProject", c.getPurchasedProject());
    put(map, "experienceCardType", c.getExperienceCardType());
    put(map, "pendingOrderStatus", c.getPendingOrderStatus());
    put(map, "purchaseDate", c.getPurchaseDate());
    put(map, "customerLevel", c.getCustomerLevel());
    put(map, "postpartumMonths", c.getPostpartumMonths());
    put(map, "parity", c.getParity());
    put(map, "deliveryMethod", c.getDeliveryMethod());
    put(map, "breastfeeding", c.getBreastfeeding());
    put(map, "lochiaPeriod", c.getLochiaPeriod());
    put(map, "pregnancyWeight", c.getPregnancyWeight());
    put(map, "currentWeight", c.getCurrentWeight());
    put(map, "bodyConcerns", c.getBodyConcerns());
    put(map, "diastasisRecti", c.getDiastasisRecti());
    put(map, "urineLeakage", c.getUrineLeakage());
    put(map, "pubicLumbago", c.getPubicLumbago());
    put(map, "prevRepairExp", c.getPrevRepairExp());
    put(map, "postpartumCheck", c.getPostpartumCheck());
    put(map, "exerciseHabits", c.getExerciseHabits());
    put(map, "intentLevel", c.getIntentLevel());
    put(map, "worries", c.getWorries());
    put(map, "customerStage", c.getCustomerStage());
    put(map, "internalNote", c.getInternalNote());
    put(map, "customerProfileSummary", c.getCustomerProfileSummary());
    put(map, "firstTrackingCapture", c.getFirstTrackingCapture());
    put(map, "secondTrackingCapture", c.getSecondTrackingCapture());
    put(map, "thirdTrackingCapture", c.getThirdTrackingCapture());
    put(map, "lastFollowupAt", c.getLastFollowupAt());
    put(map, "followupNotes", c.getFollowupNotes());
    put(map, "nextFollowupAt", c.getNextFollowupAt());
    put(map, "nextFollowupDir", c.getNextFollowupDir());
    put(map, "appointmentDate", c.getAppointmentDate());
    put(map, "appointmentStore", c.getAppointmentStore());
    put(map, "appointmentItem", c.getAppointmentItem());
    put(map, "arrived", c.getArrived());
    put(map, "appointmentStatus", c.getAppointmentStatus());
    put(map, "appointmentTime", c.getAppointmentTime());
    put(map, "arrivalSourceRowId", c.getArrivalSourceRowId());
    put(map, "arrivalHandoverRecord", c.getArrivalHandoverRecord());
    put(map, "arrivalProjectType", c.getArrivalProjectType());
    put(map, "arrivalExperienceProject", c.getArrivalExperienceProject());
    put(map, "historicalExperienceCount", c.getHistoricalExperienceCount());
    put(map, "customerReport", c.getCustomerReport());
    put(map, "receptionTeacher", c.getReceptionTeacher());
    put(map, "receptionConsultant", c.getReceptionConsultant());
    put(map, "voucherRedeemed", c.getVoucherRedeemed());
    put(map, "transactionAmount", c.getTransactionAmount());
    put(map, "transactionAt", c.getTransactionAt());
    put(map, "transactionPrimaryReason", c.getTransactionPrimaryReason());
    put(map, "sourceTable", c.getSourceTable());
    put(map, "sourceRowId", c.getSourceRowId());
    put(map, "syncedAt", c.getSyncedAt());
    put(map, "version", c.getVersion());
    return map;
  }

  private Customer fromHash(Map<Object, Object> raw) {
    Customer c = new Customer();
    c.setId(parseLong(raw.get("id")));
    c.setPhone(str(raw.get("phone")));
    c.setNickname(str(raw.get("nickname")));
    c.setCustomerName(str(raw.get("customerName")));
    c.setWechatId(str(raw.get("wechatId")));
    c.setSourceChannel(str(raw.get("sourceChannel")));
    c.setLeadType(str(raw.get("leadType")));
    c.setLeadCaptureType(str(raw.get("leadCaptureType")));
    c.setLeadCaptureMethod(str(raw.get("leadCaptureMethod")));
    c.setPlatformLeadAt(parseDateTime(raw.get("platformLeadAt")));
    c.setAdvertisingType(str(raw.get("advertisingType")));
    c.setGlobalAdvertisementId(str(raw.get("globalAdvertisementId")));
    c.setStandardAdvertisementId(str(raw.get("standardAdvertisementId")));
    c.setContentId(str(raw.get("contentId")));
    c.setVideoId(str(raw.get("videoId")));
    c.setOrderNumber(str(raw.get("orderNumber")));
    c.setConversionTrace(str(raw.get("conversionTrace")));
    c.setPersonalityType(str(raw.get("personalityType")));
    c.setAssignedKeeper(str(raw.get("assignedKeeper")));
    c.setAssignedAt(parseDateTime(raw.get("assignedAt")));
    c.setLeadInitialProcessedAt(parseDateTime(raw.get("leadInitialProcessedAt")));
    c.setLeadInitialProcessedBy(str(raw.get("leadInitialProcessedBy")));
    c.setLeadRetainedUntil(parseDateTime(raw.get("leadRetainedUntil")));
    c.setLeadInvalid(Boolean.parseBoolean(String.valueOf(raw.getOrDefault("leadInvalid", false))));
    c.setPreviousAssignedKeeper(str(raw.get("previousAssignedKeeper")));
    c.setPreviousPlatformLeadAt(parseDateTime(raw.get("previousPlatformLeadAt")));
    c.setAssignmentMonth(str(raw.get("assignmentMonth")));
    c.setIntendedStore(str(raw.get("intendedStore")));
    c.setIntendedProject(str(raw.get("intendedProject")));
    c.setPurchasedProject(str(raw.get("purchasedProject")));
    c.setExperienceCardType(str(raw.get("experienceCardType")));
    c.setPendingOrderStatus(str(raw.get("pendingOrderStatus")));
    c.setPurchaseDate(parseDate(raw.get("purchaseDate")));
    c.setCustomerLevel(str(raw.get("customerLevel")));
    c.setPostpartumMonths(parseDecimal(raw.get("postpartumMonths")));
    c.setParity(str(raw.get("parity")));
    c.setDeliveryMethod(str(raw.get("deliveryMethod")));
    c.setBreastfeeding(str(raw.get("breastfeeding")));
    c.setLochiaPeriod(str(raw.get("lochiaPeriod")));
    c.setPregnancyWeight(parseDecimal(raw.get("pregnancyWeight")));
    c.setCurrentWeight(parseDecimal(raw.get("currentWeight")));
    c.setBodyConcerns(str(raw.get("bodyConcerns")));
    c.setDiastasisRecti(str(raw.get("diastasisRecti")));
    c.setUrineLeakage(str(raw.get("urineLeakage")));
    c.setPubicLumbago(str(raw.get("pubicLumbago")));
    c.setPrevRepairExp(str(raw.get("prevRepairExp")));
    c.setPostpartumCheck(str(raw.get("postpartumCheck")));
    c.setExerciseHabits(str(raw.get("exerciseHabits")));
    c.setIntentLevel(str(raw.get("intentLevel")));
    c.setWorries(str(raw.get("worries")));
    c.setCustomerStage(str(raw.get("customerStage")));
    c.setInternalNote(str(raw.get("internalNote")));
    c.setCustomerProfileSummary(str(raw.get("customerProfileSummary")));
    c.setFirstTrackingCapture(str(raw.get("firstTrackingCapture")));
    c.setSecondTrackingCapture(str(raw.get("secondTrackingCapture")));
    c.setThirdTrackingCapture(str(raw.get("thirdTrackingCapture")));
    c.setLastFollowupAt(parseDateTime(raw.get("lastFollowupAt")));
    c.setFollowupNotes(str(raw.get("followupNotes")));
    c.setNextFollowupAt(parseDateTime(raw.get("nextFollowupAt")));
    c.setNextFollowupDir(str(raw.get("nextFollowupDir")));
    c.setAppointmentDate(parseDate(raw.get("appointmentDate")));
    c.setAppointmentStore(str(raw.get("appointmentStore")));
    c.setAppointmentItem(str(raw.get("appointmentItem")));
    c.setArrived(str(raw.get("arrived")));
    c.setAppointmentStatus(str(raw.get("appointmentStatus")));
    c.setAppointmentTime(str(raw.get("appointmentTime")));
    c.setArrivalSourceRowId(str(raw.get("arrivalSourceRowId")));
    c.setArrivalHandoverRecord(str(raw.get("arrivalHandoverRecord")));
    c.setArrivalProjectType(str(raw.get("arrivalProjectType")));
    c.setArrivalExperienceProject(str(raw.get("arrivalExperienceProject")));
    c.setHistoricalExperienceCount(str(raw.get("historicalExperienceCount")));
    c.setCustomerReport(str(raw.get("customerReport")));
    c.setReceptionTeacher(str(raw.get("receptionTeacher")));
    c.setReceptionConsultant(str(raw.get("receptionConsultant")));
    c.setVoucherRedeemed(str(raw.get("voucherRedeemed")));
    c.setTransactionAmount(parseDecimal(raw.get("transactionAmount")));
    c.setTransactionAt(parseDateTime(raw.get("transactionAt")));
    c.setTransactionPrimaryReason(str(raw.get("transactionPrimaryReason")));
    c.setSourceTable(str(raw.get("sourceTable")));
    c.setSourceRowId(str(raw.get("sourceRowId")));
    c.setSyncedAt(parseDateTime(raw.get("syncedAt")));
    c.setVersion(parseInt(raw.get("version")));
    return c;
  }

  private static void put(Map<String, String> map, String key, Object value) {
    if (value != null) {
      map.put(key, value.toString());
    }
  }

  private static String str(Object value) {
    return value == null ? null : value.toString();
  }

  private static Long parseLong(Object value) {
    return value == null ? null : Long.parseLong(value.toString());
  }

  private static Integer parseInt(Object value) {
    return value == null ? null : Integer.parseInt(value.toString());
  }

  private static BigDecimal parseDecimal(Object value) {
    return value == null ? null : new BigDecimal(value.toString());
  }

  private static LocalDateTime parseDateTime(Object value) {
    return value == null ? null : LocalDateTime.parse(value.toString());
  }

  private static LocalDate parseDate(Object value) {
    return value == null ? null : LocalDate.parse(value.toString());
  }
}
