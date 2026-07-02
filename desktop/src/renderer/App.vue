<template>
  <main class="shell">
    <CopyBackfillAgent />
    <ChatRecognitionPanel />
    <CustomerProfilePanel />
    <ReplySuggestionPanel />
  </main>
</template>

<script setup lang="ts">
import ChatRecognitionPanel from './modules/chat-recognition/ChatRecognitionPanel.vue';
import CopyBackfillAgent from './modules/copy-backfill/CopyBackfillAgent.vue';
import CustomerProfilePanel from './modules/customer-profile/CustomerProfilePanel.vue';
import ReplySuggestionPanel from './modules/reply-suggestions/ReplySuggestionPanel.vue';
</script>
