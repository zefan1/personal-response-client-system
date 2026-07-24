<template>
  <section v-if="state.visible" class="template-library-overlay" @click.self="closeTemplateLibrary" @keydown.esc="closeTemplateLibrary">
    <aside class="template-library-shell" aria-label="模板库">
      <header class="template-library-head">
        <div>
          <h2>模板库</h2>
          <p>{{ state.tab === 'PERSONAL' ? '随时复用自己保存的话术' : '已发布的团队共用话术' }}</p>
        </div>
        <div class="template-library-head-actions">
          <button v-if="state.tab === 'PERSONAL'" class="primary small" type="button" @click="openPersonalTemplateEditor()">新建</button>
          <button class="icon-close-button" type="button" aria-label="关闭模板库" title="关闭模板库" @click="closeTemplateLibrary">
            <span aria-hidden="true">×</span>
          </button>
        </div>
      </header>

      <nav class="template-library-tabs" aria-label="模板分类">
        <button
          data-testid="template-tab-personal"
          :class="{ active: state.tab === 'PERSONAL' }"
          type="button"
          @click="setTemplateLibraryTab('PERSONAL')"
        >我的模板</button>
        <button
          data-testid="template-tab-team"
          :class="{ active: state.tab === 'TEAM' }"
          type="button"
          @click="setTemplateLibraryTab('TEAM')"
        >团队模板</button>
      </nav>

      <p v-if="state.error" class="template-library-notice error">{{ state.error }}</p>
      <p v-else-if="state.toast" class="template-library-notice">{{ state.toast }}</p>

      <div v-if="state.loading" class="template-library-loading" aria-label="正在加载模板">
        <span></span><span></span><span></span>
      </div>

      <div v-else-if="state.tab === 'PERSONAL'" class="template-library-list">
        <article v-for="template in state.personal" :key="template.id" class="template-library-item">
          <div class="template-library-item-head">
            <div>
              <strong>{{ template.title }}</strong>
              <small>{{ metadataLine(template.metadata) }}</small>
            </div>
            <small v-if="template.usageCount" class="template-library-usage">已用 {{ template.usageCount }} 次</small>
          </div>
          <p>{{ template.body }}</p>
          <footer>
            <button
              :data-testid="`copy-personal-template-${template.id}`"
              class="primary small"
              type="button"
              :disabled="state.copyingId === `personal-${template.id}`"
              @click="copyPersonalTemplate(template)"
            >{{ state.copyingId === `personal-${template.id}` ? '记录中' : '复制' }}</button>
          </footer>
        </article>
        <p v-if="!state.personal.length" class="template-library-empty">还没有个人模板</p>
      </div>

      <div v-else class="template-library-list">
        <article v-for="template in state.team" :key="template.quickSearchItemId" class="template-library-item">
          <div class="template-library-item-head">
            <div>
              <strong>{{ template.title }}</strong>
              <small>{{ metadataLine(template.metadata) }}</small>
            </div>
            <code>{{ template.shortcutCode }}</code>
          </div>
          <p>{{ template.body }}</p>
          <footer>
            <button
              :data-testid="`copy-team-template-${template.quickSearchItemId}`"
              class="primary small"
              type="button"
              :disabled="state.copyingId === `team-${template.quickSearchItemId}`"
              @click="copyTeamTemplate(template)"
            >{{ state.copyingId === `team-${template.quickSearchItemId}` ? '记录中' : '复制' }}</button>
            <button
              :data-testid="`save-team-template-${template.quickSearchItemId}`"
              class="secondary small"
              type="button"
              @click="saveTeamTemplateAsPersonal(template)"
            >另存为我的模板</button>
          </footer>
        </article>
        <p v-if="!state.team.length" class="template-library-empty">暂时没有团队模板</p>
      </div>
    </aside>
  </section>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue';
import { eventBus } from '../../shared/eventBus';
import {
  closeTemplateLibrary,
  copyPersonalTemplate,
  copyTeamTemplate,
  openPersonalTemplateEditor,
  openTemplateLibrary,
  setTemplateLibraryTab,
  templateLibraryState as state
} from './templateLibraryStore';
import type { TeamTemplate, TemplateLibraryTab, TemplateMetadata } from './templateTypes';

