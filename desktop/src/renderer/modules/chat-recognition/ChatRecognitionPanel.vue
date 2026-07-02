<template>
  <section class="recognition">
    <div class="toolbar">
      <button class="primary" :disabled="captureDisabled" :title="captureTitle" @click="captureFromWindow">
        识别聊天
      </button>
      <button class="secondary" @click="openTextMode">文字通道</button>
    </div>

    <p v-if="state.imageServiceStatus === 'DOWN'" class="banner">
      图片识别暂不可用，请复制聊天文字后使用文字通道
    </p>

    <form v-if="state.isTwoBoxMode" class="two-box" @submit.prevent="submitText">
      <input v-model="state.customerIdentityInput" placeholder="客户标识" />
      <textarea v-model="state.chatContentInput" placeholder="聊天内容"></textarea>
      <button class="primary" :disabled="state.isRecognizePending">发送文字</button>
    </form>

    <p v-if="state.toast" class="toast">{{ state.toast }}</p>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted } from 'vue';
import { eventBus } from '../../shared/eventBus';
import { connectWsMessageBus } from '../../shared/wsMessageBus';
import {
  handleImageServiceStatus,
  recognitionState as state,
  recognizeClipboardImage,
  triggerRecognize
} from './recognitionStore';

const captureDisabled = computed(() => state.isRecognizePending || state.imageServiceStatus === 'DOWN');
const captureTitle = computed(() => state.imageServiceStatus === 'DOWN'
  ? '图片识别暂不可用，请使用文字通道'
  : '截取微信/企业微信窗口并识别');

let removeClipboardListener: (() => void) | null = null;
let removeStatusListener: (() => void) | null = null;

onMounted(() => {
  connectWsMessageBus();
  removeClipboardListener = window.desktopBridge.onClipboardImage((payload) => {
    void recognizeClipboardImage(payload);
  });
  removeStatusListener = eventBus.on('image:status-changed', handleImageServiceStatus);
});

onBeforeUnmount(() => {
  removeClipboardListener?.();
  removeStatusListener?.();
});

async function captureFromWindow() {
  const result = await window.desktopBridge.captureScreenshot();
  if (!result.success || !result.imageBase64) {
    state.toast = result.error === 'NO_WECHAT_WINDOW'
      ? '未检测到微信/企业微信窗口，请手动 Alt+A 截图'
      : '截图失败，请手动 Alt+A 截图';
    return;
  }
  await triggerRecognize('BUTTON_CLICK', { imageBase64: result.imageBase64 });
}

function openTextMode() {
  state.isTwoBoxMode = true;
}

async function submitText() {
  await triggerRecognize('CLIPBOARD_TEXT', {
    customerIdentifier: state.customerIdentityInput,
    textMessage: state.chatContentInput
  });
}
</script>
