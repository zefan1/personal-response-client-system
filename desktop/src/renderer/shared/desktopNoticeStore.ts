import { reactive } from 'vue';

export const desktopNoticeState = reactive({
  message: '',
  kind: 'info' as 'info' | 'error'
});

export function setDesktopNotice(message: string, kind: 'info' | 'error' = 'info'): void {
  desktopNoticeState.message = message;
  desktopNoticeState.kind = kind;
}

export function clearDesktopNotice(): void {
  desktopNoticeState.message = '';
  desktopNoticeState.kind = 'info';
}
