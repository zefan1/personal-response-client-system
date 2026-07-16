package com.privateflow.modules.customer.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.privateflow.modules.tags.TagCandidateBuilder;
import com.privateflow.modules.tags.TagCandidatePurpose;
import com.privateflow.modules.tags.TagCategory;
import com.privateflow.modules.tags.TagValue;
import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class CustomerFilterValidatorTest {

  @Test
  void normalizesScalarFiltersAndAppliesDefaults() {
    TagCandidateBuilder candidates = mock(TagCandidateBuilder.class);
    when(candidates.build(TagCandidatePurpose.FILTER)).thenReturn(List.of());
    CustomerFilterValidator validator = new CustomerFilterValidator(candidates);

    CustomerFilter input = new CustomerFilter(
        "  alice  ",
        List.of(" store ", "store", ""),
        List.of(" XIAN_SUO "),
        List.of(),
        List.of(" 门店A "),
        List.of(),
        List.of(" 待联系 "),
        null,
        null,
        List.of(),
        null,
        null,
        null,
        0,
        0);

    CustomerFilter actual = validator.validate(input);

    assertThat(actual.keyword()).isEqualTo("alice");
    assertThat(actual.sourceChannels()).containsExactly("store");
    assertThat(actual.leadTypes()).containsExactly("XIAN_SUO");
    assertThat(actual.intendedStores()).containsExactly("门店A");
    assertThat(actual.customerStages()).containsExactly("待联系");
    assertThat(actual.tagGroupLogic()).isEqualTo(TagGroupLogic.AND);
    assertThat(actual.sortBy()).isEqualTo(CustomerSortField.UPDATED_AT);
    assertThat(actual.sortDirection()).isEqualTo(SortDirection.DESC);
    assertThat(actual.page()).isEqualTo(1);
    assertThat(actual.pageSize()).isEqualTo(20);
  }

  @Test
  void rejectsPageSizeAboveTheAdminLimit() {
    TagCandidateBuilder candidates = mock(TagCandidateBuilder.class);
    when(candidates.build(TagCandidatePurpose.FILTER)).thenReturn(List.of());
    CustomerFilterValidator validator = new CustomerFilterValidator(candidates);

    CustomerFilter input = new CustomerFilter(
        "", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
        null, null, List.of(), TagGroupLogic.AND, CustomerSortField.UPDATED_AT,
        SortDirection.DESC, 1, 51);

    assertThatThrownBy(() -> validator.validate(input))
        .isInstanceOf(ApiException.class)
        .satisfies(error -> assertThat(((ApiException) error).getErrorCode())
            .isEqualTo(ApiErrorCodes.BAD_REQUEST));
  }

  @Test
  void rejectsMultipleValuesForSingleSelectionCategory() {
    LocalDateTime now = LocalDateTime.of(2026, 7, 16, 12, 0);
    TagValue first = new TagValue(101L, 7L, "customer_stage", "NEW", "新客", true, 1, now, now);
    TagValue second = new TagValue(102L, 7L, "customer_stage", "FOLLOWUP", "跟进中", true, 2, now, now);
    TagCategory category = new TagCategory(
        7L, "customer_stage", "客户阶段", "customerStage", false, true, 1,
        List.of(first, second), now, now);
    TagCandidateBuilder candidates = mock(TagCandidateBuilder.class);
    when(candidates.build(TagCandidatePurpose.FILTER)).thenReturn(List.of(category));
    CustomerFilterValidator validator = new CustomerFilterValidator(candidates);

    CustomerFilter input = new CustomerFilter(
        "", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
        null, null,
        List.of(new TagFilterGroup(7L, List.of(101L, 102L), TagMatchMode.ANY)),
        TagGroupLogic.AND, CustomerSortField.UPDATED_AT, SortDirection.DESC, 1, 20);

    assertThatThrownBy(() -> validator.validate(input))
        .isInstanceOf(ApiException.class)
        .satisfies(error -> assertThat(((ApiException) error).getErrorCode())
            .isEqualTo(ApiErrorCodes.BAD_REQUEST));
  }
}
