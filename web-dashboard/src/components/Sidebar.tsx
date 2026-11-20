import React from 'react';
import { NavLink } from 'react-router-dom';
import { LayoutDashboard, Smartphone, MapPin, History, ShieldAlert, Terminal, LifeBuoy, Settings } from 'lucide-react';

export const Sidebar: React.FC = () => {
  const navItems = [
    { to: '/', label: 'Overview', icon: LayoutDashboard },
    { to: '/devices', label: 'My Devices', icon: Smartphone },
    { to: '/tracking', label: 'Live Tracking', icon: MapPin },
    { to: '/history', label: 'Location History', icon: History },
    { to: '/security', label: 'Security Events', icon: ShieldAlert },
    { to: '/commands', label: 'Device Commands', icon: Terminal },
    { to: '/recovery', label: 'Phone Recovery', icon: LifeBuoy },
    { to: '/settings', label: 'Settings', icon: Settings },
  ];

  return (
    <aside className="w-64 bg-[#0D131F] border-r border-[#1E293B] min-h-[calc(100vh-65px)] p-4 flex flex-col shadow-inner">
      <nav className="space-y-1.5">
        {navItems.map((item) => {
          const Icon = item.icon;
          return (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/'}
              className={({ isActive }) =>
                `flex items-center gap-3 px-3.5 py-2.5 rounded-xl font-medium text-xs tracking-wide transition-all ${
                  isActive
                    ? 'bg-gradient-to-r from-cyan-950/70 to-indigo-950/40 text-cyan-400 border border-cyan-800/60 shadow-[0_0_12px_rgba(0,210,255,0.15)]'
                    : 'text-slate-400 hover:bg-[#162032] hover:text-slate-200 border border-transparent'
                }`
              }
            >
              <Icon className="w-4 h-4 flex-shrink-0" />
              <span>{item.label}</span>
            </NavLink>
          );
        })}
      </nav>
    </aside>
  );
};
