package com.privateflow.modules.tablewrite.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.customer.infra.SystemConfigRepository;
import com.privateflow.modules.tablewrite.config.WecomApiEndpointProvider;
import com.privateflow.modules.tablewrite.config.WecomSmartSheetConfig;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;

class WecomSmartSheetClientSpringWiringTest {

  @Test
  void SpringCreatesTheSmartSheetClientDependencyChain() {
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
          "WECOM_CORP_ID=corp-1",
          "WECOM_APP_SECRET=app-secret-value",
          "WECOM_SMARTSHEET_DOC_ID=document-1",
          "WECOM_SMARTSHEET_SHEET_ID=sheet-1",
          "WECOM_SMARTSHEET_VIEW_ID=view-1",
          "WECOM_SMARTSHEET_SOURCE_TABLE=Customers",
          "WECOM_SMARTSHEET_UNIQUE_FIELD_TITLE=Customer ID");
      context.registerBean(ObjectMapper.class);
      context.registerBean(SystemConfigRepository.class, () -> mock(SystemConfigRepository.class));
      context.register(
          WecomApiEndpointProvider.class,
          WecomSmartSheetConfig.class,
          WecomAccessTokenProvider.class,
          WecomSmartSheetApiClient.class,
          WecomSmartSheetFieldCatalog.class);
      context.refresh();

      assertThat(context.getBean(WecomSmartSheetApiClient.class)).isNotNull();
      assertThat(context.getBean(WecomSmartSheetFieldCatalog.class)).isNotNull();
    }
  }
}
