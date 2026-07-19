# 跨平台前台窗口识图 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Capture the complete foreground WeChat, WeCom, browser, or Douyin support window after hiding the assistant, then send that un-cropped image to the existing multimodal recognition API with a platform-neutral schema.

**Architecture:** Extract foreground capture into a dependency-injected Electron main-process coordinator so hiding, active-window discovery, source matching, fallback capture, and window restoration are unit-testable. Keep the renderer API stable except for non-sensitive capture metadata. Extend the backend parser compatibly and migrate the recognition prompt with Flyway V72 without overwriting customized prompts.

**Tech Stack:** Electron 40, `active-win` 9, TypeScript, Vitest, Vue 3, Java 17, Spring Boot, Jackson, Flyway, Maven.

---

## File Map

- Create `desktop/src/main/foregroundWindowCapture.ts`: platform-independent capture coordinator and source matching helpers.
- Create `desktop/src/main/foregroundWindowCapture.test.ts`: Node-environment unit tests for capture sequencing and restoration.
- Modify `desktop/src/main/main.ts`: adapt Electron and `active-win` to the coordinator.
- Modify `desktop/package.json` and `desktop/package-lock.json`: add exact `active-win` runtime dependency.
- Modify `desktop/scripts/package-verify.mjs`: assert native `active-win` files are present in the packaged archive and unpacked directory.
- Modify `desktop/src/preload/preload.cts`: expose `captureMode`, width, and height without window identity.
- Modify `desktop/src/renderer/types/desktop.ts`: align renderer bridge result type.
- Modify `desktop/src/renderer/shared/desktopBridge.test.ts`: verify capture metadata stays intact.
- Modify `src/main/java/com/privateflow/modules/image/RecognitionResult.java`: add optional cross-platform identity metadata while preserving the four-argument constructor.
- Modify `src/main/java/com/privateflow/modules/image/parser/RecognitionResultParser.java`: support cross-platform status, identifier, confidence, and explicit failure reasons.
- Create `src/test/java/com/privateflow/modules/image/parser/RecognitionResultParserTest.java`: parser compatibility and failure tests.
- Create `src/main/resources/db/migration/V72__cross_platform_image_recognition_prompt.sql`: install the platform-neutral prompt while preserving custom prompts.
- Modify `scripts/verify_module_c.py`: verify the new schema and migration contract.

### Task 1: Build the foreground capture coordinator with TDD

**Files:**
- Create: `desktop/src/main/foregroundWindowCapture.test.ts`
- Create: `desktop/src/main/foregroundWindowCapture.ts`

- [ ] **Step 1: Write the failing coordinator tests**

Create a Node-environment Vitest file with three behaviors: foreground window match, screen fallback, and restoration after an exception.

