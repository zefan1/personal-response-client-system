package com.privateflow.modules.customer.service;

import com.privateflow.modules.customer.Customer;
import org.springframework.stereotype.Component;

@Component
public class CustomerMergeEngine {

  public Customer merge(Customer incoming, Customer existing) {
    if (existing == null) {
      return incoming;
    }
    Customer merged = copy(existing);
    String sourceTable = incoming.getSourceTable();
    if ("推广组客资登记表".equals(sourceTable)) {
      applyBasicInfo(merged, incoming);
      copyIfPresent(merged, incoming, true, false, false);
    } else if ("私域客资管理表".equals(sourceTable)) {
      applyBasicInfo(merged, incoming);
      merged.setCustomerStage(coalesce(incoming.getCustomerStage(), merged.getCustomerStage()));
      merged.setFollowupNotes(coalesce(incoming.getFollowupNotes(), merged.getFollowupNotes()));
      merged.setNextFollowupAt(coalesce(incoming.getNextFollowupAt(), merged.getNextFollowupAt()));
      merged.setNextFollowupDir(coalesce(incoming.getNextFollowupDir(), merged.getNextFollowupDir()));
      copyIfPresent(merged, incoming, true, true, false);
    } else if ("新客管理衔接表".equals(sourceTable)) {
      merged.setAppointmentDate(coalesce(incoming.getAppointmentDate(), merged.getAppointmentDate()));
      merged.setAppointmentStore(coalesce(incoming.getAppointmentStore(), merged.getAppointmentStore()));
      merged.setAppointmentItem(coalesce(incoming.getAppointmentItem(), merged.getAppointmentItem()));
      merged.setArrived(coalesce(incoming.getArrived(), merged.getArrived()));
      if (merged.getAssignedKeeper() == null) {
        merged.setAssignedKeeper(incoming.getAssignedKeeper());
      }
      if (merged.getNickname() == null) {
        merged.setNickname(incoming.getNickname());
      }
    } else if (sourceTable != null && sourceTable.startsWith("ASSIGNMENT:")) {
      // Assignment intake is authoritative for lead ownership and attribution
      // fields, while profile/follow-up facts remain owned by the master table.
      applyBasicInfo(merged, incoming);
    }
    copyExtendedFactsIfPresent(merged, incoming);
    if (!isAuxiliaryTable(sourceTable)) {
      merged.setSourceTable(incoming.getSourceTable());
      merged.setSourceRowId(incoming.getSourceRowId());
      merged.setSyncedAt(incoming.getSyncedAt());
    }
    return merged;
  }

  private static boolean isAuxiliaryTable(String sourceTable) {
    return sourceTable != null
        && (sourceTable.startsWith("ASSIGNMENT:") || sourceTable.startsWith("ARRIVAL:"));
  }

  /** Makes a detached working copy without changing customer lineage. */
  public Customer copyOf(Customer source) {
    return copy(source);
  }

  private void applyBasicInfo(Customer target, Customer source) {
    target.setNickname(coalesce(source.getNickname(), target.getNickname()));
    target.setWechatId(coalesce(source.getWechatId(), target.getWechatId()));
    target.setSourceChannel(coalesce(source.getSourceChannel(), target.getSourceChannel()));
    target.setLeadType(coalesce(source.getLeadType(), target.getLeadType()));
    target.setLeadCaptureType(coalesce(source.getLeadCaptureType(), target.getLeadCaptureType()));
    target.setLeadCaptureMethod(coalesce(source.getLeadCaptureMethod(), target.getLeadCaptureMethod()));
    target.setPlatformLeadAt(coalesce(source.getPlatformLeadAt(), target.getPlatformLeadAt()));
    target.setAssignedKeeper(coalesce(source.getAssignedKeeper(), target.getAssignedKeeper()));
    target.setAssignedAt(coalesce(source.getAssignedAt(), target.getAssignedAt()));
    target.setIntendedStore(coalesce(source.getIntendedStore(), target.getIntendedStore()));
    target.setIntendedProject(coalesce(source.getIntendedProject(), target.getIntendedProject()));
    target.setPurchasedProject(coalesce(source.getPurchasedProject(), target.getPurchasedProject()));
  }

