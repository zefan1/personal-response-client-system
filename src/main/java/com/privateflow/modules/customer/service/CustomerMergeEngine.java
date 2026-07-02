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
    }
    merged.setSourceTable(incoming.getSourceTable());
    merged.setSourceRowId(incoming.getSourceRowId());
    merged.setSyncedAt(incoming.getSyncedAt());
    return merged;
  }

  private void applyBasicInfo(Customer target, Customer source) {
    target.setNickname(coalesce(source.getNickname(), target.getNickname()));
    target.setSourceChannel(coalesce(source.getSourceChannel(), target.getSourceChannel()));
    target.setLeadType(coalesce(source.getLeadType(), target.getLeadType()));
    target.setAssignedKeeper(coalesce(source.getAssignedKeeper(), target.getAssignedKeeper()));
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
    }
    if (followup) {
      target.setLastFollowupAt(coalesce(source.getLastFollowupAt(), target.getLastFollowupAt()));
    }
    if (appointment) {
      target.setAppointmentDate(coalesce(source.getAppointmentDate(), target.getAppointmentDate()));
    }
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
    c.setSourceChannel(source.getSourceChannel());
    c.setLeadType(source.getLeadType());
    c.setPersonalityType(source.getPersonalityType());
    c.setAssignedKeeper(source.getAssignedKeeper());
    c.setIntendedStore(source.getIntendedStore());
    c.setIntendedProject(source.getIntendedProject());
    c.setPurchasedProject(source.getPurchasedProject());
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
    c.setLastFollowupAt(source.getLastFollowupAt());
    c.setFollowupNotes(source.getFollowupNotes());
    c.setNextFollowupAt(source.getNextFollowupAt());
    c.setNextFollowupDir(source.getNextFollowupDir());
    c.setAppointmentDate(source.getAppointmentDate());
    c.setAppointmentStore(source.getAppointmentStore());
    c.setAppointmentItem(source.getAppointmentItem());
    c.setArrived(source.getArrived());
    c.setSourceTable(source.getSourceTable());
    c.setSourceRowId(source.getSourceRowId());
    c.setSyncedAt(source.getSyncedAt());
    c.setVersion(source.getVersion());
    c.setCreatedAt(source.getCreatedAt());
    c.setUpdatedAt(source.getUpdatedAt());
    return c;
  }
}
