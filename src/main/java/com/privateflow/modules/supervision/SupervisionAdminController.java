package com.privateflow.modules.supervision;

import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.Role;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.auth.AuthUser;
import com.privateflow.modules.match.ApiResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/api/v1/supervision")
public class SupervisionAdminController {

  private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

  private final SupervisionMetricsService metricsService;
  private final SupervisionAdminReadService readService;

  public SupervisionAdminController(
      SupervisionMetricsService metricsService,
      SupervisionAdminReadService readService) {
    this.metricsService = metricsService;
    this.readService = readService;
  }

  @GetMapping("/metrics")
  public ApiResponse<Map<String, Object>> metrics(
      @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(value = "operator", required = false) String operator,
      @RequestParam(value = "channel", required = false) String channel,
      @RequestParam(value = "leadSource", required = false) String leadSource) {
    requireAdministrator();
    return ApiResponse.ok(Map.of("metrics", metricsService.report(metricsQuery(from, to, operator, channel, leadSource))));
  }

  @GetMapping("/events")
  public ApiResponse<SupervisionEventPage> events(
      @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(value = "operator", required = false) String operator,
      @RequestParam(value = "channel", required = false) String channel,
      @RequestParam(value = "leadSource", required = false) String leadSource,
      @RequestParam(value = "eventType", required = false) SupervisionEventType eventType,
      @RequestParam(value = "page", defaultValue = "1") int page,
      @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
    requireAdministrator();
    DateRange range = dateRange(from, to);
    return ApiResponse.ok(readService.events(new SupervisionEventQuery(
        range.from().atStartOfDay(),
        range.toInclusive().plusDays(1).atStartOfDay(),
        operator,
        channel,
        leadSource,
        eventType,
        page,
        pageSize)));
  }

  @GetMapping("/metadata")
  public ApiResponse<SupervisionMetadata> metadata() {
    requireAdministrator();
    return ApiResponse.ok(readService.metadata());
  }

  private SupervisionMetricsQuery metricsQuery(
      LocalDate from,
      LocalDate to,
      String operator,
      String channel,
      String leadSource) {
    DateRange range = dateRange(from, to);
    return new SupervisionMetricsQuery(
        range.from().atStartOfDay(),
        range.toInclusive().plusDays(1).atStartOfDay(),
        operator,
        channel,
        leadSource);
  }

  private DateRange dateRange(LocalDate from, LocalDate to) {
    LocalDate today = LocalDate.now(BUSINESS_ZONE);
    LocalDate actualTo = to == null ? today : to;
    LocalDate actualFrom = from == null ? actualTo.minusDays(29) : from;
    if (actualFrom.isAfter(actualTo)) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "from date must be before or equal to to date");
    }
    return new DateRange(actualFrom, actualTo);
  }

  private void requireAdministrator() {
    AuthUser user = AuthContext.current();
    if (user == null) {
      throw new ApiException(ApiErrorCodes.AUTH_FAILED, "authentication is required");
    }
    if (user.role() != Role.ADMIN) {
      throw new ApiException(ApiErrorCodes.FORBIDDEN, "administrator permission is required");
    }
  }

  private record DateRange(LocalDate from, LocalDate toInclusive) {
  }
}
