import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useDeviceStore } from '../store/deviceStore';
import type { DeviceWithPairingCodeResponse } from '../types';

export default function DevicesPage() {
  const { devices, loading, fetchDevices, addDevice, removeDevice } = useDeviceStore();
  const [showAdd, setShowAdd] = useState(false);
  const [newName, setNewName] = useState('');
  const [adding, setAdding] = useState(false);
  const [pairingResult, setPairingResult] = useState<DeviceWithPairingCodeResponse | null>(null);

  useEffect(() => {
    fetchDevices();
  }, [fetchDevices]);

  const handleAdd = async () => {
    if (!newName.trim()) return;
    setAdding(true);
    try {
      const res = await addDevice(newName.trim());
      setPairingResult(res);
      setNewName('');
      setShowAdd(false);
    } finally {
      setAdding(false);
    }
  };

  const handleRemove = async (id: string, name: string) => {
    if (!confirm(`Удалить устройство "${name}"?`)) return;
    await removeDevice(id);
  };

  return (
    <div>
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-bold">Устройства</h1>
        <button
          onClick={() => setShowAdd(true)}
          className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 transition-colors"
        >
          + Добавить устройство
        </button>
      </div>

      {/* Pairing code modal */}
      {pairingResult && (
        <div className="mb-6 rounded-xl border border-blue-200 bg-blue-50 p-6">
          <h3 className="font-semibold text-blue-900">Код привязки для "{pairingResult.name}"</h3>
          <p className="mt-2 text-4xl font-mono font-bold tracking-widest text-blue-700">
            {pairingResult.pairing_code}
          </p>
          <p className="mt-2 text-sm text-blue-600">
            Введите этот код в приложении на телефоне ребёнка
          </p>
          <button
            onClick={() => setPairingResult(null)}
            className="mt-3 rounded-lg bg-blue-600 px-4 py-2 text-sm text-white hover:bg-blue-700"
          >
            Понятно
          </button>
        </div>
      )}

      {/* Add device form */}
      {showAdd && (
        <div className="mb-6 rounded-xl border bg-white p-4">
          <h3 className="mb-3 font-medium">Новое устройство</h3>
          <div className="flex gap-2">
            <input
              type="text"
              value={newName}
              onChange={(e) => setNewName(e.target.value)}
              placeholder="Имя ребёнка"
              className="flex-1 rounded-lg border px-3 py-2 text-sm focus:border-blue-500 focus:outline-none"
              onKeyDown={(e) => e.key === 'Enter' && handleAdd()}
            />
            <button
              onClick={handleAdd}
              disabled={adding}
              className="rounded-lg bg-green-600 px-4 py-2 text-sm text-white hover:bg-green-700 disabled:opacity-50"
            >
              {adding ? '…' : 'Создать'}
            </button>
            <button
              onClick={() => setShowAdd(false)}
              className="rounded-lg border px-4 py-2 text-sm text-gray-600 hover:bg-gray-50"
            >
              Отмена
            </button>
          </div>
        </div>
      )}

      {/* Device list */}
      {loading ? (
        <div className="flex justify-center py-12">
          <div className="h-8 w-8 animate-spin rounded-full border-4 border-blue-500 border-t-transparent" />
        </div>
      ) : devices.length === 0 ? (
        <div className="rounded-xl border bg-white p-12 text-center text-gray-400">
          <p className="text-lg">Нет устройств</p>
          <p className="mt-1 text-sm">Добавьте устройство ребёнка для начала мониторинга</p>
        </div>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {devices.map((device) => (
            <div key={device.id} className="rounded-xl border bg-white p-5 shadow-sm transition-shadow hover:shadow-md">
              <div className="flex items-start justify-between">
                <div>
                  <h3 className="font-semibold text-gray-900">{device.name}</h3>
                  <p className="mt-1 text-xs text-gray-400">
                    {device.device_model || 'Не привязано'}
                  </p>
                </div>
                <span
                  className={`rounded-full px-2 py-0.5 text-xs font-medium ${
                    device.is_online
                      ? 'bg-green-100 text-green-700'
                      : 'bg-gray-100 text-gray-500'
                  }`}
                >
                  {device.is_online ? 'Онлайн' : 'Оффлайн'}
                </span>
              </div>

              {device.last_seen_at && (
                <p className="mt-2 text-xs text-gray-400">
                  Последнее подключение: {new Date(device.last_seen_at).toLocaleString('ru')}
                </p>
              )}

              <div className="mt-4 flex gap-2">
                <Link
                  to={`/device/${device.id}`}
                  className="flex-1 rounded-lg bg-blue-50 py-2 text-center text-sm font-medium text-blue-700 hover:bg-blue-100 transition-colors"
                >
                  Подробнее
                </Link>
                <button
                  onClick={() => handleRemove(device.id, device.name)}
                  className="rounded-lg border px-3 py-2 text-sm text-red-500 hover:bg-red-50 transition-colors"
                >
                  🗑
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