  private void copyIfPresent(Customer target, Customer source, boolean profile, boolean followup, boolean appointment) {
    if (profile) {
      target.setPersonalityType(coalesce(source.getPersonalityType(), target.getPersonalityType()));
      target.setPostpartumMonths(coalesce(source.getPostpartumMonths(), target.getPostpartumMonths()));
      target.setParity(coalesce(source.getParity(), target.getParity()));
      target.setDeliveryMethod(coalesce(source.getDeliveryMethod(), target.getDeliveryMethod()));
      target.setBreastfeeding(coalesce(source.getBreastfeeding(), target.getBreastfeeding()));
      target.setLochiaPeriod(coalesce(source.getLochiaPeriod(), target.getLochiaPeriod()));
      target.setPregnancyWeight(coalesce(source.getPregnancyWeight(), target.getPregnancyWeight()));
      target.setCurrentWeight(coalesce(source.getCurrentWeight(), target.getCurrentWeight()));
      target.setBodyConcerns(coalesce(source.getBodyConcerns(), target.getBodyConcerns()));
      target.setDiastasisRecti(coalesce(source.getDiastasisRecti(), target.getDiastasisRecti()));
      target.setUrineLeakage(coalesce(source.getUrineLeakage(), target.getUrineLeakage()));
      target.setPubicLumbago(coalesce(source.getPubicLumbago(), target.getPubicLumbago()));
      target.setPrevRepairExp(coalesce(source.getPrevRepairExp(), target.getPrevRepairExp()));
      target.setPostpartumCheck(coalesce(source.getPostpartumCheck(), target.getPostpartumCheck()));
      target.setExerciseHabits(coalesce(source.getExerciseHabits(), target.getExerciseHabits()));
      target.setIntentLevel(coalesce(source.getIntentLevel(), target.getIntentLevel()));
      target.setWorries(coalesce(source.getWorries(), target.getWorries()));
      target.setInternalNote(coalesce(source.getInternalNote(), target.getInternalNote()));
      target.setCustomerProfileSummary(coalesce(source.getCustomerProfileSummary(), target.getCustomerProfileSummary()));
      target.setFirstTrackingCapture(coalesce(source.getFirstTrackingCapture(), target.getFirstTrackingCapture()));
      target.setSecondTrackingCapture(coalesce(source.getSecondTrackingCapture(), target.getSecondTrackingCapture()));
      target.setThirdTrackingCapture(coalesce(source.getThirdTrackingCapture(), target.getThirdTrackingCapture()));
    }
    if (followup) {
      target.setLastFollowupAt(coalesce(source.getLastFollowupAt(), target.getLastFollowupAt()));
    }
    if (appointment) {
      target.setAppointmentDate(coalesce(source.getAppointmentDate(), target.getAppointmentDate()));
    }
  }

