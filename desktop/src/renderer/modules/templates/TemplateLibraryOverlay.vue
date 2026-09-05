<template>
  <section v-if="state.visible" class="template-library-overlay" @click.self="closeTemplateLibrary" @keydown.esc="closeTemplateLibrary">
    <Transition name="template-toast">
      <p v-if="state.toast" class="template-library-toast" role="status" aria-live="polite">{{ state.toast }}</p>
    </Transition>
    <aside class="template-library-shell" aria-label="话术库">
      <header class="template-library-head">
        <div>
          <h2>话术库</h2>
          <p v-if="state.customerContext">当前客户：{{ customerContextLabel }}</p>
          <p v-else>向下滚动浏览全部话术</p>
        </div>
        <div class="template-library-head-actions">
          <button class="primary small" type="button" @click="openPersonalTemplateEditor()">+ 新建话术</button>
          <button class="icon-close-button" type="button" aria-label="关闭话术库" title="关闭话术库" @click="closeTemplateLibrary">
            <span aria-hidden="true">×</span>
          </button>
        </div>
      </header>

      <input
        class="template-library-search"
        :value="state.query"
        placeholder="搜索标题、内容或快捷码"
        aria-label="搜索话术"
        @input="setTemplateLibraryQuery(($event.target as HTMLInputElement).value)"
      />

      <nav class="template-library-filters" aria-label="话术筛选">
        <button :class="{ active: state.scope === 'ALL' }" type="button" @click="setTemplateLibraryScope('ALL')">全部 {{ speechEntries.length }}</button>
        <button :class="{ active: state.scope === 'TEAM' }" type="button" @click="setTemplateLibraryScope('TEAM')">团队 {{ teamCount }}</button>
        <button :class="{ active: state.scope === 'PERSONAL' }" type="button" @click="setTemplateLibraryScope('PERSONAL')">我的 {{ state.personal.length }}</button>
        <button :class="{ active: state.contentFilter === 'TEXT' }" type="button" @click="setTemplateLibraryContentFilter('TEXT')">文本</button>
        <button :class="{ active: state.contentFilter === 'IMAGE' }" type="button" @click="setTemplateLibraryContentFilter('IMAGE')">图片</button>
      </nav>

      <p v-if="state.error" class="template-library-notice error">{{ state.error }}</p>

      <div v-if="state.loading" class="template-library-loading" aria-label="正在加载话术">
        <span></span><span></span><span></span>
      </div>

      <div v-else data-testid="template-library-flow" class="template-library-flow">
        <article v-for="entry in speechEntries" :key="entry.key" class="template-library-item">
          <header class="template-library-item-head">
            <h3>{{ entry.title }}</h3>
            <div class="template-library-tags" aria-label="话术标记">
              <span :class="['template-content-mark', entry.isImage ? 'image' : 'text']">{{ entry.isImage ? '图' : '文' }}</span>
              <span :class="['template-source-mark', entry.source.toLowerCase()]">{{ entry.source === 'PERSONAL' ? '我' : '团队' }}</span>
              <span v-if="entry.scene" class="template-detail-mark">{{ entry.scene }}</span>
              <code v-if="entry.shortcutCode">{{ entry.shortcutCode }}</code>
              <small v-if="entry.usageCount">已用 {{ entry.usageCount }} 次</small>
            </div>
          </header>
          <p v-if="state.customerContext" class="template-library-context"><strong>将自动带入：</strong>{{ customerContextLabel }}</p>
          <img v-if="entry.isImage && entry.imageUrl" class="template-library-image" :src="resolveResourceUrl(entry.imageUrl)" :alt="entry.title" />
          <p class="template-library-body">{{ resolvedBody(entry) }}</p>
          <p v-if="!entry.isImage && entry.imageUrl" class="template-library-entry-link">入口：{{ entry.imageUrl }}</p>
          <footer>
            <button :data-testid="`edit-${entry.source.toLowerCase()}-template-${entry.id}`" class="secondary small template-edit-button" type="button" @click="editCopy(entry)">编辑副本</button>
            <button :data-testid="`copy-${entry.source.toLowerCase()}-template-${entry.id}`" class="primary small reply-primary-copy" type="button" :disabled="isCopying(entry)" @click="copyEntry(entry)">
              {{ isCopying(entry) ? '记录中' : '复制' }}
            </button>
          </footer>
        </article>
        <p v-if="speechEntries.length === 0" class="template-library-empty">没有匹配的话术</p>
      </div>
    </aside>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted } from 'vue';
