import { loadDesktopConfig } from './config';
import { eventBus } from './eventBus';

type WsEnvelope = {
  messageId?: number;
  type: string;
  payload: unknown;
};

let socket: WebSocket | null = null;
let heartbeatTimer: number | null = null;

export function connectWsMessageBus(): void {
  const config = loadDesktopConfig();
  if (!config.accessToken || socket?.readyState === WebSocket.OPEN) {
    return;
  }
  const lastMessageId = localStorage.getItem('ws_last_message_id') ?? '0';
  socket = new WebSocket(`${config.wsUrl}?token=${encodeURIComponent(config.accessToken)}&lastMessageId=${lastMessageId}`);
  socket.onopen = () => {
    eventBus.emit('ws:status-change', { connected: true });
    socket?.send(JSON.stringify({ type: 'RECONNECT', lastMessageId }));
    heartbeatTimer = window.setInterval(() => {
      if (socket?.readyState === WebSocket.OPEN) {
        socket.send(JSON.stringify({ type: 'PING' }));
      }
    }, 30000);
  };
  socket.onmessage = (event) => {
    const envelope = JSON.parse(event.data) as WsEnvelope;
    if (envelope.messageId) {
      localStorage.setItem('ws_last_message_id', String(envelope.messageId));
    }
    if (envelope.type === 'IMAGE_SERVICE_STATUS') {
      eventBus.emit('image:status-changed', envelope.payload);
    }
    eventBus.emit(envelope.type, envelope.payload);
  };
  socket.onclose = () => {
    if (heartbeatTimer) {
      window.clearInterval(heartbeatTimer);
      heartbeatTimer = null;
    }
    socket = null;
    eventBus.emit('ws:status-change', { connected: false });
  };
  socket.onerror = () => {
    eventBus.emit('ws:status-change', { connected: false });
  };
}
