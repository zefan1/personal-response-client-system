package com.privateflow.modules.match.service;

import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.customer.service.CustomerAccessService;
import com.privateflow.modules.match.CustomerMatchErrorCodes;
import com.privateflow.modules.match.CustomerMatchException;
import com.privateflow.modules.match.util.PhoneUtils;
import com.privateflow.modules.profile.CustomerProfileView;
import com.privateflow.modules.profile.service.SuggestionQueueManager;
import com.privateflow.modules.tags.CustomerTagCategoryLock;
import com.privateflow.modules.tags.CustomerTagFoundationRepository;
import com.privateflow.modules.tags.TagCandidateBuilder;
import com.privateflow.modules.tags.TagCandidatePurpose;
import com.privateflow.modules.tags.TagCategory;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class CustomerProfileService {

  private final CustomerQueryService customerQueryService;
  private final SuggestionQueueManager suggestionQueueManager;
  private final CustomerAccessService customerAccessService;
  private final CustomerTagFoundationRepository tagRepository;
  private final TagCandidateBuilder candidateBuilder;

  @Autowired
  public CustomerProfileService(
      CustomerQueryService customerQueryService,
      SuggestionQueueManager suggestionQueueManager,
      CustomerAccessService customerAccessService,
      CustomerTagFoundationRepository tagRepository,
      TagCandidateBuilder candidateBuilder) {
    this.customerQueryService = customerQueryService;
    this.suggestionQueueManager = suggestionQueueManager;
    this.customerAccessService = customerAccessService;
    this.tagRepository = tagRepository;
    this.candidateBuilder = candidateBuilder;
  }

  public CustomerProfileService(
      CustomerQueryService customerQueryService,
      SuggestionQueueManager suggestionQueueManager,
      CustomerAccessService customerAccessService) {
    this(customerQueryService, suggestionQueueManager, customerAccessService, null, null);
  }

  public CustomerProfileView getProfile(String rawPhone) {
    String phone = PhoneUtils.clean(rawPhone);
    if (!PhoneUtils.isValid(phone)) {
      throw new CustomerMatchException(CustomerMatchErrorCodes.BAD_REQUEST, "手机号格式不正确");
    }
    try {
      Customer customer = customerQueryService.getByPhone(phone);
      if (customer == null) {
        throw new CustomerMatchException(CustomerMatchErrorCodes.CUSTOMER_NOT_FOUND, "客户未找到（无档案模式）");
      }
      if (!customerAccessService.canAccess(customer)) {
        throw new CustomerMatchException(CustomerMatchErrorCodes.CUSTOMER_NOT_FOUND, "客户未找到或不在你的负责范围");
      }
      Customer copy = copy(customer);
      copy.setPhone(PhoneUtils.mask(customer.getPhone()));
      TagExtension extension = loadTagExtension(customer);
      return new CustomerProfileView(
          copy,
          phone,
          suggestionQueueManager.listPending(phone),
          extension.currentTags(),
          extension.locks(),
          extension.editableCategories());
    } catch (CustomerMatchException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw new CustomerMatchException(
          CustomerMatchErrorCodes.MATCH_FAILED,
          "客户档案查询服务暂不可用",
          ex);
    }
  }

  private TagExtension loadTagExtension(Customer customer) {
    if (tagRepository == null || candidateBuilder == null || customer.getId() == null) {
      return TagExtension.empty();
    }
    try {
      return new TagExtension(
          List.copyOf(tagRepository.findCurrentTagDetails(customer.getId())),
          List.copyOf(tagRepository.findCategoryLocks(customer.getId())),
          List.copyOf(candidateBuilder.build(TagCandidatePurpose.MANUAL_ASSIGNMENT)));
    } catch (RuntimeException ex) {
      return TagExtension.empty();
    }
  }

  private record TagExtension(
      List<com.privateflow.modules.tags.CustomerTagQueryDto> currentTags,
      List<CustomerTagCategoryLock> locks,
      List<TagCategory> editableCategories) {
    private static TagExtension empty() {
      return new TagExtension(List.of(), List.of(), List.of());
    }
  }

  private Customer copy(Customer source) {
    Customer target = new Customer();
    target.setId(source.getId());
    target.setPhone(source.getPhone());
    target.setNickname(source.getNickname());
    target.setSourceChannel(source.getSourceChannel());
    target.setLeadType(source.getLeadType());
    target.setPersonalityType(source.getPersonalityType());
    target.setAssignedKeeper(source.getAssignedKeeper());
    target.setIntendedStore(source.getIntendedStore());
    target.setIntendedProject(source.getIntendedProject());
    target.setPurchasedProject(source.getPurchasedProject());
    target.setPostpartumMonths(source.getPostpartumMonths());
    target.setParity(source.getParity());
    target.setDeliveryMethod(source.getDeliveryMethod());
    target.setBreastfeeding(source.getBreastfeeding());
    target.setLochiaPeriod(source.getLochiaPeriod());
    target.setPregnancyWeight(source.getPregnancyWeight());
    target.setCurrentWeight(source.getCurrentWeight());
    target.setBodyConcerns(source.getBodyConcerns());
    target.setDiastasisRecti(source.getDiastasisRecti());
    target.setUrineLeakage(source.getUrineLeakage());
    target.setPubicLumbago(source.getPubicLumbago());
    target.setPrevRepairExp(source.getPrevRepairExp());
    target.setPostpartumCheck(source.getPostpartumCheck());
    target.setExerciseHabits(source.getExerciseHabits());
    target.setIntentLevel(source.getIntentLevel());
    target.setWorries(source.getWorries());
    target.setCustomerStage(source.getCustomerStage());
    target.setLastFollowupAt(source.getLastFollowupAt());
    target.setFollowupNotes(source.getFollowupNotes());
    target.setNextFollowupAt(source.getNextFollowupAt());
    target.setNextFollowupDir(source.getNextFollowupDir());
    target.setAppointmentDate(source.getAppointmentDate());
    target.setAppointmentStore(source.getAppointmentStore());
    target.setAppointmentItem(source.getAppointmentItem());
    target.setArrived(source.getArrived());
    target.setSourceTable(source.getSourceTable());
    target.setSourceRowId(source.getSourceRowId());
    target.setSyncedAt(source.getSyncedAt());
    target.setVersion(source.getVersion());
    target.setCreatedAt(source.getCreatedAt());
    target.setUpdatedAt(source.getUpdatedAt());
    return target;
  }
}
