package com.privateflow.modules.customer.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.privateflow.modules.profile.infra.ProfileWriter;
import com.privateflow.modules.tablewrite.client.WecomSmartSheetField;
import com.privateflow.modules.tablewrite.client.WecomSmartSheetFieldCatalog;
import com.privateflow.modules.tablewrite.config.WecomSmartSheetConfig;
import com.privateflow.modules.customer.infra.CustomerRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class IntentProjectMappingServiceTest {

  @Test
  void recomputeUsesTheLatestCustomerVersionAndQueuesProjectionThroughProfileEvent() {
    IntentProjectMappingRepository mappingRepository = mock(IntentProjectMappingRepository.class);
    CustomerRepository customerRepository = mock(CustomerRepository.class);
    ProfileWriter profileWriter = mock(ProfileWriter.class);
    com.privateflow.modules.customer.Customer customer = new com.privateflow.modules.customer.Customer();
    customer.setPhone("13800000000");
    customer.setPurchasedProject("产后腹直肌套餐");
    customer.setVersion(3);
    com.privateflow.modules.customer.Customer latest = new com.privateflow.modules.customer.Customer();
    latest.setPhone(customer.getPhone());
    latest.setPurchasedProject(customer.getPurchasedProject());
    latest.setVersion(4);
    when(mappingRepository.list()).thenReturn(List.of(
        new IntentProjectMappingRule(1, "postpartum", "产康", List.of("腹直肌"), 0, "ACTIVE", "意向项目", null, null)));
    when(customerRepository.findCustomersForIntentProject(true)).thenReturn(List.of(customer));
    when(customerRepository.findByPhone(customer.getPhone())).thenReturn(Optional.of(latest));
    IntentProjectMappingService service = new IntentProjectMappingService(
        mappingRepository, mock(WecomSmartSheetFieldCatalog.class), mock(WecomSmartSheetConfig.class),
        customerRepository, profileWriter);

    Map<String, Object> result = service.recompute(true);

    assertThat(result).containsEntry("scanned", 1).containsEntry("matched", 1)
        .containsEntry("databaseUpdated", 1).containsEntry("projectionQueued", 1);
    verify(profileWriter).write(eq(customer.getPhone()), eq(Map.of("intendedProject", "产康")), eq(4), eq(true), any());
  }

  @Test
  void recomputeSkipsARecordWhoseIntentionWasFilledAfterTheScan() {
    IntentProjectMappingRepository mappingRepository = mock(IntentProjectMappingRepository.class);
    CustomerRepository customerRepository = mock(CustomerRepository.class);
    ProfileWriter profileWriter = mock(ProfileWriter.class);
    com.privateflow.modules.customer.Customer scanned = new com.privateflow.modules.customer.Customer();
    scanned.setPhone("13800000000");
    scanned.setPurchasedProject("产后腹直肌套餐");
    com.privateflow.modules.customer.Customer latest = new com.privateflow.modules.customer.Customer();
    latest.setPhone(scanned.getPhone());
    latest.setPurchasedProject(scanned.getPurchasedProject());
    latest.setIntendedProject("母乳");
    when(mappingRepository.list()).thenReturn(List.of(
        new IntentProjectMappingRule(1, "postpartum", "产康", List.of("腹直肌"), 0, "ACTIVE", "意向项目", null, null)));
    when(customerRepository.findCustomersForIntentProject(true)).thenReturn(List.of(scanned));
    when(customerRepository.findByPhone(scanned.getPhone())).thenReturn(Optional.of(latest));
    IntentProjectMappingService service = new IntentProjectMappingService(
        mappingRepository, mock(WecomSmartSheetFieldCatalog.class), mock(WecomSmartSheetConfig.class),
        customerRepository, profileWriter);

    Map<String, Object> result = service.recompute(true);

    assertThat(result).containsEntry("scanned", 1).containsEntry("matched", 0)
        .containsEntry("databaseUpdated", 0).containsEntry("projectionQueued", 0);
    verifyNoInteractions(profileWriter);
  }

  @Test
  void matchesHighestPriorityAndThenLongestKeyword() {
    IntentProjectMappingRepository repository = mock(IntentProjectMappingRepository.class);
    when(repository.list()).thenReturn(List.of(
        new IntentProjectMappingRule(1, "m", "母乳", List.of("乳"), 1, "ACTIVE", "意向项目", null, null),
        new IntentProjectMappingRule(2, "c", "产康", List.of("腹直肌", "修复"), 2, "ACTIVE", "意向项目", null, null)));
    IntentProjectMappingService service = new IntentProjectMappingService(
        repository, mock(WecomSmartSheetFieldCatalog.class), mock(WecomSmartSheetConfig.class),
        mock(CustomerRepository.class), mock(ProfileWriter.class));

    assertThat(service.match("产后腹直肌修复套餐")).contains("产康");
    assertThat(service.match("通乳服务")).contains("母乳");
  }

  @Test
  void ignoresDisabledAndOrphanedRules() {
    IntentProjectMappingRepository repository = mock(IntentProjectMappingRepository.class);
    when(repository.list()).thenReturn(List.of(
        new IntentProjectMappingRule(1, "x", "产康", List.of("修复"), 99, "DISABLED", "意向项目", null, null),
        new IntentProjectMappingRule(2, "y", "母乳", List.of("修复"), 1, "ORPHANED", "意向项目", null, null)));
    IntentProjectMappingService service = new IntentProjectMappingService(
        repository, null, null, null, null);

    assertThat(service.match("修复套餐")).isEmpty();
  }

  @Test
  void explicitRefreshInvalidatesCachedWecomOptionsBeforeReading() {
    IntentProjectMappingRepository repository = mock(IntentProjectMappingRepository.class);
    WecomSmartSheetFieldCatalog fieldCatalog = mock(WecomSmartSheetFieldCatalog.class);
    WecomSmartSheetConfig config = mock(WecomSmartSheetConfig.class);
    when(config.documentId()).thenReturn("document");
    when(config.sheetId()).thenReturn("sheet");
    when(config.viewId()).thenReturn("view");
    when(config.uniqueFieldTitle()).thenReturn("联系方式");
    when(repository.exists()).thenReturn(false);
    when(repository.list()).thenReturn(List.of());
    WecomSmartSheetField field = new WecomSmartSheetField("field", "意向项目", "FIELD_TYPE_SINGLE_SELECT",
        Map.of("母乳", "option-breastfeeding"), false);
    when(fieldCatalog.visibleFields(any(), any())).thenReturn(Map.of("意向项目", field));
    IntentProjectMappingService service = new IntentProjectMappingService(
        repository, fieldCatalog, config, mock(CustomerRepository.class), mock(ProfileWriter.class));

    service.refreshOptions();

    verify(fieldCatalog).invalidate();
    verify(repository).observe("option-breastfeeding", "母乳", true);
  }
}
