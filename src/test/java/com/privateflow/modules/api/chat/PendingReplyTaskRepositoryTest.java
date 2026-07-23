package com.privateflow.modules.api.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.match.Confidence;
import com.privateflow.modules.match.CustomerSummary;
import java.sql.Timestamp;
import com.privateflow.modules.skill.SkillResponse;
import com.privateflow.modules.skill.Suggestion;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class PendingReplyTaskRepositoryTest {

  private JdbcTemplate jdbcTemplate;
  private PendingReplyTaskRepository repository;
  private PendingReplyTaskService service;

  @BeforeEach
  void setUp() {
    jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
        "jdbc:h2:mem:pending_reply_tasks;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "sa",
        ""));
    jdbcTemplate.execute("DROP TABLE IF EXISTS pending_reply_task_candidates");
    jdbcTemplate.execute("DROP TABLE IF EXISTS pending_reply_tasks");
    jdbcTemplate.execute("""
        CREATE TABLE pending_reply_tasks (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          task_id CHAR(36) NOT NULL UNIQUE,
          reply_session_id VARCHAR(80) NOT NULL,
          username VARCHAR(64) NOT NULL,
          status VARCHAR(32) NOT NULL,
          recognized_nickname VARCHAR(255),
          recognized_phone VARCHAR(32),
          platform_identifier VARCHAR(255),
          lead_type VARCHAR(32),
          source_table VARCHAR(255),
          client_message TEXT NOT NULL,
          chat_context_json CLOB NOT NULL,
          selected_phone VARCHAR(32),
          result_json CLOB,
          error_code VARCHAR(32),
          generation_started_at TIMESTAMP,
          finished_at TIMESTAMP,
          expires_at TIMESTAMP NOT NULL,
          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
          updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
          version INT NOT NULL DEFAULT 0
        )
        """);
    jdbcTemplate.execute("""
        CREATE TABLE pending_reply_task_candidates (
          task_id BIGINT NOT NULL,
          phone VARCHAR(32) NOT NULL,
          rank_no SMALLINT NOT NULL,
          PRIMARY KEY (task_id, phone)
        )
        """);
    repository = new PendingReplyTaskRepository(jdbcTemplate, new ObjectMapper());
    service = new PendingReplyTaskService(repository, org.mockito.Mockito.mock(ChatTaskConfig.class));
  }

  @Test
  void onlyOneClaimCanMoveWaitingTaskToGenerating() {
    PendingReplyTask task = repository.create(new PendingReplyTaskDraft(
        "reply-100-1",
        "keeper-1",
        "同名客户",
        null,
        null,
        "TUAN_GOU",
        "customer_sheet",
        "我想了解项目",
        List.of(Map.of("role", "client", "text", "我想了解项目")),
        List.of(
            candidate("18800001111", "同名客户甲"),
            candidate("18800002222", "同名客户乙"))));

    assertThat(repository.claim(task.taskId(), "keeper-1", "18800002222")).isTrue();
    assertThat(repository.claim(task.taskId(), "keeper-1", "18800002222")).isFalse();
    assertThat(repository.findOwned(task.taskId(), "keeper-1").orElseThrow().status())
        .isEqualTo(PendingReplyTaskStatus.GENERATING);
  }

  @Test
  void createPersistsFullCandidatePhoneInsteadOfMaskedDisplayPhone() {
    CustomerSummary candidate = new CustomerSummary(
        "188****1111",
        "18800001111",
        "same-name customer",
        "WECHAT",
        "TUAN_GOU",
        "keeper-1",
        null,
        "Store A",
        Confidence.HIGH);

    PendingReplyTask task = repository.create(new PendingReplyTaskDraft(
        "reply-full-candidate-phone",
        "keeper-1",
        "same-name customer",
        null,
        null,
        "TUAN_GOU",
        null,
        "I want to know more",
        List.of(Map.of("role", "client", "text", "I want to know more")),
        List.of(candidate)));

    assertThat(task.candidatePhones()).containsExactly("18800001111");
    assertThat(repository.claim(task.taskId(), "keeper-1", "188****1111")).isFalse();
    assertThat(repository.claim(task.taskId(), "keeper-1", "18800001111")).isTrue();
  }

  @Test
  void failedTaskCannotBeReclaimedWithAnotherCandidatePhone() {
    PendingReplyTask task = repository.create(new PendingReplyTaskDraft(
        "reply-retry-same-customer",
        "keeper-1",
        "same-name customer",
        null,
        null,
        "TUAN_GOU",
        "customer_sheet",
        "I want to know more",
        List.of(Map.of("role", "client", "text", "I want to know more")),
        List.of(
            candidate("18800001111", "same-name customer A"),
            candidate("18800002222", "same-name customer B"))));
    assertThat(repository.claim(task.taskId(), "keeper-1", "18800001111")).isTrue();
    assertThat(repository.markFailed(task.taskId(), "keeper-1", ApiErrorCodes.INTERNAL_ERROR)).isTrue();

    assertThat(repository.claim(task.taskId(), "keeper-1", "18800002222")).isFalse();

    PendingReplyTask failed = repository.findOwned(task.taskId(), "keeper-1").orElseThrow();
    assertThat(failed.status()).isEqualTo(PendingReplyTaskStatus.FAILED);
    assertThat(failed.selectedPhone()).isEqualTo("18800001111");
  }

  @Test
  void failedTaskRetriesOnlyWithItsOriginalSelectedPhone() {
    PendingReplyTask task = repository.create(new PendingReplyTaskDraft(
        "reply-retry-original-customer",
        "keeper-1",
        "same-name customer",
        null,
        null,
        "TUAN_GOU",
        "customer_sheet",
        "I want to know more",
        List.of(Map.of("role", "client", "text", "I want to know more")),
        List.of(
            candidate("18800001111", "same-name customer A"),
            candidate("18800002222", "same-name customer B"))));
    assertThat(repository.claim(task.taskId(), "keeper-1", "18800001111")).isTrue();
    assertThat(repository.markFailed(task.taskId(), "keeper-1", ApiErrorCodes.INTERNAL_ERROR)).isTrue();

    assertThat(repository.claimRetry(task.taskId(), "keeper-1", "18800002222")).isFalse();
    assertThat(repository.claimRetry(task.taskId(), "keeper-1", "18800001111")).isTrue();

    PendingReplyTask generating = repository.findOwned(task.taskId(), "keeper-1").orElseThrow();
    assertThat(generating.status()).isEqualTo(PendingReplyTaskStatus.GENERATING);
    assertThat(generating.selectedPhone()).isEqualTo("18800001111");
  }

  @Test
  void claimDoesNotSelectPhoneWhenItsCandidateIsRemovedBeforeStateTransition() {
    PendingReplyTask task = repository.create(new PendingReplyTaskDraft(
        "reply-100-1",
        "keeper-1",
        "same-name customer",
        null,
        null,
        "TUAN_GOU",
        "customer_sheet",
        "I want to know more",
        List.of(Map.of("role", "client", "text", "I want to know more")),
        List.of(candidate("18800001111", "same-name customer"))));
    PendingReplyTaskRepository racingRepository = new PendingReplyTaskRepository(
        new CandidateRemovingJdbcTemplate(jdbcTemplate.getDataSource(), task.taskId()),
        new ObjectMapper());

    assertThat(racingRepository.claim(task.taskId(), "keeper-1", "18800001111")).isFalse();

    PendingReplyTask unchanged = repository.findOwned(task.taskId(), "keeper-1").orElseThrow();
    assertThat(unchanged.status()).isEqualTo(PendingReplyTaskStatus.WAITING_CUSTOMER);
    assertThat(unchanged.selectedPhone()).isNull();
  }

  @Test
  void markReadyPersistsResponseOnlyForGeneratingTask() {
    PendingReplyTask task = repository.create(new PendingReplyTaskDraft(
        "reply-100-1",
        "keeper-1",
        "same-name customer",
        null,
        null,
        "TUAN_GOU",
        "customer_sheet",
        "I want to know more",
        List.of(Map.of("role", "client", "text", "I want to know more")),
        List.of(candidate("18800001111", "same-name customer"))));
    ChatResponse response = new ChatResponse(
        "18800001111",
        "same-name customer",
        false,
        null,
        new SkillResponse(List.of(new Suggestion("reply text", "NEXT_STEP", "")), null, null, null),
        null,
        ChatReplySource.skill());

    assertThat(repository.markReady(task.taskId(), "keeper-1", response)).isFalse();
    assertThat(repository.claim(task.taskId(), "keeper-1", "18800001111")).isTrue();
    assertThat(repository.markReady(task.taskId(), "keeper-1", response)).isTrue();
    PendingReplyTask ready = repository.findOwned(task.taskId(), "keeper-1").orElseThrow();
    assertThat(ready.status()).isEqualTo(PendingReplyTaskStatus.READY);
    assertThat(ready.resultJson()).contains("reply text");
    assertThat(ready.resultJson()).contains("\"replySessionId\":\"reply-100-1\"");
    assertThat(repository.markReady(task.taskId(), "keeper-1", response)).isFalse();
  }

  @Test
  void markReadyRejectsResponseWithoutDisplayableSuggestion() {
    PendingReplyTask task = repository.create(new PendingReplyTaskDraft(
        "reply-100-1",
        "keeper-1",
        "same-name customer",
        null,
        null,
        "TUAN_GOU",
        "customer_sheet",
        "I want to know more",
        List.of(Map.of("role", "client", "text", "I want to know more")),
        List.of(candidate("18800001111", "same-name customer"))));
    ChatResponse warningOnly = new ChatResponse(
        "18800001111",
        "same-name customer",
        false,
        null,
        null,
        "generation warning",
        ChatReplySource.skill());

    assertThat(repository.claim(task.taskId(), "keeper-1", "18800001111")).isTrue();
    assertThatThrownBy(() -> repository.markReady(task.taskId(), "keeper-1", warningOnly))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("invalid pending reply task response");

    PendingReplyTask generating = repository.findOwned(task.taskId(), "keeper-1").orElseThrow();
    assertThat(generating.status()).isEqualTo(PendingReplyTaskStatus.GENERATING);
    assertThat(generating.resultJson()).isNull();
  }

  @Test
  void markReadyRejectsResponseForCustomerOtherThanTheSelection() {
    PendingReplyTask task = createClaimedTask();

    assertThatThrownBy(() -> repository.markReady(
        task.taskId(),
        "keeper-1",
        displayableResponse("18800002222")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("invalid pending reply task response");

    assertStillGeneratingWithoutResult(task.taskId());
  }

  @Test
  void markReadyRejectsResponseWithoutTheSelectedCustomerPhone() {
    PendingReplyTask task = createClaimedTask();

    assertThatThrownBy(() -> repository.markReady(
        task.taskId(),
        "keeper-1",
        displayableResponse(null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("invalid pending reply task response");

    assertStillGeneratingWithoutResult(task.taskId());
  }

  @Test
  void markReadyDoesNotOverwriteAReplyTaskRetriedForTheSameCustomer() {
    PendingReplyTask task = repository.create(new PendingReplyTaskDraft(
        "reply-100-1",
        "keeper-1",
        "same-name customer",
        null,
        null,
        "TUAN_GOU",
        "customer_sheet",
        "I want to know more",
        List.of(Map.of("role", "client", "text", "I want to know more")),
        List.of(
            candidate("18800001111", "same-name customer A"),
            candidate("18800002222", "same-name customer B"))));
    assertThat(repository.claim(task.taskId(), "keeper-1", "18800001111")).isTrue();
    PendingReplyTaskRepository interleavingRepository = new PendingReplyTaskRepository(
        new ReadyResultInterleavingJdbcTemplate(jdbcTemplate.getDataSource(), () -> {
          assertThat(repository.markFailed(task.taskId(), "keeper-1", "80-10004")).isTrue();
          assertThat(repository.claimRetry(task.taskId(), "keeper-1", "18800001111")).isTrue();
        }),
        new ObjectMapper());

    assertThat(interleavingRepository.markReady(
        task.taskId(),
        "keeper-1",
        displayableResponse("18800001111"))).isFalse();

    PendingReplyTask current = repository.findOwned(task.taskId(), "keeper-1").orElseThrow();
    assertThat(current.status()).isEqualTo(PendingReplyTaskStatus.GENERATING);
    assertThat(current.selectedPhone()).isEqualTo("18800001111");
    assertThat(current.resultJson()).isNull();
  }

  @Test
  void recoversReadyResponseForItsCurrentOwner() {
    PendingReplyTask task = repository.create(new PendingReplyTaskDraft(
        "reply-100-1",
        "keeper-1",
        "same-name customer",
        null,
        null,
        "TUAN_GOU",
        "customer_sheet",
        "I want to know more",
        List.of(Map.of("role", "client", "text", "I want to know more")),
        List.of(candidate("18800001111", "same-name customer"))));
    ChatResponse response = new ChatResponse(
        "18800001111",
        "same-name customer",
        false,
        null,
        new SkillResponse(List.of(new Suggestion("saved reply", "NEXT_STEP", "")), null, null, null),
        null,
        ChatReplySource.skill());

    assertThat(repository.claim(task.taskId(), "keeper-1", "18800001111")).isTrue();
    assertThat(repository.markReady(task.taskId(), "keeper-1", response)).isTrue();

    PendingReplyTaskView recovered = service.getRecoverable(task.taskId(), "keeper-1");

    assertThat(recovered.taskId()).isEqualTo(task.taskId());
    assertThat(recovered.replySessionId()).isEqualTo("reply-100-1");
    assertThat(recovered.status()).isEqualTo(PendingReplyTaskStatus.READY);
    assertThat(recovered.selectedPhone()).isEqualTo("18800001111");
    assertThat(recovered.expiresAt()).isEqualTo(task.expiresAt());
    assertThat(recovered.response().skill().suggestions())
        .containsExactly(new Suggestion("saved reply", "NEXT_STEP", ""));
  }

  @Test
  void rejectsInvalidSavedReadyResultAsDataError() {
    PendingReplyTask task = repository.create(new PendingReplyTaskDraft(
        "reply-100-1",
        "keeper-1",
        "same-name customer",
        null,
        null,
        "TUAN_GOU",
        "customer_sheet",
        "I want to know more",
        List.of(Map.of("role", "client", "text", "I want to know more")),
        List.of(candidate("18800001111", "same-name customer"))));
    jdbcTemplate.update(
        "UPDATE pending_reply_tasks SET status = ?, result_json = ? WHERE task_id = ?",
        PendingReplyTaskStatus.READY.name(),
        "{not-json",
        task.taskId());

    assertThatThrownBy(() -> service.getRecoverable(task.taskId(), "keeper-1"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("invalid pending reply task result");
  }

  @Test
  void rejectsSavedReadyResultWithoutTheRequiredResponsePayload() {
    PendingReplyTask task = repository.create(new PendingReplyTaskDraft(
        "reply-100-1",
        "keeper-1",
        "same-name customer",
        null,
        null,
        "TUAN_GOU",
        "customer_sheet",
        "I want to know more",
        List.of(Map.of("role", "client", "text", "I want to know more")),
        List.of(candidate("18800001111", "same-name customer"))));
    jdbcTemplate.update(
        "UPDATE pending_reply_tasks SET status = ?, result_json = ? WHERE task_id = ?",
        PendingReplyTaskStatus.READY.name(),
        "{}",
        task.taskId());

    assertThatThrownBy(() -> service.getRecoverable(task.taskId(), "keeper-1"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("invalid pending reply task result");
  }

  @Test
  void rejectsSavedReadyResultFromAnotherReplySession() {
    PendingReplyTask task = repository.create(new PendingReplyTaskDraft(
        "reply-100-1",
        "keeper-1",
        "same-name customer",
        null,
        null,
        "TUAN_GOU",
        "customer_sheet",
        "I want to know more",
        List.of(Map.of("role", "client", "text", "I want to know more")),
        List.of(candidate("18800001111", "same-name customer"))));
    jdbcTemplate.update(
        "UPDATE pending_reply_tasks SET status = ?, result_json = ? WHERE task_id = ?",
        PendingReplyTaskStatus.READY.name(),
        "{\"replySessionId\":\"other-session\",\"response\":{\"phone\":\"18800001111\","
            + "\"skill\":{\"suggestions\":[{\"text\":\"saved reply\","
            + "\"direction\":\"NEXT_STEP\",\"reason\":\"\"}]}}}",
        task.taskId());

    assertThatThrownBy(() -> service.getRecoverable(task.taskId(), "keeper-1"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("invalid pending reply task result");
  }

  @Test
  void rejectsSavedReadyResultForAnotherSelectedCustomer() {
    PendingReplyTask task = repository.create(new PendingReplyTaskDraft(
        "reply-100-1",
        "keeper-1",
        "same-name customer",
        null,
        null,
        "TUAN_GOU",
        "customer_sheet",
        "I want to know more",
        List.of(Map.of("role", "client", "text", "I want to know more")),
        List.of(candidate("18800001111", "same-name customer"))));
    jdbcTemplate.update(
        "UPDATE pending_reply_tasks SET status = ?, selected_phone = ?, result_json = ? WHERE task_id = ?",
        PendingReplyTaskStatus.READY.name(),
        "18800001111",
        "{\"replySessionId\":\"reply-100-1\",\"response\":{\"phone\":\"18800002222\","
            + "\"skill\":{\"suggestions\":[{\"text\":\"saved reply\","
            + "\"direction\":\"NEXT_STEP\",\"reason\":\"\"}]}}}",
        task.taskId());

    assertThatThrownBy(() -> service.getRecoverable(task.taskId(), "keeper-1"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("invalid pending reply task result");
  }

  @Test
  void rejectsSavedReadyResultWithWarningOnlyResponse() {
    PendingReplyTask task = repository.create(new PendingReplyTaskDraft(
        "reply-100-1",
        "keeper-1",
        "same-name customer",
        null,
        null,
        "TUAN_GOU",
        "customer_sheet",
        "I want to know more",
        List.of(Map.of("role", "client", "text", "I want to know more")),
        List.of(candidate("18800001111", "same-name customer"))));
    jdbcTemplate.update(
        "UPDATE pending_reply_tasks SET status = ?, result_json = ? WHERE task_id = ?",
        PendingReplyTaskStatus.READY.name(),
        "{\"replySessionId\":\"reply-100-1\",\"response\":{\"warning\":\"internal warning\"}}",
        task.taskId());

    assertThatThrownBy(() -> service.getRecoverable(task.taskId(), "keeper-1"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("invalid pending reply task result");
  }

  @Test
  void rejectsSavedReadyResultWithInternalGuidanceButNoSuggestion() {
    PendingReplyTask task = repository.create(new PendingReplyTaskDraft(
        "reply-100-1",
        "keeper-1",
        "same-name customer",
        null,
        null,
        "TUAN_GOU",
        "customer_sheet",
        "I want to know more",
        List.of(Map.of("role", "client", "text", "I want to know more")),
        List.of(candidate("18800001111", "same-name customer"))));
    jdbcTemplate.update(
        "UPDATE pending_reply_tasks SET status = ?, result_json = ? WHERE task_id = ?",
        PendingReplyTaskStatus.READY.name(),
        "{\"replySessionId\":\"reply-100-1\",\"response\":{\"skill\":{"
            + "\"suggestions\":[],\"guidance\":\"internal guidance\"}}}",
        task.taskId());

    assertThatThrownBy(() -> service.getRecoverable(task.taskId(), "keeper-1"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("invalid pending reply task result");
  }

  @Test
  void releaseSelectionClearsTheSelectionOnlyForTheGeneratingTaskOwner() {
    PendingReplyTask task = repository.create(new PendingReplyTaskDraft(
        "reply-100-1",
        "keeper-1",
        "same-name customer",
        null,
        null,
        "TUAN_GOU",
        "customer_sheet",
        "I want to know more",
        List.of(Map.of("role", "client", "text", "I want to know more")),
        List.of(candidate("18800001111", "same-name customer"))));
    assertThat(repository.claim(task.taskId(), "keeper-1", "18800001111")).isTrue();

    assertThat(repository.releaseSelection(task.taskId(), "other-keeper")).isFalse();
    assertThat(repository.findOwned(task.taskId(), "keeper-1").orElseThrow().status())
        .isEqualTo(PendingReplyTaskStatus.GENERATING);
    assertThat(repository.releaseSelection(task.taskId(), "keeper-1")).isTrue();
    PendingReplyTask waiting = repository.findOwned(task.taskId(), "keeper-1").orElseThrow();
    assertThat(waiting.status()).isEqualTo(PendingReplyTaskStatus.WAITING_CUSTOMER);
    assertThat(waiting.selectedPhone()).isNull();
    assertThat(repository.releaseSelection(task.taskId(), "keeper-1")).isFalse();
  }

  @Test
  void markFailedStoresThePublicErrorOnlyForTheGeneratingTaskOwner() {
    PendingReplyTask task = repository.create(new PendingReplyTaskDraft(
        "reply-100-1",
        "keeper-1",
        "same-name customer",
        null,
        null,
        "TUAN_GOU",
        "customer_sheet",
        "I want to know more",
        List.of(Map.of("role", "client", "text", "I want to know more")),
        List.of(candidate("18800001111", "same-name customer"))));
    assertThat(repository.claim(task.taskId(), "keeper-1", "18800001111")).isTrue();

    assertThat(repository.markFailed(task.taskId(), "other-keeper", "80-10004")).isFalse();
    assertThat(repository.markFailed(task.taskId(), "keeper-1", "80-10004")).isTrue();
    PendingReplyTask failed = repository.findOwned(task.taskId(), "keeper-1").orElseThrow();
    assertThat(failed.status()).isEqualTo(PendingReplyTaskStatus.FAILED);
    assertThat(failed.selectedPhone()).isEqualTo("18800001111");
    assertThat(failed.errorCode()).isEqualTo("80-10004");
    assertThat(repository.markFailed(task.taskId(), "keeper-1", "80-10004")).isFalse();
  }

  @Test
  void recoveryExpiresWaitingAndFailedTasksAndOmitsThemFromActiveTasks() {
    LocalDateTime now = LocalDateTime.of(2026, 7, 22, 12, 0);
    PendingReplyTask waiting = createTask("reply-waiting-expired", "keeper-1");
    PendingReplyTask failed = createTask("reply-failed-expired", "keeper-1");
    assertThat(repository.claim(failed.taskId(), "keeper-1", "18800001111")).isTrue();
    assertThat(repository.markFailed(failed.taskId(), "keeper-1", ApiErrorCodes.INTERNAL_ERROR)).isTrue();
    jdbcTemplate.update(
        "UPDATE pending_reply_tasks SET expires_at = ? WHERE task_id IN (?, ?)",
        Timestamp.valueOf(now.minusSeconds(1)),
        waiting.taskId(),
        failed.taskId());

    repository.recoverExpiredAndStalledTasks(now, 120);

    assertThat(repository.findOwned(waiting.taskId(), "keeper-1").orElseThrow().status())
        .isEqualTo(PendingReplyTaskStatus.EXPIRED);
    assertThat(repository.findOwned(failed.taskId(), "keeper-1").orElseThrow().status())
        .isEqualTo(PendingReplyTaskStatus.EXPIRED);
    assertThat(repository.findActiveOwned("keeper-1", now)).isEmpty();
  }

  @Test
  void recoveryFailsExpiredStaleGenerationBeforeExpiringItOnTheNextRun() {
    LocalDateTime now = LocalDateTime.of(2026, 7, 22, 12, 0);
    PendingReplyTask stale = createTask("reply-stale", "keeper-1");
    PendingReplyTask staleAndExpired = createTask("reply-stale-expired", "keeper-1");
    assertThat(repository.claim(stale.taskId(), "keeper-1", "18800001111")).isTrue();
    assertThat(repository.claim(staleAndExpired.taskId(), "keeper-1", "18800001111")).isTrue();
    jdbcTemplate.update(
        "UPDATE pending_reply_tasks SET generation_started_at = ?, expires_at = ? WHERE task_id = ?",
        Timestamp.valueOf(now.minusSeconds(121)),
        Timestamp.valueOf(now.plusHours(1)),
        stale.taskId());
    jdbcTemplate.update(
        "UPDATE pending_reply_tasks SET generation_started_at = ?, expires_at = ? WHERE task_id = ?",
        Timestamp.valueOf(now.minusSeconds(121)),
        Timestamp.valueOf(now.minusSeconds(1)),
        staleAndExpired.taskId());
    Integer versionBeforeRecovery = jdbcTemplate.queryForObject(
        "SELECT version FROM pending_reply_tasks WHERE task_id = ?",
        Integer.class,
        staleAndExpired.taskId());

    repository.recoverExpiredAndStalledTasks(now, 120);

    PendingReplyTask failed = repository.findOwned(stale.taskId(), "keeper-1").orElseThrow();
    assertThat(failed.status()).isEqualTo(PendingReplyTaskStatus.FAILED);
    assertThat(failed.errorCode()).isEqualTo(ApiErrorCodes.INTERNAL_ERROR);
    PendingReplyTask failedThenExpired = repository.findOwned(staleAndExpired.taskId(), "keeper-1")
        .orElseThrow();
    assertThat(failedThenExpired.status()).isEqualTo(PendingReplyTaskStatus.FAILED);
    assertThat(failedThenExpired.errorCode()).isEqualTo(ApiErrorCodes.INTERNAL_ERROR);
    assertThat(jdbcTemplate.queryForObject(
        "SELECT version FROM pending_reply_tasks WHERE task_id = ?",
        Integer.class,
        staleAndExpired.taskId())).isEqualTo(versionBeforeRecovery + 1);

    repository.recoverExpiredAndStalledTasks(now, 120);

    PendingReplyTask expired = repository.findOwned(staleAndExpired.taskId(), "keeper-1").orElseThrow();
    assertThat(expired.status()).isEqualTo(PendingReplyTaskStatus.EXPIRED);
    assertThat(jdbcTemplate.queryForObject(
        "SELECT version FROM pending_reply_tasks WHERE task_id = ?",
        Integer.class,
        staleAndExpired.taskId())).isEqualTo(versionBeforeRecovery + 2);
  }

  @Test
  void recoveryKeepsGenerationAtTheTimeoutBoundaryInProgress() {
    LocalDateTime now = LocalDateTime.of(2026, 7, 22, 12, 0);
    PendingReplyTask task = createTask("reply-timeout-boundary", "keeper-1");
    assertThat(repository.claim(task.taskId(), "keeper-1", "18800001111")).isTrue();
    jdbcTemplate.update(
        "UPDATE pending_reply_tasks SET generation_started_at = ?, expires_at = ? WHERE task_id = ?",
        Timestamp.valueOf(now.minusSeconds(120)),
        Timestamp.valueOf(now.plusHours(1)),
        task.taskId());

    repository.recoverExpiredAndStalledTasks(now, 120);

    assertThat(repository.findOwned(task.taskId(), "keeper-1").orElseThrow().status())
        .isEqualTo(PendingReplyTaskStatus.GENERATING);
  }

  @Test
  void recoveryExcludesActiveGenerationButFailsOtherStaleGeneration() {
    LocalDateTime now = LocalDateTime.of(2026, 7, 22, 12, 0);
    PendingReplyTask active = createTask("reply-active-stale", "keeper-1");
    PendingReplyTask abandoned = createTask("reply-abandoned-stale", "keeper-1");
    assertThat(repository.claim(active.taskId(), "keeper-1", "18800001111")).isTrue();
    assertThat(repository.claim(abandoned.taskId(), "keeper-1", "18800001111")).isTrue();
    jdbcTemplate.update(
        "UPDATE pending_reply_tasks SET generation_started_at = ? WHERE task_id IN (?, ?)",
        Timestamp.valueOf(now.minusSeconds(121)),
        active.taskId(),
        abandoned.taskId());
    Integer activeVersion = jdbcTemplate.queryForObject(
        "SELECT version FROM pending_reply_tasks WHERE task_id = ?",
        Integer.class,
        active.taskId());
    Integer abandonedVersion = jdbcTemplate.queryForObject(
        "SELECT version FROM pending_reply_tasks WHERE task_id = ?",
        Integer.class,
        abandoned.taskId());

    int recovered = repository.recoverExpiredAndStalledTasks(
        now,
        120,
        Set.of(active.taskId()));

    assertThat(recovered).isOne();
    assertThat(repository.findOwned(active.taskId(), "keeper-1").orElseThrow().status())
        .isEqualTo(PendingReplyTaskStatus.GENERATING);
    assertThat(repository.findOwned(abandoned.taskId(), "keeper-1").orElseThrow().status())
        .isEqualTo(PendingReplyTaskStatus.FAILED);
    assertThat(jdbcTemplate.queryForObject(
        "SELECT version FROM pending_reply_tasks WHERE task_id = ?",
        Integer.class,
        active.taskId())).isEqualTo(activeVersion);
    assertThat(jdbcTemplate.queryForObject(
        "SELECT version FROM pending_reply_tasks WHERE task_id = ?",
        Integer.class,
        abandoned.taskId())).isEqualTo(abandonedVersion + 1);
  }

  @Test
  void activeTasksAreOwnerScopedUnexpiredAndStablyOrderedByCreationTime() {
    LocalDateTime now = LocalDateTime.of(2026, 7, 22, 12, 0);
    PendingReplyTask waiting = createTask("reply-waiting-active", "keeper-1");
    PendingReplyTask ready = createTask("reply-ready-active", "keeper-1");
    PendingReplyTask cancelled = createTask("reply-cancelled", "keeper-1");
    PendingReplyTask anotherOwner = createTask("reply-other-owner", "keeper-2");
    assertThat(repository.claim(ready.taskId(), "keeper-1", "18800001111")).isTrue();
    assertThat(repository.markReady(ready.taskId(), "keeper-1", displayableResponse("18800001111"))).isTrue();
    jdbcTemplate.update(
        "UPDATE pending_reply_tasks SET status = ?, created_at = ?, expires_at = ? WHERE task_id = ?",
        PendingReplyTaskStatus.CANCELLED.name(),
        Timestamp.valueOf(now.minusHours(3)),
        Timestamp.valueOf(now.plusHours(1)),
        cancelled.taskId());
    jdbcTemplate.update(
        "UPDATE pending_reply_tasks SET created_at = ?, expires_at = ? WHERE task_id = ?",
        Timestamp.valueOf(now.minusHours(2)),
        Timestamp.valueOf(now.plusHours(1)),
        waiting.taskId());
    jdbcTemplate.update(
        "UPDATE pending_reply_tasks SET created_at = ?, expires_at = ? WHERE task_id = ?",
        Timestamp.valueOf(now.minusHours(1)),
        Timestamp.valueOf(now.plusHours(1)),
        ready.taskId());
    jdbcTemplate.update(
        "UPDATE pending_reply_tasks SET created_at = ?, expires_at = ? WHERE task_id = ?",
        Timestamp.valueOf(now.minusHours(4)),
        Timestamp.valueOf(now.plusHours(1)),
        anotherOwner.taskId());

    List<PendingReplyTask> tasks = repository.findActiveOwned("keeper-1", now);

    assertThat(tasks).extracting(PendingReplyTask::taskId)
        .containsExactly(waiting.taskId(), ready.taskId());
    assertThat(tasks).extracting(PendingReplyTask::status)
        .containsExactly(PendingReplyTaskStatus.WAITING_CUSTOMER, PendingReplyTaskStatus.READY);
  }

  @Test
  void cancelOnlyTransitionsWaitingOrFailedTaskAndIncrementsVersionOnce() {
    PendingReplyTask waiting = createTask("reply-cancel-waiting", "keeper-1");
    PendingReplyTask failed = createClaimedTask();
    assertThat(repository.markFailed(failed.taskId(), "keeper-1", ApiErrorCodes.INTERNAL_ERROR)).isTrue();
    PendingReplyTask generating = createClaimedTask();
    PendingReplyTask ready = createTask("reply-cancel-ready", "keeper-1");
    assertThat(repository.claim(ready.taskId(), "keeper-1", "18800001111")).isTrue();
    assertThat(repository.markReady(ready.taskId(), "keeper-1", displayableResponse("18800001111"))).isTrue();
    Integer versionBeforeCancel = jdbcTemplate.queryForObject(
        "SELECT version FROM pending_reply_tasks WHERE task_id = ?",
        Integer.class,
        waiting.taskId());

    assertThat(repository.cancel(waiting.taskId(), "keeper-1")).isTrue();
    assertThat(repository.cancel(waiting.taskId(), "keeper-1")).isFalse();
    assertThat(repository.cancel(failed.taskId(), "keeper-1")).isTrue();
    assertThat(repository.cancel(generating.taskId(), "keeper-1")).isFalse();
    assertThat(repository.cancel(ready.taskId(), "keeper-1")).isFalse();
    assertThat(repository.cancel(waiting.taskId(), "keeper-2")).isFalse();

    assertThat(repository.findOwned(waiting.taskId(), "keeper-1").orElseThrow().status())
        .isEqualTo(PendingReplyTaskStatus.CANCELLED);
    assertThat(repository.findOwned(failed.taskId(), "keeper-1").orElseThrow().status())
        .isEqualTo(PendingReplyTaskStatus.CANCELLED);
    assertThat(repository.findOwned(generating.taskId(), "keeper-1").orElseThrow().status())
        .isEqualTo(PendingReplyTaskStatus.GENERATING);
    assertThat(repository.findOwned(ready.taskId(), "keeper-1").orElseThrow().status())
        .isEqualTo(PendingReplyTaskStatus.READY);
    assertThat(jdbcTemplate.queryForObject(
        "SELECT version FROM pending_reply_tasks WHERE task_id = ?",
        Integer.class,
        waiting.taskId())).isEqualTo(versionBeforeCancel + 1);
  }

  @Test
  void physicalCleanupDeletesOnlyTerminalTasksFinishedBeforeTheRetentionCutoff() {
    LocalDateTime cutoff = LocalDateTime.of(2026, 7, 20, 0, 0);
    PendingReplyTask expiredOld = createTask("reply-expired-old", "keeper-1");
    PendingReplyTask readyOld = createTask("reply-ready-old", "keeper-1");
    PendingReplyTask failedOld = createTask("reply-failed-old", "keeper-1");
    PendingReplyTask cancelledOld = createTask("reply-cancelled-old", "keeper-1");
    PendingReplyTask expiredAtFinishBoundary = createTask("reply-expired-finish-boundary", "keeper-1");
    PendingReplyTask readyWithoutFinishTime = createTask("reply-ready-without-finish-time", "keeper-1");
    PendingReplyTask waitingOld = createTask("reply-waiting-old", "keeper-1");
    PendingReplyTask generatingOld = createTask("reply-generating-old", "keeper-1");
    LocalDateTime oldExpiry = cutoff.minusSeconds(1);

    setTaskStatusAndTimes(expiredOld, PendingReplyTaskStatus.EXPIRED, oldExpiry, oldExpiry);
    setTaskStatusAndTimes(readyOld, PendingReplyTaskStatus.READY, oldExpiry, oldExpiry);
    setTaskStatusAndTimes(failedOld, PendingReplyTaskStatus.FAILED, oldExpiry, oldExpiry);
    setTaskStatusAndTimes(cancelledOld, PendingReplyTaskStatus.CANCELLED, oldExpiry, oldExpiry);
    setTaskStatusAndTimes(expiredAtFinishBoundary, PendingReplyTaskStatus.EXPIRED, oldExpiry, cutoff);
    setTaskStatusAndTimes(readyWithoutFinishTime, PendingReplyTaskStatus.READY, oldExpiry, null);
    setTaskStatusAndTimes(waitingOld, PendingReplyTaskStatus.WAITING_CUSTOMER, oldExpiry, null);
    setTaskStatusAndTimes(generatingOld, PendingReplyTaskStatus.GENERATING, oldExpiry, null);

    assertThat(repository.deletePhysicallyExpiredBefore(cutoff)).isEqualTo(4);

    assertThat(repository.findOwned(expiredOld.taskId(), "keeper-1")).isEmpty();
    assertThat(repository.findOwned(readyOld.taskId(), "keeper-1")).isEmpty();
    assertThat(repository.findOwned(failedOld.taskId(), "keeper-1")).isEmpty();
    assertThat(repository.findOwned(cancelledOld.taskId(), "keeper-1")).isEmpty();
    assertThat(repository.findOwned(expiredAtFinishBoundary.taskId(), "keeper-1")).isPresent();
    assertThat(repository.findOwned(readyWithoutFinishTime.taskId(), "keeper-1")).isPresent();
    assertThat(repository.findOwned(waitingOld.taskId(), "keeper-1")).isPresent();
    assertThat(repository.findOwned(generatingOld.taskId(), "keeper-1")).isPresent();
  }

  @Test
  void createUsesShanghaiTimeForTheFullTwentyFourHourTtlWhenJvmDefaultIsUtc() {
    TimeZone originalDefault = TimeZone.getDefault();
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    try {
      LocalDateTime lowerBound = toMicroseconds(
          LocalDateTime.now(ZoneId.of("Asia/Shanghai")).plusHours(24));

      PendingReplyTask task = createTask("reply-utc-ttl", "keeper-1");

      LocalDateTime upperBound = toMicroseconds(
          LocalDateTime.now(ZoneId.of("Asia/Shanghai")).plusHours(24));
      assertThat(task.expiresAt()).isBetween(lowerBound, upperBound);
    } finally {
      TimeZone.setDefault(originalDefault);
    }
  }

  @Test
  void activeGenerationCreatedUnderUtcDoesNotBecomeStaleInShanghaiTime() {
    TimeZone originalDefault = TimeZone.getDefault();
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    try {
      PendingReplyTask task = createTask("reply-utc-generation", "keeper-1");
      assertThat(repository.claim(task.taskId(), "keeper-1", "18800001111")).isTrue();

      repository.recoverExpiredAndStalledTasks(
          LocalDateTime.now(ZoneId.of("Asia/Shanghai")),
          120);

      assertThat(repository.findOwned(task.taskId(), "keeper-1").orElseThrow().status())
          .isEqualTo(PendingReplyTaskStatus.GENERATING);
    } finally {
      TimeZone.setDefault(originalDefault);
    }
  }

  private PendingReplyTask createClaimedTask() {
    PendingReplyTask task = repository.create(new PendingReplyTaskDraft(
        "reply-100-1",
        "keeper-1",
        "same-name customer",
        null,
        null,
        "TUAN_GOU",
        "customer_sheet",
        "I want to know more",
        List.of(Map.of("role", "client", "text", "I want to know more")),
        List.of(candidate("18800001111", "same-name customer"))));
    assertThat(repository.claim(task.taskId(), "keeper-1", "18800001111")).isTrue();
    return repository.findOwned(task.taskId(), "keeper-1").orElseThrow();
  }

  private void setTaskStatusAndTimes(
      PendingReplyTask task,
      PendingReplyTaskStatus status,
      LocalDateTime expiresAt,
      LocalDateTime finishedAt) {
    jdbcTemplate.update(
        "UPDATE pending_reply_tasks SET status = ?, expires_at = ?, finished_at = ? WHERE task_id = ?",
        status.name(),
        Timestamp.valueOf(expiresAt),
        finishedAt == null ? null : Timestamp.valueOf(finishedAt),
        task.taskId());
  }

  private LocalDateTime toMicroseconds(LocalDateTime value) {
    return value.withNano((value.getNano() / 1_000) * 1_000);
  }

  private PendingReplyTask createTask(String replySessionId, String username) {
    return repository.create(new PendingReplyTaskDraft(
        replySessionId,
        username,
        "same-name customer",
        null,
        null,
        "TUAN_GOU",
        "customer_sheet",
        "I want to know more",
        List.of(Map.of("role", "client", "text", "I want to know more")),
        List.of(candidate("18800001111", "same-name customer"))));
  }

  private ChatResponse displayableResponse(String phone) {
    return new ChatResponse(
        phone,
        "same-name customer",
        false,
        null,
        new SkillResponse(List.of(new Suggestion("reply text", "NEXT_STEP", "")), null, null, null),
        null,
        ChatReplySource.skill());
  }

  private void assertStillGeneratingWithoutResult(String taskId) {
    PendingReplyTask generating = repository.findOwned(taskId, "keeper-1").orElseThrow();
    assertThat(generating.status()).isEqualTo(PendingReplyTaskStatus.GENERATING);
    assertThat(generating.resultJson()).isNull();
  }

  private CustomerSummary candidate(String phone, String nickname) {
    return new CustomerSummary(
        phone,
        phone,
        nickname,
        "WECHAT",
        "TUAN_GOU",
        "keeper-1",
        LocalDateTime.of(2026, 7, 22, 10, 0),
        "门店 A",
        Confidence.HIGH);
  }

  private static final class CandidateRemovingJdbcTemplate extends JdbcTemplate {

    private final String taskId;
    private boolean removeCandidateBeforeTaskUpdate = true;

    private CandidateRemovingJdbcTemplate(javax.sql.DataSource dataSource, String taskId) {
      super(dataSource);
      this.taskId = taskId;
    }

    @Override
    public int update(String sql, Object... args) {
      if (removeCandidateBeforeTaskUpdate && sql.contains("UPDATE pending_reply_tasks")) {
        removeCandidateBeforeTaskUpdate = false;
        super.update("DELETE FROM pending_reply_task_candidates WHERE task_id = "
            + "(SELECT id FROM pending_reply_tasks WHERE task_id = ?)", taskId);
      }
      return super.update(sql, args);
    }
  }

  private static final class ReadyResultInterleavingJdbcTemplate extends JdbcTemplate {

    private final Runnable beforeReadyUpdate;
    private boolean interleaveBeforeReadyUpdate = true;

    private ReadyResultInterleavingJdbcTemplate(
        javax.sql.DataSource dataSource,
        Runnable beforeReadyUpdate) {
      super(dataSource);
      this.beforeReadyUpdate = beforeReadyUpdate;
    }

    @Override
    public int update(String sql, Object... args) {
      if (interleaveBeforeReadyUpdate && sql.contains("SET status = ?, result_json = ?")) {
        interleaveBeforeReadyUpdate = false;
        beforeReadyUpdate.run();
      }
      return super.update(sql, args);
    }
  }
}
