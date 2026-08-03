import { loadDesktopConfig } from './config';
import { eventBus } from './eventBus';

type WsEnvelope = {
  messageId?: number;
  type: string;
  payload: unknown;
};

let socket: WebSocket | null = null;
let heartbeatTimer: number | null = null;
let reconnectTimer: number | null = null;
let reconnectAttempt = 0;
let shouldReconnect = false;
const RECONNECT_BASE_DELAY_MS = 1000;
const RECONNECT_MAX_DELAY_MS = 10000;

export function connectWsMessageBus(): void {
  const config = loadDesktopConfig();
  shouldReconnect = Boolean(config.accessToken);
  if (!config.accessToken || socket?.readyState === WebSocket.OPEN || socket?.readyState === WebSocket.CONNECTING) {
    return;
  }
  const lastMessageId = localStorage.getItem('ws_last_message_id') ?? '0';
  const currentSocket = new WebSocket(`${config.wsUrl}?token=${encodeURIComponent(config.accessToken)}&lastMessageId=${lastMessageId}`);
  socket = currentSocket;
  currentSocket.onopen = () => {
    if (socket !== currentSocket) return;
    clearReconnectTimer();
    reconnectAttempt = 0;
    eventBus.emit('ws:status-change', { connected: true });
    currentSocket.send(JSON.stringify({ type: 'RECONNECT', lastMessageId }));
    heartbeatTimer = window.setInterval(() => {
      if (currentSocket.readyState === WebSocket.OPEN) {
        currentSocket.send(JSON.stringify({ type: 'PING' }));
      }
    }, 30000);
  };
  currentSocket.onmessage = (event) => {
    const envelope = JSON.parse(event.data) as WsEnvelope;
    if (envelope.messageId) {
      localStorage.setItem('ws_last_message_id', String(envelope.messageId));
    }
    if (envelope.type === 'IMAGE_SERVICE_STATUS') {
      eventBus.emit('image:status-changed', envelope.payload);
    }
    if (envelope.type === 'AUTH_INVALIDATED') {
      shouldReconnect = false;
      clearReconnectTimer();
      eventBus.emit('auth:expired', envelope.payload);
    }
    eventBus.emit(envelope.type, envelope.payload);
  };
  currentSocket.onclose = () => {
    if (socket !== currentSocket) return;
    if (heartbeatTimer) {
      window.clearInterval(heartbeatTimer);
      heartbeatTimer = null;
    }
    socket = null;
    eventBus.emit('ws:status-change', { connected: false });
    scheduleReconnect();
  };
  currentSocket.onerror = () => {
    eventBus.emit('ws:status-change', { connected: false });
  };
}

export function disconnectWsMessageBus(): void {
  shouldReconnect = false;
  clearReconnectTimer();
  clearHeartbeatTimer();
  const currentSocket = socket;
  socket = null;
  currentSocket?.close();
}

function scheduleReconnect(): void {
  if (!shouldReconnect || reconnectTimer !== null) return;
  const delay = Math.min(RECONNECT_BASE_DELAY_MS * (2 ** reconnectAttempt), RECONNECT_MAX_DELAY_MS);
  reconnectAttempt += 1;
  reconnectTimer = window.setTimeout(() => {
    reconnectTimer = null;
    connectWsMessageBus();
  }, delay);
}

function clearReconnectTimer(): void {
  if (reconnectTimer === null) return;
  window.clearTimeout(reconnectTimer);
  reconnectTimer = null;
}

function clearHeartbeatTimer(): void {
  if (heartbeatTimer === null) return;
  window.clearInterval(heartbeatTimer);
  heartbeatTimer = null;
}
