import { createApp, nextTick, type App } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import AdminDevConsole from './AdminDevConsole.vue';

const apiMocks = vi.hoisted(() => ({
  getJson: vi.fn(),
  postJson: vi.fn(),
  putJson: vi.fn(),
  deleteJson: vi.fn()
}));

vi.mock('../../shared/apiClient', () => ({
  getJson: apiMocks.getJson,
  postJson: apiMocks.postJson,
  putJson: apiMocks.putJson,
  deleteJson: apiMocks.deleteJson
}));

type MountedConsole = {
  app: App<Element>;
  host: HTMLDivElement;
};

async function flushUi() {
  await Promise.resolve();
  await Promise.resolve();
  await nextTick();
}

async function mountDevConsole(): Promise<MountedConsole> {
  const host = document.createElement('div');
  document.body.appendChild(host);
  const app = createApp(AdminDevConsole, { accountName: 'admin' });
  app.mount(host);
  await flushUi();
  return { app, host };
}

describe('AdminDevConsole', () => {
  beforeEach(() => {
    apiMocks.getJson.mockResolvedValue({ success: true, data: { items: [] }, errorCode: null, message: null });
    apiMocks.postJson.mockResolvedValue({ success: true, data: {}, errorCode: null, message: null });
    apiMocks.putJson.mockResolvedValue({ success: true, data: {}, errorCode: null, message: null });
    apiMocks.deleteJson.mockResolvedValue({ success: true, data: {}, errorCode: null, message: null });
  });

  afterEach(() => {
    document.body.innerHTML = '';
    apiMocks.getJson.mockReset();
    apiMocks.postJson.mockReset();
    apiMocks.putJson.mockReset();
    apiMocks.deleteJson.mockReset();
  });

  it('renders the explicitly development-only API console surface', async () => {
    const { app, host } = await mountDevConsole();

    expect(host.querySelector('.admin-console')).toBeTruthy();
    expect(host.textContent).toContain('开发调试台');
    expect(host.textContent).toContain('请求体 JSON');
    expect(host.textContent).toContain('目标 ID');
    expect(host.querySelectorAll('.admin-nav-button').length).toBeGreaterThan(4);
    expect(apiMocks.getJson).toHaveBeenCalledWith('/admin/api/v1/health');

    app.unmount();
  });

  it('emits route switch events without touching production admin code', async () => {
    const host = document.createElement('div');
    document.body.appendChild(host);
    const onSwitchAdmin = vi.fn();
    const onSwitchDesktop = vi.fn();
    const onLogout = vi.fn();
    const app = createApp({
      components: { AdminDevConsole },
      setup() {
        return { onSwitchAdmin, onSwitchDesktop, onLogout };
      },
      template: `
        <AdminDevConsole
          account-name="admin"
          @switch-admin="onSwitchAdmin"
          @switch-desktop="onSwitchDesktop"
          @logout="onLogout"
        />
      `
    });
    app.mount(host);
    await flushUi();

    const buttons = [...host.querySelectorAll('.admin-toolbar-actions button')] as HTMLButtonElement[];
    expect(buttons.map((button) => button.textContent)).toEqual(['正式后台', '工作台', '退出']);
    buttons[0].click();
    buttons[1].click();
    buttons[2].click();
    expect(onSwitchAdmin).toHaveBeenCalledTimes(1);
    expect(onSwitchDesktop).toHaveBeenCalledTimes(1);
    expect(onLogout).toHaveBeenCalledTimes(1);

    app.unmount();
  });
});
