package com.privateflow.modules.tags;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.infra.CustomerRepository;
import com.privateflow.modules.customer.service.CustomerAccessService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class CustomerTagQueryServiceTest {

  private final CustomerTagFoundationRepository tagRepository =
      mock(CustomerTagFoundationRepository.class);
  private final CustomerRepository customerRepository = mock(CustomerRepository.class);
  private final CustomerAccessService accessService = mock(CustomerAccessService.class);
  private final CustomerTagQueryService service =
      new CustomerTagQueryService(tagRepository, customerRepository, accessService);

  @Test
  void currentReloadsCustomerBeforeAccessAndReturnsImmutableJoinedDetails() {
    Customer storedCustomer = customer(7L, "real-keeper");
    List<CustomerTagQueryDto> repositoryResult = new ArrayList<>(List.of(tagDetail()));
    when(customerRepository.findById(7L)).thenReturn(Optional.of(storedCustomer));
    when(accessService.canAccess(storedCustomer)).thenReturn(true);
    when(tagRepository.findCurrentTagDetails(7L)).thenReturn(repositoryResult);

    List<CustomerTagQueryDto> result = service.current(7L);

    assertThat(result).containsExactly(tagDetail());
    assertThatThrownBy(() -> result.add(tagDetail()))
        .isInstanceOf(UnsupportedOperationException.class);
    verify(customerRepository).findById(7L);
    verify(accessService).canAccess(storedCustomer);
    verify(tagRepository).findCurrentTagDetails(7L);
  }

  @ParameterizedTest
  @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
  void rejectsInvalidCustomerIdBeforeDatabaseOrAccess(long customerId) {
    assertThatThrownBy(() -> service.current(customerId))
        .isInstanceOfSatisfying(ApiException.class, ex -> {
          assertThat(ex.getErrorCode()).isEqualTo(ApiErrorCodes.BAD_REQUEST);
          assertThat(ex.getMessage()).isEqualTo("客户编号必须大于 0");
        });

    verifyNoInteractions(customerRepository, accessService, tagRepository);
  }

  @Test
  void rejectsMissingCustomerBeforeAccessOrTagQuery() {
    when(customerRepository.findById(7L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.history(7L, 10))
        .isInstanceOfSatisfying(ApiException.class, ex -> {
          assertThat(ex.getErrorCode()).isEqualTo(ApiErrorCodes.BAD_REQUEST);
          assertThat(ex.getMessage()).isEqualTo("客户不存在：7");
        });

    verify(customerRepository).findById(7L);
    verifyNoInteractions(accessService, tagRepository);
  }

  @Test
  void forbiddenCustomerDoesNotReachTagRepository() {
    Customer storedCustomer = customer(7L, "real-keeper");
    when(customerRepository.findById(7L)).thenReturn(Optional.of(storedCustomer));
    when(accessService.canAccess(storedCustomer)).thenReturn(false);

    assertThatThrownBy(() -> service.current(7L))
        .isInstanceOfSatisfying(ApiException.class, ex -> {
          assertThat(ex.getErrorCode()).isEqualTo(ApiErrorCodes.FORBIDDEN);
          assertThat(ex.getMessage()).isEqualTo("无权查看该客户标签");
        });

    verify(customerRepository).findById(7L);
    verify(accessService).canAccess(storedCustomer);
    verifyNoInteractions(tagRepository);
  }

  @ParameterizedTest
  @MethodSource("historyLimits")
  void clampsHistoryLimitBeforeRepositoryCall(int requestedLimit, int expectedLimit) {
    Customer storedCustomer = customer(7L, "real-keeper");
    when(customerRepository.findById(7L)).thenReturn(Optional.of(storedCustomer));
    when(accessService.canAccess(storedCustomer)).thenReturn(true);
    when(tagRepository.findTagHistoryDetails(7L, expectedLimit)).thenReturn(List.of(tagDetail()));

    assertThat(service.history(7L, requestedLimit)).containsExactly(tagDetail());

    verify(tagRepository).findTagHistoryDetails(7L, expectedLimit);
  }

  private static Stream<Arguments> historyLimits() {
    return Stream.of(
        Arguments.of(-10, 1),
        Arguments.of(0, 1),
        Arguments.of(1, 1),
        Arguments.of(25, 25),
        Arguments.of(1000, 1000),
        Arguments.of(1001, 1000),
        Arguments.of(Integer.MAX_VALUE, 1000));
  }

  private Customer customer(long id, String assignedKeeper) {
    Customer customer = new Customer();
    customer.setId(id);
    customer.setAssignedKeeper(assignedKeeper);
    return customer;
  }

  private CustomerTagQueryDto tagDetail() {
    return new CustomerTagQueryDto(
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
        LocalDateTime.of(2026, 7, 14, 10, 5));
  }
}
