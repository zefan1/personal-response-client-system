import { createApp } from 'vue';
import App from './App.vue';
import { initializeOfflineManager } from './shared/offlineManager';
import './styles.css';

void initializeOfflineManager().finally(() => {
  createApp(App).mount('#app');
});
