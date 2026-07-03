package com.privateflow.modules.versions;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class DesktopVersionRepositoryTest {

  private JdbcTemplate jdbcTemplate;
  private DesktopVersionRepository repository;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource(
        "jdbc:h2:mem:versions;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "sa",
        "");
    jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.execute("DROP TABLE IF EXISTS desktop_versions");
    jdbcTemplate.execute("DROP TABLE IF EXISTS desktop_client_versions");
    jdbcTemplate.execute("""
        CREATE TABLE desktop_versions (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          version VARCHAR(20) NOT NULL,
          platform VARCHAR(10) NOT NULL,
          status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
          update_strategy VARCHAR(20) NOT NULL DEFAULT 'OPTIONAL',
          gradual_percent INT DEFAULT NULL,
          download_url VARCHAR(500) DEFAULT NULL,
          file_size BIGINT DEFAULT NULL,
          changelog TEXT NOT NULL,
          revoked_at DATETIME DEFAULT NULL,
          revoke_reason VARCHAR(500) DEFAULT NULL,
          alternative_version VARCHAR(20) DEFAULT NULL,
          published_at DATETIME DEFAULT NULL,
          created_by VARCHAR(20) NOT NULL,
          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
          updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
          UNIQUE (version, platform)
        )
        """);
    jdbcTemplate.execute("""
        CREATE TABLE desktop_client_versions (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          client_id VARCHAR(100) NOT NULL,
          version VARCHAR(20) NOT NULL,
          platform VARCHAR(10) NOT NULL,
          os_version VARCHAR(100) DEFAULT NULL,
          last_reported_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
          UNIQUE (client_id)
        )
        """);
    repository = new DesktopVersionRepository(jdbcTemplate);
  }

  @Test
  void createsUpdatesPublishesAndRevokesVersionRows() {
    long id = repository.create(new DesktopVersionCreateRequest(
        "1.2.3",
        DesktopPlatform.WINDOWS,
        "https://example.com/app.exe",
        "initial",
        UpdateStrategy.OPTIONAL,
        null,
        123L), "admin");

    DesktopVersion created = repository.findByVersionAndPlatform("1.2.3", DesktopPlatform.WINDOWS).orElseThrow();
    id = created.id();
    assertThat(created.status()).isEqualTo(VersionStatus.DRAFT);
    assertThat(created.updateStrategy()).isEqualTo(UpdateStrategy.OPTIONAL);
    assertThat(created.downloadUrl()).isEqualTo("https://example.com/app.exe");

    repository.update(id, new DesktopVersionUpdateRequest(
        "1.2.4",
        "https://example.com/app-updated.exe",
        "updated",
        UpdateStrategy.GRADUAL,
        35,
        456L), created);
    DesktopVersion updated = repository.find(id).orElseThrow();
    assertThat(updated.version()).isEqualTo("1.2.4");
    assertThat(updated.updateStrategy()).isEqualTo(UpdateStrategy.GRADUAL);
    assertThat(updated.gradualPercent()).isEqualTo(35);

    repository.publish(id);
    DesktopVersion published = repository.latestPublished(DesktopPlatform.WINDOWS).orElseThrow();
    assertThat(published.id()).isEqualTo(id);
    assertThat(published.status()).isEqualTo(VersionStatus.PUBLISHED);
    assertThat(published.publishedAt()).isNotNull();

    repository.revoke(id, new VersionRevokeRequest("bad installer", null));
    DesktopVersion revoked = repository.find(id).orElseThrow();
    assertThat(revoked.status()).isEqualTo(VersionStatus.REVOKED);
    assertThat(revoked.revokeReason()).isEqualTo("bad installer");
    assertThat(revoked.revokedAt()).isNotNull();
  }

  @Test
  void reportsDesktopClientVersionWithUpsertSemantics() {
    VersionReportRequest first = new VersionReportRequest("client-a", "1.0.0", DesktopPlatform.WINDOWS, "Windows 11");
    VersionReportRequest second = new VersionReportRequest("client-a", "1.0.1", DesktopPlatform.WINDOWS, "Windows 12");

    repository.report(first);
    repository.report(second);

    Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM desktop_client_versions WHERE client_id = 'client-a'", Long.class);
    String version = jdbcTemplate.queryForObject("SELECT version FROM desktop_client_versions WHERE client_id = 'client-a'", String.class);
    String osVersion = jdbcTemplate.queryForObject("SELECT os_version FROM desktop_client_versions WHERE client_id = 'client-a'", String.class);
    assertThat(count).isEqualTo(1L);
    assertThat(version).isEqualTo("1.0.1");
    assertThat(osVersion).isEqualTo("Windows 12");
  }
}
