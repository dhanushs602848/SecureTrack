import React from 'react';
import { NavLink } from 'react-router-dom';
import { LayoutDashboard, Smartphone, MapPin, History, ShieldAlert, Terminal, Settings } from 'lucide-react';

export const Sidebar: React.FC = () => {
  const navItems = [
    { to: '/', label: 'Overview', icon: LayoutDashboard },
    { to: '/devices', label: 'My Devices', icon: Smartphone },
    { to: '/tracking', label: 'Live Tracking', icon: MapPin },
    { to: '/history', label: 'Location History', icon: History },
    { to: '/security', label: 'Security Events', icon: ShieldAlert },
    { to: '/commands', label: 'Device Commands', icon: Terminal },
    { to: '/settings', label: 'Settings', icon: Settings },
  ];

  return (
    <aside className="w-64 bg-white border-r border-[#DFE2E7] min-h-[calc(100vh-73px)] p-4 flex flex-col">
      <nav className="space-y-1">
        {navItems.map((item) => {
          const Icon = item.icon;
          return (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/'}
              className={({ isActive }) =>
                `flex items-center gap-3 px-4 py-3 rounded-xl font-medium text-sm transition-all ${
                  isActive
                    ? 'bg-[#152C4F] text-white shadow-sm'
                    : 'text-[#62666D] hover:bg-[#F0F2F5] hover:text-[#1A1C1E]'
                }`
              }
            >
              <Icon className="w-5 h-5" />
              {item.label}
            </NavLink>
          );
        })}
      </nav>
    </aside>
  );
};
