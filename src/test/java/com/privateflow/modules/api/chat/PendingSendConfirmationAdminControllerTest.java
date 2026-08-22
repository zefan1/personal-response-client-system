package com.privateflow.modules.api.chat;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.privateflow.modules.api.web.GlobalApiExceptionHandler;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PendingSendConfirmationAdminControllerTest {

  private PendingSendConfirmationAdminService service;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    service = org.mockito.Mockito.mock(PendingSendConfirmationAdminService.class);
    mockMvc = MockMvcBuilders.standaloneSetup(new PendingSendConfirmationAdminController(service))
        .setControllerAdvice(new GlobalApiExceptionHandler())
        .build();
  }

  @Test
  void returnsConfirmationSummaryForTheSelectedOperator() throws Exception {
    when(service.summary(14, "keeper-a")).thenReturn(Map.of("sentCount", 4L, "unsentCount", 1L));

    mockMvc.perform(get("/admin/api/v1/reply-confirmations/summary")
            .param("days", "14")
            .param("operator", "keeper-a"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.sentCount").value(4));
  }
}
