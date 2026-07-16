package com.privateflow.modules.customer.admin;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.privateflow.modules.api.ApiException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CustomerAdminSearchControllerTest {

  @Test
  void bindsKeywordAndPagination() throws Exception {
    CustomerAdminSearchService service = mock(CustomerAdminSearchService.class);
    when(service.search("1111", 2, 10)).thenReturn(new CustomerAdminSearchPage(
        List.of(new CustomerAdminListItem(1L, "13800001111", "王女士", "企微", "TUAN_GOU", "18800000001", "万江店", "产后修复", "待跟进", "HIGH", null, null, null, null, null, null, "私域客资管理表", null)),
        11,
        2,
        10,
        2));
    MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new CustomerAdminSearchController(service)).build();

    mockMvc.perform(get("/admin/api/v1/customers/search")
            .param("q", "1111")
            .param("page", "2")
            .param("page_size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(11))
        .andExpect(jsonPath("$.data.items[0].nickname").value("王女士"))
        .andExpect(jsonPath("$.data.items[0].phone").value("13800001111"))
        .andExpect(jsonPath("$.data.items[0].tags").isArray());

    verify(service).search("1111", 2, 10);
  }

  @Test
  void serviceValidatesBoundsWithChineseMessages() {
    CustomerAdminSearchRepository repository = mock(CustomerAdminSearchRepository.class);
    CustomerAdminSearchService service = new CustomerAdminSearchService(repository);

    assertThatThrownBy(() -> service.search("", 0, 20))
        .isInstanceOf(ApiException.class)
        .hasMessage("页码必须大于等于 1");
    assertThatThrownBy(() -> service.search("", 1, 51))
        .isInstanceOf(ApiException.class)
        .hasMessage("每页数量必须在 1-50 之间");
  }
}
