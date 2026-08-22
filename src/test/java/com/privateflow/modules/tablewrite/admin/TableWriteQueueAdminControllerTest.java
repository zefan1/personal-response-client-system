package com.privateflow.modules.tablewrite.admin;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.privateflow.modules.api.web.GlobalApiExceptionHandler;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TableWriteQueueAdminControllerTest {

  private TableWriteQueueAdminService service;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    service = org.mockito.Mockito.mock(TableWriteQueueAdminService.class);
    mockMvc = MockMvcBuilders.standaloneSetup(new TableWriteQueueAdminController(service))
        .setControllerAdvice(new GlobalApiExceptionHandler())
        .build();
  }

  @Test
  void listsFailuresAndRequeuesOneRecord() throws Exception {
    when(service.listFailed(20)).thenReturn(List.of(Map.of("id", 17L, "errorMsg", "relay timeout")));
    when(service.requeueFailed(17L)).thenReturn(Map.of("id", 17L, "status", "PENDING"));

    mockMvc.perform(get("/admin/api/v1/table-writes/failed").param("limit", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].id").value(17));

    mockMvc.perform(post("/admin/api/v1/table-writes/17/requeue"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("PENDING"));
    verify(service).requeueFailed(17L);
    verify(service).listFailed(eq(20));
  }

  @Test
  void resolvesOnePermanentFailure() throws Exception {
    when(service.resolveFailed(12L)).thenReturn(Map.of("id", 12L, "status", "RESOLVED"));

    mockMvc.perform(post("/admin/api/v1/table-writes/12/resolve"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("RESOLVED"));
    verify(service).resolveFailed(12L);
  }
}
