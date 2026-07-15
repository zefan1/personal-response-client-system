package com.privateflow.modules.tags;

import java.math.BigDecimal;
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
    if (purpose == null) {
      return rejected(TagSelectionValidationReason.PURPOSE_REQUIRED, null, List.of(), null);
    }
    TagDirectorySnapshot snapshot = directoryService.getSnapshot();
    TagCategory category = snapshot.categoriesByKey().get(categoryKey);
    return validate(
        purpose,
        category,
        categoryKey,
        valueCodes,
        code -> resolveByCode(snapshot, categoryKey, code),
        context);
  }

  public TagSelectionValidationResult validateIds(
      TagCandidatePurpose purpose,
      long categoryId,
      List<Long> valueIds,
      TagSelectionContext context) {
    if (purpose == null) {
      return rejected(TagSelectionValidationReason.PURPOSE_REQUIRED, null, List.of(), null);
    }
    TagDirectorySnapshot snapshot = directoryService.getSnapshot();
    TagCategory category = snapshot.categoriesById().get(categoryId);
    return validate(purpose, category, categoryId, valueIds, snapshot.valuesById()::get, context);
  }

  private <T> TagSelectionValidationResult validate(
      TagCandidatePurpose purpose,
      TagCategory category,
      Object categoryInput,
      List<T> requestedValues,
      Function<T, TagValue> resolver,
      TagSelectionContext rawContext) {
    if (purpose == null) {
      return rejected(TagSelectionValidationReason.PURPOSE_REQUIRED, category, List.of(), null);
    }
    if (category == null) {
      return rejected(TagSelectionValidationReason.CATEGORY_NOT_FOUND, null, List.of(), categoryInput);
    }
    if (!category.isEnabled()) {
      return rejected(TagSelectionValidationReason.CATEGORY_DISABLED, category, List.of(), categoryInput);
    }
    if (category.mergedIntoId() != null) {
      return rejected(TagSelectionValidationReason.CATEGORY_MERGED, category, List.of(), categoryInput);
    }
    if (!candidateBuilder.isCategoryAllowed(purpose, category)) {
      return rejected(TagSelectionValidationReason.PURPOSE_NOT_ALLOWED, category, List.of(), categoryInput);
    }

    List<T> requested = requestedValues == null ? List.of() : new ArrayList<>(requestedValues);
    HashSet<T> seen = new HashSet<>();
    for (T requestedValue : requested) {
      if (!seen.add(requestedValue)) {
        return rejected(TagSelectionValidationReason.DUPLICATE_VALUES, category, List.of(), requestedValue);
      }
    }

    List<TagValue> resolved = new ArrayList<>();
    for (T requestedValue : requested) {
      TagValue value = resolver.apply(requestedValue);
      if (value == null) {
        return rejected(TagSelectionValidationReason.VALUE_NOT_FOUND, category, resolved, requestedValue);
      }
      resolved.add(value);
      if (value.categoryId() != category.id()) {
        return rejected(TagSelectionValidationReason.VALUE_CATEGORY_MISMATCH, category, resolved, requestedValue);
      }
      if (!value.isEnabled()) {
        return rejected(TagSelectionValidationReason.VALUE_DISABLED, category, resolved, requestedValue);
      }
      if (value.mergedIntoId() != null) {
        return rejected(TagSelectionValidationReason.VALUE_MERGED, category, resolved, requestedValue);
      }
      if (!candidateBuilder.isAllowed(purpose, category, value)) {
        return rejected(TagSelectionValidationReason.PURPOSE_NOT_ALLOWED, category, resolved, requestedValue);
      }
    }

    if (category.selectionMode() == TagSelectionMode.SINGLE && resolved.size() != 1) {
      return rejected(TagSelectionValidationReason.SINGLE_VALUE_COUNT_INVALID, category, resolved, requested);
    }
    if (category.selectionMode() == TagSelectionMode.MULTI && resolved.isEmpty()) {
      return rejected(TagSelectionValidationReason.MULTI_VALUE_REQUIRED, category, resolved, requested);
    }

    TagSelectionContext context = rawContext == null ? TagSelectionContext.empty() : rawContext;
    if (purpose == TagCandidatePurpose.SYSTEM_INFERENCE) {
      if (isBlank(context.evidence())) {
        return rejected(TagSelectionValidationReason.EVIDENCE_REQUIRED, category, resolved, context.evidence());
      }
      if (context.validMessageCount() < category.minEvidenceMessages()) {
        return rejected(
            TagSelectionValidationReason.EVIDENCE_MESSAGES_INSUFFICIENT,
            category,
            resolved,
            context.validMessageCount());
      }
      if (context.confidence() == null) {
        return rejected(TagSelectionValidationReason.CONFIDENCE_REQUIRED, category, resolved, null);
      }
      if (context.confidence().compareTo(BigDecimal.ZERO) < 0
          || context.confidence().compareTo(BigDecimal.ONE) > 0) {
        return rejected(
            TagSelectionValidationReason.CONFIDENCE_OUT_OF_RANGE,
            category,
            resolved,
            context.confidence());
      }
      if (context.confidence().compareTo(category.minConfidence()) < 0) {
        return rejected(
            TagSelectionValidationReason.CONFIDENCE_TOO_LOW,
            category,
            resolved,
            context.confidence());
      }
    }
    if ((purpose == TagCandidatePurpose.IMPORT || purpose == TagCandidatePurpose.FOLLOWUP_RULE)
        && isBlank(context.businessBasis())) {
      return rejected(
          TagSelectionValidationReason.BUSINESS_BASIS_REQUIRED,
          category,
          resolved,
          context.businessBasis());
    }
    return TagSelectionValidationResult.accepted(category, resolved);
  }

  private TagSelectionValidationResult rejected(
      TagSelectionValidationReason reason,
      TagCategory category,
      List<TagValue> values,
      Object rejectedInput) {
    return TagSelectionValidationResult.rejected(reason, category, values, rejectedInput);
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
