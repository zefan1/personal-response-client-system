package com.privateflow.modules.notices;

import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.Role;
import com.privateflow.modules.api.alert.SystemAlertRepository;
import com.privateflow.modules.api.audit.AuditLogger;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.auth.AuthUser;
import com.privateflow.modules.api.ws.WsMessage;
import com.privateflow.modules.api.ws.WsPushService;
import com.privateflow.modules.customer.infra.SystemConfigRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NoticeService {

  private static final DateTimeFormatter NOTICE_DAY = DateTimeFormatter.BASIC_ISO_DATE;
  private final NoticeRepository repository;
  private final SystemConfigRepository configRepository;
  private final SystemAlertRepository alertRepository;
  private final WsPushService wsPushService;
  private final AuditLogger auditLogger;

  public NoticeService(
      NoticeRepository repository,
      SystemConfigRepository configRepository,
      SystemAlertRepository alertRepository,
      WsPushService wsPushService,
      AuditLogger auditLogger) {
    this.repository = repository;
    this.configRepository = configRepository;
    this.alertRepository = alertRepository;
    this.wsPushService = wsPushService;
    this.auditLogger = auditLogger;
  }

  public Map<String, Object> list(NoticeStatus status, NoticeLevel level, NoticeSource source, String statusFilter, int page, Integer size) {
    requireAdmin();
    Boolean stopped = null;
    NoticeStatus actualStatus = status;
    if ("STOPPED".equalsIgnoreCase(statusFilter)) {
      stopped = true;
      actualStatus = null;
    }
    int safePage = Math.max(1, page);
    int safeSize = Math.max(10, Math.min(size == null ? intConfig("notice.list_page_size", 20) : size, 50));
    long total = repository.count(actualStatus, level, source, stopped);
    return Map.of(
        "items", repository.list(actualStatus, level, source, stopped, safePage, safeSize),
        "total", total,
        "page", safePage,
        "size", safeSize,
        "totalPages", Math.max(1, (int) Math.ceil(total / (double) safeSize)));
  }

  @Transactional
  public SystemNotice create(NoticeCreateRequest request) {
    requireAdmin();
    NoticeCreateRequest normalized = validateCreate(request);
    LocalDateTime now = LocalDateTime.now();
    boolean immediate = normalized.publishType() == PublishType.IMMEDIATE;
    LocalDateTime publishAt = immediate ? now : normalized.publishAt();
    LocalDateTime expireAt = publishAt.plusDays(expireDays(normalized.expireDays()));
    SystemNotice notice = new SystemNotice(
        0L,
        nextNoticeId(false),
        normalized.title().trim(),
        normalized.content().trim(),
        normalized.level(),
        NoticeSource.MANUAL,
        immediate ? NoticeStatus.PUBLISHED : NoticeStatus.SCHEDULED,
        false,
        publishAt,
        immediate ? now : null,
        expireAt,
        null,
        AuthContext.username(),
        null,
        null);
    long id = repository.create(notice);
    SystemNotice saved = require(id);
    auditLogger.log("CREATE_NOTICE", AuthContext.username(), "system_notices", saved.noticeId(), saved.title());
    if (immediate) {
      broadcast(saved);
    }
    return saved;
  }

  @Transactional
  public SystemNotice update(long id, NoticeUpdateRequest request) {
    requireAdmin();
    SystemNotice existing = require(id);
    if (existing.status() != NoticeStatus.SCHEDULED || existing.isStopped()) {
      throw new ApiException(ApiErrorCodes.VERSION_STATUS_INVALID, "只有未停止的定时公告可以编辑");
    }
    NoticeUpdateRequest normalized = validateUpdate(request);
    LocalDateTime publishAt = normalized.publishAt() == null ? existing.publishAt() : normalized.publishAt();
    validateScheduleTime(publishAt);
    LocalDateTime expireAt = publishAt.plusDays(expireDays(normalized.expireDays()));
    NoticeUpdateRequest update = new NoticeUpdateRequest(
        normalized.title(),
        normalized.content(),
        normalized.level(),
        publishAt,
        normalized.expireDays());
    repository.updateScheduled(id, update, expireAt);
    return require(id);
  }

  @Transactional
  public SystemNotice stop(long id) {
    requireAdmin();
    SystemNotice existing = require(id);
    if (existing.isStopped()) {
      throw new ApiException(ApiErrorCodes.VERSION_STATUS_INVALID, "公告已经停止");
    }
    int updated = repository.stop(id);
    if (updated == 0) {
      throw new ApiException(ApiErrorCodes.VERSION_STATUS_INVALID, "当前公告不能停止");
    }
    auditLogger.log("STOP_NOTICE", AuthContext.username(), "system_notices", existing.noticeId(), existing.title());
    return require(id);
  }

  @Transactional
  public void delete(long id) {
    requireAdmin();
    SystemNotice existing = require(id);
    if (!existing.isStopped()) {
      throw new ApiException(ApiErrorCodes.VERSION_STATUS_INVALID, "只有已停止公告可以删除");
    }
    repository.delete(id);
  }

  public List<Map<String, Object>> active() {
    try {
      return repository.active().stream().map(this::desktopPayload).toList();
    } catch (RuntimeException ex) {
      try {
        alertRepository.activate("NOTICE_ACTIVE_FETCH_FAILED", "WARN", "生效公告读取失败", "NOTICE", ex.getMessage());
      } catch (RuntimeException ignored) {
        // Startup notice fetch is fire-and-forget for desktop clients.
      }
      return List.of();
    }
  }

  @Transactional
  public void publishDueNotices() {
    repository.dueScheduled(100).forEach(notice -> {
      repository.markPublished(notice.id());
      SystemNotice published = require(notice.id());
      auditLogger.log("PUBLISH_NOTICE", "SYSTEM", "system_notices", published.noticeId(), published.title());
      broadcast(published);
    });
  }

  @Scheduled(fixedDelayString = "#{@noticeScheduleProperties.scanIntervalMs()}")
  @Transactional
  public void scheduledPublishScan() {
    publishDueNotices();
  }

  @Transactional
  public void createAutoNotice(String title, String content, String level, Duration ttl) {
    String safeTitle = required(title, "title");
    String safeContent = required(content, "content");
    validateLength(safeTitle, safeContent);
    NoticeLevel safeLevel = parseLevel(level);
    if (repository.activeAutoContentExists(safeContent)) {
      return;
    }
    LocalDateTime now = LocalDateTime.now();
    long hours = ttl == null || ttl.isZero() || ttl.isNegative()
        ? intConfig("notice.auto_expire_hours", 1)
        : Math.min(Math.max(1, ttl.toHours()), 24);
    SystemNotice notice = new SystemNotice(
        0L,
        nextNoticeId(true),
        safeTitle,
        safeContent,
        safeLevel,
        NoticeSource.AUTO,
        NoticeStatus.PUBLISHED,
        false,
        now,
        now,
        now.plusHours(hours),
        null,
        "SYSTEM",
        null,
        null);
    long id = repository.create(notice);
    broadcast(require(id));
  }

  @Transactional
  public void stopAutoNotice(String contentKeyword) {
    if (contentKeyword == null || contentKeyword.isBlank()) {
      return;
    }
    repository.stopAutoByContentKeyword(contentKeyword.trim());
  }

  private NoticeCreateRequest validateCreate(NoticeCreateRequest request) {
    if (request == null || request.level() == null || request.publishType() == null) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "请填写公告标题、内容、级别和发布方式");
    }
    String title = required(request.title(), "title");
    String content = required(request.content(), "content");
    validateLength(title, content);
    if (request.publishType() == PublishType.SCHEDULED) {
      if (request.publishAt() == null) {
        throw new ApiException(ApiErrorCodes.BAD_REQUEST, "定时公告必须填写发布时间");
      }
      validateScheduleTime(request.publishAt());
    }
    validateExpireDays(request.expireDays());
    return new NoticeCreateRequest(title, content, request.level(), request.publishType(), request.publishAt(), request.expireDays());
  }

  private NoticeUpdateRequest validateUpdate(NoticeUpdateRequest request) {
    if (request == null || request.level() == null) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "请填写公告标题、内容和级别");
    }
    String title = required(request.title(), "title");
    String content = required(request.content(), "content");
    validateLength(title, content);
    validateExpireDays(request.expireDays());
    return new NoticeUpdateRequest(title, content, request.level(), request.publishAt(), request.expireDays());
  }

  private void validateScheduleTime(LocalDateTime publishAt) {
    LocalDateTime now = LocalDateTime.now();
    if (!publishAt.isAfter(now.plusMinutes(1))) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "定时发布时间必须晚于当前时间 1 分钟以上");
    }
    if (publishAt.isAfter(now.plusDays(intConfig("notice.max_schedule_days", 30)))) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "定时发布时间超出允许范围");
    }
  }

  private void validateExpireDays(Integer expireDays) {
    if (expireDays == null) {
      return;
    }
    if (expireDays < 1 || expireDays > 30) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "有效天数必须在 1-30 天之间");
    }
  }

  private int expireDays(Integer expireDays) {
    return expireDays == null ? intConfig("notice.default_expire_days", 7) : expireDays;
  }

  private void validateLength(String title, String content) {
    if (title.length() > intConfig("notice.max_title_chars", 100)) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "公告标题过长");
    }
    if (content.length() > intConfig("notice.max_content_chars", 500)) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "公告内容过长");
    }
  }

  private String required(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, ("title".equals(name) ? "标题" : "内容") + "不能为空");
    }
    return value.trim();
  }

  private SystemNotice require(long id) {
    return repository.find(id).orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "公告不存在"));
  }

  private void broadcast(SystemNotice notice) {
    wsPushService.broadcastWs(WsMessage.unsaved("SYSTEM_NOTICE", desktopPayload(notice)));
  }

  private Map<String, Object> desktopPayload(SystemNotice notice) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("noticeId", notice.noticeId());
    payload.put("title", notice.title());
    payload.put("content", notice.content());
    payload.put("level", notice.level().name());
    payload.put("createdAt", notice.createdAt());
    payload.put("expireAt", notice.expireAt());
    return payload;
  }

  private String nextNoticeId(boolean auto) {
    String date = LocalDate.now().format(NOTICE_DAY);
    String prefix = auto ? "notice-auto-" + date + "-" : "notice-" + date + "-";
    int next = repository.countForDatePrefix(prefix) + 1;
    return prefix + "%03d".formatted(next);
  }

  private NoticeLevel parseLevel(String level) {
    try {
      return NoticeLevel.valueOf(level == null ? "INFO" : level.trim().toUpperCase());
    } catch (IllegalArgumentException ex) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "公告级别必须是普通、提醒或故障");
    }
  }

  private void requireAdmin() {
    AuthUser user = AuthContext.current();
    if (user == null || user.role() != Role.ADMIN) {
      throw new ApiException(ApiErrorCodes.FORBIDDEN, "当前账号没有后台权限");
    }
  }

  private int intConfig(String key, int fallback) {
    return configRepository.findValue(key).map(value -> {
      try {
        return Integer.parseInt(value);
      } catch (NumberFormatException ex) {
        return fallback;
      }
    }).orElse(fallback);
  }
}
