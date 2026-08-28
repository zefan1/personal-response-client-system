<template>
  <section class="assignment-table-panel" aria-label="分配表管理">
    <header class="section-inline-head assignment-table-panel-head">
      <div>
        <h2>分配表</h2>
      </div>
      <div class="assignment-table-actions">
        <button class="secondary small" type="button" :disabled="loading" @click="refresh">刷新</button>
      </div>
    </header>

    <p v-if="panelError" class="admin-message error">{{ panelError }}</p>

    <div v-if="loading && !loaded" class="assignment-table-loading">正在读取分配表...</div>
    <div v-else-if="activeTable" class="assignment-table-active">
      <div>
        <span class="assignment-table-label">当前使用</span>
        <strong>{{ activeTable.tableName }}</strong>
        <small>{{ activeTable.monthKey }} · 已完成绑定</small>
      </div>
      <button class="secondary small" type="button" :disabled="opening" @click="openTable(activeTable)">
        打开表格
      </button>
    </div>
    <p v-else class="empty-panel">还没有可用的分配表，请先创建一张。</p>

  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { openAssignmentTable } from '../../shared/desktopBridge';
import {
  loadAssignmentTables,
  type AssignmentTable
} from '../../shared/assignmentTableStore';

const tables = ref<AssignmentTable[]>([]);
const loaded = ref(false);
const loading = ref(false);
const opening = ref(false);
const panelError = ref('');

const activeTable = computed(() => tables.value.find((table) => table.status === 'ACTIVE') ?? null);

onMounted(() => { void refresh(); });

async function refresh(): Promise<void> {
  loading.value = true;
  panelError.value = '';
  try {
    tables.value = await loadAssignmentTables();
    loaded.value = true;
  } catch (cause) {
    panelError.value = cause instanceof Error ? cause.message : '分配表加载失败';
  } finally {
    loading.value = false;
  }
}

async function openTable(table: AssignmentTable): Promise<void> {
  if (!table.documentUrl || opening.value) return;
  opening.value = true;
  try {
    const result = await openAssignmentTable(table.documentUrl);
    if (!result.success) panelError.value = result.message || '无法打开企业微信表格';
  } finally {
    opening.value = false;
  }
}
</script>