```ts
// @vitest-environment node
import { describe, expect, it, vi } from 'vitest';
import {
  captureForegroundWindow,
  parseElectronWindowId,
  type CaptureDependencies,
  type CaptureSource
} from './foregroundWindowCapture.js';

function image(bytes: string, width = 900, height = 700) {
  return {
    toPNG: () => Buffer.from(bytes),
    getSize: () => ({ width, height })
  };
}

function source(id: string, name: string, bytes: string): CaptureSource {
  return { id, name, thumbnail: image(bytes) };
}

function dependencies(overrides: Partial<CaptureDependencies> = {}): CaptureDependencies {
  const state = { visible: true, focused: true, alwaysOnTop: true };
  return {
    assistantWindow: {
      isVisible: () => state.visible,
      isFocused: () => state.focused,
      isAlwaysOnTop: () => state.alwaysOnTop,
      hide: vi.fn(() => { state.visible = false; state.focused = false; }),
      show: vi.fn(() => { state.visible = true; }),
      showInactive: vi.fn(() => { state.visible = true; }),
      focus: vi.fn(() => { state.focused = true; }),
      setAlwaysOnTop: vi.fn((value: boolean) => { state.alwaysOnTop = value; }),
      getNativeWindowHandle: () => Buffer.from([11, 0, 0, 0])
    },
    getActiveWindow: vi.fn()
      .mockResolvedValueOnce({ id: 11, title: '私域辅助系统', ownerName: 'electron.exe', bounds: { width: 420, height: 760 } })
      .mockResolvedValueOnce({ id: 77, title: '抖音企业号', ownerName: 'msedge.exe', bounds: { width: 1400, height: 900 } }),
    getSources: vi.fn(async (types) => types[0] === 'window'
      ? [source('window:77:0', '抖音企业号', 'window-image')]
      : [source('screen:0:0', 'Screen 1', 'screen-image')]),
    delay: vi.fn(async () => undefined),
    minImageDimension: 200,
    ...overrides
  };
}

describe('foregroundWindowCapture', () => {
  it('parses Electron native window source ids', () => {
    expect(parseElectronWindowId('window:77:0')).toBe(77);
    expect(parseElectronWindowId('screen:0:0')).toBeNull();
  });

  it('hides the assistant and captures the complete foreground window', async () => {
    const deps = dependencies();

    const result = await captureForegroundWindow(deps);

    expect(result).toMatchObject({
      success: true,
      captureMode: 'FOREGROUND_WINDOW',
      imageBase64: Buffer.from('window-image').toString('base64'),
      width: 900,
      height: 700
    });
    expect(deps.assistantWindow.hide).toHaveBeenCalledOnce();
    expect(deps.assistantWindow.show).toHaveBeenCalledOnce();
    expect(deps.assistantWindow.focus).toHaveBeenCalledOnce();
  });

  it('uses a hidden-screen fallback when no window source matches', async () => {
    const deps = dependencies({
      getSources: vi.fn(async (types) => types[0] === 'window'
        ? []
        : [source('screen:0:0', 'Screen 1', 'screen-image')])
    });

    await expect(captureForegroundWindow(deps)).resolves.toMatchObject({
      success: true,
      captureMode: 'SCREEN_FALLBACK',
      imageBase64: Buffer.from('screen-image').toString('base64')
    });
  });

  it('restores the assistant after capture throws', async () => {
    const deps = dependencies({
      getSources: vi.fn(async () => { throw new Error('capture unavailable'); })
    });

    await expect(captureForegroundWindow(deps)).resolves.toMatchObject({
      success: false,
      error: 'CAPTURE_FAILED'
    });
    expect(deps.assistantWindow.show).toHaveBeenCalledOnce();
    expect(deps.assistantWindow.focus).toHaveBeenCalledOnce();
  });
});
```

- [ ] **Step 2: Run the test and verify RED**

Run from `desktop`:

```powershell
npm run test -- src/main/foregroundWindowCapture.test.ts
```

Expected: FAIL because `foregroundWindowCapture.ts` does not exist.

- [ ] **Step 3: Implement the minimal coordinator**

Create `foregroundWindowCapture.ts` with local structural interfaces so unit tests do not import Electron.

```ts
export type CaptureMode = 'FOREGROUND_WINDOW' | 'SCREEN_FALLBACK';

export type ScreenshotCaptureResult = {
  success: boolean;
  imageBase64?: string;
  width?: number;
  height?: number;
  captureMode?: CaptureMode;
  error?: 'CAPTURE_FAILED';
  message?: string;
};

export type ActiveWindowInfo = {
  id: number;
  title: string;
  ownerName: string;
  bounds: { width: number; height: number };
};

export type CaptureSource = {
  id: string;
  name: string;
  thumbnail: {
    toPNG(): Buffer;
    getSize(): { width: number; height: number };
  };
};

export type CaptureDependencies = {
  assistantWindow: {
    isVisible(): boolean;
    isFocused(): boolean;
    isAlwaysOnTop(): boolean;
    hide(): void;
    show(): void;
    showInactive(): void;
    focus(): void;
    setAlwaysOnTop(value: boolean): void;
    getNativeWindowHandle(): Buffer;
  };
  getActiveWindow(): Promise<ActiveWindowInfo | undefined>;
  getSources(types: Array<'window' | 'screen'>, size: { width: number; height: number }): Promise<CaptureSource[]>;
  delay(ms: number): Promise<void>;
  minImageDimension: number;
};

export function parseElectronWindowId(sourceId: string): number | null {
  const match = /^window:(\d+):/.exec(sourceId);
  return match ? Number(match[1]) : null;
}

export async function captureForegroundWindow(deps: CaptureDependencies): Promise<ScreenshotCaptureResult> {
  const previous = {
    visible: deps.assistantWindow.isVisible(),
    focused: deps.assistantWindow.isFocused(),
    alwaysOnTop: deps.assistantWindow.isAlwaysOnTop()
  };
  const ownId = nativeWindowId(deps.assistantWindow.getNativeWindowHandle());
  deps.assistantWindow.hide();
  try {
    const active = await waitForTargetWindow(deps, ownId);
    if (active) {
      const windowSources = await deps.getSources(['window'], boundedSize(active.bounds));
      const matched = matchWindowSource(windowSources, active);
      const windowResult = matched && imageResult(matched, 'FOREGROUND_WINDOW', deps.minImageDimension);
      if (windowResult) return windowResult;
    }
    const screenSources = await deps.getSources(['screen'], { width: 1920, height: 1080 });
    const screen = screenSources.find((item) => item.id.startsWith('screen:0:')) ?? screenSources[0];
    return screen && imageResult(screen, 'SCREEN_FALLBACK', deps.minImageDimension)
      || { success: false, error: 'CAPTURE_FAILED', message: 'No usable foreground window or screen source detected' };
  } catch {
    return { success: false, error: 'CAPTURE_FAILED', message: 'Screenshot capture failed' };
  } finally {
    if (deps.assistantWindow.isAlwaysOnTop() !== previous.alwaysOnTop) {
      deps.assistantWindow.setAlwaysOnTop(previous.alwaysOnTop);
    }
    if (previous.visible) {
      if (previous.focused) {
        deps.assistantWindow.show();
        deps.assistantWindow.focus();
      } else {
        deps.assistantWindow.showInactive();
      }
    }
  }
}
```

