import { useEffect, useState } from 'react';
import { useOutletContext } from 'react-router-dom';
import { calls } from '../../api/client';
import type { CallLogResponse } from '../../types';

interface Ctx { deviceId: string }

const callTypeLabels: Record<string, string> = {
  incoming: '📥 Входящий',
  outgoing: '📤 Исходящий',
  missed: '❌ Пропущенный',
};

export default function CallsTab() {
  const { deviceId } = useOutletContext<Ctx>();
  const [items, setItems] = useState<CallLogResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState('');
  const [page, setPage] = useState(1);

  useEffect(() => {
    setLoading(true);
    calls
      .list(deviceId, { call_type: filter || undefined, page })
      .then(setItems)
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [deviceId, filter, page]);

  const formatDuration = (secs: number) => {
    const m = Math.floor(secs / 60);
    const s = secs % 60;
    return `${m}:${String(s).padStart(2, '0')}`;
  };

  return (
    <div className="rounded-xl border bg-white p-5">
      <div className="mb-4 flex items-center gap-3">
        <h3 className="font-semibold">Журнал звонков</h3>
        <select
          value={filter}
          onChange={(e) => { setFilter(e.target.value); setPage(1); }}
          className="ml-auto rounded border px-2 py-1 text-sm"
        >
          <option value="">Все</option>
          <option value="incoming">Входящие</option>
          <option value="outgoing">Исходящие</option>
          <option value="missed">Пропущенные</option>
        </select>
      </div>

      {loading ? (
        <div className="flex justify-center py-8">
          <div className="h-6 w-6 animate-spin rounded-full border-4 border-blue-500 border-t-transparent" />
        </div>
      ) : items.length === 0 ? (
        <p className="py-8 text-center text-gray-400">Нет звонков</p>
      ) : (
        <>
          <div className="divide-y">
            {items.map((call) => (
              <div key={call.id} className="flex items-center justify-between py-3 text-sm">
                <div>
                  <p className="font-medium">{call.contact_name || call.phone_number}</p>
                  {call.contact_name && (
                    <p className="text-xs text-gray-400">{call.phone_number}</p>
                  )}
                </div>
                <div className="text-right">
                  <p className="text-xs text-gray-500">
                    {callTypeLabels[call.call_type] || call.call_type}
                  </p>
                  <p className="text-xs text-gray-400">
                    {formatDuration(call.duration_seconds)} • {new Date(call.called_at).toLocaleString('ru')}
                  </p>
                </div>
              </div>
            ))}
          </div>

          <div className="mt-4 flex justify-center gap-2">
            <button
              onClick={() => setPage((p) => Math.max(1, p - 1))}
              disabled={page === 1}
              className="rounded border px-3 py-1 text-sm disabled:opacity-50"
            >
              ←
            </button>
            <span className="px-3 py-1 text-sm text-gray-500">Стр. {page}</span>
            <button
              onClick={() => setPage((p) => p + 1)}
              disabled={items.length < 50}
              className="rounded border px-3 py-1 text-sm disabled:opacity-50"
            >
              →
            </button>
          </div>
        </>
      )}
    </div>
  );
}
