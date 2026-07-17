package com.privateflow.modules.tags;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class TagExchangeService {

  private final TagDirectoryService directoryService;
  private final TagSelectionValidator selectionValidator;

  public TagExchangeService(
      TagDirectoryService directoryService,
      TagSelectionValidator selectionValidator) {
    this.directoryService = directoryService;
    this.selectionValidator = selectionValidator;
  }

  public TagExchangeResult prepareInbound(
      TagExchangeSourceType sourceType,
      String sourceRecordId,
      Map<String, ?> fields) {
    return prepare(sourceType, sourceRecordId, fields, false);
  }

  public TagExchangeResult prepareOutbound(
      TagExchangeSourceType sourceType,
      String sourceRecordId,
      Map<String, ?> fields) {
    return prepare(sourceType, sourceRecordId, fields, true);
  }

  private TagExchangeResult prepare(
      TagExchangeSourceType sourceType,
      String sourceRecordId,
      Map<String, ?> fields,
      boolean outbound) {
    if (sourceType == null || fields == null || fields.isEmpty()) {
      return new TagExchangeResult(Map.of(), List.of(), List.of());
    }
    TagDirectorySnapshot snapshot = directoryService.getSnapshot();
    Map<String, Object> accepted = new LinkedHashMap<>();
    Set<String> filtered = new LinkedHashSet<>();
    List<TagExchangeUnmatchedValue> unmatched = new ArrayList<>();
    for (Map.Entry<String, ?> entry : fields.entrySet()) {
      String field = entry.getKey();
      Object raw = entry.getValue();
      TagCategory category = findBoundCategory(snapshot, field);
      if (category == null) {
        if (raw != null) {
          accepted.put(field, raw);
        }
        continue;
      }
      String rawValue = raw == null ? "" : String.valueOf(raw).trim();
      if (rawValue.isBlank()) {
        if (outbound) {
          filtered.add(field);
        }
        continue;
      }
      List<String> tokens = splitTokens(rawValue, category.selectionMode());
      List<String> resolvedCodes = new ArrayList<>();
      List<String> unresolvedTokens = new ArrayList<>();
      for (String token : tokens) {
        TagValue resolved = resolve(category, token);
        if (resolved == null) {
          unresolvedTokens.add(token);
        } else {
          resolvedCodes.add(resolved.tagValue());
        }
      }

      TagSelectionValidationResult validation = resolvedCodes.isEmpty()
          ? null
          : selectionValidator.validateCodes(
              TagCandidatePurpose.IMPORT,
              category.categoryKey(),
              resolvedCodes,
              new TagSelectionContext(null, 0, null, businessBasis(sourceType, sourceRecordId, field)));
      boolean acceptedValues = validation != null && validation.accepted();
      if (!unresolvedTokens.isEmpty() || !acceptedValues) {
        List<String> rejected = new ArrayList<>(unresolvedTokens);
        if (!acceptedValues && validation != null && validation.rejectedInput() != null) {
          String rejectedInput = String.valueOf(validation.rejectedInput());
          if (!rejected.contains(rejectedInput)) {
            rejected.add(rejectedInput);
          }
        }
        if (rejected.isEmpty()) {
          rejected.addAll(tokens);
        }
        unmatched.add(new TagExchangeUnmatchedValue(
            field,
            rawValue,
            rejected,
            category.id(),
            sourceType,
            sourceRecordId));
        if (outbound) {
          filtered.add(field);
        }
      }
      if (acceptedValues) {
        String normalized = validation.values().stream()
            .map(TagValue::tagValue)
            .reduce((left, right) -> left + "," + right)
            .orElse("");
        if (!normalized.isBlank()) {
          accepted.put(field, normalized);
        }
      }
    }
    return new TagExchangeResult(accepted, List.copyOf(filtered), unmatched);
  }

  private TagCategory findBoundCategory(TagDirectorySnapshot snapshot, String boundField) {
    if (boundField == null) {
      return null;
    }
    return snapshot.categories().stream()
        .filter(category -> boundField.equals(category.boundField()))
        .findFirst()
        .orElse(null);
  }

  private TagValue resolve(TagCategory category, String token) {
    List<TagValue> matches = category.values().stream()
        .filter(value -> token.equals(value.tagValue())
            || token.equals(value.displayName())
            || value.synonyms().contains(token))
        .toList();
    return matches.size() == 1 ? matches.get(0) : null;
  }

  private List<String> splitTokens(String rawValue, TagSelectionMode selectionMode) {
    if (selectionMode == TagSelectionMode.SINGLE) {
      return List.of(rawValue);
    }
    String normalized = rawValue
        .replace('，', ',')
        .replace('、', ',')
        .replace(';', ',')
        .replace('|', ',')
        .replace('\r', ',')
        .replace('\n', ',')
        .replace('\t', ',');
    return Arrays.stream(normalized.split(",", -1))
        .map(String::trim)
        .filter(token -> !token.isBlank())
        .toList();
  }

  private String businessBasis(
      TagExchangeSourceType sourceType,
      String sourceRecordId,
      String field) {
    return "tag exchange source=" + sourceType + ", record=" + sourceRecordId + ", field=" + field;
  }
}
