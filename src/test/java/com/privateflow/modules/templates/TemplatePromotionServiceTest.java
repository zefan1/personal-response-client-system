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
import com.privateflow.modules.quicksearch.admin.QuickSearchAdminService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TemplatePromotionServiceTest {

  @AfterEach
  void clearAuthentication() {
    AuthContext.clear();
  }

  @Test
  void publishesCandidateOnceThroughQuickSearchAndBroadcastsRefresh() {
    PersonalTemplateRepository repository = mock(PersonalTemplateRepository.class);
    QuickSearchAdminService quickSearch = mock(QuickSearchAdminService.class);
    when(repository.findCandidateForUpdate(42L)).thenReturn(Optional.of(candidate(42L, TemplatePromotionCandidateStatus.CANDIDATE)));
    when(quickSearch.createTeamTemplate(any())).thenReturn(77L);
    AuthContext.set(new AuthUser("admin-a", "Admin A", Role.ADMIN, null));

    Map<String, Object> published = new TemplatePromotionService(repository, quickSearch).publish(
        42L, new PublishTeamTemplateRequest("Team opening", "TM42", null, true));

    assertThat(published).containsEntry("candidateId", 42L).containsEntry("quickSearchItemId", 77L);
    verify(repository).insertPublication(42L, 77L, "admin-a");
    verify(repository).markPublished(42L, "admin-a");
    verify(quickSearch).broadcastTeamTemplateRefresh();
  }

  @Test
  void refusesToPublishAnAlreadyDecidedCandidate() {
    PersonalTemplateRepository repository = mock(PersonalTemplateRepository.class);
    when(repository.findCandidateForUpdate(42L)).thenReturn(Optional.of(candidate(42L, TemplatePromotionCandidateStatus.PUBLISHED)));
    AuthContext.set(new AuthUser("admin-a", "Admin A", Role.ADMIN, null));

    assertThatThrownBy(() -> new TemplatePromotionService(repository, mock(QuickSearchAdminService.class)).publish(
        42L, new PublishTeamTemplateRequest("Team opening", "TM42", null, true)))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("already decided");
  }

  @Test
  void notPublishingChangesOnlySupervisorCandidateState() {
    PersonalTemplateRepository repository = mock(PersonalTemplateRepository.class);
    when(repository.findCandidateForUpdate(43L)).thenReturn(Optional.of(candidate(43L, TemplatePromotionCandidateStatus.CANDIDATE)));
    AuthContext.set(new AuthUser("admin-a", "Admin A", Role.ADMIN, null));

    new TemplatePromotionService(repository, mock(QuickSearchAdminService.class)).markNotPublished(43L);

    verify(repository).markNotPublished(43L, "admin-a");
  }

  private TemplatePromotionCandidate candidate(long id, TemplatePromotionCandidateStatus status) {
    return new TemplatePromotionCandidate(
        id,
        11L,
        "keeper-a",
        "Original AI reply",
        "Edited opening",
        "Edited body",
        new TemplateMetadata("wecom", "new-lead", "LEAD", List.of("warm")),
        status,
        null,
        null,
        LocalDateTime.of(2026, 7, 24, 13, 0),
        3L);
  }
}
