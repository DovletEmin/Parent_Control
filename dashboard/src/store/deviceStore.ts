import { create } from 'zustand';
import { devices as devicesApi } from '../api/client';
import type { DeviceResponse, DeviceWithPairingCodeResponse } from '../types';

interface DeviceState {
  devices: DeviceResponse[];
  loading: boolean;
  fetchDevices: () => Promise<void>;
  addDevice: (name: string) => Promise<DeviceWithPairingCodeResponse>;
  removeDevice: (id: string) => Promise<void>;
  updateDeviceStatus: (deviceId: string, isOnline: boolean) => void;
}

export const useDeviceStore = create<DeviceState>((set, get) => ({
  devices: [],
  loading: false,

  fetchDevices: async () => {
    set({ loading: true });
    try {
      const list = await devicesApi.list();
      set({ devices: list, loading: false });
    } catch {
      set({ loading: false });
    }
  },

  addDevice: async (name) => {
    const res = await devicesApi.create({ name });
    set({ devices: [...get().devices, res] });
    return res;
  },

  removeDevice: async (id) => {
    await devicesApi.remove(id);
    set({ devices: get().devices.filter((d) => d.id !== id) });
  },

  updateDeviceStatus: (deviceId, isOnline) => {
    set({
      devices: get().devices.map((d) =>
        d.id === deviceId ? { ...d, is_online: isOnline } : d,
      ),
    });
  },
}));
