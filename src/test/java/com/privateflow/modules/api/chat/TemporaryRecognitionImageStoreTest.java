package com.privateflow.modules.api.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.privateflow.modules.customer.infra.SystemConfigRepository;
import com.privateflow.modules.image.config.ImageConfig;
import com.privateflow.modules.image.config.ImageConfigProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class TemporaryRecognitionImageStoreTest {

  private static final Instant NOW = Instant.parse("2026-07-23T10:00:00Z");

  @TempDir
  Path temporaryDirectory;

  private final Map<String, String> configValues = new HashMap<>();
  private TemporaryRecognitionImageStore store;
  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    configValues.put("chat.recognition_temp_root", temporaryDirectory.toString());
    configValues.put("chat.recognition_temp_ttl_seconds", "600");
    configValues.put("chat.recognition_temp_max_total_bytes", "100");

    SystemConfigRepository configRepository = mock(SystemConfigRepository.class);
    when(configRepository.findValue(org.mockito.ArgumentMatchers.anyString())).thenAnswer(invocation ->
        Optional.ofNullable(configValues.get(invocation.getArgument(0))));

    ImageConfigProvider imageConfigProvider = mock(ImageConfigProvider.class);
    when(imageConfigProvider.get()).thenReturn(new ImageConfig(
        "", "", 5000, 20, 1920, 85, "", "", 3));

    store = new TemporaryRecognitionImageStore(
        configRepository,
        imageConfigProvider,
        Clock.fixed(NOW, ZoneOffset.UTC));

    DriverManagerDataSource dataSource = new DriverManagerDataSource(
        "jdbc:h2:mem:temporary_recognition_store;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
    jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.execute("DROP TABLE IF EXISTS pending_reply_tasks");
    jdbcTemplate.execute("CREATE TABLE pending_reply_tasks (id BIGINT PRIMARY KEY, image_base64 CLOB, ocr_payload CLOB)");
  }

  @Test
  void deletesImageAfterSuccessfulReadAndLeavesNoBusinessRecord() {
    byte[] image = "customer chat".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    String token = store.put(image);

    assertThat(store.read(token)).isEqualTo(image);
    store.delete(token);

    assertThat(store.exists(token)).isFalse();
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pending_reply_tasks", Long.class)).isZero();
  }

  @Test
  void removesExpiredFilesAfterTenMinutes() throws Exception {
    String token = store.put("expired image".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    Files.setLastModifiedTime(
        temporaryDirectory.resolve(token + ".jpg"),
        FileTime.from(NOW));

    store.cleanupExpired(NOW.plusSeconds(600));

    assertThat(store.exists(token)).isFalse();
  }

  @Test
  void enforcesSingleImageAndTotalTemporaryCapacity() {
    configValues.put("chat.recognition_temp_max_total_bytes", "4");

    store.put(new byte[] {1, 2, 3});

    assertThatThrownBy(() -> store.put(new byte[] {4, 5}))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("temporary recognition image capacity");
    assertThatThrownBy(() -> store.put(new byte[21]))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("image limit");
  }

  @Test
  void clearsExpiredFilesBeforeRejectingAFullTemporaryDirectory() throws Exception {
    configValues.put("chat.recognition_temp_max_total_bytes", "4");
    String expiredToken = store.put(new byte[] {1, 2, 3});
    Files.setLastModifiedTime(
        temporaryDirectory.resolve(expiredToken + ".jpg"),
        FileTime.from(NOW.minusSeconds(600)));

    String freshToken = store.put(new byte[] {4, 5});

    assertThat(store.exists(expiredToken)).isFalse();
    assertThat(store.exists(freshToken)).isTrue();
  }

  @Test
  void rejectsTokensThatCouldEscapeTheTemporaryDirectory() {
    assertThatThrownBy(() -> store.read("../outside"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("invalid recognition image token");
  }
}
