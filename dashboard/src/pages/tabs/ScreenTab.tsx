import { useEffect, useRef, useState, useCallback } from 'react';
import { useOutletContext } from 'react-router-dom';
import { useWsStore } from '../../store/wsStore';
import { commands } from '../../api/client';
import type { WsMessage, DeviceResponse } from '../../types';
import Peer from 'simple-peer';

interface Ctx { deviceId: string; device: DeviceResponse }

export default function ScreenTab() {
  const { deviceId, device } = useOutletContext<Ctx>();
  const videoRef = useRef<HTMLVideoElement>(null);
  const peerRef = useRef<Peer.Instance | null>(null);
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [streaming, setStreaming] = useState(false);
  const [connecting, setConnecting] = useState(false);
  const [status, setStatus] = useState('');
  const [error, setError] = useState('');
  const send = useWsStore((s) => s.send);
  const wsConnected = useWsStore((s) => s.connected);
  const onWs = useWsStore((s) => s.on);

  const createPeer = useCallback(() => {
    if (peerRef.current) return;

    const peer = new Peer({
      initiator: true,
      trickle: true,
      config: {
        iceServers: [
          { urls: 'stun:stun.l.google.com:19302' },
          { urls: 'stun:stun1.l.google.com:19302' },
        ],
      },
    });

    peer.on('signal', (data) => {
      if (data.type === 'offer') {
        setStatus('Отправка предложения…');
        send({
          type: 'screen_offer',
          device_id: deviceId,
          sdp: data.sdp,
        });
      } else if (data.type === 'candidate' && data.candidate) {
        const c = data.candidate;
        send({
          type: 'screen_ice',
          device_id: deviceId,
          candidate: `${c.sdpMid}|${c.sdpMLineIndex}|${c.candidate}`,
        });
      }
    });

    peer.on('stream', (stream) => {
      if (timeoutRef.current) clearTimeout(timeoutRef.current);
      if (videoRef.current) {
        videoRef.current.srcObject = stream;
      }
      setStreaming(true);
      setConnecting(false);
      setStatus('');
    });

    peer.on('close', () => {
      setStreaming(false);
      setConnecting(false);
    });

    peer.on('error', (err) => {
      setError(`Ошибка WebRTC: ${err.message}`);
      setStreaming(false);
      setConnecting(false);
    });

    peerRef.current = peer;
  }, [deviceId, send]);

  useEffect(() => {
    const unsubReady = onWs('screen_ready', (msg: WsMessage) => {
      if (msg.device_id !== deviceId) return;
      setStatus('Устройство готово, установка соединения…');
      createPeer();
    });

    const unsubAnswer = onWs('screen_answer', (msg: WsMessage) => {
      if (msg.device_id !== deviceId) return;
      if (peerRef.current && msg.sdp) {
        setStatus('Получен ответ, подключение…');
        peerRef.current.signal({ type: 'answer', sdp: msg.sdp });
      }
    });

    const unsubIce = onWs('screen_ice', (msg: WsMessage) => {
      if (msg.device_id !== deviceId) return;
      if (peerRef.current && msg.candidate) {
        const parts = msg.candidate.split('|');
        if (parts.length >= 3) {
          peerRef.current.signal({
            type: 'candidate',
            candidate: {
              candidate: parts[2],
              sdpMid: parts[0],
              sdpMLineIndex: parseInt(parts[1], 10),
            },
          } as Peer.SignalData);
        }
      }
    });

    return () => {
      unsubReady();
      unsubAnswer();
      unsubIce();
    };
  }, [deviceId, onWs, createPeer]);

  useEffect(() => {
    return () => {
      if (timeoutRef.current) clearTimeout(timeoutRef.current);
      if (peerRef.current) {
        peerRef.current.destroy();
        peerRef.current = null;
      }
    };
  }, []);

  const startStream = async () => {
    setError('');

    if (!device?.is_online) {
      setError('Устройство оффлайн. Экран доступен только когда устройство в сети.');
      return;
    }

    if (!wsConnected) {
      setError('Нет подключения к серверу. Попробуйте обновить страницу.');
      return;
    }

    setConnecting(true);
    setStatus('Отправка команды…');

    timeoutRef.current = setTimeout(() => {
      if (!streaming) {
        setError('Устройство не ответило. Пользователь должен подтвердить запись экрана на устройстве.');
        stopStream();
      }
    }, 30_000); // 30s — user needs to approve dialog

    try {
      await commands.send(deviceId, { command_type: 'request_screen' });
      setStatus('Ожидание подтверждения на устройстве…');
    } catch {
      setError('Не удалось отправить команду');
      setConnecting(false);
    }
  };

  const stopStream = () => {
    if (timeoutRef.current) {
      clearTimeout(timeoutRef.current);
      timeoutRef.current = null;
    }
    if (peerRef.current) {
      peerRef.current.destroy();
      peerRef.current = null;
    }
    if (videoRef.current) {
      videoRef.current.srcObject = null;
    }
    send({ type: 'screen_stop', device_id: deviceId });
    setStreaming(false);
    setConnecting(false);
    setStatus('');
  };

  return (
    <div className="rounded-xl border bg-white p-5">
      <div className="mb-4 flex items-center justify-between">
        <h3 className="font-semibold">Экран устройства</h3>
        {streaming ? (
          <button
            onClick={stopStream}
            className="rounded-lg bg-red-600 px-4 py-2 text-sm text-white hover:bg-red-700"
          >
            Остановить
          </button>
        ) : (
          <button
            onClick={startStream}
            disabled={connecting || !device?.is_online}
            className="rounded-lg bg-purple-600 px-4 py-2 text-sm text-white hover:bg-purple-700 disabled:opacity-50"
          >
            {connecting ? 'Подключение…' : !device?.is_online ? '🖥 Устройство оффлайн' : '🖥 Смотреть экран'}
          </button>
        )}
      </div>

      {error && <p className="mb-4 text-sm text-red-600">{error}</p>}
      {connecting && status && <p className="mb-4 text-sm text-purple-600">{status}</p>}

      <div className="relative aspect-[9/16] max-h-[70vh] mx-auto overflow-hidden rounded-lg bg-gray-900">
        <video
          ref={videoRef}
          autoPlay
          playsInline
          muted
          className="h-full w-full object-contain"
        />
        {!streaming && !connecting && (
          <div className="absolute inset-0 flex items-center justify-center text-white/50">
            <p>Нажмите «Смотреть экран» для просмотра</p>
          </div>
        )}
        {connecting && (
          <div className="absolute inset-0 flex flex-col items-center justify-center gap-3">
            <div className="h-8 w-8 animate-spin rounded-full border-4 border-white border-t-transparent" />
          </div>
        )}
        {streaming && (
          <div className="absolute left-3 top-3 flex items-center gap-1.5 rounded-full bg-purple-600 px-2.5 py-1">
            <span className="h-2 w-2 animate-pulse rounded-full bg-white" />
            <span className="text-xs font-medium text-white">LIVE</span>
          </div>
        )}
      </div>

      <p className="mt-3 text-xs text-gray-400 text-center">
        Для начала трансляции пользователь должен подтвердить запись экрана на устройстве.
      </p>
    </div>
  );
}
