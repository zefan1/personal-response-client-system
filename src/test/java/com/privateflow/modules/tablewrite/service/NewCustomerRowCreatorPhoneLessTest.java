package com.privateflow.modules.tablewrite.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.privateflow.common.events.CustomerMessageSentEvent;
import com.privateflow.modules.customer.admin.DatasourceAdminRepository;
import com.privateflow.modules.customer.infra.CustomerRepository;
import com.privateflow.modules.tablewrite.TableWriteErrorCodes;
import com.privateflow.modules.tablewrite.TableWriteException;
import com.privateflow.modules.tablewrite.client.WecomTableClient;
import com.privateflow.modules.tablewrite.config.TableConfigProvider;
import com.privateflow.modules.tablewrite.infra.TableFieldMappingResolver;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class NewCustomerRowCreatorPhoneLessTest {

  @Test
  void blocksExternalCreateWhenConfiguredUniquePhoneValueIsUnavailable() {
    NewCustomerRowCreator creator = new NewCustomerRowCreator(
        mock(WecomTableClient.class),
        mock(TableConfigProvider.class),
        mock(TableFieldMappingResolver.class),
        mock(CustomerRepository.class),
        mock(DatasourceAdminRepository.class),
        mock(ApplicationEventPublisher.class));
    CustomerMessageSentEvent event = new CustomerMessageSentEvent(
        null, "仅昵称", true, "th1zyU", "XIAN_SUO", "首次咨询", List.of(),
        "已发送回复", "NEXT", null, false, "keeper");

    assertThatThrownBy(() -> creator.create(event))
        .isInstanceOf(TableWriteException.class)
        .extracting("errorCode")
        .isEqualTo(TableWriteErrorCodes.TABLE_WRITE_BLOCKED);
  }
}
