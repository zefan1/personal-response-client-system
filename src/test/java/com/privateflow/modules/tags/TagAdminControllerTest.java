package com.privateflow.modules.tags;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.api.ApiException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TagAdminControllerTest {

  private TagAdminService service;
  private MockMvc mockMvc;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    service = org.mockito.Mockito.mock(TagAdminService.class);
    mockMvc = MockMvcBuilders
        .standaloneSetup(new TagAdminController(service))
        .build();
    objectMapper = new ObjectMapper();
  }

  @Test
  void listReturnsCategoriesAndValues() throws Exception {
    when(service.list()).thenReturn(Map.of("categories", List.of(category(1L, "source", List.of(value(8L, true))))));

    mockMvc.perform(get("/admin/api/v1/tags/categories"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.categories[0].categoryKey").value("source"))
        .andExpect(jsonPath("$.data.categories[0].values[0].tagValue").value("wechat"));
  }

  @Test
  void categoryCrudDelegatesToService() throws Exception {
    TagCategoryRequest request = new TagCategoryRequest("Source", "source", true, 10);
    when(service.createCategory(any())).thenReturn(category(2L, "source_custom", List.of()));
    when(service.updateCategory(eq(2L), any())).thenReturn(category(2L, "source_custom", List.of()));
    doNothing().when(service).deleteCategory(2L);

    mockMvc.perform(post("/admin/api/v1/tags/categories")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(2))
        .andExpect(jsonPath("$.data.categoryName").value("Source"));

    mockMvc.perform(put("/admin/api/v1/tags/categories/2")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.categoryKey").value("source_custom"));

    mockMvc.perform(delete("/admin/api/v1/tags/categories/2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(service).createCategory(any());
    verify(service).updateCategory(eq(2L), any());
    verify(service).deleteCategory(2L);
  }

  @Test
  void valueCrudAndToggleDelegatesToService() throws Exception {
    TagValueRequest request = new TagValueRequest(2L, "wechat", "WeChat", true, 3);
    when(service.createValue(any())).thenReturn(value(9L, true));
    when(service.updateValue(eq(9L), any())).thenReturn(value(9L, true));
    when(service.toggleValue(9L, false)).thenReturn(value(9L, false));
    doNothing().when(service).deleteValue(9L);

    mockMvc.perform(post("/admin/api/v1/tags/values")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(9))
        .andExpect(jsonPath("$.data.displayName").value("WeChat"));

    mockMvc.perform(put("/admin/api/v1/tags/values/9")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.tagValue").value("wechat"));

    mockMvc.perform(put("/admin/api/v1/tags/values/9/toggle")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"isEnabled\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.isEnabled").value(false));

    mockMvc.perform(delete("/admin/api/v1/tags/values/9"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(service).createValue(any());
    verify(service).updateValue(eq(9L), any());
    verify(service).toggleValue(9L, false);
    verify(service).deleteValue(9L);
  }

  @Test
  void builtinCategoryDeleteFailureMapsToForbidden() throws Exception {
    doThrow(new ApiException(TagErrorCodes.BUILTIN_CATEGORY_DELETE_FORBIDDEN, "builtin category cannot be deleted"))
        .when(service).deleteCategory(1L);

    mockMvc.perform(delete("/admin/api/v1/tags/categories/1"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.errorCode").value(TagErrorCodes.BUILTIN_CATEGORY_DELETE_FORBIDDEN));
  }

  @Test
  void valueConflictFailureMapsToBadRequest() throws Exception {
    when(service.createValue(any())).thenThrow(new ApiException(TagErrorCodes.VALUE_EXISTS, "tag value exists"));

    mockMvc.perform(post("/admin/api/v1/tags/values")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"categoryId\":2,\"tagValue\":\"wechat\",\"displayName\":\"WeChat\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.errorCode").value(TagErrorCodes.VALUE_EXISTS));
  }

  private TagCategory category(long id, String key, List<TagValue> values) {
    LocalDateTime now = LocalDateTime.of(2026, 7, 3, 12, 0);
    return new TagCategory(id, key, "Source", "source", false, true, 10, values, now, now);
  }

  private TagValue value(long id, boolean enabled) {
    LocalDateTime now = LocalDateTime.of(2026, 7, 3, 12, 0);
    return new TagValue(id, 2L, "source", "wechat", "WeChat", enabled, 3, now, now);
  }
}
