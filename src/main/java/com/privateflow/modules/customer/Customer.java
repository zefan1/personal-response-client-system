package com.privateflow.modules.customer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class Customer {

  private Long id;
  private String phone;
  private String nickname;
  private String sourceChannel;
  private String leadType;
  private String personalityType;
  private String assignedKeeper;
  private String intendedStore;
  private String intendedProject;
  private String purchasedProject;
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
  private LocalDateTime lastFollowupAt;
  private String followupNotes;
  private LocalDateTime nextFollowupAt;
  private String nextFollowupDir;
  private LocalDate appointmentDate;
  private String appointmentStore;
  private String appointmentItem;
  private String arrived;
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
  public String getSourceChannel() { return sourceChannel; }
  public void setSourceChannel(String sourceChannel) { this.sourceChannel = sourceChannel; }
  public String getLeadType() { return leadType; }
  public void setLeadType(String leadType) { this.leadType = leadType; }
  public String getPersonalityType() { return personalityType; }
  public void setPersonalityType(String personalityType) { this.personalityType = personalityType; }
  public String getAssignedKeeper() { return assignedKeeper; }
  public void setAssignedKeeper(String assignedKeeper) { this.assignedKeeper = assignedKeeper; }
  public String getIntendedStore() { return intendedStore; }
  public void setIntendedStore(String intendedStore) { this.intendedStore = intendedStore; }
  public String getIntendedProject() { return intendedProject; }
  public void setIntendedProject(String intendedProject) { this.intendedProject = intendedProject; }
  public String getPurchasedProject() { return purchasedProject; }
  public void setPurchasedProject(String purchasedProject) { this.purchasedProject = purchasedProject; }
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
