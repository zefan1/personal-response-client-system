package com.privateflow.modules.customer.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DatasourceAdminControllerTest {

  private DatasourceAdminService service;
  private MockMvc mockMvc;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    service = org.mockito.Mockito.mock(DatasourceAdminService.class);
    mockMvc = MockMvcBuilders
        .standaloneSetup(new DatasourceAdminController(service))
        .setControllerAdvice(new GlobalApiExceptionHandler())
        .build();
    objectMapper = new ObjectMapper();
  }

  @Test
  void datasourceCrudToggleAndReplaceDelegateToService() throws Exception {
    DatasourceRequest request = new DatasourceRequest("crm", "sheet-1", "crm_table", "primary");
    when(service.list()).thenReturn(Map.of("items", List.of(datasource(7L, true)), "total", 1));
    when(service.create(any())).thenReturn(datasource(8L, true));
    when(service.update(eq(8L), any())).thenReturn(datasource(8L, true));
    when(service.toggle(8L, false)).thenReturn(datasource(8L, false));
    when(service.replace(eq(8L), any())).thenReturn(Map.of("id", 8, "sheetId", "sheet-2"));
    when(service.delete(8L)).thenReturn(Map.of("deleted", true));

    mockMvc.perform(get("/admin/api/v1/datasources"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].sourceTable").value("crm_table"));
    mockMvc.perform(post("/admin/api/v1/datasources")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(8));
    mockMvc.perform(put("/admin/api/v1/datasources/8")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("crm"));
    mockMvc.perform(put("/admin/api/v1/datasources/8/toggle")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"enabled\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.enabled").value(false));
    mockMvc.perform(put("/admin/api/v1/datasources/8/replace")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"sheetId\":\"sheet-2\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.sheetId").value("sheet-2"));
    mockMvc.perform(delete("/admin/api/v1/datasources/8"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.deleted").value(true));

    verify(service).create(any());
    verify(service).update(eq(8L), any());
    verify(service).toggle(8L, false);
    verify(service).replace(eq(8L), any());
    verify(service).delete(8L);
  }

  @Test
  void mappingEndpointsDelegateToService() throws Exception {
    when(service.mappings(7L)).thenReturn(Map.of("mappings", List.of(mapping(1L, "phone", "phone"))));
    when(service.saveMappings(eq(7L), any())).thenReturn(Map.of("saved", 1));
    when(service.mappingVersions(7L)).thenReturn(Map.of("versions", List.of(new MappingVersionDto(3, 1, "admin", LocalDateTime.of(2026, 7, 3, 12, 0), "initial"))));
    when(service.restoreMappings(eq(7L), any())).thenReturn(Map.of("restoredVersion", 3));
    when(service.compareMappings(7L)).thenReturn(Map.of("summary", Map.of("changed", 1)));

    mockMvc.perform(get("/admin/api/v1/datasources/7/mappings"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.mappings[0].sourceField").value("phone"));
    mockMvc.perform(put("/admin/api/v1/datasources/7/mappings")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"mappings\":[{\"sourceField\":\"phone\",\"targetField\":\"phone\",\"enabled\":true}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.saved").value(1));
    mockMvc.perform(get("/admin/api/v1/datasources/7/mappings/versions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.versions[0].version").value(3));
    mockMvc.perform(post("/admin/api/v1/datasources/7/mappings/restore")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"version\":3}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.restoredVersion").value(3));
    mockMvc.perform(get("/admin/api/v1/datasources/7/mappings/compare"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.summary.changed").value(1));

    verify(service).saveMappings(eq(7L), any());
    verify(service).restoreMappings(eq(7L), any());
  }

  @Test
  void metadataSyncAndImportLogEndpointsReturnPayloads() throws Exception {
    when(service.columns(7L)).thenReturn(Map.of("columns", List.of("phone"), "fetchStatus", "OK"));
    when(service.customerFields()).thenReturn(Map.of("fields", List.of(new CustomerFieldDto("phone", "Phone", "base"))));
    when(service.syncStatus()).thenReturn(Map.of("running", false));
    when(service.sync(7L)).thenReturn(Map.of("started", true));
    when(service.importLogs()).thenReturn(Map.of("logs", List.of(Map.of("fileName", "customers.csv")), "total", 1));

    mockMvc.perform(get("/admin/api/v1/datasources/7/columns"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.fetchStatus").value("OK"));
    mockMvc.perform(get("/admin/api/v1/customer-fields"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.fields[0].fieldName").value("phone"));
    mockMvc.perform(get("/admin/api/v1/datasources/sync-status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.running").value(false));
    mockMvc.perform(post("/admin/api/v1/datasources/7/sync"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.started").value(true));
    mockMvc.perform(get("/admin/api/v1/datasources/import-logs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.logs[0].fileName").value("customers.csv"));
  }

  @Test
  void importCsvBindsMultipartFile() throws Exception {
    MockMultipartFile file = new MockMultipartFile("file", "customers.csv", "text/csv", "phone,name\n13800000000,Alice\n".getBytes());
    when(service.importCsv(any())).thenReturn(new CsvImportResult(1, 1, 0, 0, List.of()));

    mockMvc.perform(multipart("/admin/api/v1/datasources/import").file(file))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalRows").value(1))
        .andExpect(jsonPath("$.data.created").value(1));

    verify(service).importCsv(any());
  }

  @Test
  void serviceConflictMapsToStandardConflictBody() throws Exception {
    when(service.saveMappings(eq(7L), any())).thenThrow(new ApiException(ApiErrorCodes.CONFLICT, "duplicate target field"));

    mockMvc.perform(put("/admin/api/v1/datasources/7/mappings")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"mappings\":[{\"sourceField\":\"a\",\"targetField\":\"phone\",\"enabled\":true}]}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.errorCode").value(ApiErrorCodes.CONFLICT));
  }

  private Datasource datasource(long id, boolean enabled) {
    LocalDateTime now = LocalDateTime.of(2026, 7, 3, 12, 0);
    return new Datasource(id, "crm", "sheet-1", "crm_table", "primary", enabled, 1, null, "OK", "admin", now, now);
  }

  private FieldMappingDto mapping(long id, String source, String target) {
    return new FieldMappingDto(id, source, target, true);
  }
}
