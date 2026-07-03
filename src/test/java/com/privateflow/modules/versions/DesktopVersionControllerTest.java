package com.privateflow.modules.versions;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.web.GlobalApiExceptionHandler;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DesktopVersionControllerTest {

  private DesktopVersionService service;
  private MockMvc mockMvc;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    service = org.mockito.Mockito.mock(DesktopVersionService.class);
    mockMvc = MockMvcBuilders
        .standaloneSetup(new DesktopVersionController(service))
        .setControllerAdvice(new GlobalApiExceptionHandler())
        .build();
    objectMapper = new ObjectMapper();
  }

  @Test
  void listWrapsServiceResultInApiResponse() throws Exception {
    when(service.list(null, DesktopPlatform.WINDOWS, 1, 20)).thenReturn(Map.of(
        "total", 0,
        "page", 1,
        "size", 20,
        "items", java.util.List.of()));

    mockMvc.perform(get("/admin/api/v1/versions")
            .param("platform", "WINDOWS"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.total").value(0))
        .andExpect(jsonPath("$.data.items").isArray());
  }

  @Test
  void createReturnsBadRequestForMissingPackage() throws Exception {
    DesktopVersionCreateRequest request = new DesktopVersionCreateRequest(
        "1.0.0",
        DesktopPlatform.WINDOWS,
        "",
        "changelog",
        UpdateStrategy.OPTIONAL,
        null,
        null);
    when(service.create(any())).thenThrow(new ApiException(ApiErrorCodes.VERSION_PACKAGE_MISSING, "file or downloadUrl is required"));

    mockMvc.perform(post("/admin/api/v1/versions")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.errorCode").value(ApiErrorCodes.VERSION_PACKAGE_MISSING));
  }

  @Test
  void publishConflictMapsToHttp409() throws Exception {
    when(service.publish(99L)).thenThrow(new ApiException(ApiErrorCodes.VERSION_STATUS_INVALID, "only draft version can be published"));

    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/admin/api/v1/versions/99/publish"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.errorCode").value(ApiErrorCodes.VERSION_STATUS_INVALID));
  }

  @Test
  void versionCheckExposesUpdatePayload() throws Exception {
    DesktopVersion latest = new DesktopVersion(
        7L,
        "2.0.0",
        DesktopPlatform.WINDOWS,
        VersionStatus.PUBLISHED,
        UpdateStrategy.OPTIONAL,
        null,
        "https://example.com/app.exe",
        123L,
        "new release",
        null,
        null,
        null,
        LocalDateTime.now(),
        "admin",
        LocalDateTime.now(),
        LocalDateTime.now());
    when(service.versionCheck(eq(DesktopPlatform.WINDOWS), eq("1.0.0"), eq("client-a"))).thenReturn(Map.of(
        "hasUpdate", true,
        "latestVersion", latest,
        "currentVersionRevoked", false,
        "revokeInfo", "",
        "reportIntervalHours", 24));

    mockMvc.perform(get("/api/v1/desktop/version-check")
            .param("platform", "WINDOWS")
            .param("currentVersion", "1.0.0")
            .param("clientId", "client-a"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.hasUpdate").value(true))
        .andExpect(jsonPath("$.data.latestVersion.version").value("2.0.0"));
  }
}
