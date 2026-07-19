package com.privateflow.modules.api.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.privateflow.modules.api.security.SecretCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class AiEnvironmentRepositoryTest {

  private JdbcTemplate jdbcTemplate;
  private SecretCipher secretCipher;
  private AiEnvironmentRepository repository;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource(
        "jdbc:h2:mem:ai_env;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "sa",
        "");
    jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.execute("DROP TABLE IF EXISTS skill_environments");
    jdbcTemplate.execute("DROP TABLE IF EXISTS image_environments");
    jdbcTemplate.execute("DROP TABLE IF EXISTS llm_environments");
    jdbcTemplate.execute("""
        CREATE TABLE skill_environments (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          env_name VARCHAR(50) NOT NULL,
          provider VARCHAR(20) NOT NULL,
          base_url VARCHAR(500) NOT NULL,
          api_key VARCHAR(500) NOT NULL,
          api_key_last4 VARCHAR(4) NOT NULL,
          protocol VARCHAR(50) NOT NULL DEFAULT 'OPENAI_COMPATIBLE',
          is_active TINYINT NOT NULL DEFAULT 0,
          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
          updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
        """);
    jdbcTemplate.execute("""
        CREATE TABLE image_environments (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          env_name VARCHAR(50) NOT NULL,
          provider VARCHAR(20) NOT NULL,
          base_url VARCHAR(500) NOT NULL,
          api_key VARCHAR(500) NOT NULL,
          api_key_last4 VARCHAR(4) NOT NULL,
          is_active TINYINT NOT NULL DEFAULT 0,
          last_test_at DATETIME DEFAULT NULL,
          last_test_ok TINYINT DEFAULT NULL,
          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
          updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
        """);
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
    secretCipher = new SecretCipher("test-secret-key");
    repository = new AiEnvironmentRepository(jdbcTemplate, secretCipher);
  }

  @Test
  void storesEncryptedSecretAndPreservesExistingSecretWhenApiKeyOmitted() {
    repository.create(AiEnvironmentType.SKILL, new AiEnvironmentRequest("生产", "https://skill.example.com", "secret-1234"));
    long id = repository.list(AiEnvironmentType.SKILL).get(0).id();
    String encrypted = repository.encryptedApiKey(AiEnvironmentType.SKILL, id);

    assertThat(encrypted).startsWith("{aes-gcm}");
    assertThat(encrypted).doesNotContain("secret-1234");

    repository.update(AiEnvironmentType.SKILL, id, new AiEnvironmentRequest("生产二", "https://skill2.example.com", null));

    AiEnvironment updated = repository.find(AiEnvironmentType.SKILL, id).orElseThrow();
    assertThat(updated.envName()).isEqualTo("生产二");
    assertThat(updated.baseUrl()).isEqualTo("https://skill2.example.com");
    assertThat(updated.apiKeyLast4()).isEqualTo("1234");
    assertThat(updated.protocol()).isEqualTo("OPENAI_COMPATIBLE");
    assertThat(secretCipher.decrypt(repository.encryptedApiKey(AiEnvironmentType.SKILL, id))).isEqualTo("secret-1234");
    assertThat(repository.decryptApiKey(AiEnvironmentType.SKILL, id)).isEqualTo("secret-1234");
  }

  @Test
  void storesLlmEnvironmentParametersAndPreservesSecretWhenApiKeyOmitted() {
    repository.create(AiEnvironmentType.LLM, new AiEnvironmentRequest(
        "LLM 生产",
        "https://llm.example.com",
        "llm-secret-9999",
        "gpt-4.1-mini",
        "openai_compatible",
        10000,
        0.2,
        1024));
    long id = repository.list(AiEnvironmentType.LLM).get(0).id();
    String encrypted = repository.encryptedApiKey(AiEnvironmentType.LLM, id);

    assertThat(encrypted).startsWith("{aes-gcm}");
    assertThat(encrypted).doesNotContain("llm-secret-9999");

    repository.update(AiEnvironmentType.LLM, id, new AiEnvironmentRequest(
        "LLM 备用",
        "https://llm-backup.example.com",
        "",
        "qwen-plus",
        "OPENAI_COMPATIBLE",
        15000,
        0.3,
        2048));

    AiEnvironment updated = repository.find(AiEnvironmentType.LLM, id).orElseThrow();
    assertThat(updated.envName()).isEqualTo("LLM 备用");
    assertThat(updated.baseUrl()).isEqualTo("https://llm-backup.example.com");
    assertThat(updated.model()).isEqualTo("qwen-plus");
    assertThat(updated.protocol()).isEqualTo("OPENAI_COMPATIBLE");
    assertThat(updated.timeoutMs()).isEqualTo(15000);
    assertThat(updated.temperature()).isEqualTo(0.3);
    assertThat(updated.maxTokens()).isEqualTo(2048);
    assertThat(updated.apiKeyLast4()).isEqualTo("9999");
    assertThat(secretCipher.decrypt(repository.encryptedApiKey(AiEnvironmentType.LLM, id))).isEqualTo("llm-secret-9999");
  }
}
