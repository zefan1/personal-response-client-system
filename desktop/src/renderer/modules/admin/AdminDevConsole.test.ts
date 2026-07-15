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

async function flushRequests() {
  await flushUi();
  await flushUi();
  await flushUi();
}

async function mountDevConsole(): Promise<MountedConsole> {
  const host = document.createElement('div');
  document.body.appendChild(host);
  const app = createApp(AdminDevConsole, { accountName: 'admin' });
  app.mount(host);
  await flushUi();
  return { app, host };
}

function findButton(host: HTMLElement, text: string): HTMLButtonElement {
  const button = [...host.querySelectorAll('button')].find((item) => item.textContent?.includes(text)) as HTMLButtonElement | undefined;
  expect(button).toBeTruthy();
  return button as HTMLButtonElement;
}

function findActionPanel(host: HTMLElement, actionName: string): HTMLElement {
  const panel = [...host.querySelectorAll('.admin-action-panel')]
    .find((item) => item.querySelector('h3')?.textContent === actionName) as HTMLElement | undefined;
  expect(panel).toBeTruthy();
  return panel as HTMLElement;
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

  it('emits admin and logout events without exposing the web desktop route', async () => {
    const host = document.createElement('div');
    document.body.appendChild(host);
    const onSwitchAdmin = vi.fn();
    const onLogout = vi.fn();
    const app = createApp({
      components: { AdminDevConsole },
      setup() {
        return { onSwitchAdmin, onLogout };
      },
      template: `
        <AdminDevConsole
          account-name="admin"
          @switch-admin="onSwitchAdmin"
          @logout="onLogout"
        />
      `
    });
    app.mount(host);
    await flushUi();

    const buttons = [...host.querySelectorAll('.admin-toolbar-actions button')] as HTMLButtonElement[];
    expect(buttons.map((button) => button.textContent)).toEqual(['正式后台', '退出']);
    buttons[0].click();
    buttons[1].click();
    expect(onSwitchAdmin).toHaveBeenCalledTimes(1);
    expect(onLogout).toHaveBeenCalledTimes(1);

    app.unmount();
  });

  it('loads enabled unmerged categories from every page before creating a tag value', async () => {
    apiMocks.getJson.mockImplementation(async (path: string) => {
      if (!path.startsWith('/admin/api/v1/tags/categories')) {
        return { success: true, data: { items: [] }, errorCode: null, message: null };
      }
      const query = new URL(path, 'http://localhost').searchParams;
      const page = Number(query.get('page'));
      return {
        success: true,
        data: page === 1
          ? { items: [], page: 1, size: 100, total: 1, totalPages: 2 }
          : {
              items: [{ id: 72, isEnabled: true, mergedIntoId: null }],
              page: 2,
              size: 100,
              total: 1,
              totalPages: 2
            },
        errorCode: null,
        message: null
      };
    });
    const { app, host } = await mountDevConsole();

    findButton(host, '跟进规则与标签').click();
    await flushRequests();
    const categoryPaths = apiMocks.getJson.mock.calls
      .map(([path]) => String(path))
      .filter((path) => path.startsWith('/admin/api/v1/tags/categories'));
    expect(categoryPaths).toHaveLength(2);
    expect(categoryPaths.every((path) => {
      const query = new URL(path, 'http://localhost').searchParams;
      return query.get('enabled') === 'true'
        && query.get('merged') === 'false'
        && query.get('size') === '100';
    })).toBe(true);
    expect(categoryPaths.map((path) => new URL(path, 'http://localhost').searchParams.get('page')))
      .toEqual(['1', '2']);
    const panel = findActionPanel(host, '创建标签值');
    const body = JSON.parse((panel.querySelector('textarea') as HTMLTextAreaElement).value);
    expect(body).toEqual({ categoryId: 72, displayName: '人工验收标签', isEnabled: true, sortOrder: 99 });

    findButton(panel, '执行').click();
    await flushRequests();
    expect(apiMocks.postJson).toHaveBeenCalledWith('/admin/api/v1/tags/values', {
      categoryId: 72,
      displayName: '人工验收标签',
      isEnabled: true,
      sortOrder: 99
    });
    expect((apiMocks.postJson.mock.calls[0]?.[1] as Record<string, unknown>).tagValue).toBeUndefined();

    app.unmount();
  });

  it('replaces a selected category id that became invalid after refresh', async () => {
    let categoryRequestCount = 0;
    apiMocks.getJson.mockImplementation(async (path: string) => {
      if (!path.startsWith('/admin/api/v1/tags/categories')) {
        return { success: true, data: { items: [] }, errorCode: null, message: null };
      }
      categoryRequestCount += 1;
      const id = categoryRequestCount === 1 ? 72 : 73;
      return {
        success: true,
        data: {
          items: [{ id, isEnabled: true, mergedIntoId: null }],
          page: 1,
          size: 100,
          total: 1,
          totalPages: 1
        },
        errorCode: null,
        message: null
      };
    });
    const { app, host } = await mountDevConsole();

    findButton(host, '跟进规则与标签').click();
    await flushRequests();
    const panel = findActionPanel(host, '创建标签值');
    expect(JSON.parse((panel.querySelector('textarea') as HTMLTextAreaElement).value).categoryId).toBe(72);

    findButton(host.querySelector('.admin-section-head') as HTMLElement, '刷新全部').click();
    await flushRequests();
    expect(JSON.parse((panel.querySelector('textarea') as HTMLTextAreaElement).value).categoryId).toBe(73);

    app.unmount();
  });

  it('resolves an arbitrary category id from the current backend candidates before submit', async () => {
    apiMocks.getJson.mockImplementation(async (path: string) => ({
      success: true,
      data: path.startsWith('/admin/api/v1/tags/categories')
        ? {
            items: [{ id: 72, isEnabled: true, mergedIntoId: null }],
            page: 1,
            size: 100,
            total: 1,
            totalPages: 1
          }
        : { items: [] },
      errorCode: null,
      message: null
    }));
    const { app, host } = await mountDevConsole();

    findButton(host, '跟进规则与标签').click();
    await flushRequests();
    const panel = findActionPanel(host, '创建标签值');
    const textarea = panel.querySelector('textarea') as HTMLTextAreaElement;
    textarea.value = JSON.stringify({ categoryId: 999, displayName: '人工验收标签', isEnabled: true, sortOrder: 99 });
    textarea.dispatchEvent(new Event('input', { bubbles: true }));

    findButton(panel, '执行').click();
    await flushRequests();
    expect(apiMocks.postJson).toHaveBeenCalledWith('/admin/api/v1/tags/values', {
      categoryId: 72,
      displayName: '人工验收标签',
      isEnabled: true,
      sortOrder: 99
    });

    app.unmount();
  });

  it('rejects create-tag-value in Chinese when no enabled unmerged category is available', async () => {
    const { app, host } = await mountDevConsole();

    findButton(host, '跟进规则与标签').click();
    await flushRequests();
    const panel = findActionPanel(host, '创建标签值');
    findButton(panel, '执行').click();
    await flushRequests();

    expect(apiMocks.postJson).not.toHaveBeenCalled();
    expect(host.querySelector('.admin-message.error')?.textContent).toContain('没有可用的标签分类');

    app.unmount();
  });
});
