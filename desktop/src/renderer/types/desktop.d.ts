import type { DesktopBridge } from '../../preload/preload';

declare global {
  interface Window {
    desktopBridge?: DesktopBridge;
  }
}