import { onQuickSearchHide, onQuickSearchShow } from '../../shared/desktopBridge';
import { resolveResourceUrl } from '../../shared/config';
import { eventBus } from '../../shared/eventBus';
import {
  closeTemplateLibrary,
  copyPersonalTemplate,
  copyShortcutSpeech,
  copyTeamTemplate,
  handleTemplateLibraryConfigRefresh,
  openPersonalTemplateEditor,
  openTemplateLibrary,
  resolveTemplateLibraryText,
  setTemplateLibraryContentFilter,
  setTemplateLibraryQuery,
  setTemplateLibraryScope,
  templateLibraryState as state
} from './templateLibraryStore';
import type { PersonalTemplate, TeamTemplate, TemplateMetadata } from './templateTypes';
import type { QuickSearchCustomerContext, QuickSearchItem } from '../quick-search/types';

type SpeechEntry = {
  key: string;
  id: number;
  source: 'PERSONAL' | 'TEAM' | 'SHORTCUT';
  title: string;
  body: string;
  metadata: TemplateMetadata;
  shortcutCode?: string;
  usageCount?: number;
  imageUrl?: string | null;
  isImage: boolean;
  scene?: string | null;
  raw: PersonalTemplate | TeamTemplate | QuickSearchItem;
};

const disposers: Array<() => void> = [];

const allEntries = computed<SpeechEntry[]>(() => [
  ...state.team.map((template) => toTeamEntry(template)),
  ...state.shortcuts.map((item) => toShortcutEntry(item)),
  ...state.personal.map((template) => toPersonalEntry(template))
]);

const speechEntries = computed(() => {
  const query = state.query.trim().toLowerCase();
  return allEntries.value.filter((entry) => {
    if (state.scope === 'PERSONAL' && entry.source !== 'PERSONAL') return false;
    if (state.scope === 'TEAM' && entry.source === 'PERSONAL') return false;
    if (state.contentFilter === 'TEXT' && entry.isImage) return false;
    if (state.contentFilter === 'IMAGE' && !entry.isImage) return false;
    if (!query) return true;
    return [entry.title, entry.body, entry.shortcutCode ?? '', entry.scene ?? '']
      .some((value) => value.toLowerCase().includes(query));
  });
});

const teamCount = computed(() => allEntries.value.filter((entry) => entry.source !== 'PERSONAL').length);
const customerContextLabel = computed(() => {
  const context = state.customerContext;
  const nickname = context?.nickname?.trim() || '当前客户';
  return [nickname, leadTypeLabel(context?.leadType), customerIntent(context?.customer)].filter(Boolean).join(' · ');
});

onMounted(() => {
  disposers.push(eventBus.on<QuickSearchCustomerContext | undefined>('template-library:show', (context) => {
    void openTemplateLibrary(context);
  }));
  disposers.push(eventBus.on('CONFIG_REFRESH', handleTemplateLibraryConfigRefresh));
  disposers.push(onQuickSearchShow(() => void openTemplateLibrary()));
  disposers.push(onQuickSearchHide(closeTemplateLibrary));
});

onBeforeUnmount(() => disposers.splice(0).forEach((dispose) => dispose()));

