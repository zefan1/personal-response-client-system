package com.privateflow.modules.analytics;

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
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TagAnalyticsService {

  private static final Duration DEFAULT_WINDOW = Duration.ofDays(7);
  private static final Duration MAX_WINDOW = Duration.ofDays(90);

  private final CustomerFilterValidator filterValidator;
  private final CustomerAccessScopeResolver accessScopeResolver;
  private final CustomerFilterQueryBuilder queryBuilder;
  private final TagAnalyticsRepository repository;
  private final Clock clock;

  @Autowired
  public TagAnalyticsService(
      CustomerFilterValidator filterValidator,
      CustomerAccessScopeResolver accessScopeResolver,
      CustomerFilterQueryBuilder queryBuilder,
      TagAnalyticsRepository repository) {
    this(filterValidator, accessScopeResolver, queryBuilder, repository, Clock.systemDefaultZone());
  }

  TagAnalyticsService(
      CustomerFilterValidator filterValidator,
      CustomerAccessScopeResolver accessScopeResolver,
      CustomerFilterQueryBuilder queryBuilder,
      TagAnalyticsRepository repository,
      Clock clock) {
    this.filterValidator = filterValidator;
    this.accessScopeResolver = accessScopeResolver;
    this.queryBuilder = queryBuilder;
    this.repository = repository;
    this.clock = clock;
  }

  public TagAnalyticsResponse analyze(TagAnalyticsRequest request) {
    requireAdmin();
    TagAnalyticsRequest safe = request == null
        ? new TagAnalyticsRequest(null, List.of(), null, null, null)
        : request;
    TagAnalyticsWindow window = normalizeWindow(safe);
    CustomerFilter filter = filterValidator.validate(
        safe.customerFilter() == null ? CustomerFilter.empty() : safe.customerFilter().toFilter());
    CustomerAccessScope scope = accessScopeResolver.currentScope();
    CustomerQuerySpec optionSpec = queryBuilder.build(CustomerFilter.empty(), scope);
    CustomerQuerySpec dataSpec = dataSpec(filter, safe.teamLeaderIds(), scope);
    return repository.analyze(dataSpec, optionSpec, window);
  }

  private CustomerQuerySpec dataSpec(
      CustomerFilter filter,
      List<Long> rawTeamIds,
      CustomerAccessScope scope) {
    List<Long> teamIds = normalizeTeamIds(rawTeamIds);
    if (teamIds.isEmpty()) {
      return queryBuilder.build(filter, scope);
    }
    List<String> teamKeepers = repository.resolveEnabledKeeperPhones(teamIds);
    List<String> keepers = filter.assignedKeepers().isEmpty()
        ? teamKeepers
        : filter.assignedKeepers().stream().filter(teamKeepers::contains).toList();
    if (keepers.isEmpty()) {
      return new CustomerQuerySpec(" WHERE 1=0", List.of(), "c.id ASC");
    }
    return queryBuilder.build(withKeepers(filter, keepers), scope);
  }

  private TagAnalyticsWindow normalizeWindow(TagAnalyticsRequest request) {
    LocalDateTime now = LocalDateTime.now(clock);
    LocalDateTime from = request.tagFrom();
    LocalDateTime to = request.tagTo();
    if (from == null && to == null) {
      to = now;
      from = to.minus(DEFAULT_WINDOW);
    } else if (from == null) {
      from = to.minus(DEFAULT_WINDOW);
    } else if (to == null) {
      to = from.plus(DEFAULT_WINDOW);
    }
    if (from.isAfter(to) || Duration.between(from, to).compareTo(MAX_WINDOW) > 0) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "tag analytics window must be between 0 and 90 days");
    }
    TagTrendGranularity granularity = request.granularity() == null
        ? TagTrendGranularity.DAY
        : request.granularity();
    return new TagAnalyticsWindow(from, to, granularity);
  }

  private List<Long> normalizeTeamIds(List<Long> values) {
    if (values == null || values.isEmpty()) {
      return List.of();
    }
    if (values.stream().anyMatch(value -> value == null || value <= 0)) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "teamLeaderIds must contain positive ids");
    }
    return values.stream().distinct().toList();
  }

  private CustomerFilter withKeepers(CustomerFilter filter, List<String> keepers) {
    return new CustomerFilter(
        filter.keyword(), filter.sourceChannels(), filter.leadTypes(), keepers,
        filter.intendedStores(), filter.intendedProjects(), filter.customerStages(),
        filter.updatedFrom(), filter.updatedTo(), filter.tagGroups(), filter.tagGroupLogic(),
        filter.sortBy(), filter.sortDirection(), filter.page(), filter.pageSize());
  }

  private void requireAdmin() {
    AuthUser user = AuthContext.current();
    if (user == null || user.role() != Role.ADMIN) {
      throw new ApiException(ApiErrorCodes.FORBIDDEN, "permission denied");
    }
  }
}
