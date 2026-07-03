package com.privateflow.modules.api.audit;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.web.GlobalApiExceptionHandler;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuditLogControllerTest {

  private AuditLogService service;
  private MockMvc mockMvc;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    service = org.mockito.Mockito.mock(AuditLogService.class);
    mockMvc = MockMvcBuilders
        .standaloneSetup(new AuditLogController(service))
        .setControllerAdvice(new GlobalApiExceptionHandler())
        .build();
    objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
  }

  @Test
  void listBuildsQueryFromFiltersAndWrapsResult() throws Exception {
    when(service.list(any())).thenReturn(Map.of(
        "items", List.of(entry()),
        "total", 1,
        "page", 2,
        "size", 30,
        "totalPages", 1));
    ArgumentCaptor<AuditLogQuery> captor = ArgumentCaptor.forClass(AuditLogQuery.class);

    mockMvc.perform(get("/admin/api/v1/audit-logs")
            .param("action", "CREATE_NOTICE,STOP_NOTICE")
            .param("operator", "admin")
            .param("targetType", "notice")
            .param("targetId", "notice-1")
            .param("keyword", "hello")
            .param("startDate", "2026-07-01")
            .param("endDate", "2026-07-03")
            .param("page", "2")
            .param("size", "30"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.items[0].action").value("CREATE_NOTICE"));

    org.mockito.Mockito.verify(service).list(captor.capture());
    AuditLogQuery query = captor.getValue();
    org.assertj.core.api.Assertions.assertThat(query.actions()).containsExactly("CREATE_NOTICE", "STOP_NOTICE");
    org.assertj.core.api.Assertions.assertThat(query.operator()).isEqualTo("admin");
    org.assertj.core.api.Assertions.assertThat(query.startDate()).isEqualTo(LocalDate.of(2026, 7, 1));
    org.assertj.core.api.Assertions.assertThat(query.endDate()).isEqualTo(LocalDate.of(2026, 7, 3));
    org.assertj.core.api.Assertions.assertThat(query.page()).isEqualTo(2);
    org.assertj.core.api.Assertions.assertThat(query.size()).isEqualTo(30);
  }

  @Test
  void invalidDateQueryReturnsBadRequest() throws Exception {
    mockMvc.perform(get("/admin/api/v1/audit-logs").param("startDate", "not-a-date"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.errorCode").value(ApiErrorCodes.BAD_REQUEST));
  }

  @Test
  void actionsReturnsConfiguredActionOptions() throws Exception {
    when(service.actions()).thenReturn(Map.of(
        "actions", List.of(Map.of("action", "CREATE_NOTICE", "label", "Create notice", "group", "notice")),
        "targetTypes", List.of(Map.of("type", "notice", "label", "Notice"))));

    mockMvc.perform(get("/admin/api/v1/audit-logs/actions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.actions[0].action").value("CREATE_NOTICE"))
        .andExpect(jsonPath("$.data.targetTypes[0].type").value("notice"));
  }

  @Test
  void exportAcceptsEmptyBodyAndReturnsProcessingState() throws Exception {
    when(service.export(any())).thenReturn(Map.of(
        "exportId", "exp_abc12345",
        "status", "PROCESSING",
        "totalCount", 3,
        "message", "processing"));

    mockMvc.perform(post("/admin/api/v1/audit-logs/export")
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.exportId").value("exp_abc12345"))
        .andExpect(jsonPath("$.data.status").value("PROCESSING"));
  }

  @Test
  void exportRequestBodyBuildsQuery() throws Exception {
    AuditExportRequest request = new AuditExportRequest(
        "ACCOUNT_CREATE",
        "admin",
        "account",
        "7",
        "keyword",
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 3));
    when(service.export(any())).thenReturn(Map.of("exportId", "exp_body", "status", "PROCESSING", "totalCount", 1));
    ArgumentCaptor<AuditLogQuery> captor = ArgumentCaptor.forClass(AuditLogQuery.class);

    mockMvc.perform(post("/admin/api/v1/audit-logs/export")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.exportId").value("exp_body"));

    org.mockito.Mockito.verify(service).export(captor.capture());
    AuditLogQuery query = captor.getValue();
    org.assertj.core.api.Assertions.assertThat(query.actions()).containsExactly("ACCOUNT_CREATE");
    org.assertj.core.api.Assertions.assertThat(query.page()).isEqualTo(1);
    org.assertj.core.api.Assertions.assertThat(query.size()).isEqualTo(20);
  }

  @Test
  void exportTooLargeMapsToBadRequest() throws Exception {
    when(service.export(any())).thenThrow(new ApiException(ApiErrorCodes.BAD_REQUEST, "export data too large"));

    mockMvc.perform(post("/admin/api/v1/audit-logs/export")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.errorCode").value(ApiErrorCodes.BAD_REQUEST));
  }

  @Test
  void exportStatusReturnsTerminalFlag() throws Exception {
    when(service.exportStatus("exp_done")).thenReturn(Map.of(
        "exportId", "exp_done",
        "status", "COMPLETED",
        "terminal", true,
        "totalCount", 2,
        "downloadUrl", "/admin/api/v1/audit-logs/export/exp_done/download"));

    mockMvc.perform(get("/admin/api/v1/audit-logs/export/exp_done"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.status").value("COMPLETED"))
        .andExpect(jsonPath("$.data.terminal").value(true));
  }

  @Test
  void downloadReturnsCsvWithAttachmentHeader() throws Exception {
    when(service.downloadCsv("exp_done")).thenReturn("time,action\n2026-07-03,CREATE_NOTICE\n");

    mockMvc.perform(get("/admin/api/v1/audit-logs/export/exp_done/download"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("exp_done.csv")))
        .andExpect(content().contentType("text/csv"))
        .andExpect(content().string(containsString("CREATE_NOTICE")));
  }

  @Test
  void downloadNotReadyMapsToBadRequest() throws Exception {
    when(service.downloadCsv("exp_pending")).thenThrow(new ApiException(ApiErrorCodes.BAD_REQUEST, "export is not ready"));

    mockMvc.perform(get("/admin/api/v1/audit-logs/export/exp_pending/download"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.errorCode").value(ApiErrorCodes.BAD_REQUEST));
  }

  private AuditLogEntry entry() {
    return new AuditLogEntry(
        1L,
        "admin",
        "CREATE_NOTICE",
        "notice",
        "notice-1",
        "{\"title\":\"hello\"}",
        LocalDateTime.of(2026, 7, 3, 12, 0));
  }
}