function toPersonalEntry(template: PersonalTemplate): SpeechEntry {
  return {
    key: `personal-${template.id}`,
    id: template.id,
    source: 'PERSONAL',
    title: template.title,
    body: template.body,
    metadata: template.metadata,
    usageCount: template.usageCount,
    isImage: false,
    scene: template.metadata.scene,
    raw: template
  };
}

function toTeamEntry(template: TeamTemplate): SpeechEntry {
  return {
    key: `team-${template.quickSearchItemId}`,
    id: template.quickSearchItemId,
    source: 'TEAM',
    title: template.title,
    body: template.body,
    metadata: template.metadata,
    shortcutCode: template.shortcutCode,
    isImage: false,
    scene: template.metadata.scene,
    raw: template
  };
}

function toShortcutEntry(item: QuickSearchItem): SpeechEntry {
  return {
    key: `shortcut-${item.id}`,
    id: item.id,
    source: 'SHORTCUT',
    title: item.title,
    body: item.content,
    metadata: { scene: item.scene, leadType: item.leadType, labels: [] },
    shortcutCode: item.shortcutCode,
    imageUrl: item.imageUrl,
    isImage: item.contentType === 'IMAGE',
    scene: item.scene,
    raw: item
  };
}

function resolvedBody(entry: SpeechEntry): string {
  return entry.isImage ? entry.body : resolveTemplateLibraryText(entry.body);
}

function editCopy(entry: SpeechEntry): void {
  openPersonalTemplateEditor({
    title: entry.title,
    body: entry.body,
    originalAiReply: entry.body,
    metadata: entry.metadata
  });
}

function isCopying(entry: SpeechEntry): boolean {
  const prefix = entry.source === 'PERSONAL' ? 'personal' : entry.source === 'TEAM' ? 'team' : 'shortcut';
  return state.copyingId === `${prefix}-${entry.id}`;
}

function copyEntry(entry: SpeechEntry): void {
  if (entry.source === 'PERSONAL') {
    void copyPersonalTemplate(entry.raw as PersonalTemplate, resolvedBody(entry));
    return;
  }
  if (entry.source === 'TEAM') {
    void copyTeamTemplate(entry.raw as TeamTemplate, resolvedBody(entry));
    return;
  }
  void copyShortcutSpeech(entry.raw as QuickSearchItem);
}

function leadTypeLabel(value?: string | null): string {
  if (value === 'TUAN_GOU') return '团购客资';
  if (value === 'XIAN_SUO') return '线索客资';
  return value || '';
}

function customerIntent(customer?: Record<string, unknown>): string {
  const value = customer?.intentLevel ?? customer?.意向等级;
  return typeof value === 'string' ? value : '';
}
</script>

