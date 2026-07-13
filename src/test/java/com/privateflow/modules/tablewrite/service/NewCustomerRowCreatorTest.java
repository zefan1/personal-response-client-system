package com.privateflow.modules.tablewrite.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.privateflow.modules.customer.admin.DatasourceAdminRepository;
import com.privateflow.modules.customer.sync.SheetSource;
import com.privateflow.modules.tablewrite.TableWriteErrorCodes;
import com.privateflow.modules.tablewrite.TableWriteException;
import com.privateflow.modules.tablewrite.client.WecomTableClient;
import com.privateflow.modules.tablewrite.config.TableConfigProvider;
import com.privateflow.modules.tablewrite.infra.TableFieldMappingResolver;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class NewCustomerRowCreatorTest {

  private final DatasourceAdminRepository datasourceRepository = mock(DatasourceAdminRepository.class);
  private final NewCustomerRowCreator creator = new NewCustomerRowCreator(
      mock(WecomTableClient.class),
      mock(TableConfigProvider.class),
      mock(TableFieldMappingResolver.class),
      mock(com.privateflow.modules.customer.infra.CustomerRepository.class),
      datasourceRepository,
      mock(ApplicationEventPublisher.class));

  @Test
  void resolvesBlankSourceTableFromEnabledDatasource() {
    when(datasourceRepository.defaultWriteSource()).thenReturn(Optional.of(new SheetSource(7L, "sheet-1", "私域客资管理表")));

    assertThat(creator.resolveSourceTable(" ")).isEqualTo("私域客资管理表");
  }

  @Test
  void rejectsBlankSourceTableWhenNoEnabledDatasourceExists() {
    when(datasourceRepository.defaultWriteSource()).thenReturn(Optional.empty());

    assertThatThrownBy(() -> creator.resolveSourceTable(null))
        .isInstanceOf(TableWriteException.class)
        .extracting("errorCode")
        .isEqualTo(TableWriteErrorCodes.CONFIG_MISSING);
  }
}