  private void copyExtendedFactsIfPresent(Customer target, Customer source) {
    target.setCustomerName(coalesce(source.getCustomerName(), target.getCustomerName()));
    target.setAdvertisingType(coalesce(source.getAdvertisingType(), target.getAdvertisingType()));
    target.setGlobalAdvertisementId(coalesce(source.getGlobalAdvertisementId(), target.getGlobalAdvertisementId()));
    target.setStandardAdvertisementId(coalesce(source.getStandardAdvertisementId(), target.getStandardAdvertisementId()));
    target.setContentId(coalesce(source.getContentId(), target.getContentId()));
    target.setVideoId(coalesce(source.getVideoId(), target.getVideoId()));
    target.setOrderNumber(coalesce(source.getOrderNumber(), target.getOrderNumber()));
    target.setConversionTrace(coalesce(source.getConversionTrace(), target.getConversionTrace()));
    target.setPreviousAssignedKeeper(coalesce(source.getPreviousAssignedKeeper(), target.getPreviousAssignedKeeper()));
    target.setPreviousPlatformLeadAt(coalesce(source.getPreviousPlatformLeadAt(), target.getPreviousPlatformLeadAt()));
    target.setAssignmentMonth(coalesce(source.getAssignmentMonth(), target.getAssignmentMonth()));
    target.setExperienceCardType(coalesce(source.getExperienceCardType(), target.getExperienceCardType()));
    target.setPendingOrderStatus(coalesce(source.getPendingOrderStatus(), target.getPendingOrderStatus()));
    target.setPurchaseDate(coalesce(source.getPurchaseDate(), target.getPurchaseDate()));
    target.setCustomerLevel(coalesce(source.getCustomerLevel(), target.getCustomerLevel()));
    target.setArrivalHandoverRecord(coalesce(source.getArrivalHandoverRecord(), target.getArrivalHandoverRecord()));
    target.setArrivalProjectType(coalesce(source.getArrivalProjectType(), target.getArrivalProjectType()));
    target.setArrivalExperienceProject(coalesce(source.getArrivalExperienceProject(), target.getArrivalExperienceProject()));
    target.setHistoricalExperienceCount(coalesce(source.getHistoricalExperienceCount(), target.getHistoricalExperienceCount()));
    target.setCustomerReport(coalesce(source.getCustomerReport(), target.getCustomerReport()));
    target.setReceptionTeacher(coalesce(source.getReceptionTeacher(), target.getReceptionTeacher()));
    target.setReceptionConsultant(coalesce(source.getReceptionConsultant(), target.getReceptionConsultant()));
    target.setVoucherRedeemed(coalesce(source.getVoucherRedeemed(), target.getVoucherRedeemed()));
    target.setTransactionAmount(coalesce(source.getTransactionAmount(), target.getTransactionAmount()));
    target.setTransactionAt(coalesce(source.getTransactionAt(), target.getTransactionAt()));
    target.setTransactionPrimaryReason(coalesce(source.getTransactionPrimaryReason(), target.getTransactionPrimaryReason()));
  }

  private static <T> T coalesce(T incoming, T existing) {
    if (incoming instanceof String text && text.isBlank()) {
      return existing;
    }
    return incoming == null ? existing : incoming;
  }