<style scoped>
.template-library-overlay { position: fixed; inset: 0; z-index: 80; display: flex; justify-content: flex-end; background: rgba(19, 27, 39, 0.38); }
.template-library-shell { display: grid; grid-template-rows: auto auto auto auto minmax(0, 1fr); width: min(680px, 100vw); min-height: 100%; padding: 20px; overflow: hidden; background: #ffffff; border-left: 1px solid #d9e0e8; box-shadow: -12px 0 30px rgba(22, 34, 51, 0.15); }
.template-library-head, .template-library-head-actions, .template-library-item-head, .template-library-item footer, .template-library-tags { display: flex; align-items: center; }
.template-library-head { justify-content: space-between; gap: 12px; }
.template-library-head h2, .template-library-head p, .template-library-item h3, .template-library-item p { margin: 0; }
.template-library-head h2 { font-size: 18px; }
.template-library-head p, .template-library-item small { color: #607083; font-size: 12px; }
.template-library-head-actions { gap: 8px; }
.template-library-search { box-sizing: border-box; width: 100%; min-height: 38px; margin-top: 18px; padding: 8px 10px; border: 1px solid #c9d4df; border-radius: 4px; color: #1e2a38; font: inherit; }
.template-library-search:focus { outline: 2px solid #b9d4ed; border-color: #5487b6; }
.template-library-filters { display: flex; flex-wrap: wrap; gap: 6px; margin: 12px 0; }
.template-library-filters button { min-height: 30px; padding: 0 10px; border: 1px solid #d8e0e8; border-radius: 4px; color: #526173; background: #ffffff; }
.template-library-filters button.active { border-color: #5487b6; color: #163b66; background: #eef6ff; font-weight: 700; }
.template-library-notice { margin: 0 0 10px; color: #2e6b48; font-size: 13px; }
.template-library-notice.error { color: #b42318; }
.template-library-toast {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 90;
  width: min(360px, calc(100vw - 48px));
  margin: 0;
  border: 1px solid #b7dec5;
  border-left: 4px solid #2e9b63;
  border-radius: 6px;
  padding: 9px 12px;
  background: #f0faf3;
  color: #176b3a;
  font-size: 13px;
  line-height: 1.35;
  box-shadow: 0 10px 28px rgb(16 24 40 / 16%);
}
.template-toast-enter-active,
.template-toast-leave-active { transition: opacity 0.16s ease, transform 0.16s ease; }
.template-toast-enter-from,
.template-toast-leave-to { opacity: 0; transform: translateY(8px); }
.template-library-flow { display: grid; align-content: start; gap: 10px; min-height: 0; overflow-y: auto; padding: 2px 2px 16px; }
.template-library-item { display: grid; gap: 10px; padding: 14px; border: 1px solid #dfe6ed; border-radius: 6px; background: #ffffff; }
.template-library-item-head { align-items: flex-start; justify-content: space-between; gap: 10px; }
.template-library-item h3 { min-width: 0; color: #213047; font-size: 15px; line-height: 1.35; overflow-wrap: anywhere; }
.template-library-tags { flex: 0 0 auto; flex-wrap: wrap; justify-content: flex-end; gap: 4px; }
.template-library-tags span, .template-library-tags code, .template-library-tags small { padding: 3px 5px; border-radius: 3px; font-size: 11px; white-space: nowrap; }
.template-content-mark.text { color: #245b91; background: #eef6ff; }
.template-content-mark.image { color: #8a4d10; background: #fff4df; }
.template-source-mark.personal { color: #2e6b48; background: #eaf6ef; }
.template-source-mark.team, .template-source-mark.shortcut { color: #6941c6; background: #f2efff; }
.template-detail-mark { color: #59687a; background: #f1f4f7; }
.template-library-tags code { color: #245b91; background: #eef6ff; }
.template-library-context { color: #607083; font-size: 12px; }
.template-library-context strong { margin-right: 4px; color: #435267; }
.template-library-body { color: #263445; line-height: 1.55; overflow-wrap: anywhere; }
.template-library-entry-link { color: #607083; font-size: 12px; overflow-wrap: anywhere; }
.template-library-image { width: min(220px, 100%); max-height: 140px; object-fit: cover; border: 1px solid #dfe6ed; border-radius: 4px; }
.template-library-item footer { justify-content: flex-end; gap: 8px; }
.template-edit-button { min-width: 72px; }
.reply-primary-copy { min-width: 54px; }
.template-library-empty { margin: 48px 0; color: #718096; text-align: center; }
.template-library-loading { display: flex; gap: 6px; align-items: center; justify-content: center; min-height: 120px; }
.template-library-loading span { width: 7px; height: 7px; border-radius: 50%; background: #4e81b8; animation: template-loading 0.9s infinite alternate; }
.template-library-loading span:nth-child(2) { animation-delay: 0.15s; }
.template-library-loading span:nth-child(3) { animation-delay: 0.3s; }
@keyframes template-loading { to { opacity: 0.25; transform: translateY(-4px); } }
@media (max-width: 520px) { .template-library-shell { width: 100%; padding: 14px; } .template-library-item-head { display: grid; } .template-library-tags { justify-content: flex-start; } }
</style>
