import { useEffect, useState, useRef, useCallback } from 'react';
import { useOutletContext } from 'react-router-dom';
import { location as locationApi, commands } from '../../api/client';
import { useWsStore } from '../../store/wsStore';
import type { LocationResponse, LocationLatestResponse, WsMessage, DeviceResponse } from '../../types';
import L from 'leaflet';

interface Ctx { deviceId: string; device: DeviceResponse }

export default function LocationTab() {
  const { deviceId, device } = useOutletContext<Ctx>();
  const [latest, setLatest] = useState<LocationLatestResponse | null>(null);
  const [history, setHistory] = useState<LocationResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [requesting, setRequesting] = useState(false);
  const mapRef = useRef<HTMLDivElement>(null);
  const mapInstanceRef = useRef<L.Map | null>(null);
  const markerRef = useRef<L.Marker | null>(null);
  const circleRef = useRef<L.Circle | null>(null);
  const onWs = useWsStore((s) => s.on);

  // Listen for real-time location updates via WS
  useEffect(() => {
    const unsub = onWs('device_location', (msg: WsMessage) => {
      if (msg.device_id !== deviceId) return;
      if (msg.latitude != null && msg.longitude != null) {
        const newLatest: LocationLatestResponse = {
          latitude: msg.latitude,
          longitude: msg.longitude,
          accuracy: msg.accuracy ?? null,
          altitude: null,
          speed: null,
          recorded_at: msg.recorded_at ?? new Date().toISOString(),
        };
        setLatest(newLatest);

        // Update map marker in real-time
        if (mapInstanceRef.current) {
          const latlng: L.LatLngExpression = [msg.latitude, msg.longitude];
          mapInstanceRef.current.setView(latlng, mapInstanceRef.current.getZoom());

          if (markerRef.current) {
            markerRef.current.setLatLng(latlng);
          }
          if (circleRef.current && msg.accuracy) {
            circleRef.current.setLatLng(latlng);
            circleRef.current.setRadius(msg.accuracy);
          }
        }
      }
    });
    return unsub;
  }, [deviceId, onWs]);

  useEffect(() => {
    Promise.all([
      locationApi.latest(deviceId).catch(() => null),
      locationApi.history(deviceId, { page: 1 }).catch(() => []),
    ]).then(([lat, hist]) => {
      setLatest(lat);
      setHistory(hist as LocationResponse[]);
      setLoading(false);
    });
  }, [deviceId]);

  const requestLocation = useCallback(async () => {
    setRequesting(true);
    try {
      await commands.send(deviceId, { command_type: 'request_location' });
    } catch { /* ignore */ }
    finally { setRequesting(false); }
  }, [deviceId]);

  useEffect(() => {
    if (!mapRef.current || loading) return;
    if (mapInstanceRef.current) {
      mapInstanceRef.current.remove();
      mapInstanceRef.current = null;
    }

    const center: [number, number] = latest
      ? [latest.latitude, latest.longitude]
      : history.length > 0
        ? [history[0].latitude, history[0].longitude]
        : [55.75, 37.62]; // Moscow fallback

    const map = L.map(mapRef.current).setView(center, 14);

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; OpenStreetMap',
      maxZoom: 19,
    }).addTo(map);

    // Current position marker
    if (latest) {
      const icon = L.divIcon({
        className: '',
        html: '<div style="width:16px;height:16px;background:#3b82f6;border:3px solid white;border-radius:50%;box-shadow:0 1px 4px rgba(0,0,0,0.3)"></div>',
        iconSize: [16, 16],
        iconAnchor: [8, 8],
      });

      const marker = L.marker([latest.latitude, latest.longitude], { icon })
        .addTo(map)
        .bindPopup(
          `<b>Текущая позиция</b><br/>
           ${latest.accuracy ? `±${Math.round(latest.accuracy)}м<br/>` : ''}
           ${new Date(latest.recorded_at).toLocaleString('ru')}`,
        );
      markerRef.current = marker;

      if (latest.accuracy) {
        const circle = L.circle([latest.latitude, latest.longitude], {
          radius: latest.accuracy,
          color: '#3b82f6',
          fillOpacity: 0.1,
          weight: 1,
        }).addTo(map);
        circleRef.current = circle;
      }
    }

    // History polyline
    if (history.length > 1) {
      const coords: [number, number][] = history.map((p) => [p.latitude, p.longitude]);
      L.polyline(coords, { color: '#6366f1', weight: 3, opacity: 0.7 }).addTo(map);
    }

    mapInstanceRef.current = map;

    return () => {
      map.remove();
      mapInstanceRef.current = null;
    };
  }, [latest, history, loading]);

  return (
    <div className="rounded-xl border bg-white p-5">
      <div className="mb-4 flex items-center justify-between">
        <h3 className="font-semibold">Геолокация</h3>
        <button
          onClick={requestLocation}
          disabled={requesting || !device?.is_online}
          className="rounded-lg bg-blue-600 px-4 py-2 text-sm text-white hover:bg-blue-700 disabled:opacity-50"
        >
          {requesting ? '⏳ Запрос…' : '📍 Запросить локацию'}
        </button>
      </div>

      {loading ? (
        <div className="flex justify-center py-12">
          <div className="h-6 w-6 animate-spin rounded-full border-4 border-blue-500 border-t-transparent" />
        </div>
      ) : (
        <>
          <div
            ref={mapRef}
            className="mb-4 h-[500px] w-full rounded-lg border"
            style={{ zIndex: 0 }}
          />

          {latest && (
            <div className="flex items-center gap-4 text-sm text-gray-600">
              <span>
                📍 {latest.latitude.toFixed(6)}, {latest.longitude.toFixed(6)}
              </span>
              {latest.accuracy && <span>±{Math.round(latest.accuracy)}м</span>}
              {latest.speed != null && latest.speed > 0 && (
                <span>🏃 {(latest.speed * 3.6).toFixed(1)} км/ч</span>
              )}
              <span className="ml-auto text-gray-400">
                {new Date(latest.recorded_at).toLocaleString('ru')}
              </span>
            </div>
          )}

          {!latest && !history.length && (
            <p className="text-center text-gray-400">Нет данных о местоположении</p>
          )}
        </>
      )}
    </div>
  );
}
