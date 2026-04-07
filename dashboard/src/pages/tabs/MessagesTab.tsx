import { useEffect, useState } from 'react';
import { useOutletContext } from 'react-router-dom';
import { messages } from '../../api/client';
import type { MessageResponse } from '../../types';

interface Ctx { deviceId: string }

export default function MessagesTab() {
  const { deviceId } = useOutletContext<Ctx>();
  const [items, setItems] = useState<MessageResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(1);

  useEffect(() => {
    setLoading(true);
    messages
      .list(deviceId, { contact: search || undefined, page })
      .then(setItems)
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [deviceId, search, page]);

  return (
    <div className="rounded-xl border bg-white p-5">
      <div className="mb-4 flex items-center gap-3">
        <h3 className="font-semibold">Сообщения</h3>
        <input
          type="text"
          placeholder="Поиск по контакту…"
          value={search}
          onChange={(e) => { setSearch(e.target.value); setPage(1); }}
          className="ml-auto rounded border px-3 py-1 text-sm focus:border-blue-500 focus:outline-none"
        />
      </div>

      {loading ? (
        <div className="flex justify-center py-8">
          <div className="h-6 w-6 animate-spin rounded-full border-4 border-blue-500 border-t-transparent" />
        </div>
      ) : items.length === 0 ? (
        <p className="py-8 text-center text-gray-400">Нет сообщений</p>
      ) : (
        <>
          <div className="space-y-3">
            {items.map((msg) => (
              <div
                key={msg.id}
                className={`rounded-lg p-3 text-sm ${
                  msg.is_incoming
                    ? 'bg-gray-50 border-l-4 border-blue-400'
                    : 'bg-green-50 border-l-4 border-green-400'
                }`}
              >
                <div className="mb-1 flex items-center justify-between">
                  <span className="font-medium">
                    {msg.is_incoming ? `📥 ${msg.sender}` : `📤 → ${msg.receiver || '?'}`}
                  </span>
                  <span className="text-xs text-gray-400">
                    {new Date(msg.sent_at).toLocaleString('ru')}
                  </span>
                </div>
                <p className="text-gray-700 whitespace-pre-wrap break-words">{msg.body}</p>
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
