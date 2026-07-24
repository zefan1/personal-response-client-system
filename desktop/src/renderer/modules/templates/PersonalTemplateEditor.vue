<template>
  <section v-if="state.visible" class="template-editor-overlay" @click.self="closePersonalTemplateEditor" @keydown.esc="closePersonalTemplateEditor">
    <form class="template-editor" @submit.prevent="submit">
      <header>
        <div>
          <h2>保存为模板</h2>
          <p>确认后可立即在我的模板中复用</p>
        </div>
        <button class="icon-close-button" type="button" aria-label="关闭保存模板" title="关闭保存模板" @click="closePersonalTemplateEditor">
          <span aria-hidden="true">×</span>
        </button>
      </header>

      <label>
        模板标题
        <input v-model="state.draft.title" name="title" maxlength="120" autocomplete="off" required />
      </label>
      <label>
        模板正文
        <textarea v-model="state.draft.body" name="body" maxlength="4000" rows="8" required />
      </label>
      <div class="template-editor-metadata">
        <label>
          渠道
          <input v-model="state.draft.metadata.channelCode" name="channelCode" maxlength="100" autocomplete="off" />
        </label>
        <label>
          场景
          <input v-model="state.draft.metadata.scene" name="scene" maxlength="100" autocomplete="off" />
        </label>
        <label>
          线索类型
          <input v-model="state.draft.metadata.leadType" name="leadType" maxlength="100" autocomplete="off" />
        </label>
        <label>
          标签
          <input v-model="labelsInput" name="labels" maxlength="1600" autocomplete="off" />
        </label>
      </div>
      <p v-if="libraryState.error" class="template-editor-error">{{ libraryState.error }}</p>
      <footer>
        <button class="secondary" type="button" :disabled="libraryState.saving" @click="closePersonalTemplateEditor">取消</button>
        <button class="primary" type="submit" :disabled="libraryState.saving || !state.draft.title.trim() || !state.draft.body.trim()">
          {{ libraryState.saving ? '保存中' : '确认保存' }}
        </button>
      </footer>
    </form>
  </section>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { eventBus } from '../../shared/eventBus';
import {
  closePersonalTemplateEditor,
  openPersonalTemplateEditor,
  savePersonalTemplate,
  templateEditorState as state,
  templateLibraryState as libraryState
} from './templateLibraryStore';
import type { PersonalTemplateDraft } from './templateTypes';

const labelsInput = ref('');
let dispose: (() => void) | null = null;

onMounted(() => {
  dispose = eventBus.on<Partial<PersonalTemplateDraft>>('template-editor:show', (draft) => {
    openPersonalTemplateEditor(draft);
  });
});

onBeforeUnmount(() => dispose?.());

watch(
  () => state.visible,
  (visible) => {
    if (visible) {
      labelsInput.value = (state.draft.metadata.labels ?? []).join(', ');
    }
  },
  { immediate: true }
);

async function submit(): Promise<void> {
  await savePersonalTemplate({
    ...state.draft,
    metadata: {
      ...state.draft.metadata,
      labels: labelsInput.value.split(',').map((value) => value.trim()).filter(Boolean)
    }
  });
}
</script>

<style scoped>
.template-editor-overlay {
  position: fixed;
  inset: 0;
  z-index: 81;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgba(19, 27, 39, 0.48);
}

.template-editor {
  display: grid;
  gap: 14px;
  width: min(640px, 100%);
  max-height: min(780px, calc(100vh - 40px));
  padding: 22px;
  overflow: auto;
  background: #ffffff;
  border: 1px solid #dce4ec;
  border-radius: 6px;
  box-shadow: 0 18px 44px rgba(17, 29, 43, 0.25);
}

.template-editor header,
.template-editor footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.template-editor h2,
.template-editor p {
  margin: 0;
}

.template-editor h2 { font-size: 18px; }
.template-editor header p { margin-top: 4px; color: #657386; font-size: 13px; }

.template-editor > label,
.template-editor-metadata label {
  display: grid;
  gap: 6px;
  color: #344255;
  font-size: 13px;
}

.template-editor input,
.template-editor textarea {
  box-sizing: border-box;
  width: 100%;
  border: 1px solid #c9d4df;
  border-radius: 4px;
  color: #1e2a38;
  background: #ffffff;
  font: inherit;
}

.template-editor input { min-height: 36px; padding: 7px 9px; }
.template-editor textarea { min-height: 156px; padding: 9px; resize: vertical; line-height: 1.55; }
.template-editor input:focus,
.template-editor textarea:focus { outline: 2px solid #b9d4ed; border-color: #5487b6; }

.template-editor-metadata {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.template-editor-error { color: #b42318; font-size: 13px; }
.template-editor footer { justify-content: flex-end; }

@media (max-width: 520px) {
  .template-editor-overlay { padding: 0; }
  .template-editor { min-height: 100%; max-height: 100%; border-radius: 0; }
  .template-editor-metadata { grid-template-columns: 1fr; }
}
</style>
