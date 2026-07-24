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
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class TemporaryRecognitionImageStoreTest {

  private static final Instant NOW = Instant.parse("2026-07-23T10:00:00Z");

  @TempDir
  Path temporaryDirectory;

  @TempDir
  Path externalDirectory;

  private final Map<String, String> configValues = new HashMap<>();
  private SystemConfigRepository configRepository;
  private ImageConfigProvider imageConfigProvider;
  private TemporaryRecognitionImageStore store;
  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    configValues.put("chat.recognition_temp_root", "active");
    configValues.put("chat.recognition_temp_ttl_seconds", "600");
    configValues.put("chat.recognition_temp_max_total_bytes", "100");

    configRepository = mock(SystemConfigRepository.class);
    when(configRepository.findValue(org.mockito.ArgumentMatchers.anyString())).thenAnswer(invocation ->
        Optional.ofNullable(configValues.get(invocation.getArgument(0))));

    imageConfigProvider = mock(ImageConfigProvider.class);
    when(imageConfigProvider.get()).thenReturn(new ImageConfig(
        "", "", 5000, 20, 1920, 85, "", "", 3));

    store = new TemporaryRecognitionImageStore(
        configRepository,
        imageConfigProvider,
        Clock.fixed(NOW, ZoneOffset.UTC),
        temporaryDirectory);

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
        storedImagePath(token),
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
        storedImagePath(expiredToken),
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

  @Test
  void rejectsAnAbsoluteTemporaryDirectoryOutsideTheApplicationRoot() {
    configValues.put("chat.recognition_temp_root", externalDirectory.toString());

    assertThatThrownBy(() -> store.put(new byte[] {1}))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("relative directory");
  }

  @Test
  void rejectsTraversalOutsideTheApplicationTemporaryRoot() throws Exception {
    String traversal = "../../recognition-escape-" + UUID.randomUUID();
    Path escapedRoot = Path.of(traversal).toAbsolutePath().normalize();
    configValues.put("chat.recognition_temp_root", traversal);

    try {
      assertThatThrownBy(() -> store.put(new byte[] {1}))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("relative directory");
    } finally {
      deleteTree(escapedRoot);
    }
  }

  @Test
  void enforcesFiveMegabyteHardLimitWhenImageConfigurationAllowsMore() {
    int hardLimit = 5 * 1024 * 1024;
    configValues.put("chat.recognition_temp_max_total_bytes", String.valueOf(hardLimit + 2));
    ImageConfigProvider permissiveImageConfig = mock(ImageConfigProvider.class);
    when(permissiveImageConfig.get()).thenReturn(new ImageConfig(
        "", "", 5000, hardLimit * 4, 1920, 85, "", "", 3));
    TemporaryRecognitionImageStore permissiveStore = new TemporaryRecognitionImageStore(
        configRepository,
        permissiveImageConfig,
        Clock.fixed(NOW, ZoneOffset.UTC),
        temporaryDirectory);

    assertThatThrownBy(() -> permissiveStore.put(new byte[hardLimit + 1]))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("5MB hard limit");
  }

  private void deleteTree(Path root) throws Exception {
    if (!Files.exists(root)) {
      return;
    }
    try (Stream<Path> paths = Files.walk(root)) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(path);
      }
    }
  }

  private Path storedImagePath(String token) {
    return temporaryDirectory.resolve("active").resolve(token + ".jpg");
  }
}