Add private helpers for ten 50 ms foreground checks, little-endian native window ID parsing, exact source ID matching followed by title plus aspect-ratio matching, bounded thumbnail dimensions, and minimum image validation. Do not log or return title, owner, bounds, or window ID.

- [ ] **Step 4: Run the focused test and verify GREEN**

```powershell
npm run test -- src/main/foregroundWindowCapture.test.ts
```

Expected: 4 tests pass.

- [ ] **Step 5: Commit the coordinator**

```powershell
git add desktop/src/main/foregroundWindowCapture.ts desktop/src/main/foregroundWindowCapture.test.ts
git commit -m "feat: add foreground window capture coordinator"
```

### Task 2: Integrate `active-win` and Electron IPC

**Files:**
- Modify: `desktop/package.json`
- Modify: `desktop/package-lock.json`
- Modify: `desktop/src/main/main.ts`

- [ ] **Step 1: Install the exact runtime dependency**

Run from `desktop`:

```powershell
npm install active-win@9.0.0 --save-exact
```

Expected: `active-win` appears under `dependencies`, and the lockfile records version `9.0.0`.

- [ ] **Step 2: Replace the screen-only IPC implementation**

Import the dependency and coordinator:

```ts
import activeWindow from 'active-win';
import { captureForegroundWindow } from './foregroundWindowCapture.js';
```

Replace `registerScreenshotCapture()` with an adapter that returns `CAPTURE_FAILED` when `mainWindow` is absent and otherwise calls the coordinator:

```ts
function registerScreenshotCapture() {
  ipcMain.handle('screenshot:capture', async () => {
    const assistantWindow = mainWindow;
    if (!assistantWindow) {
      return { success: false, error: 'CAPTURE_FAILED', message: 'Assistant window is unavailable' };
    }
    return captureForegroundWindow({
      assistantWindow,
      getActiveWindow: async () => {
        const current = await activeWindow();
        if (!current) return undefined;
        return {
          id: current.id,
          title: current.title,
          ownerName: current.owner?.name ?? '',
          bounds: { width: current.bounds.width, height: current.bounds.height }
        };
      },
      getSources: async (types, thumbnailSize) => desktopCapturer.getSources({
        types,
        thumbnailSize,
        fetchWindowIcons: false
      }),
      delay: (ms) => new Promise((resolve) => setTimeout(resolve, ms)),
      minImageDimension: DESKTOP_DEFAULTS.clipboardMinImageDimension
    });
  });
}
```

Do not emit window title, process name, window ID, or bounds to console output.

- [ ] **Step 3: Run coordinator tests and main-process typecheck**

```powershell
npm run test -- src/main/foregroundWindowCapture.test.ts
npx tsc -p tsconfig.main.json --noEmit
```

Expected: both commands exit 0.

- [ ] **Step 4: Run Electron smoke to load the native dependency**

