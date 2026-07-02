<template>
  <main class="shell">
    <CopyBackfillAgent />
    <NewLeadToastAgent />
    <QuickSearchOverlay />
    <BatchTemplateOverlay />
    <HelpModeAgent />
    <ChatRecognitionPanel />
    <FollowupListPanel />
    <CustomerProfilePanel />
    <ReplySuggestionPanel />
  </main>
</template>

<script setup lang="ts">
import ChatRecognitionPanel from './modules/chat-recognition/ChatRecognitionPanel.vue';
import BatchTemplateOverlay from './modules/batch-template/BatchTemplateOverlay.vue';
import CopyBackfillAgent from './modules/copy-backfill/CopyBackfillAgent.vue';
import CustomerProfilePanel from './modules/customer-profile/CustomerProfilePanel.vue';
import FollowupListPanel from './modules/followup-list/FollowupListPanel.vue';
import HelpModeAgent from './modules/help-mode/HelpModeAgent.vue';
import NewLeadToastAgent from './modules/new-lead-toast/NewLeadToastAgent.vue';
import QuickSearchOverlay from './modules/quick-search/QuickSearchOverlay.vue';
import ReplySuggestionPanel from './modules/reply-suggestions/ReplySuggestionPanel.vue';
</script>
