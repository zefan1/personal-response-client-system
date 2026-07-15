package com.privateflow.modules.profile;

import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.tags.CustomerTagCategoryLock;
import com.privateflow.modules.tags.CustomerTagQueryDto;
import com.privateflow.modules.tags.TagCategory;
import java.util.List;

public record CustomerProfileView(
    Customer customer,
    String phoneFull,
    List<ProfileSuggestion> pendingSuggestions,
    List<CustomerTagQueryDto> currentTags,
    List<CustomerTagCategoryLock> tagLocks,
    List<TagCategory> editableTagCategories
) {

  public CustomerProfileView {
    pendingSuggestions = pendingSuggestions == null ? List.of() : List.copyOf(pendingSuggestions);
    currentTags = currentTags == null ? List.of() : List.copyOf(currentTags);
    tagLocks = tagLocks == null ? List.of() : List.copyOf(tagLocks);
    editableTagCategories = editableTagCategories == null ? List.of() : List.copyOf(editableTagCategories);
  }

  public CustomerProfileView(
      Customer customer,
      String phoneFull,
      List<ProfileSuggestion> pendingSuggestions) {
    this(customer, phoneFull, pendingSuggestions, List.of(), List.of(), List.of());
  }
}