```powershell
npm run electron:smoke
```

Expected: `electron_smoke=passed`; no native-module load error occurs.

- [ ] **Step 5: Commit the Electron integration**

```powershell
git add desktop/package.json desktop/package-lock.json desktop/src/main/main.ts
git commit -m "feat: capture the active foreground window"
```

### Task 3: Align preload and renderer capture contracts

**Files:**
- Modify: `desktop/src/preload/preload.cts`
- Modify: `desktop/src/renderer/types/desktop.ts`
- Modify: `desktop/src/renderer/shared/desktopBridge.test.ts`

- [ ] **Step 1: Write the failing bridge metadata test**

Import `captureScreenshot` in `desktopBridge.test.ts` and add:

```ts
it('preserves non-sensitive foreground capture metadata', async () => {
  const capture = vi.fn(async () => ({
    success: true,
    imageBase64: 'image',
    width: 1400,
    height: 900,
    captureMode: 'FOREGROUND_WINDOW' as const
  }));
  (window as unknown as { desktopBridge: { captureScreenshot: typeof capture } }).desktopBridge = {
    captureScreenshot: capture
  };

  const result = await captureScreenshot();
  expect(result).toMatchObject({ success: true, imageBase64: 'image' });
  expect(result.width).toBe(1400);
  expect(result.height).toBe(900);
  expect(result.captureMode).toBe('FOREGROUND_WINDOW');
});
```

- [ ] **Step 2: Run the test and verify RED**

```powershell
npm run test -- src/renderer/shared/desktopBridge.test.ts
```

Expected: TypeScript transform fails because `BridgeResult` does not define width, height, or captureMode.

- [ ] **Step 3: Update both bridge result types**

Replace `screenTitle` in preload with:

```ts
width?: number;
height?: number;
captureMode?: 'FOREGROUND_WINDOW' | 'SCREEN_FALLBACK';
```

Add the same optional fields to renderer `BridgeResult`. Do not expose title, process, ID, URL, or window bounds.

- [ ] **Step 4: Run bridge and App tests**

```powershell
npm run test -- src/renderer/shared/desktopBridge.test.ts src/renderer/App.test.ts
```

Expected: all tests pass.

- [ ] **Step 5: Commit the contract change**

```powershell
git add desktop/src/preload/preload.cts desktop/src/renderer/types/desktop.ts desktop/src/renderer/shared/desktopBridge.test.ts
git commit -m "test: cover foreground capture metadata"
```

### Task 4: Extend the recognition parser compatibly

**Files:**
- Create: `src/test/java/com/privateflow/modules/image/parser/RecognitionResultParserTest.java`
- Modify: `src/main/java/com/privateflow/modules/image/RecognitionResult.java`
- Modify: `src/main/java/com/privateflow/modules/image/parser/RecognitionResultParser.java`

- [ ] **Step 1: Write the failing parser tests**

Create tests for the legacy schema, a Douyin result without a phone number, and explicit inability to determine:

```java
package com.privateflow.modules.image.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.image.ImageRecognitionException;
import org.junit.jupiter.api.Test;

class RecognitionResultParserTest {

  private final RecognitionResultParser parser = new RecognitionResultParser(new ObjectMapper());

  @Test
  void keepsTheLegacyRecognitionSchemaCompatible() {
    var result = parser.parse("""
        {"nickname":"Alice","phone":"138-0000-0001","messages":[{"role":"client","text":"hello"}],"timestamp":"12:00"}
        """);

    assertThat(result.nickname()).isEqualTo("Alice");
    assertThat(result.phone()).isEqualTo("13800000001");
    assertThat(result.messages()).hasSize(1);
  }

  @Test
  void parsesDouyinIdentityWithoutInventingAPhoneNumber() {
    var result = parser.parse("""
        {
          "status":"OK",
          "platform":"DOUYIN_WEB",
          "nickname":null,
          "phone":null,
          "customerIdentifier":"douyin_user_88",
          "messages":[{"role":"client","text":"想了解价格"}],
          "timestamp":null,
          "confidence":0.92,
          "failureReason":null
        }
        """);

    assertThat(result.nickname()).isEqualTo("douyin_user_88");
    assertThat(result.phone()).isNull();
    assertThat(result.customerIdentifier()).isEqualTo("douyin_user_88");
    assertThat(result.platform()).isEqualTo("DOUYIN_WEB");
    assertThat(result.confidence()).isEqualTo(0.92);
  }

  @Test
  void surfacesTheModelFailureReasonWithoutGuessing() {
    assertThatThrownBy(() -> parser.parse("""
        {
          "status":"UNABLE_TO_DETERMINE",
          "platform":"UNKNOWN",
          "messages":[],
          "confidence":0,
          "failureReason":"当前窗口未显示可识别的主聊天会话"
        }
        """))
        .isInstanceOf(ImageRecognitionException.class)
        .hasMessage("当前窗口未显示可识别的主聊天会话");
  }
}
```

