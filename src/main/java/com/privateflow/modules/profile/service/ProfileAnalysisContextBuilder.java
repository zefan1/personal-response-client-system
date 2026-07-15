package com.privateflow.modules.profile.service;

import com.privateflow.common.events.CustomerMessageSentEvent;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.skill.ProfileAnalysisContext;
import com.privateflow.modules.tags.CustomerTagCategoryLock;
import com.privateflow.modules.tags.CustomerTagFoundationRepository;
import com.privateflow.modules.tags.TagCandidateBuilder;
import com.privateflow.modules.tags.TagCandidatePurpose;
import com.privateflow.modules.tags.TagCategory;
import com.privateflow.modules.tags.TagDirectoryService;
import com.privateflow.modules.tags.TagValue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ProfileAnalysisContextBuilder {

  private static final int MAX_RECENT_MESSAGES = 10;
  private static final int MAX_MESSAGE_CHARS = 400;
  private static final int MAX_PROFILE_TEXT_CHARS = 500;

  private final TagCandidateBuilder candidateBuilder;
  private final TagDirectoryService directoryService;
  private final CustomerTagFoundationRepository tagRepository;

  public ProfileAnalysisContextBuilder(
      TagCandidateBuilder candidateBuilder,
      TagDirectoryService directoryService,
      CustomerTagFoundationRepository tagRepository) {
    this.candidateBuilder = candidateBuilder;
    this.directoryService = directoryService;
    this.tagRepository = tagRepository;
  }

  public ProfileAnalysisContext build(
      Customer customer,
      Map<String, Object> existingProfile,
      List<CustomerMessageSentEvent.ChatMessage> rawMessages) {
    if (customer == null || customer.getId() == null || customer.getId() <= 0) {
      return ProfileAnalysisContext.empty();
    }
    long customerId = customer.getId();
    List<CustomerTagCategoryLock> activeLocks = tagRepository.findCategoryLocks(customerId).stream()
        .filter(CustomerTagCategoryLock::locked)
        .toList();
    Set<Long> lockedCategoryIds = new LinkedHashSet<>();
    activeLocks.forEach(lock -> lockedCategoryIds.add(lock.categoryId()));
    var snapshot = directoryService.getSnapshot();

    List<ProfileAnalysisContext.LockedCategory> lockedCategories = activeLocks.stream()
        .map(lock -> {
          TagCategory category = snapshot.categoriesById().get(lock.categoryId());
          return category == null ? null : new ProfileAnalysisContext.LockedCategory(
              category.categoryKey(),
              category.categoryName(),
              lock.lockReason());
        })
        .filter(java.util.Objects::nonNull)
        .toList();
    List<ProfileAnalysisContext.CategoryCandidate> candidates = candidateBuilder
        .build(TagCandidatePurpose.SYSTEM_INFERENCE).stream()
        .filter(category -> !lockedCategoryIds.contains(category.id()))
        .map(this::categoryCandidate)
        .toList();
    List<ProfileAnalysisContext.CurrentTag> currentTags = tagRepository.findCurrentTagDetails(customerId).stream()
        .map(tag -> new ProfileAnalysisContext.CurrentTag(
            tag.categoryKey(),
            tag.categoryName(),
            tag.tagValue(),
            tag.tagDisplayName(),
            tag.sourceType()))
        .toList();
    List<ProfileAnalysisContext.ConversationMessage> recentMessages = recentMessages(rawMessages);
    int effectiveMessageCount = (int) recentMessages.stream()
        .filter(message -> "client".equals(message.role()))
        .count();
    return new ProfileAnalysisContext(
        customerId,
        customer.getVersion() == null ? 0 : customer.getVersion(),
        effectiveMessageCount,
        recentMessages,
        sanitizeProfile(existingProfile),
        currentTags,
        lockedCategories,
        candidates);
  }

  private ProfileAnalysisContext.CategoryCandidate categoryCandidate(TagCategory category) {
    return new ProfileAnalysisContext.CategoryCandidate(
        category.categoryKey(),
        category.categoryName(),
        category.purpose(),
        category.selectionMode().name(),
        category.autoUpdateMode().name(),
        category.minConfidence(),
        category.minEvidenceMessages(),
        category.cooldownHours(),
        category.uncertainPolicy().name(),
        category.values().stream().map(this::tagCandidate).toList());
  }

  private ProfileAnalysisContext.TagCandidate tagCandidate(TagValue value) {
    return new ProfileAnalysisContext.TagCandidate(
        value.tagValue(),
        value.displayName(),
        value.meaning(),
        value.applicableWhen(),
        value.notApplicableWhen(),
        value.positiveExamples(),
        value.negativeExamples(),
        value.synonyms());
  }

  private List<ProfileAnalysisContext.ConversationMessage> recentMessages(
      List<CustomerMessageSentEvent.ChatMessage> rawMessages) {
    if (rawMessages == null || rawMessages.isEmpty()) {
      return List.of();
    }
    List<ProfileAnalysisContext.ConversationMessage> normalized = new ArrayList<>();
    for (CustomerMessageSentEvent.ChatMessage message : rawMessages) {
      if (message == null || message.text() == null || message.text().isBlank()) {
        continue;
      }
      String role = normalizeRole(message.role());
      if (role == null) {
        continue;
      }
      normalized.add(new ProfileAnalysisContext.ConversationMessage(
          role,
          clip(message.text().trim(), MAX_MESSAGE_CHARS),
          message.timestamp()));
    }
    int fromIndex = Math.max(0, normalized.size() - MAX_RECENT_MESSAGES);
    return List.copyOf(normalized.subList(fromIndex, normalized.size()));
  }

  private String normalizeRole(String role) {
    if ("client".equalsIgnoreCase(role) || "customer".equalsIgnoreCase(role)) {
      return "client";
    }
    if ("keeper".equalsIgnoreCase(role) || "staff".equalsIgnoreCase(role)) {
      return "keeper";
    }
    return null;
  }

  private Map<String, Object> sanitizeProfile(Map<String, Object> existingProfile) {
    Map<String, Object> sanitized = new LinkedHashMap<>();
    if (existingProfile != null) {
      existingProfile.forEach((key, value) -> {
        if (key == null || value == null || "phone".equalsIgnoreCase(key) || "assignedKeeper".equalsIgnoreCase(key)) {
          return;
        }
        if (value instanceof String text) {
          if (!text.isBlank()) {
            sanitized.put(key, clip(text.trim(), MAX_PROFILE_TEXT_CHARS));
          }
        } else {
          sanitized.put(key, value);
        }
      });
      Object phone = existingProfile.get("phone");
      if (phone instanceof String text && text.length() >= 4) {
        sanitized.put("phoneLast4", text.substring(text.length() - 4));
      }
    }
    return Map.copyOf(sanitized);
  }

  private String clip(String value, int maxChars) {
    return value.substring(0, Math.min(value.length(), maxChars));
  }
}