  private Customer copy(Customer source) {
    Customer c = new Customer();
    c.setId(source.getId());
    c.setPhone(source.getPhone());
    c.setNickname(source.getNickname());
    c.setCustomerName(source.getCustomerName());
    c.setWechatId(source.getWechatId());
    c.setSourceChannel(source.getSourceChannel());
    c.setLeadType(source.getLeadType());
    c.setLeadCaptureType(source.getLeadCaptureType());
    c.setLeadCaptureMethod(source.getLeadCaptureMethod());
    c.setPlatformLeadAt(source.getPlatformLeadAt());
    c.setAdvertisingType(source.getAdvertisingType());
    c.setGlobalAdvertisementId(source.getGlobalAdvertisementId());
    c.setStandardAdvertisementId(source.getStandardAdvertisementId());
    c.setContentId(source.getContentId());
    c.setVideoId(source.getVideoId());
    c.setOrderNumber(source.getOrderNumber());
    c.setConversionTrace(source.getConversionTrace());
    c.setPersonalityType(source.getPersonalityType());
    c.setAssignedKeeper(source.getAssignedKeeper());
    c.setAssignedAt(source.getAssignedAt());
    c.setLeadInitialProcessedAt(source.getLeadInitialProcessedAt());
    c.setLeadInitialProcessedBy(source.getLeadInitialProcessedBy());
    c.setLeadRetainedUntil(source.getLeadRetainedUntil());
    c.setLeadInvalid(source.isLeadInvalid());
    c.setPreviousAssignedKeeper(source.getPreviousAssignedKeeper());
    c.setPreviousPlatformLeadAt(source.getPreviousPlatformLeadAt());
    c.setAssignmentMonth(source.getAssignmentMonth());
    c.setIntendedStore(source.getIntendedStore());
    c.setIntendedProject(source.getIntendedProject());
    c.setPurchasedProject(source.getPurchasedProject());
    c.setExperienceCardType(source.getExperienceCardType());
    c.setPendingOrderStatus(source.getPendingOrderStatus());
    c.setPurchaseDate(source.getPurchaseDate());
    c.setCustomerLevel(source.getCustomerLevel());
    c.setPostpartumMonths(source.getPostpartumMonths());
    c.setParity(source.getParity());
    c.setDeliveryMethod(source.getDeliveryMethod());
    c.setBreastfeeding(source.getBreastfeeding());
    c.setLochiaPeriod(source.getLochiaPeriod());
    c.setPregnancyWeight(source.getPregnancyWeight());
    c.setCurrentWeight(source.getCurrentWeight());
    c.setBodyConcerns(source.getBodyConcerns());
    c.setDiastasisRecti(source.getDiastasisRecti());
    c.setUrineLeakage(source.getUrineLeakage());
    c.setPubicLumbago(source.getPubicLumbago());
    c.setPrevRepairExp(source.getPrevRepairExp());
    c.setPostpartumCheck(source.getPostpartumCheck());
    c.setExerciseHabits(source.getExerciseHabits());
    c.setIntentLevel(source.getIntentLevel());
    c.setWorries(source.getWorries());
    c.setCustomerStage(source.getCustomerStage());
    c.setInternalNote(source.getInternalNote());
    c.setCustomerProfileSummary(source.getCustomerProfileSummary());
    c.setFirstTrackingCapture(source.getFirstTrackingCapture());
    c.setSecondTrackingCapture(source.getSecondTrackingCapture());
    c.setThirdTrackingCapture(source.getThirdTrackingCapture());
    c.setLastFollowupAt(source.getLastFollowupAt());
    c.setFollowupNotes(source.getFollowupNotes());
    c.setNextFollowupAt(source.getNextFollowupAt());
    c.setNextFollowupDir(source.getNextFollowupDir());
    c.setAppointmentDate(source.getAppointmentDate());
    c.setAppointmentStore(source.getAppointmentStore());
    c.setAppointmentItem(source.getAppointmentItem());
    c.setArrived(source.getArrived());
    c.setAppointmentStatus(source.getAppointmentStatus());
    c.setAppointmentTime(source.getAppointmentTime());
    c.setArrivalSourceRowId(source.getArrivalSourceRowId());
    c.setArrivalHandoverRecord(source.getArrivalHandoverRecord());
    c.setArrivalProjectType(source.getArrivalProjectType());
    c.setArrivalExperienceProject(source.getArrivalExperienceProject());
    c.setHistoricalExperienceCount(source.getHistoricalExperienceCount());
    c.setCustomerReport(source.getCustomerReport());
    c.setReceptionTeacher(source.getReceptionTeacher());
    c.setReceptionConsultant(source.getReceptionConsultant());
    c.setVoucherRedeemed(source.getVoucherRedeemed());
    c.setTransactionAmount(source.getTransactionAmount());
    c.setTransactionAt(source.getTransactionAt());
    c.setTransactionPrimaryReason(source.getTransactionPrimaryReason());
    c.setSourceTable(source.getSourceTable());
    c.setSourceRowId(source.getSourceRowId());
    c.setSyncedAt(source.getSyncedAt());
    c.setVersion(source.getVersion());
    c.setCreatedAt(source.getCreatedAt());
    c.setUpdatedAt(source.getUpdatedAt());
    return c;
  }
}
