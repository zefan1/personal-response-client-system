<template>
  <section v-if="state.pendingClipboardImage" class="clipboard-confirm-agent" role="status" aria-live="polite">
    <div class="clipboard-confirm-copy">
      <strong>发现新截图</strong>
      <p>{{ descriptionText }}</p>
    </div>
    <div class="clipboard-confirm-actions">
      <button class="primary small" type="button" @click="confirmRecognition">识别</button>
      <button class="icon-close-button" type="button" aria-label="忽略截图" title="忽略截图" @click="ignoreScreenshot">
        <span aria-hidden="true">×</span>
      </button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { loadDesktopConfig } from '../../shared/config';
import { desktopStatusState } from '../../shared/desktopStatusStore';
import {
  dismissPendingClipboardImage,
  recognizePendingClipboardImage,
  recognitionState as state
} from './recognitionStore';

let autoDismissTimer: number | null = null;
let countdownTimer: number | null = null;
let activeToken = '';
const remainingSeconds = ref(0);

const promptSeconds = computed(() => {
  const configured = desktopStatusState.runtimeConfig.clipboardScreenshotConfirmPromptS;
  const local = loadDesktopConfig().clipboardScreenshotConfirmPromptS;
  return normalizePromptSeconds(configured ?? local);
});

const descriptionText = computed(() => {
  if (promptSeconds.value === 0) {
    return '确认这是客户聊天后再识别，普通截图不会消耗识别资源。';
  }
  return `${remainingSeconds.value || promptSeconds.value} 秒后自动忽略，确认是客户聊天后再识别。`;
});

watch(
  () => `${state.pendingClipboardImage?.md5 ?? ''}:${state.pendingClipboardImage?.imageBase64 ?? ''}:${promptSeconds.value}`,
  () => {
    resetTimers();
    const payload = state.pendingClipboardImage;
    if (!payload) {
      return;
    }
    activeToken = `${payload.md5}:${Date.now()}`;
    remainingSeconds.value = promptSeconds.value;
    if (promptSeconds.value === 0) {
      return;
    }
    const token = activeToken;
    countdownTimer = window.setInterval(() => {
      if (activeToken !== token || !state.pendingClipboardImage) {
        resetTimers();
        return;
      }
      remainingSeconds.value = Math.max(0, remainingSeconds.value - 1);
    }, 1000);
    autoDismissTimer = window.setTimeout(() => {
      if (activeToken === token && state.pendingClipboardImage) {
        dismissPendingClipboardImage();
      }
    }, promptSeconds.value * 1000);
  },
  { immediate: true }
);

onBeforeUnmount(() => {
  resetTimers();
});

async function confirmRecognition(): Promise<void> {
  resetTimers();
  await recognizePendingClipboardImage();
}

function ignoreScreenshot(): void {
  resetTimers();
  dismissPendingClipboardImage();
}

function resetTimers(): void {
  if (autoDismissTimer) {
    window.clearTimeout(autoDismissTimer);
    autoDismissTimer = null;
  }
  if (countdownTimer) {
    window.clearInterval(countdownTimer);
    countdownTimer = null;
  }
}

function normalizePromptSeconds(value: number): number {
  if (!Number.isFinite(value)) {
    return 10;
  }
  const integer = Math.trunc(value);
  if (integer === 0 || (integer >= 3 && integer <= 60)) {
    return integer;
  }
  return 10;
}
</script>
