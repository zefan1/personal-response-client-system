package com.privateflow.modules.quicksearch.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.web.GlobalApiExceptionHandler;
import com.privateflow.modules.quicksearch.ContentType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class QuickSearchAdminControllerTest {

  private QuickSearchAdminService service;
  private MockMvc mockMvc;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    service = org.mockito.Mockito.mock(QuickSearchAdminService.class);
    mockMvc = MockMvcBuilders
        .standaloneSetup(new QuickSearchAdminController(service))
        .setControllerAdvice(new GlobalApiExceptionHandler())
        .build();
    objectMapper = new ObjectMapper();
  }

  @Test
  void listWrapsItemsInApiResponse() throws Exception {
    when(service.list()).thenReturn(List.of(item(7L, ContentType.MINI_PROGRAM, "GENERAL", true)));

    mockMvc.perform(get("/admin/api/v1/quick-search/items"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].id").value(7))
        .andExpect(jsonPath("$.data[0].contentType").value("MINI_PROGRAM"))
        .andExpect(jsonPath("$.data[0].leadType").value("GENERAL"));
  }

  @Test
  void createReturnsCreatedId() throws Exception {
    QuickSearchItemRequest request = new QuickSearchItemRequest(
        ContentType.TEMPLATE,
        "GENERAL",
        "acceptance",
        "ACCEPT1",
        "content",
        null,
        10,
        true,
        null);
    when(service.create(any())).thenReturn(Map.of("id", 42L));

    mockMvc.perform(post("/admin/api/v1/quick-search/items")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(42));
  }

  @Test
  void invalidCreateMapsToBadRequest() throws Exception {
    when(service.create(any())).thenThrow(new ApiException(ApiErrorCodes.BAD_REQUEST, "leadType invalid"));

    mockMvc.perform(post("/admin/api/v1/quick-search/items")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"contentType\":\"TEMPLATE\",\"leadType\":\"PENDING\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.errorCode").value(ApiErrorCodes.BAD_REQUEST));
  }

  @Test
  void updateReturnsUpdatedItemState() throws Exception {
    QuickSearchItemRequest request = new QuickSearchItemRequest(
        null,
        "GENERAL",
        "updated",
        "ACCEPT2",
        "updated content",
        null,
        9,
        true,
        null);
    when(service.update(eq(5L), any())).thenReturn(item(5L, ContentType.TEMPLATE, "GENERAL", true));

    mockMvc.perform(put("/admin/api/v1/quick-search/items/5")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(5))
        .andExpect(jsonPath("$.data.enabled").value(true));
  }

  @Test
  void toggleReturnsEnabledState() throws Exception {
    when(service.toggle(5L)).thenReturn(Map.of("isEnabled", false));

    mockMvc.perform(put("/admin/api/v1/quick-search/items/5/toggle"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.isEnabled").value(false));
  }

  @Test
  void deleteCallsServiceAndReturnsSuccess() throws Exception {
    doNothing().when(service).delete(5L);

    mockMvc.perform(delete("/admin/api/v1/quick-search/items/5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(service).delete(5L);
  }

  @Test
  void uploadReturnsImageUrl() throws Exception {
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "acceptance.png",
        "image/png",
        new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
    when(service.uploadImage(any())).thenReturn(new ImageUploadResponse("cos://quick-search/test.png"));

    mockMvc.perform(multipart("/admin/api/v1/upload/image").file(file))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.imageUrl").value("cos://quick-search/test.png"));
  }

  @Test
  void unsupportedUploadTypeMapsToBadRequest() throws Exception {
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "acceptance.txt",
        "text/plain",
        "not an image".getBytes());
    when(service.uploadImage(any())).thenThrow(new ApiException(ApiErrorCodes.BAD_REQUEST, "image type unsupported"));

    mockMvc.perform(multipart("/admin/api/v1/upload/image").file(file))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.errorCode").value(ApiErrorCodes.BAD_REQUEST));
  }

  private QuickSearchAdminItem item(Long id, ContentType contentType, String leadType, boolean enabled) {
    LocalDateTime now = LocalDateTime.of(2026, 7, 3, 12, 0);
    return new QuickSearchAdminItem(
        id,
        contentType,
        leadType,
        "title",
        "ACCEPT",
        "content",
        null,
        1,
        enabled,
        "admin",
        now,
        now);
  }
}
