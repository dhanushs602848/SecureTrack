import React from 'react';
import { Shield, LogOut, User, Sun, Moon, Laptop, Menu, X } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';

interface NavbarProps {
  mobileMenuOpen?: boolean;
  onToggleMobileMenu?: () => void;
}

export const Navbar: React.FC<NavbarProps> = ({ mobileMenuOpen = false, onToggleMobileMenu }) => {
  const { user, logout } = useAuth();
  const { theme, setTheme, resolvedTheme } = useTheme();

  const cycleTheme = () => {
    if (theme === 'system') setTheme('light');
    else if (theme === 'light') setTheme('dark');
    else setTheme('system');
  };

  return (
    <header className="bg-white dark:bg-[#0F172A] text-slate-800 dark:text-white border-b border-slate-200 dark:border-[#1E293B] px-3 sm:px-4 md:px-6 py-2.5 sm:py-3 flex items-center justify-between shadow-xs sticky top-0 z-50 transition-colors duration-200">
      <div className="flex items-center gap-2 sm:gap-3 min-w-0">
        {onToggleMobileMenu && (
          <button
            onClick={onToggleMobileMenu}
            aria-label={mobileMenuOpen ? 'Close navigation menu' : 'Open navigation menu'}
            className="md:hidden p-2 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 dark:bg-[#162032] dark:hover:bg-[#1f2c42] dark:text-cyan-400 border border-slate-300 dark:border-slate-700/60 transition-all flex items-center justify-center shrink-0"
          >
            {mobileMenuOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
          </button>
        )}
        <div className="w-9 h-9 sm:w-10 sm:h-10 rounded-xl bg-gradient-to-br from-indigo-500/10 to-cyan-500/20 border border-indigo-500/30 dark:border-cyan-500/40 flex items-center justify-center shadow-[0_0_15px_rgba(0,210,255,0.15)] shrink-0">
          <Shield className="w-4 h-4 sm:w-5 sm:h-5 text-indigo-600 dark:text-cyan-400" />
        </div>
        <div className="min-w-0">
          <div className="flex items-center gap-1.5 sm:gap-2">
            <h1 className="text-sm sm:text-lg font-bold tracking-tight text-slate-900 dark:text-white font-mono truncate">
              SecureTrack<span className="text-indigo-600 dark:text-cyan-400">.io</span>
            </h1>
            <span className="hidden sm:inline-flex text-[10px] font-bold bg-emerald-50 dark:bg-cyan-950/60 text-emerald-700 dark:text-cyan-400 border border-emerald-200 dark:border-cyan-800/50 px-2 py-0.5 rounded-full items-center gap-1 shrink-0">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 dark:bg-cyan-400 animate-pulse" />
              v1.8.0
            </span>
          </div>
          <p className="text-[10px] sm:text-xs text-slate-500 dark:text-slate-400 truncate max-w-[130px] xs:max-w-[200px] sm:max-w-none">Zero-Trust Telemetry &amp; Anti-Theft</p>
        </div>
      </div>

      <div className="flex items-center gap-1.5 sm:gap-3 shrink-0">
        {/* Quick Theme Toggle Button */}
        <button
          onClick={cycleTheme}
          title={`Theme: ${theme.toUpperCase()} (Click to toggle)`}
          className="p-2 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 dark:bg-[#162032] dark:hover:bg-[#1f2c42] dark:text-cyan-400 border border-slate-300 dark:border-slate-700/60 transition-all flex items-center gap-1.5 text-xs font-mono"
        >
          {theme === 'system' ? (
            <Laptop className="w-4 h-4 text-indigo-600 dark:text-violet-400" />
          ) : resolvedTheme === 'dark' ? (
            <Moon className="w-4 h-4 text-cyan-400" />
          ) : (
            <Sun className="w-4 h-4 text-amber-500" />
          )}
          <span className="hidden sm:inline capitalize">{theme}</span>
        </button>

        <div className="hidden md:flex items-center gap-2 text-xs bg-slate-100 text-slate-700 dark:bg-[#162032] dark:text-slate-300 px-3 py-1.5 rounded-xl border border-slate-300 dark:border-slate-700/60 max-w-[180px]">
          <User className="w-3.5 h-3.5 text-indigo-600 dark:text-cyan-400 shrink-0" />
          <span className="font-medium truncate">{user?.fullName || user?.email}</span>
        </div>

        <button
          onClick={logout}
          className="flex items-center gap-1.5 text-xs text-rose-600 hover:text-white hover:bg-rose-600 dark:text-rose-300 dark:hover:text-white bg-rose-50 hover:bg-rose-600 dark:bg-rose-950/30 dark:hover:bg-rose-900/50 px-2.5 sm:px-3 py-1.5 rounded-xl border border-rose-200 dark:border-rose-800/40 font-medium transition-colors"
        >
          <LogOut className="w-3.5 h-3.5" />
          <span className="hidden xs:inline">Sign Out</span>
        </button>
      </div>
    </header>
  );
};
