package com.privateflow.modules.notices;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.web.GlobalApiExceptionHandler;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class NoticeControllerTest {

  private NoticeService service;
  private MockMvc mockMvc;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    service = org.mockito.Mockito.mock(NoticeService.class);
    mockMvc = MockMvcBuilders
        .standaloneSetup(new NoticeController(service))
        .setControllerAdvice(new GlobalApiExceptionHandler())
        .build();
    objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
  }

  @Test
  void listBindsFiltersAndWrapsItems() throws Exception {
    SystemNotice notice = notice(3L, NoticeLevel.WARN, NoticeStatus.SCHEDULED, false);
    when(service.list(NoticeStatus.SCHEDULED, NoticeLevel.WARN, NoticeSource.MANUAL, "SCHEDULED", 2, 30)).thenReturn(Map.of(
        "items", List.of(notice),
        "total", 1,
        "page", 2,
        "size", 30,
        "totalPages", 1));

    mockMvc.perform(get("/admin/api/v1/notices")
            .param("page", "2")
            .param("size", "30")
            .param("status", "SCHEDULED")
            .param("level", "WARN")
            .param("source", "MANUAL"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.totalPages").value(1))
        .andExpect(jsonPath("$.data.items[0].status").value("SCHEDULED"))
        .andExpect(jsonPath("$.data.items[0].level").value("WARN"));

    verify(service).list(NoticeStatus.SCHEDULED, NoticeLevel.WARN, NoticeSource.MANUAL, "SCHEDULED", 2, 30);
  }

  @Test
  void invalidStatusFilterReturnsBadRequest() throws Exception {
    mockMvc.perform(get("/admin/api/v1/notices").param("status", "BROKEN"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.errorCode").value(ApiErrorCodes.BAD_REQUEST));
  }

  @Test
  void createImmediateReturnsPublishedNotice() throws Exception {
    NoticeCreateRequest request = new NoticeCreateRequest(
        "title",
        "content",
        NoticeLevel.INFO,
        PublishType.IMMEDIATE,
        null,
        1);
    when(service.create(any())).thenReturn(notice(4L, NoticeLevel.INFO, NoticeStatus.PUBLISHED, false));

    mockMvc.perform(post("/admin/api/v1/notices")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(4))
        .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
  }

  @Test
  void createValidationFailureMapsToBadRequest() throws Exception {
    when(service.create(any())).thenThrow(new ApiException(ApiErrorCodes.BAD_REQUEST, "publishAt is required"));

    mockMvc.perform(post("/admin/api/v1/notices")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"n\",\"content\":\"c\",\"level\":\"INFO\",\"publishType\":\"SCHEDULED\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.errorCode").value(ApiErrorCodes.BAD_REQUEST));
  }

  @Test
  void updateScheduledReturnsUpdatedNotice() throws Exception {
    NoticeUpdateRequest request = new NoticeUpdateRequest(
        "updated",
        "content",
        NoticeLevel.ERROR,
        LocalDateTime.of(2026, 7, 4, 12, 0),
        2);
    when(service.update(eq(5L), any())).thenReturn(notice(5L, NoticeLevel.ERROR, NoticeStatus.SCHEDULED, false));

    mockMvc.perform(put("/admin/api/v1/notices/5")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(5))
        .andExpect(jsonPath("$.data.level").value("ERROR"));
  }

  @Test
  void stopReturnsStoppedNotice() throws Exception {
    when(service.stop(5L)).thenReturn(notice(5L, NoticeLevel.WARN, NoticeStatus.SCHEDULED, true));

    mockMvc.perform(put("/admin/api/v1/notices/5/stop"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(5))
        .andExpect(jsonPath("$.data.isStopped").value(true));
  }

  @Test
  void deleteCallsServiceAndReturnsSuccess() throws Exception {
    doNothing().when(service).delete(5L);

    mockMvc.perform(delete("/admin/api/v1/notices/5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(service).delete(5L);
  }

  @Test
  void activeReturnsDesktopPayloadList() throws Exception {
    when(service.active()).thenReturn(List.of(Map.of(
        "noticeId", "notice-20260703-001",
        "title", "active",
        "level", "INFO")));

    mockMvc.perform(get("/api/v1/notices/active"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].noticeId").value("notice-20260703-001"))
        .andExpect(jsonPath("$.data[0].level").value("INFO"));
  }

  @Test
  void statusConflictMapsToHttp409() throws Exception {
    when(service.stop(9L)).thenThrow(new ApiException(ApiErrorCodes.VERSION_STATUS_INVALID, "notice is already stopped"));

    mockMvc.perform(put("/admin/api/v1/notices/9/stop"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.errorCode").value(ApiErrorCodes.VERSION_STATUS_INVALID));
  }

  private SystemNotice notice(long id, NoticeLevel level, NoticeStatus status, boolean stopped) {
    LocalDateTime now = LocalDateTime.of(2026, 7, 3, 12, 0);
    return new SystemNotice(
        id,
        "notice-20260703-%03d".formatted(id),
        "title",
        "content",
        level,
        NoticeSource.MANUAL,
        status,
        stopped,
        now.plusHours(1),
        status == NoticeStatus.PUBLISHED ? now : null,
        now.plusDays(1),
        stopped ? now.plusMinutes(5) : null,
        "admin",
        now,
        now);
  }
}
