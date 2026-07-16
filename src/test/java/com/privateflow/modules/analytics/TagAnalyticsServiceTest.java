package com.privateflow.modules.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.Role;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.auth.AuthUser;
import com.privateflow.modules.customer.admin.CustomerAccessScope;
import com.privateflow.modules.customer.admin.CustomerAccessScopeResolver;
import com.privateflow.modules.customer.admin.CustomerFilter;
import com.privateflow.modules.customer.admin.CustomerFilterQueryBuilder;
import com.privateflow.modules.customer.admin.CustomerFilterValidator;
import com.privateflow.modules.customer.admin.CustomerQuerySpec;
import com.privateflow.modules.customer.admin.CustomerSearchRequest;
import com.privateflow.modules.tags.TagCandidateBuilder;
import com.privateflow.modules.tags.TagCandidatePurpose;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TagAnalyticsServiceTest {

  private TagAnalyticsRepository repository;
  private TagAnalyticsService service;

  @BeforeEach
  void setUp() {
    TagCandidateBuilder candidates = mock(TagCandidateBuilder.class);
    when(candidates.build(TagCandidatePurpose.FILTER)).thenReturn(List.of());
    CustomerAccessScopeResolver accessScopeResolver = mock(CustomerAccessScopeResolver.class);
    when(accessScopeResolver.currentScope()).thenReturn(CustomerAccessScope.all());
    repository = mock(TagAnalyticsRepository.class);
    service = new TagAnalyticsService(
        new CustomerFilterValidator(candidates),
        accessScopeResolver,
        new CustomerFilterQueryBuilder(),
        repository,
        Clock.fixed(Instant.parse("2026-07-16T04:00:00Z"), ZoneId.of("Asia/Shanghai")));
    AuthContext.set(new AuthUser("admin", "管理员", Role.ADMIN, null));
  }

  @AfterEach
  void tearDown() {
    AuthContext.clear();
  }

  @Test
  void defaultsToSevenDaysAndIntersectsTeamWithExplicitEmployees() {
    CustomerSearchRequest filter = new CustomerSearchRequest(
        null, List.of("企微"), null, List.of("keeper-2", "keeper-3"),
        null, null, null, null, null, null, null, null, null, null, null);
    TagAnalyticsRequest request = new TagAnalyticsRequest(filter, List.of(9L), null, null, null);
    when(repository.resolveEnabledKeeperPhones(List.of(9L)))
        .thenReturn(List.of("keeper-1", "keeper-2"));
    TagAnalyticsWindow expectedWindow = defaultWindow();
    when(repository.analyze(any(), any(), eq(expectedWindow)))
        .thenReturn(TagAnalyticsResponse.empty(expectedWindow));

    service.analyze(request);

    ArgumentCaptor<CustomerQuerySpec> dataSpec = ArgumentCaptor.forClass(CustomerQuerySpec.class);
    verify(repository).analyze(dataSpec.capture(), any(), eq(expectedWindow));
    assertThat(dataSpec.getValue().whereClause()).contains("c.source_channel IN (?)");
    assertThat(dataSpec.getValue().args()).contains("企微", "keeper-2");
    assertThat(dataSpec.getValue().args()).doesNotContain("keeper-1", "keeper-3");
  }

  @Test
  void emptyTeamEmployeeIntersectionProducesNoMatchSpec() {
    when(repository.resolveEnabledKeeperPhones(List.of(9L))).thenReturn(List.of("keeper-1"));
    TagAnalyticsRequest request = new TagAnalyticsRequest(
        requestFilter(List.of("keeper-2")), List.of(9L), null, null, TagTrendGranularity.DAY);
    TagAnalyticsWindow window = defaultWindow();
    when(repository.analyze(any(), any(), eq(window))).thenReturn(TagAnalyticsResponse.empty(window));

    service.analyze(request);

    ArgumentCaptor<CustomerQuerySpec> spec = ArgumentCaptor.forClass(CustomerQuerySpec.class);
    verify(repository).analyze(spec.capture(), any(), eq(window));
    assertThat(spec.getValue().whereClause()).isEqualTo(" WHERE 1=0");
  }

  @Test
  void rejectsNonAdminAndRangesLongerThanNinetyDays() {
    AuthContext.set(new AuthUser("keeper-1", "管家", Role.KEEPER, null));
    assertThatThrownBy(() -> service.analyze(new TagAnalyticsRequest(null, List.of(), null, null, null)))
        .isInstanceOf(ApiException.class)
        .satisfies(error -> assertThat(((ApiException) error).getErrorCode())
            .isEqualTo(ApiErrorCodes.FORBIDDEN));

    AuthContext.set(new AuthUser("admin", "管理员", Role.ADMIN, null));
    assertThatThrownBy(() -> service.analyze(new TagAnalyticsRequest(
        null, List.of(), LocalDateTime.of(2026, 1, 1, 0, 0),
        LocalDateTime.of(2026, 7, 16, 0, 0), TagTrendGranularity.DAY)))
        .isInstanceOf(ApiException.class)
        .satisfies(error -> assertThat(((ApiException) error).getErrorCode())
            .isEqualTo(ApiErrorCodes.BAD_REQUEST));
  }

  private CustomerSearchRequest requestFilter(List<String> keepers) {
    return new CustomerSearchRequest(
        null, null, null, keepers, null, null, null,
        null, null, null, null, null, null, null, null);
  }

  private TagAnalyticsWindow defaultWindow() {
    return new TagAnalyticsWindow(
        LocalDateTime.of(2026, 7, 9, 12, 0),
        LocalDateTime.of(2026, 7, 16, 12, 0),
        TagTrendGranularity.DAY);
  }
}
