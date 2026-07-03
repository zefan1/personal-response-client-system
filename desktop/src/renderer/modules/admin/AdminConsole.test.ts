import { createApp, nextTick, type App } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import AdminConsole from './AdminConsole.vue';

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

async function mountConsole(): Promise<MountedConsole> {
  const host = document.createElement('div');
  document.body.appendChild(host);
  const app = createApp(AdminConsole, { accountName: 'admin' });
  app.mount(host);
  await flushUi();
  return { app, host };
}

function setInputValue(element: HTMLInputElement | HTMLSelectElement, value: string) {
  element.value = value;
  element.dispatchEvent(new Event('input', { bubbles: true }));
  element.dispatchEvent(new Event('change', { bubbles: true }));
}

function parsedBody(panel: Element): Record<string, unknown> {
  const textarea = panel.querySelector('textarea') as HTMLTextAreaElement | null;
  expect(textarea).toBeTruthy();
  return JSON.parse(textarea?.value ?? '{}');
}

describe('AdminConsole', () => {
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

  it('renders all admin sections and loads the initial read panels from APIs', async () => {
    const { app, host } = await mountConsole();

    expect(host.querySelectorAll('.admin-nav-button').length).toBeGreaterThanOrEqual(9);
    expect(host.querySelectorAll('.admin-read-panel').length).toBeGreaterThanOrEqual(3);
    expect(apiMocks.getJson).toHaveBeenCalledWith('/admin/api/v1/health');
    expect(apiMocks.getJson).toHaveBeenCalledWith('/admin/api/v1/configs');

    app.unmount();
  });

  it('keeps structured text, enum, and number controls synchronized with the JSON body', async () => {
    const { app, host } = await mountConsole();
    const navButtons = [...host.querySelectorAll('.admin-nav-button')] as HTMLButtonElement[];
    navButtons[1].click();
    await flushUi();

    const createSkillPanel = host.querySelector('.admin-action-panel') as HTMLElement | null;
    expect(createSkillPanel).toBeTruthy();
    const textInput = createSkillPanel?.querySelector('.admin-field-grid input:not([type])') as HTMLInputElement | null;
    const enumSelect = createSkillPanel?.querySelector('.admin-field-grid select') as HTMLSelectElement | null;
    const numberInput = createSkillPanel?.querySelector('.admin-field-grid input[type="number"]') as HTMLInputElement | null;
    expect(textInput).toBeTruthy();
    expect(enumSelect).toBeTruthy();
    expect(numberInput).toBeTruthy();

    setInputValue(textInput as HTMLInputElement, 'component-sync-skill');
    await flushUi();
    expect(Object.values(parsedBody(createSkillPanel as HTMLElement))).toContain('component-sync-skill');

    setInputValue(enumSelect as HTMLSelectElement, 'ACTIVE_REPLY');
    await flushUi();
    expect(Object.values(parsedBody(createSkillPanel as HTMLElement))).toContain('ACTIVE_REPLY');

    setInputValue(numberInput as HTMLInputElement, '77');
    await flushUi();
    expect(Object.values(parsedBody(createSkillPanel as HTMLElement))).toContain(77);

    app.unmount();
  });

  it('keeps structured boolean controls synchronized with the JSON body', async () => {
    const { app, host } = await mountConsole();
    const navButtons = [...host.querySelectorAll('.admin-nav-button')] as HTMLButtonElement[];
    navButtons[1].click();
    await flushUi();

    const togglePanel = [...host.querySelectorAll('.admin-action-panel')]
      .find((panel) => panel.querySelector('.admin-field-grid input[type="checkbox"]'));
    expect(togglePanel).toBeTruthy();
    const checkbox = togglePanel?.querySelector('.admin-field-grid input[type="checkbox"]') as HTMLInputElement | null;
    expect(checkbox).toBeTruthy();
    expect(parsedBody(togglePanel as Element).enabled).toBe(false);

    checkbox?.click();
    checkbox?.dispatchEvent(new Event('change', { bubbles: true }));
    await flushUi();

    expect(parsedBody(togglePanel as Element).enabled).toBe(true);
    app.unmount();
  });
});
