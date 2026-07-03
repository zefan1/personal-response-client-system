package com.privateflow.modules.api.account;

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
import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.Role;
import com.privateflow.modules.api.web.GlobalApiExceptionHandler;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AccountAdminControllerTest {

  private AccountAdminService service;
  private MockMvc mockMvc;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    service = org.mockito.Mockito.mock(AccountAdminService.class);
    mockMvc = MockMvcBuilders
        .standaloneSetup(new AccountAdminController(service))
        .setControllerAdvice(new GlobalApiExceptionHandler())
        .build();
    objectMapper = new ObjectMapper();
  }

  @Test
  void listBindsPagingRoleKeywordAndEnabledFilter() throws Exception {
    AccountAdminItem item = account(5L, "13900000001", Role.LEADER, null, true);
    when(service.list(2, 30, Role.LEADER, "139", true)).thenReturn(Map.of(
        "total", 1,
        "page", 2,
        "pageSize", 30,
        "list", List.of(item)));

    mockMvc.perform(get("/admin/api/v1/accounts")
            .param("page", "2")
            .param("page_size", "30")
            .param("role", "LEADER")
            .param("keyword", "139")
            .param("is_enabled", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.list[0].role").value("LEADER"))
        .andExpect(jsonPath("$.data.list[0].isEnabled").value(true));

    verify(service).list(2, 30, Role.LEADER, "139", true);
  }

  @Test
  void invalidRoleQueryReturnsBadRequest() throws Exception {
    mockMvc.perform(get("/admin/api/v1/accounts").param("role", "OWNER"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.errorCode").value(ApiErrorCodes.BAD_REQUEST));
  }

  @Test
  void createReturnsCreatedAccount() throws Exception {
    AccountCreateRequest request = new AccountCreateRequest("13900000002", "pass1234", "leader", Role.LEADER, null);
    when(service.create(any())).thenReturn(account(6L, "13900000002", Role.LEADER, null, true));

    mockMvc.perform(post("/admin/api/v1/accounts")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(6))
        .andExpect(jsonPath("$.data.phone").value("13900000002"))
        .andExpect(jsonPath("$.data.role").value("LEADER"));
  }

  @Test
  void createValidationFailureMapsToBadRequest() throws Exception {
    when(service.create(any())).thenThrow(new ApiException(ApiErrorCodes.BAD_REQUEST, "phone format invalid"));

    mockMvc.perform(post("/admin/api/v1/accounts")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"phone\":\"123\",\"password\":\"pass1234\",\"displayName\":\"bad\",\"role\":\"LEADER\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.errorCode").value(ApiErrorCodes.BAD_REQUEST));
  }

  @Test
  void updateReturnsUpdatedAccount() throws Exception {
    AccountUpdateRequest request = new AccountUpdateRequest("keeper", Role.KEEPER, 5L, true);
    when(service.update(eq(7L), any())).thenReturn(account(7L, "13900000003", Role.KEEPER, 5L, true));

    mockMvc.perform(put("/admin/api/v1/accounts/7")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(7))
        .andExpect(jsonPath("$.data.role").value("KEEPER"))
        .andExpect(jsonPath("$.data.leaderId").value(5));
  }

  @Test
  void togglePassesBooleanAndReturnsState() throws Exception {
    when(service.toggle(7L, false)).thenReturn(account(7L, "13900000003", Role.KEEPER, 5L, false));

    mockMvc.perform(put("/admin/api/v1/accounts/7/toggle")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"isEnabled\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(7))
        .andExpect(jsonPath("$.data.isEnabled").value(false));

    verify(service).toggle(7L, false);
  }

  @Test
  void resetPasswordReturnsRevocationFlag() throws Exception {
    when(service.resetPassword(eq(7L), any())).thenReturn(Map.of("id", 7L, "revokedRefreshToken", true));

    mockMvc.perform(put("/admin/api/v1/accounts/7/reset-password")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"newPassword\":\"pass5678\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(7))
        .andExpect(jsonPath("$.data.revokedRefreshToken").value(true));
  }

  @Test
  void deleteCallsServiceAndReturnsSuccess() throws Exception {
    doNothing().when(service).delete(7L);

    mockMvc.perform(delete("/admin/api/v1/accounts/7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(service).delete(7L);
  }

  private AccountAdminItem account(long id, String phone, Role role, Long leaderId, boolean enabled) {
    return new AccountAdminItem(
        id,
        phone,
        "display",
        role,
        role.name(),
        leaderId,
        leaderId == null ? null : "leader",
        enabled,
        null,
        LocalDateTime.of(2026, 7, 3, 12, 0));
  }
}
