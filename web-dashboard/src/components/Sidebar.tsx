import React from 'react';
import { NavLink } from 'react-router-dom';
import { LayoutDashboard, Smartphone, MapPin, History, ShieldAlert, Terminal, LifeBuoy, Settings, X, Shield } from 'lucide-react';

interface SidebarProps {
  isOpen?: boolean;
  onClose?: () => void;
}

export const Sidebar: React.FC<SidebarProps> = ({ isOpen = false, onClose }) => {
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
    <>
      {/* Mobile Backdrop */}
      {isOpen && (
        <div
          onClick={onClose}
          className="fixed inset-0 z-40 bg-black/60 backdrop-blur-xs md:hidden"
          aria-hidden="true"
        />
      )}

      {/* Sidebar / Mobile Drawer */}
      <aside
        className={`
          fixed inset-y-0 left-0 z-50 w-64 bg-white dark:bg-[#0D131F] border-r border-slate-200 dark:border-[#1E293B] p-4 flex flex-col shadow-xl dark:shadow-inner transition-transform duration-200 ease-in-out
          md:static md:translate-x-0 md:min-h-[calc(100vh-65px)] md:shadow-sm
          ${isOpen ? 'translate-x-0 flex' : '-translate-x-full md:translate-x-0 hidden md:flex'}
        `}
      >
        {/* Mobile Drawer Header */}
        <div className="flex md:hidden items-center justify-between pb-3 mb-3 border-b border-slate-200 dark:border-slate-800">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-indigo-500/10 to-cyan-500/20 border border-indigo-500/30 dark:border-cyan-500/40 flex items-center justify-center">
              <Shield className="w-4 h-4 text-indigo-600 dark:text-cyan-400" />
            </div>
            <span className="font-bold text-sm font-mono text-slate-900 dark:text-white">
              Navigation
            </span>
          </div>
          <button
            onClick={onClose}
            aria-label="Close menu"
            className="p-1.5 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-700 dark:bg-slate-800 dark:text-slate-300"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <nav className="space-y-1.5 flex-1">
          {navItems.map((item) => {
            const Icon = item.icon;
            return (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.to === '/'}
                onClick={() => onClose?.()}
                className={({ isActive }) =>
                  `flex items-center gap-3 px-3.5 py-2.5 rounded-xl font-medium text-xs tracking-wide transition-all ${
                    isActive
                      ? 'bg-indigo-50 text-indigo-700 border border-indigo-200 shadow-sm dark:bg-gradient-to-r dark:from-cyan-950/70 dark:to-indigo-950/40 dark:text-cyan-400 dark:border-cyan-800/60 dark:shadow-[0_0_12px_rgba(0,210,255,0.15)] font-semibold'
                      : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900 dark:text-slate-400 dark:hover:bg-[#162032] dark:hover:text-slate-200 border border-transparent'
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
    </>
  );
};
