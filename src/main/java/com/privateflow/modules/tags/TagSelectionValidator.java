package com.privateflow.modules.tags;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;
import org.springframework.stereotype.Service;

@Service
public class TagSelectionValidator {

  private final TagDirectoryService directoryService;
  private final TagCandidateBuilder candidateBuilder;

  public TagSelectionValidator(
      TagDirectoryService directoryService,
      TagCandidateBuilder candidateBuilder) {
    this.directoryService = directoryService;
    this.candidateBuilder = candidateBuilder;
  }

  public TagSelectionValidationResult validateCodes(
      TagCandidatePurpose purpose,
      String categoryKey,
      List<String> valueCodes,
      TagSelectionContext context) {
    TagDirectorySnapshot snapshot = directoryService.getSnapshot();
    TagCategory category = snapshot.categoriesByKey().get(categoryKey);
    return validate(
        purpose,
        category,
        valueCodes,
        code -> resolveByCode(snapshot, categoryKey, code),
        context);
  }

  public TagSelectionValidationResult validateIds(
      TagCandidatePurpose purpose,
      long categoryId,
      List<Long> valueIds,
      TagSelectionContext context) {
    TagDirectorySnapshot snapshot = directoryService.getSnapshot();
    TagCategory category = snapshot.categoriesById().get(categoryId);
    return validate(purpose, category, valueIds, snapshot.valuesById()::get, context);
  }

  private <T> TagSelectionValidationResult validate(
      TagCandidatePurpose purpose,
      TagCategory category,
      List<T> requestedValues,
      Function<T, TagValue> resolver,
      TagSelectionContext rawContext) {
    if (category == null) {
      return rejected(TagSelectionValidationReason.CATEGORY_NOT_FOUND, null, List.of());
    }
    if (!category.isEnabled()) {
      return rejected(TagSelectionValidationReason.CATEGORY_DISABLED, category, List.of());
    }
    if (category.mergedIntoId() != null) {
      return rejected(TagSelectionValidationReason.CATEGORY_MERGED, category, List.of());
    }

    List<T> requested = requestedValues == null ? List.of() : new ArrayList<>(requestedValues);
    if (new HashSet<>(requested).size() != requested.size()) {
      return rejected(TagSelectionValidationReason.DUPLICATE_VALUES, category, List.of());
    }

    List<TagValue> resolved = new ArrayList<>();
    for (T requestedValue : requested) {
      TagValue value = resolver.apply(requestedValue);
      if (value == null) {
        return rejected(TagSelectionValidationReason.VALUE_NOT_FOUND, category, resolved);
      }
      resolved.add(value);
      if (value.categoryId() != category.id()) {
        return rejected(TagSelectionValidationReason.VALUE_CATEGORY_MISMATCH, category, resolved);
      }
      if (!value.isEnabled()) {
        return rejected(TagSelectionValidationReason.VALUE_DISABLED, category, resolved);
      }
      if (value.mergedIntoId() != null) {
        return rejected(TagSelectionValidationReason.VALUE_MERGED, category, resolved);
      }
      if (!candidateBuilder.isAllowed(purpose, category, value)) {
        return rejected(TagSelectionValidationReason.PURPOSE_NOT_ALLOWED, category, resolved);
      }
    }

    if (category.selectionMode() == TagSelectionMode.SINGLE && resolved.size() != 1) {
      return rejected(TagSelectionValidationReason.SINGLE_VALUE_COUNT_INVALID, category, resolved);
    }
    if (category.selectionMode() == TagSelectionMode.MULTI && resolved.isEmpty()) {
      return rejected(TagSelectionValidationReason.MULTI_VALUE_REQUIRED, category, resolved);
    }

    TagSelectionContext context = rawContext == null ? TagSelectionContext.empty() : rawContext;
    if (purpose == TagCandidatePurpose.SYSTEM_INFERENCE) {
      if (isBlank(context.evidence())) {
        return rejected(TagSelectionValidationReason.EVIDENCE_REQUIRED, category, resolved);
      }
      if (context.validMessageCount() < category.minEvidenceMessages()) {
        return rejected(TagSelectionValidationReason.EVIDENCE_MESSAGES_INSUFFICIENT, category, resolved);
      }
      if (context.confidence() == null) {
        return rejected(TagSelectionValidationReason.CONFIDENCE_REQUIRED, category, resolved);
      }
      if (context.confidence().compareTo(category.minConfidence()) < 0) {
        return rejected(TagSelectionValidationReason.CONFIDENCE_TOO_LOW, category, resolved);
      }
    }
    if ((purpose == TagCandidatePurpose.IMPORT || purpose == TagCandidatePurpose.FOLLOWUP_RULE)
        && isBlank(context.businessBasis())) {
      return rejected(TagSelectionValidationReason.BUSINESS_BASIS_REQUIRED, category, resolved);
    }
    return TagSelectionValidationResult.accepted(category, resolved);
  }

  private TagSelectionValidationResult rejected(
      TagSelectionValidationReason reason,
      TagCategory category,
      List<TagValue> values) {
    return TagSelectionValidationResult.rejected(reason, category, values);
  }

  private TagValue resolveByCode(
      TagDirectorySnapshot snapshot,
      String categoryKey,
      String valueCode) {
    if (valueCode == null) {
      return null;
    }
    TagValue exact = snapshot.valuesByCategoryAndCode().get(new TagValueCode(categoryKey, valueCode));
    if (exact != null) {
      return exact;
    }
    List<TagValue> globalMatches = snapshot.valuesByCode().get(valueCode);
    return globalMatches == null || globalMatches.isEmpty() ? null : globalMatches.get(0);
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
