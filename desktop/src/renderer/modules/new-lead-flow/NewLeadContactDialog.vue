<template>
  <div v-if="state.open" class="lead-contact-gate" role="dialog" aria-modal="true" aria-labelledby="lead-contact-title">
    <section class="lead-contact-dialog">
      <header class="lead-contact-header">
        <div>
          <p>新客资</p>
          <h2 id="lead-contact-title">{{ state.item?.nickname || '补充微信昵称' }}</h2>
        </div>
        <button class="icon-button" type="button" aria-label="关闭" title="关闭" :disabled="state.saving" @click="closeLeadContact">×</button>
      </header>

      <div class="lead-contact-value">
        <span>{{ state.item?.contactType === 'WECHAT' ? '微信号' : '手机号' }}</span>
        <strong>{{ maskedContact }}</strong>
        <span v-if="state.copiedContact" class="lead-contact-copied">已复制</span>
      </div>

      <label v-if="!state.item?.nickname" class="lead-contact-field">
        微信昵称
        <input v-model="state.nicknameDraft" autofocus placeholder="添加好友后看到的微信昵称" :disabled="state.saving" />
      </label>
      <p v-else class="lead-contact-hint">已存在微信昵称，确认后会记入本次初步处理。</p>

      <label v-if="state.templates.length" class="lead-contact-field">
        添加好友话术
        <select v-model="state.selectedTemplateId" :disabled="state.saving">
          <option v-for="template in state.templates" :key="template.id" :value="template.id">{{ template.name }}</option>
        </select>
      </label>
      <p v-if="state.templates.length" class="lead-contact-template-preview">{{ selectedTemplateText }}</p>
      <p v-if="state.loading" class="lead-contact-hint">正在读取当前话术...</p>
      <p v-if="state.error" class="lead-contact-error" role="alert">{{ state.error }}</p>

      <footer class="lead-contact-actions">
        <button class="secondary small" type="button" :disabled="state.saving || !state.templates.length" @click="copySelectedFriendRequestTemplate">复制话术</button>
        <button class="primary small" type="button" :disabled="state.saving" @click="confirmLeadContact">{{ state.saving ? '保存中...' : state.templates.length ? '确认并复制话术' : '确认' }}</button>
      </footer>
    </section>
  </div>
  <p v-if="state.toast && !state.open" class="toast lead-contact-toast">{{ state.toast }}</p>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted } from 'vue';
import { eventBus } from '../../shared/eventBus';
import { closeLeadContact, confirmLeadContact, copySelectedFriendRequestTemplate, newLeadFlowState as state } from './newLeadFlowStore';
import { openLeadContact } from './newLeadFlowStore';
import type { LeadContactItem } from './types';

let dispose: (() => void) | null = null;
onMounted(() => {
  dispose = eventBus.on<LeadContactItem>('new-lead:copy-contact', (item) => { void openLeadContact(item); });
});
onBeforeUnmount(() => dispose?.());

const maskedContact = computed(() => {
  const value = state.item?.contactValue || state.item?.phoneFull || state.item?.phone || '';
  if (state.item?.contactType === 'WECHAT') return value;
  return value.length >= 7 ? `${value.slice(0, 3)}****${value.slice(-4)}` : value;
});

const selectedTemplateText = computed(() => {
  const selected = state.templates.find((template) => template.id === state.selectedTemplateId) ?? state.templates[0];
  return selected?.text ?? '';
});
</script>

<style scoped>
.lead-contact-gate { position: fixed; inset: 0; z-index: 1200; display: grid; place-items: center; padding: 24px; background: rgba(16, 20, 24, .62); }
.lead-contact-dialog { width: min(480px, 100%); padding: 22px; border: 1px solid #d7dce2; border-radius: 8px; background: #fff; color: #20252b; box-shadow: 0 20px 48px rgba(0,0,0,.24); }
.lead-contact-header, .lead-contact-actions, .lead-contact-value { display: flex; align-items: center; gap: 12px; }
.lead-contact-header { justify-content: space-between; align-items: flex-start; }
.lead-contact-header p, .lead-contact-header h2 { margin: 0; }
.lead-contact-header p { color: #52606d; font-size: 13px; }
.lead-contact-header h2 { margin-top: 4px; font-size: 21px; }
.icon-button { width: 32px; height: 32px; border: 0; background: transparent; color: #52606d; font-size: 22px; cursor: pointer; }
.lead-contact-value { margin: 20px 0; padding: 12px 0; border-top: 1px solid #e5e8eb; border-bottom: 1px solid #e5e8eb; }
.lead-contact-value span:first-child { color: #52606d; }
.lead-contact-value strong { flex: 1; }
.lead-contact-copied { color: #2d6a4f; font-size: 13px; }
.lead-contact-field { display: grid; gap: 6px; margin-top: 14px; color: #38434d; font-size: 13px; }
.lead-contact-field input, .lead-contact-field select { min-height: 38px; padding: 8px 10px; border: 1px solid #c8d0d8; border-radius: 5px; background: #fff; color: #20252b; }
.lead-contact-hint, .lead-contact-template-preview { margin: 12px 0 0; color: #52606d; font-size: 13px; line-height: 1.5; }
.lead-contact-template-preview { padding: 10px; background: #f5f7f8; white-space: pre-wrap; }
.lead-contact-error { margin: 12px 0 0; color: #b42318; font-size: 13px; }
.lead-contact-actions { justify-content: flex-end; margin-top: 20px; }
.lead-contact-toast { position: fixed; right: 24px; bottom: 24px; z-index: 1201; }
</style>
