import { reactive } from 'vue';
import { getJson, postJson } from '../../shared/apiClient';
import { writeClipboardText } from '../../shared/desktopBridge';
import type {
  PersonalTemplate,
  PersonalTemplateDraft,
  TeamTemplate,
  TemplateLibraryTab,
  TemplateMetadata
} from './templateTypes';

const EMPTY_METADATA: TemplateMetadata = { labels: [] };

export const templateLibraryState = reactive({
  visible: false,
  tab: 'PERSONAL' as TemplateLibraryTab,
  personal: [] as PersonalTemplate[],
  team: [] as TeamTemplate[],
  loading: false,
  saving: false,
  copyingId: '',
  error: '',
  toast: ''
});

export const templateEditorState = reactive({
  visible: false,
  draft: emptyDraft()
});

export async function openTemplateLibrary(tab: TemplateLibraryTab = 'PERSONAL'): Promise<void> {
  templateLibraryState.visible = true;
  templateLibraryState.tab = tab;
  await refreshTemplateLibrary();
}

export function closeTemplateLibrary(): void {
  templateLibraryState.visible = false;
}

export function setTemplateLibraryTab(tab: TemplateLibraryTab): void {
  templateLibraryState.tab = tab;
}

export async function refreshTemplateLibrary(): Promise<void> {
  templateLibraryState.loading = true;
  templateLibraryState.error = '';
  try {
    const [personalResponse, teamResponse] = await Promise.all([
      getJson<PersonalTemplate[]>('/api/v1/templates/personal'),
      getJson<TeamTemplate[]>('/api/v1/templates/team')
    ]);
    if (!personalResponse.success || !teamResponse.success) {
      throw new Error(personalResponse.message || teamResponse.message || '模板加载失败');
    }
    templateLibraryState.personal = personalResponse.data ?? [];
    templateLibraryState.team = teamResponse.data ?? [];
  } catch (error) {
    templateLibraryState.error = error instanceof Error ? error.message : '模板加载失败';
  } finally {
    templateLibraryState.loading = false;
  }
}

export function openPersonalTemplateEditor(draft?: Partial<PersonalTemplateDraft>): void {
  templateEditorState.draft = {
    title: draft?.title?.trim() ?? '',
    body: draft?.body ?? '',
    originalAiReply: draft?.originalAiReply ?? draft?.body ?? '',
    metadata: normalizeMetadata(draft?.metadata),
    sourceReplySessionId: draft?.sourceReplySessionId ?? null
  };
  templateEditorState.visible = true;
}

export function closePersonalTemplateEditor(): void {
  templateEditorState.visible = false;
}

export async function savePersonalTemplate(draft: PersonalTemplateDraft): Promise<boolean> {
  templateLibraryState.saving = true;
  templateLibraryState.error = '';
  try {
    const response = await postJson<PersonalTemplate>('/api/v1/templates/personal', normalizeDraft(draft));
    if (!response.success || !response.data) {
      throw new Error(response.message || '模板保存失败');
    }
    templateLibraryState.personal = [response.data, ...templateLibraryState.personal.filter((item) => item.id !== response.data?.id)];
    templateLibraryState.tab = 'PERSONAL';
    templateLibraryState.visible = true;
    templateLibraryState.toast = '已保存到我的模板';
    closePersonalTemplateEditor();
    return true;
  } catch (error) {
    templateLibraryState.error = error instanceof Error ? error.message : '模板保存失败';
    return false;
  } finally {
    templateLibraryState.saving = false;
  }
}

export async function copyPersonalTemplate(template: PersonalTemplate): Promise<void> {
  const copied = await copyText(template.body);
  if (!copied) return;
  await recordUse(`/api/v1/templates/personal/${template.id}/use`, `personal-${template.id}`);
}

export async function copyTeamTemplate(template: TeamTemplate): Promise<void> {
  const copied = await copyText(template.body);
  if (!copied) return;
  await recordUse(`/api/v1/templates/team/${template.quickSearchItemId}/use`, `team-${template.quickSearchItemId}`);
}

async function copyText(body: string): Promise<boolean> {
  templateLibraryState.error = '';
  const result = await writeClipboardText(body);
  if (!result?.success) {
    templateLibraryState.error = '复制失败，请重试';
    return false;
  }
  templateLibraryState.toast = '已复制到剪贴板';
  return true;
}

async function recordUse(path: string, copyingId: string): Promise<void> {
  templateLibraryState.copyingId = copyingId;
  try {
    const response = await postJson<{ recorded: boolean }>(path, {});
    if (!response.success) {
      templateLibraryState.error = response.message || '复制已完成，但使用记录失败';
    }
  } catch {
    templateLibraryState.error = '复制已完成，但使用记录失败';
  } finally {
    templateLibraryState.copyingId = '';
  }
}

function emptyDraft(): PersonalTemplateDraft {
  return {
    title: '',
    body: '',
    originalAiReply: '',
    metadata: { ...EMPTY_METADATA },
    sourceReplySessionId: null
  };
}

function normalizeDraft(draft: PersonalTemplateDraft): PersonalTemplateDraft {
  return {
    title: draft.title.trim(),
    body: draft.body.trim(),
    originalAiReply: draft.originalAiReply.trim(),
    metadata: normalizeMetadata(draft.metadata),
    sourceReplySessionId: draft.sourceReplySessionId?.trim() || null
  };
}

function normalizeMetadata(metadata?: TemplateMetadata | null): TemplateMetadata {
  return {
    channelCode: metadata?.channelCode?.trim() || null,
    scene: metadata?.scene?.trim() || null,
    leadType: metadata?.leadType?.trim() || null,
    labels: (metadata?.labels ?? []).map((label) => label.trim()).filter(Boolean)
  };
}
