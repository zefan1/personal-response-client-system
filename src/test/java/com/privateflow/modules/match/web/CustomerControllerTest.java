package com.privateflow.modules.match.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.match.Confidence;
import com.privateflow.modules.match.CustomerMatchErrorCodes;
import com.privateflow.modules.match.CustomerMatchException;
import com.privateflow.modules.match.CustomerSearchResult;
import com.privateflow.modules.match.CustomerSummary;
import com.privateflow.modules.match.service.CustomerProfileService;
import com.privateflow.modules.match.service.CustomerSearchService;
import com.privateflow.modules.profile.BatchResolveResult;
import com.privateflow.modules.profile.CustomerProfileView;
import com.privateflow.modules.profile.ManualProfileUpdateResult;
import com.privateflow.modules.profile.ProfileErrorCodes;
import com.privateflow.modules.profile.ProfileUpdateException;
import com.privateflow.modules.profile.service.ManualEditHandler;
import com.privateflow.modules.profile.service.SuggestionQueueManager;
import com.privateflow.modules.tablewrite.ManualSaveResult;
import com.privateflow.modules.tablewrite.TableWriteErrorCodes;
import com.privateflow.modules.tablewrite.TableWriteException;
import com.privateflow.modules.tablewrite.service.ManualSaveHandler;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CustomerControllerTest {

  private CustomerSearchService customerSearchService;
  private CustomerProfileService customerProfileService;
  private ManualEditHandler manualEditHandler;
  private SuggestionQueueManager suggestionQueueManager;
  private ManualSaveHandler manualSaveHandler;
  private MockMvc mockMvc;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    customerSearchService = org.mockito.Mockito.mock(CustomerSearchService.class);
    customerProfileService = org.mockito.Mockito.mock(CustomerProfileService.class);
    manualEditHandler = org.mockito.Mockito.mock(ManualEditHandler.class);
    suggestionQueueManager = org.mockito.Mockito.mock(SuggestionQueueManager.class);
    manualSaveHandler = org.mockito.Mockito.mock(ManualSaveHandler.class);
    mockMvc = MockMvcBuilders
        .standaloneSetup(new CustomerController(
            customerSearchService,
            customerProfileService,
            manualEditHandler,
            suggestionQueueManager,
            manualSaveHandler))
        .build();
    objectMapper = new ObjectMapper();
  }

  @Test
  void searchBindsQueryAndLimit() throws Exception {
    when(customerSearchService.search("Alice", 5)).thenReturn(new CustomerSearchResult(List.of(
        new CustomerSummary("138****0000", "13800000000", "Alice", "TUAN_GOU", "keeper-1", LocalDateTime.of(2026, 7, 3, 12, 0), "Store A", Confidence.HIGH)), 1));

    mockMvc.perform(get("/api/v1/customers/search").param("q", "Alice").param("limit", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.customers[0].phone").value("138****0000"))
        .andExpect(jsonPath("$.data.customers[0].phoneFull").value("13800000000"))
        .andExpect(jsonPath("$.data.customers[0].confidence").value("HIGH"));

    verify(customerSearchService).search("Alice", 5);
  }

  @Test
  void profileReturnsCustomerView() throws Exception {
    when(customerProfileService.getProfile("13800000000")).thenReturn(profile("13800000000", "Alice"));

    mockMvc.perform(get("/api/v1/customers/13800000000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.customer.phone").value("13800000000"))
        .andExpect(jsonPath("$.data.phoneFull").value("13800000000"))
        .andExpect(jsonPath("$.data.customer.nickname").value("Alice"));
  }

  @Test
  void batchDeduplicatesPhonesAndSkipsMissingCustomers() throws Exception {
    when(customerProfileService.getProfile("13800000000")).thenReturn(profile("13800000000", "Alice"));
    when(customerProfileService.getProfile("13900000000"))
        .thenThrow(new CustomerMatchException(CustomerMatchErrorCodes.CUSTOMER_NOT_FOUND, "not found"));

    mockMvc.perform(post("/api/v1/customers/batch")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"phones\":[\"13800000000\",\" 13800000000 \",\"13900000000\",\"\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.customers.length()").value(1))
        .andExpect(jsonPath("$.data.customers[0].customer.nickname").value("Alice"));

    verify(customerProfileService).getProfile("13800000000");
    verify(customerProfileService).getProfile("13900000000");
  }

  @Test
  void updateResolveAndSaveDelegateToServices() throws Exception {
    when(manualEditHandler.update(eq("13800000000"), any())).thenReturn(new ManualProfileUpdateResult(3));
    when(suggestionQueueManager.batchResolve(eq("13800000000"), any())).thenReturn(new BatchResolveResult(2, 1, 0));
    when(manualSaveHandler.save(eq("13800000000"), any())).thenReturn(new ManualSaveResult(true, List.of("nickname")));

    mockMvc.perform(put("/api/v1/customers/13800000000")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"version\":2,\"fields\":{\"nickname\":\"Alice B\"},\"operator\":\"admin\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.version").value(3));
    mockMvc.perform(post("/api/v1/customers/13800000000/suggestions/batch-resolve")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"action\":\"CONFIRM\",\"suggestionIds\":[1,2],\"operator\":\"admin\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.confirmed").value(2));
    mockMvc.perform(post("/api/v1/customers/13800000000/save-to-table")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"sourceTable\":\"crm_table\",\"sourceRowId\":\"row-1\",\"fields\":{\"nickname\":\"Alice B\"}}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.written").value(true))
        .andExpect(jsonPath("$.data.updatedFields[0]").value("nickname"));
  }

  @Test
  void emptyBatchRequestMapsToBadRequest() throws Exception {
    mockMvc.perform(post("/api/v1/customers/batch")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"phones\":[]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.errorCode").value(CustomerMatchErrorCodes.BAD_REQUEST));
  }

  @Test
  void profileNotFoundMapsToNotFound() throws Exception {
    when(customerProfileService.getProfile("13800000000"))
        .thenThrow(new CustomerMatchException(CustomerMatchErrorCodes.CUSTOMER_NOT_FOUND, "not found"));

    mockMvc.perform(get("/api/v1/customers/13800000000"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.errorCode").value(CustomerMatchErrorCodes.CUSTOMER_NOT_FOUND));
  }

  @Test
  void profileVersionConflictMapsToConflict() throws Exception {
    when(manualEditHandler.update(eq("13800000000"), any()))
        .thenThrow(new ProfileUpdateException(ProfileErrorCodes.VERSION_CONFLICT, "version conflict"));

    mockMvc.perform(put("/api/v1/customers/13800000000")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"version\":1,\"fields\":{\"nickname\":\"Alice B\"},\"operator\":\"admin\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.errorCode").value(ProfileErrorCodes.VERSION_CONFLICT));
  }

  @Test
  void tableWriteQueueFullMapsToTooManyRequests() throws Exception {
    when(manualSaveHandler.save(eq("13800000000"), any()))
        .thenThrow(new TableWriteException(TableWriteErrorCodes.TABLE_WRITE_QUEUE_FULL, "queue full"));

    mockMvc.perform(post("/api/v1/customers/13800000000/save-to-table")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"sourceTable\":\"crm_table\",\"sourceRowId\":\"row-1\",\"fields\":{\"nickname\":\"Alice B\"}}"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.errorCode").value(TableWriteErrorCodes.TABLE_WRITE_QUEUE_FULL));
  }

  private CustomerProfileView profile(String phone, String nickname) {
    Customer customer = new Customer();
    customer.setPhone(phone);
    customer.setNickname(nickname);
    customer.setLeadType("TUAN_GOU");
    customer.setVersion(2);
    return new CustomerProfileView(customer, phone, List.of());
  }
}
