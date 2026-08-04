import { createApp, nextTick } from 'vue';
import { afterEach, describe, expect, it } from 'vitest';
import AdminNavigation from './AdminNavigation.vue';
import type { AdminNavGroup, AdminSection } from './adminNavigation';

const activeSection = section('skill-scenes', 'config-center');
const groups: AdminNavGroup[] = [
  {
    key: 'config-center',
    title: 'Config',
    subtitle: 'Config subtitle',
    defaultKey: 'skill-scenes',
    pages: [activeSection, section('configuration-center', 'config-center')]
  }
];

const mounts: Array<{ app: ReturnType<typeof createApp>; host: HTMLDivElement }> = [];

afterEach(() => {
  mounts.splice(0).forEach(({ app, host }) => {
    app.unmount();
    host.remove();
  });
});

describe('AdminNavigation', () => {
  it('emits the selected page key while preserving the current active page marker', async () => {
    const selected: string[] = [];
    const { host } = mountNavigation((key) => selected.push(key));

    expect(host.querySelector('.ops-admin-subnav-button.active')?.textContent).toContain('skill-scenes');
    expect(host.querySelector('.ops-admin-subnav-button.active')?.getAttribute('aria-current')).toBe('page');

    host.querySelectorAll<HTMLButtonElement>('.ops-admin-subnav-button')[1].click();
    await nextTick();

    expect(selected).toEqual(['configuration-center']);
  });
});

function mountNavigation(onSelect: (key: string) => void) {
  const host = document.createElement('div');
  document.body.appendChild(host);
  const app = createApp(AdminNavigation, {
    groups,
    activeSection,
    activeSectionKey: 'skill-scenes',
    onSelect
  });
  app.mount(host);
  mounts.push({ app, host });
  return { app, host };
}

function section(key: AdminSection['key'], groupKey: AdminSection['groupKey']): AdminSection {
  return { key, groupKey, group: groupKey, module: key, title: key, subtitle: key, description: key, primaryAction: key };
}
