package com.privateflow.modules.templates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.Role;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.auth.AuthUser;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PersonalTemplateServiceTest {

  @AfterEach
  void clearAuthentication() {
    AuthContext.clear();
  }

  @Test
  void savesImmediatelyForEmployeeAndCreatesCandidateSilently() {
    PersonalTemplateRepository repository = mock(PersonalTemplateRepository.class);
    TemplateMetadata metadata = new TemplateMetadata("wecom", "new-lead", "LEAD", List.of("warm"));
    when(repository.insertPersonal(eq("keeper-a"), any(), any(), any(), any())).thenReturn(41L);
    when(repository.findPersonal(41L, "keeper-a")).thenReturn(java.util.Optional.of(template(41L, metadata)));
    AuthContext.set(new AuthUser("keeper-a", "Keeper A", Role.KEEPER, null));

    PersonalTemplate saved = new PersonalTemplateService(repository).save(new PersonalTemplateRequest(
        " Opening ", " Edited body ", " Original AI body ", metadata, "reply-session-1"));

    assertThat(saved.title()).isEqualTo("Opening");
    assertThat(saved.body()).isEqualTo("Edited body");
    assertThat(saved.metadata()).isEqualTo(metadata);
    verify(repository).insertCandidate(
        41L, "keeper-a", "Original AI body", "Opening", "Edited body", metadata);
  }

  @Test
  void rejectsInvalidLabelsBeforePersistingTemplate() {
    AuthContext.set(new AuthUser("keeper-a", "Keeper A", Role.KEEPER, null));
    PersonalTemplateRepository repository = mock(PersonalTemplateRepository.class);
    List<String> labels = java.util.Collections.nCopies(21, "too-many");

    assertThatThrownBy(() -> new PersonalTemplateService(repository).save(new PersonalTemplateRequest(
        "Opening", "Body", "AI", new TemplateMetadata(null, null, null, labels), null)))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("labels");
  }

  private PersonalTemplate template(long id, TemplateMetadata metadata) {
    LocalDateTime now = LocalDateTime.of(2026, 7, 24, 13, 0);
    return new PersonalTemplate(id, "Opening", "Edited body", metadata, "reply-session-1", 0, now, now);
  }
}
