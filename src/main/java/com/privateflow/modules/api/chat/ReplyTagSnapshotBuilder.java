package com.privateflow.modules.api.chat;

import com.privateflow.modules.skill.ReplyTagSnapshot;
import com.privateflow.modules.tags.CustomerTagQueryDto;
import com.privateflow.modules.tags.CustomerTagQueryService;
import com.privateflow.modules.tags.TagCategory;
import com.privateflow.modules.tags.TagDirectoryService;
import com.privateflow.modules.tags.TagDirectorySnapshot;
import com.privateflow.modules.tags.TagValue;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ReplyTagSnapshotBuilder {

  private final CustomerTagQueryService queryService;
  private final TagDirectoryService directoryService;

  public ReplyTagSnapshotBuilder(
      CustomerTagQueryService queryService,
      TagDirectoryService directoryService) {
    this.queryService = queryService;
    this.directoryService = directoryService;
  }

  public List<ReplyTagSnapshot> build(long customerId) {
    List<CustomerTagQueryDto> assignments = queryService.current(customerId);
    if (assignments.isEmpty()) {
      return List.of();
    }

    TagDirectorySnapshot directory = directoryService.getSnapshot();
    List<ReplyTagSnapshot> result = new ArrayList<>();
    for (CustomerTagQueryDto assignment : assignments) {
      TagCategory category = directory.categoriesById().get(assignment.categoryId());
      TagValue value = directory.valuesById().get(assignment.tagValueId());
      if (category == null || value == null || value.categoryId() != category.id()) {
        throw new IllegalStateException("reply tag directory metadata missing");
      }
      if (!category.isEnabled()
          || category.mergedIntoId() != null
          || !category.useForReply()
          || !value.isEnabled()
          || value.mergedIntoId() != null) {
        continue;
      }
      result.add(new ReplyTagSnapshot(
          category.categoryKey(),
          category.categoryName(),
          value.tagValue(),
          value.displayName(),
          value.meaning(),
          assignment.sourceType(),
          assignment.evidenceText(),
          assignment.manualLocked()));
    }
    return List.copyOf(result);
  }
}
