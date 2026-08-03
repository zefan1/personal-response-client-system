package com.privateflow.modules.tablewrite.service;

import com.privateflow.modules.tablewrite.client.WecomSmartSheetField;
import com.privateflow.modules.tablewrite.client.WecomSmartSheetFieldCatalog;
import com.privateflow.modules.tablewrite.infra.TableFieldMappingResolver;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ProfileProjectionFieldFilter {

  private final TableFieldMappingResolver mappingResolver;
  private final WecomSmartSheetFieldCatalog fieldCatalog;

  public ProfileProjectionFieldFilter(
      TableFieldMappingResolver mappingResolver,
      WecomSmartSheetFieldCatalog fieldCatalog) {
    this.mappingResolver = mappingResolver;
    this.fieldCatalog = fieldCatalog;
  }

  public Result filter(String sourceTable, Map<String, Object> internalFields, Duration timeout) {
    Map<String, Object> mapped = mappingResolver.toSourceFields(sourceTable, internalFields);
    Map<String, WecomSmartSheetField> visibleFields = fieldCatalog.visibleFields(timeout);
    Map<String, Object> accepted = new LinkedHashMap<>();
    List<String> skippedSelectableFields = new ArrayList<>();
    for (Map.Entry<String, Object> entry : mapped.entrySet()) {
      WecomSmartSheetField field = visibleFields.get(entry.getKey());
      if (isSelectable(field) && !isAllowedOption(field, entry.getValue())) {
        skippedSelectableFields.add(entry.getKey());
        continue;
      }
      accepted.put(entry.getKey(), entry.getValue());
    }
    return new Result(Map.copyOf(accepted), List.copyOf(skippedSelectableFields));
  }

  private boolean isSelectable(WecomSmartSheetField field) {
    return field != null && ("FIELD_TYPE_SINGLE_SELECT".equals(field.type())
        || "FIELD_TYPE_SELECT".equals(field.type()));
  }

  private boolean isAllowedOption(WecomSmartSheetField field, Object value) {
    return value instanceof String text && field.optionId(text).isPresent();
  }

  public record Result(Map<String, Object> fields, List<String> skippedSelectableFields) {
  }
}