let dispose: (() => void) | null = null;

onMounted(() => {
  dispose = eventBus.on<{ tab?: TemplateLibraryTab }>('template-library:show', (payload) => {
    void openTemplateLibrary(payload?.tab ?? 'PERSONAL');
  });
});

onBeforeUnmount(() => dispose?.());

function saveTeamTemplateAsPersonal(template: TeamTemplate): void {
  openPersonalTemplateEditor({
    body: template.body,
    originalAiReply: template.body,
    metadata: template.metadata
  });
}

function metadataLine(metadata: TemplateMetadata): string {
  const values = [metadata.channelCode, metadata.scene, metadata.leadType, ...(metadata.labels ?? [])]
    .map((value) => value?.trim())
    .filter((value): value is string => Boolean(value));
  return values.join(' · ') || '未设置适用信息';
}
</script>

<style scoped>
.template-library-overlay {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: flex;
  justify-content: flex-end;
  background: rgba(19, 27, 39, 0.38);
}

.template-library-shell {
  display: grid;
  grid-template-rows: auto auto auto minmax(0, 1fr);
  width: min(470px, 100vw);
  min-height: 100%;
  padding: 20px;
  overflow: hidden;
  background: #ffffff;
  border-left: 1px solid #d9e0e8;
  box-shadow: -12px 0 30px rgba(22, 34, 51, 0.15);
}

.template-library-head,
.template-library-item-head,
.template-library-item footer,
.template-library-head-actions {
  display: flex;
  align-items: center;
}

.template-library-head {
  justify-content: space-between;
  gap: 12px;
}

.template-library-head h2,
.template-library-head p,
.template-library-item p {
  margin: 0;
}

.template-library-head h2 {
  font-size: 18px;
}

.template-library-head p,
.template-library-item small,
.template-library-usage {
  color: #607083;
  font-size: 12px;
}

.template-library-head-actions {
  gap: 8px;
}

.template-library-tabs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4px;
  margin: 20px 0 12px;
  padding: 4px;
  background: #eef2f6;
  border-radius: 6px;
}

.template-library-tabs button {
  min-height: 34px;
  border: 0;
  border-radius: 4px;
  color: #526173;
  background: transparent;
}

.template-library-tabs button.active {
  color: #163b66;
  font-weight: 700;
  background: #ffffff;
  box-shadow: 0 1px 2px rgba(26, 42, 60, 0.12);
}

.template-library-notice {
  margin: 0 0 10px;
  color: #2e6b48;
  font-size: 13px;
}

.template-library-notice.error {
  color: #b42318;
}

.template-library-list {
  display: grid;
  align-content: start;
  gap: 10px;
  min-height: 0;
  overflow: auto;
  padding: 2px 2px 16px;
}

.template-library-item {
  display: grid;
  gap: 10px;
  padding: 14px;
  border: 1px solid #dfe6ed;
  border-radius: 6px;
  background: #ffffff;
}

.template-library-item-head {
  justify-content: space-between;
  gap: 10px;
  min-width: 0;
}

.template-library-item-head > div {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.template-library-item strong,
.template-library-item small,
.template-library-item p {
  overflow-wrap: anywhere;
}

.template-library-item p {
  display: -webkit-box;
  overflow: hidden;
  color: #263445;
  line-height: 1.55;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 4;
}

.template-library-item code {
  flex: 0 0 auto;
  padding: 3px 5px;
  color: #245b91;
  font-size: 11px;
  background: #eef6ff;
  border-radius: 3px;
}

.template-library-item footer {
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px;
}

.template-library-empty {
  margin: 48px 0;
  color: #718096;
  text-align: center;
}

.template-library-loading {
  display: flex;
  gap: 6px;
  align-items: center;
  justify-content: center;
  min-height: 120px;
}

.template-library-loading span {
  width: 7px;
  height: 7px;
  background: #4e81b8;
  border-radius: 50%;
  animation: template-loading 0.9s infinite alternate;
}

.template-library-loading span:nth-child(2) { animation-delay: 0.15s; }
.template-library-loading span:nth-child(3) { animation-delay: 0.3s; }

@keyframes template-loading {
  to { opacity: 0.25; transform: translateY(-4px); }
}
</style>
