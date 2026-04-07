import { useEffect, useState } from 'react';
import { useOutletContext } from 'react-router-dom';
import { appUsage, location } from '../../api/client';
import type { AppUsageStatsResponse, LocationLatestResponse, DeviceResponse } from '../../types';

interface Ctx {
  device: DeviceResponse;
  deviceId: string;
}

export default function OverviewTab() {
  const { device, deviceId } = useOutletContext<Ctx>();
  const [topApps, setTopApps] = useState<AppUsageStatsResponse[]>([]);
  const [latestLoc, setLatestLoc] = useState<LocationLatestResponse | null>(null);

  useEffect(() => {
    const today = new Date().toISOString().split('T')[0];
    appUsage.stats(deviceId, { date_from: today, date_to: today }).then(setTopApps).catch(() => {});
    location.latest(deviceId).then(setLatestLoc).catch(() => {});
  }, [deviceId]);

  const formatDuration = (secs: number) => {
    const h = Math.floor(secs / 3600);
    const m = Math.floor((secs % 3600) / 60);
    if (h > 0) return `${h}ч ${m}м`;
    return `${m}м`;
  };

  return (
    <div className="grid gap-6 lg:grid-cols-2">
      {/* Device info card */}
      <div className="rounded-xl border bg-white p-5">
        <h3 className="mb-3 font-semibold">Информация</h3>
        <dl className="space-y-2 text-sm">
          <div className="flex justify-between">
            <dt className="text-gray-500">Модель</dt>
            <dd>{device.device_model || '—'}</dd>
          </div>
          <div className="flex justify-between">
            <dt className="text-gray-500">Android</dt>
            <dd>{device.android_version || '—'}</dd>
          </div>
          <div className="flex justify-between">
            <dt className="text-gray-500">Статус</dt>
            <dd className={device.is_online ? 'text-green-600' : 'text-gray-400'}>
              {device.is_online ? 'Онлайн' : 'Оффлайн'}
            </dd>
          </div>
          <div className="flex justify-between">
            <dt className="text-gray-500">Последнее подключение</dt>
            <dd>{device.last_seen_at ? new Date(device.last_seen_at).toLocaleString('ru') : '—'}</dd>
          </div>
        </dl>
      </div>

      {/* Top apps today */}
      <div className="rounded-xl border bg-white p-5">
        <h3 className="mb-3 font-semibold">Приложения сегодня</h3>
        {topApps.length === 0 ? (
          <p className="text-sm text-gray-400">Нет данных</p>
        ) : (
          <ul className="space-y-2">
            {topApps
              .sort((a, b) => b.total_seconds - a.total_seconds)
              .slice(0, 5)
              .map((app) => (
                <li key={app.package_name} className="flex items-center justify-between text-sm">
                  <span className="truncate">{app.app_name}</span>
                  <span className="ml-2 whitespace-nowrap font-medium text-blue-600">
                    {formatDuration(app.total_seconds)}
                  </span>
                </li>
              ))}
          </ul>
        )}
      </div>

      {/* Latest location */}
      <div className="rounded-xl border bg-white p-5 lg:col-span-2">
        <h3 className="mb-3 font-semibold">Последняя позиция</h3>
        {latestLoc ? (
          <div className="text-sm">
            <p>
              📍 {latestLoc.latitude.toFixed(6)}, {latestLoc.longitude.toFixed(6)}
              {latestLoc.accuracy && (
                <span className="ml-2 text-gray-400">±{Math.round(latestLoc.accuracy)}м</span>
              )}
            </p>
            <p className="mt-1 text-gray-400">
              {new Date(latestLoc.recorded_at).toLocaleString('ru')}
            </p>
          </div>
        ) : (
          <p className="text-sm text-gray-400">Нет данных</p>
        )}
      </div>
    </div>
  );
}
