import { useEffect, useState } from 'react';
import { useOutletContext } from 'react-router-dom';
import { appUsage } from '../../api/client';
import type { AppUsageStatsResponse } from '../../types';

interface Ctx {
  deviceId: string;
}

export default function AppsTab() {
  const { deviceId } = useOutletContext<Ctx>();
  const [apps, setApps] = useState<AppUsageStatsResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [dateFrom, setDateFrom] = useState(() => {
    const d = new Date();
    d.setDate(d.getDate() - 7);
    return d.toISOString().split('T')[0];
  });
  const [dateTo, setDateTo] = useState(() => new Date().toISOString().split('T')[0]);

  useEffect(() => {
    setLoading(true);
    appUsage
      .stats(deviceId, { date_from: dateFrom, date_to: dateTo })
      .then(setApps)
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [deviceId, dateFrom, dateTo]);

  const formatDuration = (secs: number) => {
    const h = Math.floor(secs / 3600);
    const m = Math.floor((secs % 3600) / 60);
    if (h > 0) return `${h}ч ${m}м`;
    return `${m}м`;
  };

  const totalSec = apps.reduce((s, a) => s + a.total_seconds, 0);

  return (
    <div className="rounded-xl border bg-white p-5">
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <h3 className="font-semibold">Использование приложений</h3>
        <div className="ml-auto flex items-center gap-2 text-sm">
          <input
            type="date"
            value={dateFrom}
            onChange={(e) => setDateFrom(e.target.value)}
            className="rounded border px-2 py-1"
          />
          <span>—</span>
          <input
            type="date"
            value={dateTo}
            onChange={(e) => setDateTo(e.target.value)}
            className="rounded border px-2 py-1"
          />
        </div>
      </div>

      <p className="mb-4 text-sm text-gray-500">
        Общее время: <span className="font-medium text-gray-900">{formatDuration(totalSec)}</span>
      </p>

      {loading ? (
        <div className="flex justify-center py-8">
          <div className="h-6 w-6 animate-spin rounded-full border-4 border-blue-500 border-t-transparent" />
        </div>
      ) : apps.length === 0 ? (
        <p className="py-8 text-center text-gray-400">Нет данных за этот период</p>
      ) : (
        <div className="space-y-2">
          {apps
            .sort((a, b) => b.total_seconds - a.total_seconds)
            .map((app) => {
              const pct = totalSec > 0 ? (app.total_seconds / totalSec) * 100 : 0;
              return (
                <div key={`${app.package_name}-${app.date}`} className="group">
                  <div className="flex items-center justify-between text-sm">
                    <span className="truncate font-medium">{app.app_name}</span>
                    <span className="ml-2 whitespace-nowrap text-gray-500">
                      {formatDuration(app.total_seconds)}
                    </span>
                  </div>
                  <div className="mt-1 h-2 overflow-hidden rounded-full bg-gray-100">
                    <div
                      className="h-full rounded-full bg-blue-500 transition-all"
                      style={{ width: `${pct}%` }}
                    />
                  </div>
                  <p className="mt-0.5 text-xs text-gray-400 opacity-0 group-hover:opacity-100 transition-opacity">
                    {app.package_name}
                  </p>
                </div>
              );
            })}
        </div>
      )}
    </div>
  );
}
