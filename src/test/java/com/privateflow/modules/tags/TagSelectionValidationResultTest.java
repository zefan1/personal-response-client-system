package com.privateflow.modules.tags;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TagSelectionValidationResultTest {

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void deeplyCopiesAndProtectsRejectedInputCollectionsAndArrays() {
    List<String> nestedList = new ArrayList<>(List.of("list-original"));
    Set<String> nestedSet = new LinkedHashSet<>(Set.of("set-original"));
    String[] nestedArray = new String[] {"array-original"};
    Map<String, Object> nestedMap = new LinkedHashMap<>();
    nestedMap.put("list", nestedList);
    nestedMap.put("set", nestedSet);
    nestedMap.put("array", nestedArray);
    List<Object> input = new ArrayList<>(List.of(nestedMap));

    TagSelectionValidationResult result = TagSelectionValidationResult.rejected(
        TagSelectionValidationReason.VALUE_NOT_FOUND,
        null,
        List.of(),
        input);

    nestedList.add("list-mutated");
    nestedSet.add("set-mutated");
    nestedArray[0] = "array-mutated";
    nestedMap.put("extra", "map-mutated");
    input.add("root-mutated");

    List<?> protectedRoot = (List<?>) result.rejectedInput();
    Map<Object, Object> protectedMap = (Map<Object, Object>) protectedRoot.get(0);
    assertThat(protectedRoot).hasSize(1);
    assertThat((List<String>) protectedMap.get("list")).containsExactly("list-original");
    assertThat((Set<String>) protectedMap.get("set")).containsExactly("set-original");
    assertThat((String[]) protectedMap.get("array")).containsExactly("array-original");
    assertThat(protectedMap).doesNotContainKey("extra");
    assertThatThrownBy(() -> ((List) protectedRoot).add("blocked"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> ((List) protectedMap.get("list")).add("blocked"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> ((Set) protectedMap.get("set")).add("blocked"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> ((Map) protectedMap).put("blocked", "blocked"))
        .isInstanceOf(UnsupportedOperationException.class);

    ((String[]) protectedMap.get("array"))[0] = "caller-mutated";
    List<?> rereadRoot = (List<?>) result.rejectedInput();
    Map<Object, Object> rereadMap = (Map<Object, Object>) rereadRoot.get(0);
    assertThat((String[]) rereadMap.get("array")).containsExactly("array-original");
  }
}
