package com.privateflow.modules.quicksearch.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.alert.SystemAlertRepository;
import com.privateflow.modules.api.audit.AuditLogger;
import com.privateflow.modules.api.ws.WsPushService;
import com.privateflow.modules.customer.infra.SystemConfigRepository;
import com.privateflow.modules.quicksearch.ContentType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

class QuickSearchAdminServiceTest {

  @TempDir
  Path tempDir;

  @Test
  void listNormalizesPagingAndAllowedSortBeforeRepositoryQuery() {
    QuickSearchAdminRepository repository = mock(QuickSearchAdminRepository.class);
    when(repository.count(any())).thenReturn(21L);
    when(repository.list(any())).thenReturn(List.of());
    QuickSearchAdminService service = new QuickSearchAdminService(
        repository,
        mock(ApplicationEventPublisher.class),
        mock(WsPushService.class),
        mock(SystemAlertRepository.class),
        mock(QuickSearchImageStorage.class),
        mock(SystemConfigRepository.class),
        mock(AuditLogger.class),
        new ObjectMapper());

    Map<String, Object> result = service.list(new QuickSearchAdminListQuery(
        ContentType.IMAGE,
        "general",
        true,
        " banner ",
        -1,
        200,
        "updatedAt",
        "DESC"));

    assertThat(result).containsEntry("total", 21L)
        .containsEntry("page", 1)
        .containsEntry("size", 50)
        .containsEntry("totalPages", 1);
    ArgumentCaptor<QuickSearchAdminListQuery> query = ArgumentCaptor.forClass(QuickSearchAdminListQuery.class);
    verify(repository).count(query.capture());
    assertThat(query.getValue()).isEqualTo(new QuickSearchAdminListQuery(
        ContentType.IMAGE,
        "GENERAL",
        true,
        "banner",
        1,
        50,
        "updatedAt",
        "DESC"));
  }

  @Test
  void listRejectsInvalidFiltersBeforeRepositoryQuery() {
    QuickSearchAdminService service = new QuickSearchAdminService(
        mock(QuickSearchAdminRepository.class),
        mock(ApplicationEventPublisher.class),
        mock(WsPushService.class),
        mock(SystemAlertRepository.class),
        mock(QuickSearchImageStorage.class),
        mock(SystemConfigRepository.class),
        mock(AuditLogger.class),
        new ObjectMapper());

    assertThatThrownBy(() -> service.list(new QuickSearchAdminListQuery(
        null,
        "BROKEN",
        null,
        null,
        1,
        20,
        null,
        null)))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("线索类型不合法");

    assertThatThrownBy(() -> service.list(new QuickSearchAdminListQuery(
        null,
        null,
        null,
        null,
        1,
        20,
        "id;drop table",
        null)))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("排序字段不合法");
  }

  @Test
  void uploadImageStoresFileAndReturnsPublicUrl() throws Exception {
    SystemConfigRepository configRepository = mock(SystemConfigRepository.class);
    when(configRepository.findValue("quicksearch.storage.root")).thenReturn(Optional.of(tempDir.toString()));
    when(configRepository.findValue("quicksearch.storage.public_base_url")).thenReturn(Optional.of("/uploads/quick-search"));
    when(configRepository.findValue("quicksearch.admin.image_max_size_mb")).thenReturn(Optional.of("10"));
    QuickSearchImageStorage storage = new QuickSearchImageStorage(
        configRepository,
        tempDir.toString(),
        "/uploads/quick-search");
    QuickSearchAdminService service = new QuickSearchAdminService(
        mock(QuickSearchAdminRepository.class),
        mock(ApplicationEventPublisher.class),
        mock(WsPushService.class),
        mock(SystemAlertRepository.class),
        storage,
        configRepository,
        mock(AuditLogger.class),
        new ObjectMapper());

    ImageUploadResponse response = service.uploadImage(new MockMultipartFile(
        "file",
        "asset.png",
        "image/png",
        new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a}));

    assertThat(response.imageUrl()).startsWith("/uploads/quick-search/").endsWith(".png");
    assertThat(Files.walk(tempDir).filter(Files::isRegularFile).count()).isEqualTo(1);
  }

  @Test
  void uploadImageUsesConfiguredMaxSize() {
    SystemConfigRepository configRepository = mock(SystemConfigRepository.class);
    when(configRepository.findValue("quicksearch.admin.image_max_size_mb")).thenReturn(Optional.of("1"));
    QuickSearchAdminService service = new QuickSearchAdminService(
        mock(QuickSearchAdminRepository.class),
        mock(ApplicationEventPublisher.class),
        mock(WsPushService.class),
        mock(SystemAlertRepository.class),
        mock(QuickSearchImageStorage.class),
        configRepository,
        mock(AuditLogger.class),
        new ObjectMapper());

    assertThatThrownBy(() -> service.uploadImage(new MockMultipartFile(
        "file",
        "large.png",
        "image/png",
        new byte[1024 * 1024 + 1])))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("1MB");
  }

  @Test
  void createWritesAuditLogWithoutFullContent() {
    QuickSearchAdminRepository repository = mock(QuickSearchAdminRepository.class);
    AuditLogger auditLogger = mock(AuditLogger.class);
    QuickSearchAdminItem saved = new QuickSearchAdminItem(
        12L,
        ContentType.TEMPLATE,
        "GENERAL",
        "opening",
        "hi",
        "hello long content",
        null,
        9,
        true,
        "admin",
        null,
        null);
    when(repository.create(any(), anyString())).thenReturn(12L);
    when(repository.find(12L)).thenReturn(Optional.of(saved));
    QuickSearchAdminService service = new QuickSearchAdminService(
        repository,
        mock(ApplicationEventPublisher.class),
        mock(WsPushService.class),
        mock(SystemAlertRepository.class),
        mock(QuickSearchImageStorage.class),
        mock(SystemConfigRepository.class),
        auditLogger,
        new ObjectMapper());

    service.create(new QuickSearchItemRequest(ContentType.TEMPLATE, "GENERAL", "opening", "hi", "hello long content", null, 9, true, null));

    verify(auditLogger).log(eq("QUICK_SEARCH_CREATE"), anyString(), eq("template"), eq("12"), anyString());
  }
}
