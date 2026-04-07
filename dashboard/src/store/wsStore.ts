import { create } from 'zustand';
import { getAccessToken } from '../api/client';
import type { WsMessage } from '../types';

interface WsState {
  connected: boolean;
  ws: WebSocket | null;
  listeners: Map<string, Set<(msg: WsMessage) => void>>;
  connect: () => void;
  disconnect: () => void;
  send: (msg: WsMessage) => void;
  on: (type: string, cb: (msg: WsMessage) => void) => () => void;
}

export const useWsStore = create<WsState>((set, get) => ({
  connected: false,
  ws: null,
  listeners: new Map(),

  connect: () => {
    const token = getAccessToken();
    if (!token) return;

    const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const host = window.location.host;
    const url = `${proto}//${host}/ws/parent?access_token=${encodeURIComponent(token)}`;

    const ws = new WebSocket(url);

    ws.onopen = () => {
      set({ connected: true, ws });
    };

    ws.onmessage = (event) => {
      try {
        const msg: WsMessage = JSON.parse(event.data);
        const listeners = get().listeners.get(msg.type);
        listeners?.forEach((cb) => cb(msg));
      } catch {
        // ignore malformed messages
      }
    };

    ws.onclose = () => {
      set({ connected: false, ws: null });
      // Reconnect after 3 seconds
      setTimeout(() => {
        if (getAccessToken()) get().connect();
      }, 3000);
    };

    ws.onerror = () => {
      ws.close();
    };

    set({ ws });
  },

  disconnect: () => {
    get().ws?.close();
    set({ ws: null, connected: false });
  },

  send: (msg) => {
    const { ws, connected } = get();
    if (ws && connected) {
      ws.send(JSON.stringify(msg));
    }
  },

  on: (type, cb) => {
    const { listeners } = get();
    if (!listeners.has(type)) listeners.set(type, new Set());
    listeners.get(type)!.add(cb);
    return () => {
      listeners.get(type)?.delete(cb);
    };
  },
}));
