package com.privateflow.modules.tags;

public enum TagSelectionValidationReason {
  ACCEPTED("标签选择校验通过"),
  CATEGORY_NOT_FOUND("标签分类不存在"),
  CATEGORY_DISABLED("标签分类已停用"),
  CATEGORY_MERGED("标签分类已合并"),
  PURPOSE_REQUIRED("标签来源或用途不能为空"),
  VALUE_NOT_FOUND("标签值不存在"),
  VALUE_DISABLED("标签值已停用"),
  VALUE_MERGED("标签值已合并"),
  VALUE_CATEGORY_MISMATCH("标签值不属于所选分类"),
  PURPOSE_NOT_ALLOWED("当前来源或用途不允许选择该标签"),
  SINGLE_VALUE_COUNT_INVALID("单选分类必须且只能选择一个标签值"),
  MULTI_VALUE_REQUIRED("多选分类至少需要选择一个标签值"),
  DUPLICATE_VALUES("标签值不能重复"),
  EVIDENCE_REQUIRED("系统推断必须提供非空证据"),
  EVIDENCE_MESSAGES_INSUFFICIENT("有效证据消息数未达到分类要求"),
  CONFIDENCE_REQUIRED("系统推断必须提供置信度"),
  CONFIDENCE_OUT_OF_RANGE("系统推断置信度必须在 0 到 1 之间"),
  CONFIDENCE_TOO_LOW("系统推断置信度未达到分类阈值"),
  BUSINESS_BASIS_REQUIRED("当前来源必须提供非空业务依据");

  private final String message;

  TagSelectionValidationReason(String message) {
    this.message = message;
  }

  public String message() {
    return message;
  }
}
