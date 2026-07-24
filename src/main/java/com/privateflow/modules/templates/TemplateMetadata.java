package com.privateflow.modules.templates;

import java.util.List;

public record TemplateMetadata(
    String channelCode,
    String scene,
    String leadType,
    List<String> labels
) {

  public TemplateMetadata {
    labels = labels == null ? List.of() : List.copyOf(labels);
  }
}
