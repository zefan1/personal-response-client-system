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
      <button class="primary">发送文字</button>
    </form>

    <p v-if="state.toast" class="toast">{{ state.toast }}</p>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted } from 'vue';
import { captureScreenshot, onClipboardImage } from '../../shared/desktopBridge';
import { eventBus } from '../../shared/eventBus';
import { connectWsMessageBus } from '../../shared/wsMessageBus';
import {
  handleImageServiceStatus,
  openTextMode,
  recognitionState as state,
  recognizeClipboardImage,
  submitTextRecognition,
  triggerRecognize
} from './recognitionStore';

const captureDisabled = computed(() => state.imageServiceStatus === 'DOWN');
const captureTitle = computed(() => state.imageServiceStatus === 'DOWN'
  ? '图片识别暂不可用，请使用文字通道'
  : '截取当前屏幕并识别聊天内容');

let removeClipboardListener: (() => void) | null = null;
let removeStatusListener: (() => void) | null = null;
let removeWorkbenchCaptureListener: (() => void) | null = null;

onMounted(() => {
  connectWsMessageBus();
  removeClipboardListener = onClipboardImage((payload) => {
    void recognizeClipboardImage(payload);
  });
  removeStatusListener = eventBus.on('image:status-changed', handleImageServiceStatus);
  removeWorkbenchCaptureListener = eventBus.on('workbench:capture-chat', captureFromWindow);
});

onBeforeUnmount(() => {
  removeClipboardListener?.();
  removeStatusListener?.();
  removeWorkbenchCaptureListener?.();
});

async function captureFromWindow() {
  const result = await captureScreenshot();
  if (!result.success || !result.imageBase64) {
    state.toast = result.message ?? '屏幕截图失败，请确认系统允许桌面端录屏后重试';
    return;
  }
  await triggerRecognize('BUTTON_CLICK', { imageBase64: result.imageBase64 });
}

async function submitText() {
  await submitTextRecognition();
}
</script>
