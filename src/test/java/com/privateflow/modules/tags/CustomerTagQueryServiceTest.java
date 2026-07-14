package com.privateflow.modules.tags;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.service.CustomerAccessService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class CustomerTagQueryServiceTest {

  private final CustomerTagFoundationRepository repository =
      mock(CustomerTagFoundationRepository.class);
  private final TagDirectoryService directoryService = mock(TagDirectoryService.class);
  private final CustomerAccessService accessService = mock(CustomerAccessService.class);
  private final CustomerTagQueryService service =
      new CustomerTagQueryService(repository, directoryService, accessService);

  @Test
  void currentMapsCompleteAssignmentAndDirectoryMetadataAndReturnsImmutableList() {
    Customer customer = customer(7L);
    CustomerTagAssignment assignment = assignment(true, null, null);
    TagCategory category = category(true, null, List.of(value(true, null)));
    when(accessService.canAccess(customer)).thenReturn(true);
    when(repository.findCurrentAssignments(7L)).thenReturn(List.of(assignment));
    when(directoryService.getSnapshot()).thenReturn(snapshot(category));

    List<CustomerTagQueryDto> result = service.current(customer);

    assertThat(result).containsExactly(new CustomerTagQueryDto(
        101L,
        7L,
        3,
        10L,
        "intent_level",
        "意向等级",
        TagSelectionMode.SINGLE,
        true,
        null,
        4,
        20L,
        "HIGH",
        "高意向",
        true,
        null,
        5,
        TagSelectionMode.SINGLE,
        true,
        "SKILL",
        new BigDecimal("0.9300"),
        "客户连续询问价格和到店时间",
        6,
        301L,
        "profile-analysis",
        "prod",
        "gpt-5.1",
        "prompt-v3",
        "keeper-13800000000",
        true,
        "leader-13900000000",
        LocalDateTime.of(2026, 7, 14, 9, 0),
        99L,
        null,
        null,
        LocalDateTime.of(2026, 7, 14, 10, 0),
        LocalDateTime.of(2026, 7, 14, 10, 5)));
    assertThatThrownBy(() -> result.add(result.get(0)))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void historyKeepsInactiveAssignmentsAndDisabledMergedDirectoryState() {
    Customer customer = customer(7L);
    CustomerTagAssignment assignment = assignment(
        false,
        "标签被新判断替代",
        LocalDateTime.of(2026, 7, 15, 8, 30));
    TagCategory category = category(false, 110L, List.of(value(false, 120L)));
    when(accessService.canAccess(customer)).thenReturn(true);
    when(repository.findAssignmentHistory(7L, 25)).thenReturn(List.of(assignment));
    when(directoryService.getSnapshot()).thenReturn(snapshot(category));

    List<CustomerTagQueryDto> result = service.history(customer, 25);

    assertThat(result).singleElement().satisfies(item -> {
      assertThat(item.active()).isFalse();
      assertThat(item.invalidatedReason()).isEqualTo("标签被新判断替代");
      assertThat(item.invalidatedAt()).isEqualTo(LocalDateTime.of(2026, 7, 15, 8, 30));
      assertThat(item.categoryEnabled()).isFalse();
      assertThat(item.categoryMergedIntoId()).isEqualTo(110L);
      assertThat(item.tagValueEnabled()).isFalse();
      assertThat(item.tagValueMergedIntoId()).isEqualTo(120L);
      assertThat(item.categoryName()).isEqualTo("意向等级");
      assertThat(item.tagDisplayName()).isEqualTo("高意向");
    });
    verify(repository).findAssignmentHistory(7L, 25);
  }

  @Test
  void rejectsNullCustomerBeforeAccessOrRepositoryCalls() {
    assertThatThrownBy(() -> service.current(null))
        .isInstanceOfSatisfying(ApiException.class, ex -> {
          assertThat(ex.getErrorCode()).isEqualTo(ApiErrorCodes.BAD_REQUEST);
          assertThat(ex.getMessage()).isEqualTo("客户不能为空");
        });

    verifyNoInteractions(accessService, repository, directoryService);
  }

  @Test
  void rejectsCustomerWithoutIdBeforeAccessOrRepositoryCalls() {
    Customer customer = new Customer();

    assertThatThrownBy(() -> service.history(customer, 10))
        .isInstanceOfSatisfying(ApiException.class, ex -> {
          assertThat(ex.getErrorCode()).isEqualTo(ApiErrorCodes.BAD_REQUEST);
          assertThat(ex.getMessage()).isEqualTo("客户编号不能为空");
        });

    verifyNoInteractions(accessService, repository, directoryService);
  }

  @Test
  void forbiddenCustomerDoesNotReachRepositoryOrDirectory() {
    Customer customer = customer(7L);
    when(accessService.canAccess(customer)).thenReturn(false);

    assertThatThrownBy(() -> service.current(customer))
        .isInstanceOfSatisfying(ApiException.class, ex -> {
          assertThat(ex.getErrorCode()).isEqualTo(ApiErrorCodes.FORBIDDEN);
          assertThat(ex.getMessage()).isEqualTo("无权查看该客户标签");
        });

    verify(accessService).canAccess(customer);
    verifyNoInteractions(repository);
    verify(directoryService, never()).getSnapshot();
  }

  @Test
  void failsFastWhenAssignmentCategoryIsMissingFromDirectory() {
    Customer customer = customer(7L);
    when(accessService.canAccess(customer)).thenReturn(true);
    when(repository.findCurrentAssignments(7L)).thenReturn(List.of(assignment(true, null, null)));
    when(directoryService.getSnapshot()).thenReturn(
        TagDirectorySnapshot.empty(Instant.parse("2026-07-15T02:00:00Z")));

    assertThatThrownBy(() -> service.current(customer))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("标签目录缺少分类：10");
  }

  @Test
  void failsFastWhenAssignmentValueIsMissingFromDirectory() {
    Customer customer = customer(7L);
    when(accessService.canAccess(customer)).thenReturn(true);
    when(repository.findCurrentAssignments(7L)).thenReturn(List.of(assignment(true, null, null)));
    when(directoryService.getSnapshot()).thenReturn(snapshot(category(true, null, List.of())));

    assertThatThrownBy(() -> service.current(customer))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("标签目录缺少标签值：20");
  }

  private Customer customer(long id) {
    Customer customer = new Customer();
    customer.setId(id);
    return customer;
  }

  private CustomerTagAssignment assignment(
      boolean active,
      String invalidatedReason,
      LocalDateTime invalidatedAt) {
    return new CustomerTagAssignment(
        101L,
        7L,
        10L,
        20L,
        TagSelectionMode.SINGLE,
        active,
        "SKILL",
        new BigDecimal("0.9300"),
        "客户连续询问价格和到店时间",
        6,
        301L,
        "profile-analysis",
        "prod",
        "gpt-5.1",
        "prompt-v3",
        "keeper-13800000000",
        true,
        "leader-13900000000",
        LocalDateTime.of(2026, 7, 14, 9, 0),
        99L,
        3,
        invalidatedReason,
        invalidatedAt,
        LocalDateTime.of(2026, 7, 14, 10, 0),
        LocalDateTime.of(2026, 7, 14, 10, 5),
        active ? 20L : null,
        active ? 10L : null);
  }

  private TagCategory category(boolean enabled, Long mergedIntoId, List<TagValue> values) {
    return new TagCategory(
        10L,
        "intent_level",
        "意向等级",
        "用于判断客户当前购买意向",
        "intentLevel",
        TagSelectionMode.SINGLE,
        true,
        true,
        TagAutoUpdateMode.REPLACE,
        new BigDecimal("0.8500"),
        2,
        24,
        TagUncertainPolicy.KEEP_CURRENT,
        true,
        true,
        true,
        true,
        true,
        enabled,
        1,
        mergedIntoId,
        4,
        values,
        TagImpact.empty(),
        LocalDateTime.of(2026, 7, 1, 10, 0),
        LocalDateTime.of(2026, 7, 13, 10, 0));
  }

  private TagValue value(boolean enabled, Long mergedIntoId) {
    return new TagValue(
        20L,
        10L,
        "intent_level",
        "HIGH",
        "高意向",
        "客户近期有明确购买倾向",
        "主动询价或预约",
        "只有泛泛咨询",
        "询问本周到店时间",
        "仅点赞未沟通",
        List.of("强意向"),
        true,
        true,
        enabled,
        1,
        mergedIntoId,
        5,
        TagImpact.empty(),
        LocalDateTime.of(2026, 7, 1, 10, 0),
        LocalDateTime.of(2026, 7, 13, 11, 0));
  }

  private TagDirectorySnapshot snapshot(TagCategory category) {
    return TagDirectorySnapshot.from(
        List.of(category),
        Instant.parse("2026-07-15T02:00:00Z"));
  }
}
