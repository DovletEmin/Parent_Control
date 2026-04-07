import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuthStore } from '../store/authStore';
import { useWsStore } from '../store/wsStore';
import { useDeviceStore } from '../store/deviceStore';
import { useEffect } from 'react';

const navItems = [
  { to: '/', label: 'Устройства', icon: '📱' },
];

export default function Layout() {
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);
  const connect = useWsStore((s) => s.connect);
  const disconnect = useWsStore((s) => s.disconnect);
  const wsConnected = useWsStore((s) => s.connected);
  const onWs = useWsStore((s) => s.on);
  const updateDeviceStatus = useDeviceStore((s) => s.updateDeviceStatus);
  const navigate = useNavigate();

  useEffect(() => {
    connect();
    return () => disconnect();
  }, [connect, disconnect]);

  useEffect(() => {
    const unsub = onWs('device_status', (msg) => {
      if (msg.device_id && msg.status) {
        updateDeviceStatus(msg.device_id, msg.status === 'online');
      }
    });
    return unsub;
  }, [onWs, updateDeviceStatus]);

  const handleLogout = () => {
    disconnect();
    logout();
    navigate('/login');
  };

  return (
    <div className="flex h-screen">
      {/* Sidebar */}
      <aside className="flex w-64 flex-col border-r bg-white">
        <div className="border-b p-4">
          <h1 className="text-lg font-bold text-blue-600">🛡️ ParentControl</h1>
          <p className="mt-1 text-xs text-gray-400">{user?.email}</p>
          <div className="mt-1 flex items-center gap-1">
            <span
              className={`h-2 w-2 rounded-full ${wsConnected ? 'bg-green-500' : 'bg-red-400'}`}
            />
            <span className="text-xs text-gray-400">
              {wsConnected ? 'Онлайн' : 'Оффлайн'}
            </span>
          </div>
        </div>

        <nav className="flex-1 p-3">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                `flex items-center gap-2 rounded-lg px-3 py-2 text-sm transition-colors ${
                  isActive
                    ? 'bg-blue-50 text-blue-700 font-medium'
                    : 'text-gray-600 hover:bg-gray-50'
                }`
              }
            >
              <span>{item.icon}</span>
              {item.label}
            </NavLink>
          ))}
        </nav>

        <div className="border-t p-3">
          <button
            onClick={handleLogout}
            className="w-full rounded-lg px-3 py-2 text-left text-sm text-red-600 hover:bg-red-50 transition-colors"
          >
            Выйти
          </button>
        </div>
      </aside>

      {/* Main content */}
      <main className="flex-1 overflow-auto bg-gray-50 p-6">
        <Outlet />
      </main>
    </div>
  );
}
