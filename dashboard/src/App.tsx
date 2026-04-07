import { Routes, Route, Navigate } from 'react-router-dom';
import { useEffect } from 'react';
import { useAuthStore } from './store/authStore';
import AuthGuard from './components/AuthGuard';
import Layout from './components/Layout';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import DevicesPage from './pages/DevicesPage';
import DeviceDetailPage from './pages/DeviceDetailPage';
import OverviewTab from './pages/tabs/OverviewTab';
import AppsTab from './pages/tabs/AppsTab';

import CallsTab from './pages/tabs/CallsTab';
import MessagesTab from './pages/tabs/MessagesTab';
import MediaTab from './pages/tabs/MediaTab';
import LocationTab from './pages/tabs/LocationTab';
import CameraTab from './pages/tabs/CameraTab';
import ScreenTab from './pages/tabs/ScreenTab';

export default function App() {
  const loadUser = useAuthStore((s) => s.loadUser);

  useEffect(() => {
    const token = localStorage.getItem('access_token');
    if (token) loadUser();
  }, [loadUser]);

  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />

      <Route element={<AuthGuard />}>
        <Route element={<Layout />}>
          <Route path="/" element={<DevicesPage />} />

          <Route path="/device/:deviceId" element={<DeviceDetailPage />}>
            <Route index element={<OverviewTab />} />
            <Route path="apps" element={<AppsTab />} />
            <Route path="calls" element={<CallsTab />} />
            <Route path="messages" element={<MessagesTab />} />
            <Route path="media" element={<MediaTab />} />
            <Route path="location" element={<LocationTab />} />
            <Route path="camera" element={<CameraTab />} />
            <Route path="screen" element={<ScreenTab />} />
          </Route>
        </Route>
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
