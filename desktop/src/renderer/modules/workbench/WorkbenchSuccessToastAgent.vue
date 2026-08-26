<template>
  <p v-if="state.successToast" class="workbench-success-toast" role="status" aria-live="polite">
    {{ state.successToast }}
  </p>
</template>

<script setup lang="ts">
import { workbenchState as state } from './workbenchStore';
</script>
