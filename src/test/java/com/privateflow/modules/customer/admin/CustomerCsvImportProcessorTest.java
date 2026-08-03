package com.privateflow.modules.customer.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.infra.CustomerRepository;
import com.privateflow.modules.tags.TagExchangeResult;
import com.privateflow.modules.tags.TagExchangeService;
import com.privateflow.modules.tags.TagExchangeSourceType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

class CustomerCsvImportProcessorTest {

  @Test
  void importsAcceptedFieldsAfterTagExchangeAndCountsNewCustomers() {
    CustomerRepository customerRepository = mock(CustomerRepository.class);
    TagExchangeService exchangeService = mock(TagExchangeService.class);
    when(customerRepository.findByPhone("13800000000")).thenReturn(Optional.empty());
    when(exchangeService.prepareInbound(eq(TagExchangeSourceType.CSV_IMPORT), eq("2"), anyMap()))
        .thenReturn(new TagExchangeResult(
            Map.of("nickname", "Alice", "postpartumMonths", "5.5"), List.of(), List.of()));
    CustomerCsvImportProcessor processor = new CustomerCsvImportProcessor(customerRepository, exchangeService);
    MockMultipartFile file = new MockMultipartFile(
        "file", "customers.csv", "text/csv", "phone,nickname,postpartumMonths\n13800000000,Alice,5.5\n".getBytes());

    CsvImportResult result = processor.importCsv(file);

    ArgumentCaptor<Customer> customer = ArgumentCaptor.forClass(Customer.class);
    verify(customerRepository).upsert(customer.capture(), any(), eq(TagExchangeSourceType.CSV_IMPORT), eq("2"));
    assertThat(result.created()).isEqualTo(1);
    assertThat(result.updated()).isZero();
    assertThat(customer.getValue().getNickname()).isEqualTo("Alice");
    assertThat(customer.getValue().getPostpartumMonths()).isEqualByComparingTo(new BigDecimal("5.5"));
  }
}