- [ ] **Step 2: Run the parser test and verify RED**

Run from the repository root:

```powershell
wsl bash -lc "cd '/mnt/c/Users/85314/Desktop/私域工具/私域辅助系统' && mvn -Dtest=RecognitionResultParserTest test"
```

Expected: compilation fails because the new `RecognitionResult` accessors do not exist and the failure reason is ignored.

- [ ] **Step 3: Extend `RecognitionResult` without breaking old constructors**

```java
public record RecognitionResult(
    String nickname,
    String phone,
    List<Message> messages,
    String timestamp,
    String customerIdentifier,
    String platform,
    double confidence
) {
  public RecognitionResult(String nickname, String phone, List<Message> messages, String timestamp) {
    this(nickname, phone, messages, timestamp, null, "UNKNOWN", 0.0);
  }
}
```

- [ ] **Step 4: Parse the platform-neutral schema**

In `RecognitionResultParser.parse`:

1. Read `status`, `failureReason`, `platform`, `customerIdentifier`, and `confidence` before validating messages.
2. When status is `UNABLE_TO_DETERMINE`, throw `failed(failureReason)` with `当前窗口未显示可识别的主聊天会话` as the fallback.
3. When messages are absent or empty, prefer `failureReason` over the existing generic message.
4. Normalize the identifier and use it as nickname fallback when nickname is absent.
5. Clamp confidence to `0.0-1.0`.
6. Return the seven-argument `RecognitionResult`.

Use these helpers:

```java
private String firstNonBlank(String first, String second) {
  return first == null || first.isBlank() ? second : first;
}

private double confidence(JsonNode node) {
  if (node == null || !node.isNumber()) return 0.0;
  return Math.max(0.0, Math.min(1.0, node.asDouble()));
}
```

- [ ] **Step 5: Run parser and existing chat tests**

```powershell
wsl bash -lc "cd '/mnt/c/Users/85314/Desktop/私域工具/私域辅助系统' && mvn -Dtest=RecognitionResultParserTest,ChatOrchestrationServiceTest,AiEnvironmentServiceTest test"
```

Expected: all selected tests pass; existing four-argument `RecognitionResult` construction remains valid.

- [ ] **Step 6: Commit the parser change**

```powershell
git add src/main/java/com/privateflow/modules/image/RecognitionResult.java src/main/java/com/privateflow/modules/image/parser/RecognitionResultParser.java src/test/java/com/privateflow/modules/image/parser/RecognitionResultParserTest.java
git commit -m "feat: parse cross-platform image recognition results"
```

### Task 5: Install the platform-neutral prompt safely

**Files:**
- Create: `src/main/resources/db/migration/V72__cross_platform_image_recognition_prompt.sql`
- Modify: `scripts/verify_module_c.py`

- [ ] **Step 1: Extend the static verification first**

Update `verify_module_c.py` so `required_files` contains V72, `RecognitionResult` must contain `customerIdentifier`, `platform`, and `confidence`, and the parser must contain `UNABLE_TO_DETERMINE` and `failureReason`.

Also load V72 and require these tokens:

```python
for token in [
    "微信",
    "企业微信",
    "抖音网页后台",
    "UNKNOWN",
    "UNABLE_TO_DETERMINE",
    "customerIdentifier",
    "failureReason",
    "ON DUPLICATE KEY UPDATE",
]:
    if token not in cross_platform_prompt_sql:
        errors.append(f"V72 migration missing {token}")
```

- [ ] **Step 2: Run static verification and verify RED**

```powershell
$env:PYTHONUTF8='1'; python scripts/verify_module_c.py
```

Expected: FAIL because V72 does not exist and the new parser fields are not yet present in the verification target.

