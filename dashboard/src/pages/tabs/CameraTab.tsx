import { useEffect, useRef, useState } from 'react';
import { useOutletContext } from 'react-router-dom';
import { useWsStore } from '../../store/wsStore';
import { commands } from '../../api/client';
import type { WsMessage, DeviceResponse } from '../../types';
import Peer from 'simple-peer';

interface Ctx { deviceId: string; device: DeviceResponse }

export default function CameraTab() {
  const { deviceId, device } = useOutletContext<Ctx>();
  const videoRef = useRef<HTMLVideoElement>(null);
  const peerRef = useRef<Peer.Instance | null>(null);
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [streaming, setStreaming] = useState(false);
  const [connecting, setConnecting] = useState(false);
  const [error, setError] = useState('');
  const send = useWsStore((s) => s.send);
  const wsConnected = useWsStore((s) => s.connected);
  const onWs = useWsStore((s) => s.on);

  useEffect(() => {
    const unsubAnswer = onWs('webrtc_answer', (msg: WsMessage) => {
      if (msg.device_id !== deviceId) return;
      if (peerRef.current && msg.sdp) {
        peerRef.current.signal({ type: 'answer', sdp: msg.sdp });
      }
    });

    const unsubIce = onWs('webrtc_ice', (msg: WsMessage) => {
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
      unsubAnswer();
      unsubIce();
      if (timeoutRef.current) clearTimeout(timeoutRef.current);
      stopStream();
    };
  }, [deviceId, onWs, send]);

  const startStream = async () => {
    setError('');

    if (!device?.is_online) {
      setError('Устройство оффлайн. Камера доступна только когда устройство в сети.');
      return;
    }

    if (!wsConnected) {
      setError('Нет подключения к серверу. Попробуйте обновить страницу.');
      return;
    }

    setConnecting(true);

    // Timeout — if no stream in 15 seconds, abort
    timeoutRef.current = setTimeout(() => {
      if (!peerRef.current?.connected) {
        setError('Устройство не ответило. Убедитесь, что приложение запущено на устройстве.');
        stopStream();
      }
    }, 15_000);

    try {
      // Send command to device to start camera
      await commands.send(deviceId, { command_type: 'request_camera' });

      // Create WebRTC peer (initiator)
      const peer = new Peer({
        initiator: true,
        trickle: true,
        config: {
          iceServers: [{ urls: 'stun:stun.l.google.com:19302' }],
        },
      });

      peer.on('signal', (data) => {
        if (data.type === 'offer') {
          send({
            type: 'webrtc_offer',
            device_id: deviceId,
            sdp: data.sdp,
          });
        } else if (data.type === 'candidate' && data.candidate) {
          const c = data.candidate;
          send({
            type: 'webrtc_ice',
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
    } catch {
      setError('Не удалось запросить камеру');
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
    send({ type: 'webrtc_stop', device_id: deviceId });
    setStreaming(false);
    setConnecting(false);
  };

  return (
    <div className="rounded-xl border bg-white p-5">
      <div className="mb-4 flex items-center justify-between">
        <h3 className="font-semibold">Камера</h3>
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
            className="rounded-lg bg-blue-600 px-4 py-2 text-sm text-white hover:bg-blue-700 disabled:opacity-50"
          >
            {connecting ? 'Подключение…' : !device?.is_online ? '📷 Устройство оффлайн' : '📷 Запустить камеру'}
          </button>
        )}
      </div>

      {error && <p className="mb-4 text-sm text-red-600">{error}</p>}

      <div className="relative aspect-video overflow-hidden rounded-lg bg-gray-900">
        <video
          ref={videoRef}
          autoPlay
          playsInline
          muted
          className="h-full w-full object-contain"
        />
        {!streaming && !connecting && (
          <div className="absolute inset-0 flex items-center justify-center text-white/50">
            <p>Нажмите «Запустить камеру» для просмотра</p>
          </div>
        )}
        {connecting && (
          <div className="absolute inset-0 flex items-center justify-center">
            <div className="h-8 w-8 animate-spin rounded-full border-4 border-white border-t-transparent" />
          </div>
        )}
        {streaming && (
          <div className="absolute left-3 top-3 flex items-center gap-1.5 rounded-full bg-red-600 px-2.5 py-1">
            <span className="h-2 w-2 animate-pulse rounded-full bg-white" />
            <span className="text-xs font-medium text-white">LIVE</span>
          </div>
        )}
      </div>
    </div>
  );
}
