<template>
    <section v-for="group in groups" :key="group.key" class="ops-admin-nav-group">
      <button
        class="ops-admin-group-button"
        :class="{ active: activeSection.groupKey === group.key }"
        type="button"
        @click="$emit('select', group.defaultKey)"
      >
        <span>{{ group.title }}</span>
        <small>{{ group.subtitle }}</small>
      </button>
      <div class="ops-admin-subnav">
        <button
          v-for="section in group.pages"
          :key="section.key"
          class="ops-admin-subnav-button"
          :class="{ active: activeSectionKey === section.key }"
          type="button"
          @click="$emit('select', section.key)"
        >
          <span>{{ section.module }}</span>
          <small>{{ section.title }}</small>
        </button>
      </div>
    </section>
</template>

<script setup lang="ts">
import type { AdminNavGroup, AdminSection, SectionKey } from './adminNavigation';

withDefaults(defineProps<{
  groups: readonly AdminNavGroup[];
  activeSection: AdminSection;
  activeSectionKey: SectionKey;
  ariaLabel?: string;
}>(), {
  ariaLabel: '运营后台模块'
});

defineEmits<{
  select: [key: SectionKey];
}>();
</script>
