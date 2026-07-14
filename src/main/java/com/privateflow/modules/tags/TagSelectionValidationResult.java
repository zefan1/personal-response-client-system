package com.privateflow.modules.tags;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record TagSelectionValidationResult(
    boolean accepted,
    TagSelectionValidationReason reason,
    String message,
    TagCategory category,
    List<TagValue> values,
    Object rejectedInput
) {

  public TagSelectionValidationResult {
    values = values == null ? List.of() : List.copyOf(values);
    rejectedInput = protect(rejectedInput);
  }

  public String reasonCode() {
    return reason.name();
  }

  @Override
  public Object rejectedInput() {
    return protect(rejectedInput);
  }

  static TagSelectionValidationResult accepted(TagCategory category, List<TagValue> values) {
    return create(TagSelectionValidationReason.ACCEPTED, category, values, null);
  }

  static TagSelectionValidationResult rejected(
      TagSelectionValidationReason reason,
      TagCategory category,
      List<TagValue> values,
      Object rejectedInput) {
    return create(reason, category, values, rejectedInput);
  }

  private static TagSelectionValidationResult create(
      TagSelectionValidationReason reason,
      TagCategory category,
      List<TagValue> values,
      Object rejectedInput) {
    return new TagSelectionValidationResult(
        reason == TagSelectionValidationReason.ACCEPTED,
        reason,
        rejectedInput == null ? reason.message() : reason.message() + "，失败输入：" + rejectedInput,
        category,
        values,
        rejectedInput);
  }

  private static Object protect(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof List<?> list) {
      List<Object> copy = new ArrayList<>(list.size());
      list.forEach(item -> copy.add(protect(item)));
      return Collections.unmodifiableList(copy);
    }
    if (value instanceof Set<?> set) {
      Set<Object> copy = new LinkedHashSet<>();
      set.forEach(item -> copy.add(protect(item)));
      return Collections.unmodifiableSet(copy);
    }
    if (value instanceof Map<?, ?> map) {
      Map<Object, Object> copy = new LinkedHashMap<>();
      map.forEach((key, item) -> copy.put(protect(key), protect(item)));
      return Collections.unmodifiableMap(copy);
    }
    if (value.getClass().isArray()) {
      int length = Array.getLength(value);
      Object copy = Array.newInstance(value.getClass().getComponentType(), length);
      for (int index = 0; index < length; index++) {
        Array.set(copy, index, protect(Array.get(value, index)));
      }
      return copy;
    }
    return value;
  }
}
