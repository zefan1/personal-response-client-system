package com.privateflow.modules.customer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class Customer {

  private Long id;
  private String phone;
  private String nickname;
  private String customerName;
  private String wechatId;
  private String sourceChannel;
  private String leadType;
  private String leadCaptureType;
  private String leadCaptureMethod;
  private LocalDateTime platformLeadAt;
  private String advertisingType;
  private String globalAdvertisementId;
  private String standardAdvertisementId;
  private String contentId;
  private String videoId;
  private String orderNumber;
  private String conversionTrace;
  private String personalityType;
  private String assignedKeeper;
  private LocalDateTime assignedAt;
  private String previousAssignedKeeper;
  private LocalDateTime previousPlatformLeadAt;
  private String assignmentMonth;
  private String intendedStore;
  private String intendedProject;
  private String purchasedProject;
  private String experienceCardType;
  private String pendingOrderStatus;
  private LocalDate purchaseDate;
  private String customerLevel;
  private BigDecimal postpartumMonths;
  private String parity;
  private String deliveryMethod;
  private String breastfeeding;
  private String lochiaPeriod;
  private BigDecimal pregnancyWeight;
  private BigDecimal currentWeight;
  private String bodyConcerns;
  private String diastasisRecti;
  private String urineLeakage;
  private String pubicLumbago;
  private String prevRepairExp;
  private String postpartumCheck;
  private String exerciseHabits;
  private String intentLevel;
  private String worries;
  private String customerStage;
  private String internalNote;
  private String customerProfileSummary;
  private String firstTrackingCapture;
  private String secondTrackingCapture;
  private String thirdTrackingCapture;
  private LocalDateTime lastFollowupAt;
  private String followupNotes;
  private LocalDateTime nextFollowupAt;
  private String nextFollowupDir;
  private LocalDate appointmentDate;
  private String appointmentStore;
  private String appointmentItem;
  private String arrived;
  private String appointmentStatus;
  private String appointmentTime;
  private String arrivalSourceRowId;
  private String arrivalHandoverRecord;
  private String arrivalProjectType;
  private String arrivalExperienceProject;
  private String historicalExperienceCount;
  private String customerReport;
  private String receptionTeacher;
  private String receptionConsultant;
  private String voucherRedeemed;
  private BigDecimal transactionAmount;
  private LocalDateTime transactionAt;
  private String transactionPrimaryReason;
  private String sourceTable;
  private String sourceRowId;
  private LocalDateTime syncedAt;
  private Integer version;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public String getPhone() { return phone; }
  public void setPhone(String phone) { this.phone = phone; }
  public String getNickname() { return nickname; }
  public void setNickname(String nickname) { this.nickname = nickname; }
  public String getCustomerName() { return customerName; }
  public void setCustomerName(String customerName) { this.customerName = customerName; }
  public String getWechatId() { return wechatId; }
  public void setWechatId(String wechatId) { this.wechatId = wechatId; }
  public String getSourceChannel() { return sourceChannel; }
  public void setSourceChannel(String sourceChannel) { this.sourceChannel = sourceChannel; }
  public String getLeadType() { return leadType; }
  public void setLeadType(String leadType) { this.leadType = leadType; }
  public String getLeadCaptureType() { return leadCaptureType; }
  public void setLeadCaptureType(String leadCaptureType) { this.leadCaptureType = leadCaptureType; }
  public String getLeadCaptureMethod() { return leadCaptureMethod; }
  public void setLeadCaptureMethod(String leadCaptureMethod) { this.leadCaptureMethod = leadCaptureMethod; }
  public LocalDateTime getPlatformLeadAt() { return platformLeadAt; }
  public void setPlatformLeadAt(LocalDateTime platformLeadAt) { this.platformLeadAt = platformLeadAt; }
  public String getAdvertisingType() { return advertisingType; }
  public void setAdvertisingType(String advertisingType) { this.advertisingType = advertisingType; }
  public String getGlobalAdvertisementId() { return globalAdvertisementId; }
  public void setGlobalAdvertisementId(String globalAdvertisementId) { this.globalAdvertisementId = globalAdvertisementId; }
  public String getStandardAdvertisementId() { return standardAdvertisementId; }
  public void setStandardAdvertisementId(String standardAdvertisementId) { this.standardAdvertisementId = standardAdvertisementId; }
  public String getContentId() { return contentId; }
  public void setContentId(String contentId) { this.contentId = contentId; }
  public String getVideoId() { return videoId; }
  public void setVideoId(String videoId) { this.videoId = videoId; }
  public String getOrderNumber() { return orderNumber; }
  public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
  public String getConversionTrace() { return conversionTrace; }
  public void setConversionTrace(String conversionTrace) { this.conversionTrace = conversionTrace; }
  public String getPersonalityType() { return personalityType; }
  public void setPersonalityType(String personalityType) { this.personalityType = personalityType; }
  public String getAssignedKeeper() { return assignedKeeper; }
  public void setAssignedKeeper(String assignedKeeper) { this.assignedKeeper = assignedKeeper; }
  public LocalDateTime getAssignedAt() { return assignedAt; }
  public void setAssignedAt(LocalDateTime assignedAt) { this.assignedAt = assignedAt; }
  public String getPreviousAssignedKeeper() { return previousAssignedKeeper; }
  public void setPreviousAssignedKeeper(String previousAssignedKeeper) { this.previousAssignedKeeper = previousAssignedKeeper; }
  public LocalDateTime getPreviousPlatformLeadAt() { return previousPlatformLeadAt; }
  public void setPreviousPlatformLeadAt(LocalDateTime previousPlatformLeadAt) { this.previousPlatformLeadAt = previousPlatformLeadAt; }
  public String getAssignmentMonth() { return assignmentMonth; }
  public void setAssignmentMonth(String assignmentMonth) { this.assignmentMonth = assignmentMonth; }
  public String getIntendedStore() { return intendedStore; }
  public void setIntendedStore(String intendedStore) { this.intendedStore = intendedStore; }
  public String getIntendedProject() { return intendedProject; }
  public void setIntendedProject(String intendedProject) { this.intendedProject = intendedProject; }
  public String getPurchasedProject() { return purchasedProject; }
  public void setPurchasedProject(String purchasedProject) { this.purchasedProject = purchasedProject; }
  public String getExperienceCardType() { return experienceCardType; }
  public void setExperienceCardType(String experienceCardType) { this.experienceCardType = experienceCardType; }
  public String getPendingOrderStatus() { return pendingOrderStatus; }
  public void setPendingOrderStatus(String pendingOrderStatus) { this.pendingOrderStatus = pendingOrderStatus; }
  public LocalDate getPurchaseDate() { return purchaseDate; }
  public void setPurchaseDate(LocalDate purchaseDate) { this.purchaseDate = purchaseDate; }
  public String getCustomerLevel() { return customerLevel; }
  public void setCustomerLevel(String customerLevel) { this.customerLevel = customerLevel; }
  public BigDecimal getPostpartumMonths() { return postpartumMonths; }
  public void setPostpartumMonths(BigDecimal postpartumMonths) { this.postpartumMonths = postpartumMonths; }
  public String getParity() { return parity; }
  public void setParity(String parity) { this.parity = parity; }
  public String getDeliveryMethod() { return deliveryMethod; }
  public void setDeliveryMethod(String deliveryMethod) { this.deliveryMethod = deliveryMethod; }
  public String getBreastfeeding() { return breastfeeding; }
  public void setBreastfeeding(String breastfeeding) { this.breastfeeding = breastfeeding; }
  public String getLochiaPeriod() { return lochiaPeriod; }
  public void setLochiaPeriod(String lochiaPeriod) { this.lochiaPeriod = lochiaPeriod; }
  public BigDecimal getPregnancyWeight() { return pregnancyWeight; }
  public void setPregnancyWeight(BigDecimal pregnancyWeight) { this.pregnancyWeight = pregnancyWeight; }
  public BigDecimal getCurrentWeight() { return currentWeight; }
  public void setCurrentWeight(BigDecimal currentWeight) { this.currentWeight = currentWeight; }
  public String getBodyConcerns() { return bodyConcerns; }
  public void setBodyConcerns(String bodyConcerns) { this.bodyConcerns = bodyConcerns; }
  public String getDiastasisRecti() { return diastasisRecti; }
  public void setDiastasisRecti(String diastasisRecti) { this.diastasisRecti = diastasisRecti; }
  public String getUrineLeakage() { return urineLeakage; }
  public void setUrineLeakage(String urineLeakage) { this.urineLeakage = urineLeakage; }
  public String getPubicLumbago() { return pubicLumbago; }
  public void setPubicLumbago(String pubicLumbago) { this.pubicLumbago = pubicLumbago; }
  public String getPrevRepairExp() { return prevRepairExp; }
  public void setPrevRepairExp(String prevRepairExp) { this.prevRepairExp = prevRepairExp; }
  public String getPostpartumCheck() { return postpartumCheck; }
  public void setPostpartumCheck(String postpartumCheck) { this.postpartumCheck = postpartumCheck; }
  public String getExerciseHabits() { return exerciseHabits; }
  public void setExerciseHabits(String exerciseHabits) { this.exerciseHabits = exerciseHabits; }
  public String getIntentLevel() { return intentLevel; }
  public void setIntentLevel(String intentLevel) { this.intentLevel = intentLevel; }
  public String getWorries() { return worries; }
  public void setWorries(String worries) { this.worries = worries; }
  public String getCustomerStage() { return customerStage; }
  public void setCustomerStage(String customerStage) { this.customerStage = customerStage; }
  public String getInternalNote() { return internalNote; }
  public void setInternalNote(String internalNote) { this.internalNote = internalNote; }
  public String getCustomerProfileSummary() { return customerProfileSummary; }
  public void setCustomerProfileSummary(String customerProfileSummary) { this.customerProfileSummary = customerProfileSummary; }
  public String getFirstTrackingCapture() { return firstTrackingCapture; }
  public void setFirstTrackingCapture(String firstTrackingCapture) { this.firstTrackingCapture = firstTrackingCapture; }
  public String getSecondTrackingCapture() { return secondTrackingCapture; }
  public void setSecondTrackingCapture(String secondTrackingCapture) { this.secondTrackingCapture = secondTrackingCapture; }
  public String getThirdTrackingCapture() { return thirdTrackingCapture; }
  public void setThirdTrackingCapture(String thirdTrackingCapture) { this.thirdTrackingCapture = thirdTrackingCapture; }
  public LocalDateTime getLastFollowupAt() { return lastFollowupAt; }
  public void setLastFollowupAt(LocalDateTime lastFollowupAt) { this.lastFollowupAt = lastFollowupAt; }
  public String getFollowupNotes() { return followupNotes; }
  public void setFollowupNotes(String followupNotes) { this.followupNotes = followupNotes; }
  public LocalDateTime getNextFollowupAt() { return nextFollowupAt; }
  public void setNextFollowupAt(LocalDateTime nextFollowupAt) { this.nextFollowupAt = nextFollowupAt; }
  public String getNextFollowupDir() { return nextFollowupDir; }
  public void setNextFollowupDir(String nextFollowupDir) { this.nextFollowupDir = nextFollowupDir; }
  public LocalDate getAppointmentDate() { return appointmentDate; }
  public void setAppointmentDate(LocalDate appointmentDate) { this.appointmentDate = appointmentDate; }
  public String getAppointmentStore() { return appointmentStore; }
  public void setAppointmentStore(String appointmentStore) { this.appointmentStore = appointmentStore; }
  public String getAppointmentItem() { return appointmentItem; }
  public void setAppointmentItem(String appointmentItem) { this.appointmentItem = appointmentItem; }
  public String getArrived() { return arrived; }
  public void setArrived(String arrived) { this.arrived = arrived; }
  public String getAppointmentStatus() { return appointmentStatus; }
  public void setAppointmentStatus(String appointmentStatus) { this.appointmentStatus = appointmentStatus; }
  public String getAppointmentTime() { return appointmentTime; }
  public void setAppointmentTime(String appointmentTime) { this.appointmentTime = appointmentTime; }
  public String getArrivalSourceRowId() { return arrivalSourceRowId; }
  public void setArrivalSourceRowId(String arrivalSourceRowId) { this.arrivalSourceRowId = arrivalSourceRowId; }
  public String getArrivalHandoverRecord() { return arrivalHandoverRecord; }
  public void setArrivalHandoverRecord(String arrivalHandoverRecord) { this.arrivalHandoverRecord = arrivalHandoverRecord; }
  public String getArrivalProjectType() { return arrivalProjectType; }
  public void setArrivalProjectType(String arrivalProjectType) { this.arrivalProjectType = arrivalProjectType; }
  public String getArrivalExperienceProject() { return arrivalExperienceProject; }
  public void setArrivalExperienceProject(String arrivalExperienceProject) { this.arrivalExperienceProject = arrivalExperienceProject; }
  public String getHistoricalExperienceCount() { return historicalExperienceCount; }
  public void setHistoricalExperienceCount(String historicalExperienceCount) { this.historicalExperienceCount = historicalExperienceCount; }
  public String getCustomerReport() { return customerReport; }
  public void setCustomerReport(String customerReport) { this.customerReport = customerReport; }
  public String getReceptionTeacher() { return receptionTeacher; }
  public void setReceptionTeacher(String receptionTeacher) { this.receptionTeacher = receptionTeacher; }
  public String getReceptionConsultant() { return receptionConsultant; }
  public void setReceptionConsultant(String receptionConsultant) { this.receptionConsultant = receptionConsultant; }
  public String getVoucherRedeemed() { return voucherRedeemed; }
  public void setVoucherRedeemed(String voucherRedeemed) { this.voucherRedeemed = voucherRedeemed; }
  public BigDecimal getTransactionAmount() { return transactionAmount; }
  public void setTransactionAmount(BigDecimal transactionAmount) { this.transactionAmount = transactionAmount; }
  public LocalDateTime getTransactionAt() { return transactionAt; }
  public void setTransactionAt(LocalDateTime transactionAt) { this.transactionAt = transactionAt; }
  public String getTransactionPrimaryReason() { return transactionPrimaryReason; }
  public void setTransactionPrimaryReason(String transactionPrimaryReason) { this.transactionPrimaryReason = transactionPrimaryReason; }
  public String getSourceTable() { return sourceTable; }
  public void setSourceTable(String sourceTable) { this.sourceTable = sourceTable; }
  public String getSourceRowId() { return sourceRowId; }
  public void setSourceRowId(String sourceRowId) { this.sourceRowId = sourceRowId; }
  public LocalDateTime getSyncedAt() { return syncedAt; }
  public void setSyncedAt(LocalDateTime syncedAt) { this.syncedAt = syncedAt; }
  public Integer getVersion() { return version; }
  public void setVersion(Integer version) { this.version = version; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
