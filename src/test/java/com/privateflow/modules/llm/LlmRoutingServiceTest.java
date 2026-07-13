package com.privateflow.modules.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.ai.AiEnvironmentRepository;
import com.privateflow.modules.api.ai.AiEnvironmentRequest;
import com.privateflow.modules.api.ai.AiEnvironmentType;
import com.privateflow.modules.api.security.SecretCipher;
import com.privateflow.modules.customer.infra.SystemConfigRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class LlmRoutingServiceTest {

  private JdbcTemplate jdbcTemplate;
  private AiEnvironmentRepository environmentRepository;
  private LlmRouteRepository routeRepository;
  private LlmRoutingService routingService;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource(
        "jdbc:h2:mem:llm_routes;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "sa",
        "");
    jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.execute("DROP TABLE IF EXISTS llm_scene_routes");
    jdbcTemplate.execute("DROP TABLE IF EXISTS llm_environments");
    jdbcTemplate.execute("""
        CREATE TABLE llm_environments (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          env_name VARCHAR(50) NOT NULL,
          provider VARCHAR(20) NOT NULL,
          base_url VARCHAR(500) NOT NULL,
          api_key VARCHAR(500) NOT NULL,
          api_key_last4 VARCHAR(4) NOT NULL,
          model VARCHAR(100) NOT NULL,
          protocol VARCHAR(50) NOT NULL DEFAULT 'OPENAI_COMPATIBLE',
          timeout_ms INT NOT NULL DEFAULT 10000,
          temperature DECIMAL(4,2) NOT NULL DEFAULT 0.20,
          max_tokens INT NOT NULL DEFAULT 1024,
          is_active TINYINT NOT NULL DEFAULT 0,
          last_test_at DATETIME DEFAULT NULL,
          last_test_ok TINYINT DEFAULT NULL,
          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
          updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
        """);
    jdbcTemplate.execute("""
        CREATE TABLE llm_scene_routes (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          scene VARCHAR(50) NOT NULL,
          lead_type VARCHAR(20) NOT NULL DEFAULT '',
          llm_environment_id BIGINT NOT NULL,
          priority INT NOT NULL DEFAULT 0,
          enabled TINYINT NOT NULL DEFAULT 1,
          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
          updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
        """);
    SecretCipher secretCipher = new SecretCipher("test-secret-key");
    environmentRepository = new AiEnvironmentRepository(jdbcTemplate, secretCipher);
    routeRepository = new LlmRouteRepository(jdbcTemplate);
    SystemConfigRepository configRepository = Mockito.mock(SystemConfigRepository.class);
    Mockito.when(configRepository.findValue(Mockito.anyString())).thenReturn(Optional.empty());
    LlmConfigProvider configProvider = new LlmConfigProvider(configRepository, secretCipher);
    routingService = new LlmRoutingService(routeRepository, environmentRepository, configProvider);
  }

  @Test
  void createsRouteAndResolvesSceneSpecificEnvironment() {
    long environmentId = createEnvironment("llm-prod", "https://llm.example.com", "secret-1234", true);

    LlmSceneRoute route = routingService.create(new LlmRouteRequest(
        LlmScene.REPLY_GENERATION,
        "TUAN_GOU",
        environmentId,
        5,
        true));

    assertThat(route.id()).isPositive();
    assertThat(route.environmentName()).isEqualTo("llm-prod");
    assertThat(route.model()).isEqualTo("gpt-4.1-mini");

    LlmRouteResolution resolved = routingService.resolve(LlmScene.REPLY_GENERATION, "TUAN_GOU");

    assertThat(resolved.routeId()).isEqualTo(route.id());
    assertThat(resolved.environmentId()).isEqualTo(environmentId);
    assertThat(resolved.config().apiKey()).isEqualTo("secret-1234");
    assertThat(resolved.fallbackToActive()).isFalse();
  }

  @Test
  void fallsBackToActiveEnvironmentWhenNoRouteIsConfigured() {
    long environmentId = createEnvironment("llm-active", "https://active.example.com", "secret-5678", true);

    LlmRouteResolution resolved = routingService.resolve(LlmScene.SUMMARY, "XIAN_SUO");

    assertThat(resolved.routeId()).isNull();
    assertThat(resolved.environmentId()).isEqualTo(environmentId);
    assertThat(resolved.config().apiBaseUrl()).isEqualTo("https://active.example.com");
    assertThat(resolved.fallbackToActive()).isTrue();
  }

  @Test
  void resolvesOrderedBackupCandidatesBeforeActiveFallback() {
    long activeId = createEnvironment("llm-active", "https://active.example.com", "secret-active", true);
    long backupId = createEnvironment("llm-backup", "https://backup.example.com", "secret-backup", false);
    long primaryId = createEnvironment("llm-primary", "https://primary.example.com", "secret-primary", false);
    routingService.create(new LlmRouteRequest(LlmScene.REPLY_GENERATION, "TUAN_GOU", backupId, 20, true));
    routingService.create(new LlmRouteRequest(LlmScene.REPLY_GENERATION, "TUAN_GOU", primaryId, 10, true));

    java.util.List<LlmRouteResolution> candidates = routingService.resolveCandidates(LlmScene.REPLY_GENERATION, "TUAN_GOU");

    assertThat(candidates).extracting(LlmRouteResolution::environmentId)
        .containsExactly(primaryId, backupId, activeId);
    assertThat(candidates).extracting(LlmRouteResolution::fallbackToActive)
        .containsExactly(false, false, true);
  }

  @Test
  void rejectsDuplicateRouteForSameSceneLeadTypeAndEnvironment() {
    long environmentId = createEnvironment("llm-prod", "https://llm.example.com", "secret-1234", false);
    LlmRouteRequest request = new LlmRouteRequest(LlmScene.PROFILE_EXTRACTION, "PENDING", environmentId, 0, true);
    routingService.create(request);

    assertThatThrownBy(() -> routingService.create(request))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("同一场景、线索类型和 LLM 环境不能重复配置");
  }

  private long createEnvironment(String name, String baseUrl, String apiKey, boolean active) {
    environmentRepository.create(AiEnvironmentType.LLM, new AiEnvironmentRequest(
        name,
        baseUrl,
        apiKey,
        "gpt-4.1-mini",
        "OPENAI_COMPATIBLE",
        10000,
        0.2,
        1024));
    long id = environmentRepository.list(AiEnvironmentType.LLM).stream()
        .filter(environment -> name.equals(environment.envName()))
        .findFirst()
        .orElseThrow()
        .id();
    if (active) {
      environmentRepository.activate(AiEnvironmentType.LLM, environmentRepository.find(AiEnvironmentType.LLM, id).orElseThrow());
    }
    return id;
  }
}
