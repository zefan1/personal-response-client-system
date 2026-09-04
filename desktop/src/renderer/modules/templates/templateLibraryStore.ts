import { reactive } from 'vue';
import { getJson, postJson } from '../../shared/apiClient';
import { writeClipboardImage, writeClipboardText } from '../../shared/desktopBridge';
import { resolveQuickSearchTemplate } from '../quick-search/templateVariables';
import type { QuickSearchCustomerContext, QuickSearchItem } from '../quick-search/types';
import type {
  PersonalTemplate,
  PersonalTemplateDraft,
  TeamTemplate,
  TemplateLibraryTab,
  TemplateMetadata
} from './templateTypes';

const EMPTY_METADATA: TemplateMetadata = { labels: [] };

export type TemplateLibraryScope = 'ALL' | 'TEAM' | 'PERSONAL';
export type TemplateLibraryContentFilter = 'ALL' | 'TEXT' | 'IMAGE';

export const templateLibraryState = reactive({
  visible: false,
  tab: 'PERSONAL' as TemplateLibraryTab,
  personal: [] as PersonalTemplate[],
  team: [] as TeamTemplate[],
  shortcuts: [] as QuickSearchItem[],
  query: '',
  scope: 'ALL' as TemplateLibraryScope,
  contentFilter: 'ALL' as TemplateLibraryContentFilter,
  customerContext: null as QuickSearchCustomerContext | null,
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

export async function openTemplateLibrary(context?: QuickSearchCustomerContext): Promise<void> {
  templateLibraryState.visible = true;
  templateLibraryState.tab = 'PERSONAL';
  templateLibraryState.query = '';
  templateLibraryState.scope = 'ALL';
  templateLibraryState.contentFilter = 'ALL';
  templateLibraryState.customerContext = context?.phone?.trim() ? context : null;
  await refreshTemplateLibrary();
}

export function closeTemplateLibrary(): void {
  templateLibraryState.visible = false;
  templateLibraryState.customerContext = null;
}

export function setTemplateLibraryScope(scope: TemplateLibraryScope): void {
  templateLibraryState.scope = scope;
}

export function setTemplateLibraryContentFilter(filter: TemplateLibraryContentFilter): void {
  templateLibraryState.contentFilter = templateLibraryState.contentFilter === filter ? 'ALL' : filter;
}

export function setTemplateLibraryQuery(query: string): void {
  templateLibraryState.query = query;
}

export async function refreshTemplateLibrary(): Promise<void> {
  templateLibraryState.loading = true;
  templateLibraryState.error = '';
  try {
    const [personalResponse, teamResponse, shortcutsResponse] = await Promise.all([
      getJson<PersonalTemplate[]>('/api/v1/templates/personal'),
      getJson<TeamTemplate[]>('/api/v1/templates/team'),
      getJson<QuickSearchItem[]>('/api/v1/quick-search/items', 5000)
    ]);
    if (!personalResponse.success || !teamResponse.success || !shortcutsResponse.success) {
      throw new Error(personalResponse.message || teamResponse.message || shortcutsResponse.message || '模板加载失败');
    }
    templateLibraryState.personal = personalResponse.data ?? [];
    templateLibraryState.team = teamResponse.data ?? [];
    const publishedIds = new Set(templateLibraryState.team.map((item) => item.quickSearchItemId));
    templateLibraryState.shortcuts = (shortcutsResponse.data ?? [])
      .filter((item) => item.isEnabled && !publishedIds.has(item.id))
      .sort((left, right) => left.sortOrder - right.sortOrder || left.shortcutCode.localeCompare(right.shortcutCode));
  } catch (error) {
    templateLibraryState.error = error instanceof Error ? error.message : '模板加载失败';
  } finally {
    templateLibraryState.loading = false;
  }
}

export function handleTemplateLibraryConfigRefresh(payload: { configKeys?: string[] }): void {
  if (!templateLibraryState.visible || !payload.configKeys?.includes('quick_search')) {
    return;
  }
  void refreshTemplateLibrary();
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
    templateLibraryState.visible = true;
    templateLibraryState.toast = '已保存到我的话术';
    closePersonalTemplateEditor();
    return true;
  } catch (error) {
    templateLibraryState.error = error instanceof Error ? error.message : '模板保存失败';
    return false;
  } finally {
    templateLibraryState.saving = false;
  }
}

export async function copyPersonalTemplate(template: PersonalTemplate, body = template.body): Promise<void> {
  const copied = await copyText(body);
  if (!copied) return;
  await recordUse(`/api/v1/templates/personal/${template.id}/use`, `personal-${template.id}`);
}

export async function copyTeamTemplate(template: TeamTemplate, body = template.body): Promise<void> {
  const copied = await copyText(body);
  if (!copied) return;
  await recordUse(`/api/v1/templates/team/${template.quickSearchItemId}/use`, `team-${template.quickSearchItemId}`);
}

export async function copyShortcutSpeech(item: QuickSearchItem): Promise<void> {
  templateLibraryState.error = '';
  if (item.contentType === 'IMAGE') {
    if (!item.imageUrl) {
      templateLibraryState.error = '图片素材缺少链接';
      return;
    }
    const result = await writeClipboardImage(item.imageUrl);
    if (!result.success) {
      templateLibraryState.error = '图片复制失败，请重试';
      return;
    }
    templateLibraryState.toast = '图片已复制到剪贴板';
    return;
  }
  const context = templateLibraryState.customerContext;
  const body = resolveQuickSearchTemplate(item.content, context?.customer ?? {}, context?.phone ?? '');
  await copyText(withEntryLink(item, body));
}

export function resolveTemplateLibraryText(body: string): string {
  const context = templateLibraryState.customerContext;
  return resolveQuickSearchTemplate(body, context?.customer ?? {}, context?.phone ?? '');
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

function withEntryLink(item: QuickSearchItem, body: string): string {
  if (item.contentType === 'IMAGE') return body;
  const link = item.imageUrl?.trim();
  return link ? `${body}\n${link}` : body;
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