- [ ] **Step 3: Create V72 with a custom-prompt preservation guard**

Use one `INSERT ... ON DUPLICATE KEY UPDATE` statement. The installed prompt must:

- support WeChat, WeCom, Douyin web, and unknown platforms;
- analyze the complete current foreground window without requesting crop coordinates;
- ignore navigation, browser chrome, order panels, contact lists, and non-current conversations;
- require `status`, `platform`, optional `nickname`, optional `phone`, optional `customerIdentifier`, `messages`, optional `timestamp`, `confidence`, and `failureReason`;
- map a visible platform account into `customerIdentifier` and allow phone to be null;
- forbid guessing;
- return `UNABLE_TO_DETERMINE` with an actionable reason when no main chat is visible.

Only replace `system_configs.config_value` when the current value is blank or equals one of the two legacy built-in prompts from V2 and V21. Preserve every other customized prompt.

- [ ] **Step 4: Run static verification and targeted backend tests**

```powershell
$env:PYTHONUTF8='1'; python scripts/verify_module_c.py
wsl bash -lc "cd '/mnt/c/Users/85314/Desktop/私域工具/私域辅助系统' && mvn -Dtest=RecognitionResultParserTest test"
```

Expected: module verification passes and parser tests pass.

- [ ] **Step 5: Commit the migration and verification contract**

```powershell
git add scripts/verify_module_c.py src/main/resources/db/migration/V72__cross_platform_image_recognition_prompt.sql
git commit -m "feat: add cross-platform recognition prompt"
```

### Task 6: Run complete verification and real runtime checks

**Files:**
- No additional source files.

- [ ] **Step 1: Extend package verification for the native dependency**

Import `listPackage` from `@electron/asar` in `desktop/scripts/package-verify.mjs`. After validating `asarPath`, inspect the archive and require at least one path under `node_modules/active-win/`. Also require the corresponding `app.asar.unpacked/node_modules/active-win` directory when the archive contains a `.node` file. Add these facts to the JSON report so a packaged build cannot silently omit the foreground-window native module.

- [ ] **Step 2: Run the desktop checks separately**

From `desktop`, run each command and record its exit code:

```powershell
npm run typecheck
npm run test
npm run build
npm run electron:smoke
npm run renderer:smoke
npm run package:verify
```

Expected: all commands exit 0; both smoke commands report `passed`; package verification confirms the packaged app contains and loads the native `active-win` dependency.

- [ ] **Step 3: Run backend checks separately**

From the repository root:

```powershell
wsl bash -lc "cd '/mnt/c/Users/85314/Desktop/私域工具/私域辅助系统' && mvn test"
$env:PYTHONUTF8='1'; python scripts/verify_module_c.py
python scripts/verify_database_alignment.py
```

Expected: Maven reports zero failures/errors, Module C verification passes, and database alignment reports zero missing migrations or contract violations. Run database alignment from Windows because the script invokes WSL internally.

- [ ] **Step 4: Restart the local backend and Electron main process**

Use the existing local restart workflow so Flyway applies V72 and Electron loads the new main-process code. Do not place API keys or database passwords in command output or progress notes.

Expected: backend starts with schema version 72; Electron opens and the assistant window remains usable.

- [ ] **Step 5: Verify the prompt configuration without exposing secrets**

Log in through the local admin API and read only `image.recognition_prompt`. Confirm it contains WeChat, WeCom, Douyin web, `UNABLE_TO_DETERMINE`, and `customerIdentifier`. Do not print `image.api_key`.

- [ ] **Step 6: Perform real cross-platform acceptance**

For each available target, bring the target conversation to the foreground, click the blue recognition action once, and verify the assistant hides, restores, enters Reply Assistant, and either generates a reply or shows a specific non-hallucinated failure reason:

```text
1. WeChat desktop current conversation
2. WeCom desktop current conversation
3. Chrome or Edge Douyin web support conversation
4. A non-chat browser page, which must return UNABLE_TO_DETERMINE
```

Do not send messages automatically. Do not save or attach captured customer screenshots to test artifacts.

- [ ] **Step 7: Inspect final scope and whitespace**

```powershell
git diff --check
git status --short
git log -8 --oneline --decorate
```

Expected: no whitespace errors; the new commits contain only the files listed in this plan; the pre-existing Skill/LLM worktree changes remain untouched.
