import { useEffect, useState } from 'react';
import { useOutletContext } from 'react-router-dom';
import { media } from '../../api/client';
import type { MediaItem, MediaListResponse } from '../../types';

interface Ctx { deviceId: string }

export default function MediaTab() {
  const { deviceId } = useOutletContext<Ctx>();
  const [data, setData] = useState<MediaListResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState('');
  const [page, setPage] = useState(1);
  const [selected, setSelected] = useState<MediaItem | null>(null);

  useEffect(() => {
    setLoading(true);
    media
      .list(deviceId, { file_type: filter || undefined, page })
      .then(setData)
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [deviceId, filter, page]);

  const formatSize = (bytes: number) => {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  };

  return (
    <div className="rounded-xl border bg-white p-5">
      <div className="mb-4 flex items-center gap-3">
        <h3 className="font-semibold">
          Медиа {data && <span className="text-gray-400 font-normal">({data.total})</span>}
        </h3>
        <select
          value={filter}
          onChange={(e) => { setFilter(e.target.value); setPage(1); }}
          className="ml-auto rounded border px-2 py-1 text-sm"
        >
          <option value="">Все</option>
          <option value="photo">Фото</option>
          <option value="video">Видео</option>
        </select>
      </div>

      {loading ? (
        <div className="flex justify-center py-8">
          <div className="h-6 w-6 animate-spin rounded-full border-4 border-blue-500 border-t-transparent" />
        </div>
      ) : !data || data.items.length === 0 ? (
        <p className="py-8 text-center text-gray-400">Нет медиа</p>
      ) : (
        <>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5">
            {data.items.map((item) => (
              <div
                key={item.id}
                onClick={() => setSelected(item)}
                className="group cursor-pointer overflow-hidden rounded-lg border bg-gray-100 transition-shadow hover:shadow-md"
              >
                <div className="relative aspect-square">
                  <img
                    src={media.thumbnailUrl(item.id)}
                    alt={item.original_filename || 'media'}
                    className="h-full w-full object-cover"
                    loading="lazy"
                  />
                  {item.file_type === 'video' && (
                    <div className="absolute inset-0 flex items-center justify-center bg-black/20">
                      <span className="text-2xl text-white">▶</span>
                    </div>
                  )}
                </div>
                <div className="p-1.5">
                  <p className="truncate text-xs text-gray-500">{formatSize(item.file_size)}</p>
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
            <span className="px-3 py-1 text-sm text-gray-500">
              {page} / {Math.ceil(data.total / data.page_size)}
            </span>
            <button
              onClick={() => setPage((p) => p + 1)}
              disabled={page * data.page_size >= data.total}
              className="rounded border px-3 py-1 text-sm disabled:opacity-50"
            >
              →
            </button>
          </div>
        </>
      )}

      {/* Lightbox */}
      {selected && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4"
          onClick={() => setSelected(null)}
        >
          <div
            className="relative max-h-[90vh] max-w-4xl overflow-auto rounded-xl bg-white p-2"
            onClick={(e) => e.stopPropagation()}
          >
            {selected.file_type === 'video' ? (
              <video
                src={media.downloadUrl(selected.id)}
                controls
                className="max-h-[80vh] rounded-lg"
              />
            ) : (
              <img
                src={media.downloadUrl(selected.id)}
                alt=""
                className="max-h-[80vh] rounded-lg"
              />
            )}
            <div className="mt-2 flex items-center justify-between px-2 text-sm text-gray-500">
              <span>{selected.original_filename}</span>
              <span>{formatSize(selected.file_size)}</span>
            </div>
            <button
              onClick={() => setSelected(null)}
              className="absolute right-3 top-3 rounded-full bg-black/50 px-2 py-1 text-sm text-white hover:bg-black/70"
            >
              ✕
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
