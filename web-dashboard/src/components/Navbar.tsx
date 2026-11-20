import React from 'react';
import { Shield, LogOut, User, Radio } from 'lucide-react';
import { useAuth } from '../context/AuthContext';

export const Navbar: React.FC = () => {
  const { user, logout } = useAuth();

  return (
    <header className="bg-[#0F172A] text-white border-b border-[#1E293B] px-6 py-3.5 flex items-center justify-between shadow-lg sticky top-0 z-50">
      <div className="flex items-center gap-3">
        <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-indigo-500/20 to-cyan-500/20 border border-cyan-500/40 flex items-center justify-center shadow-[0_0_15px_rgba(0,210,255,0.2)]">
          <Shield className="w-5 h-5 text-cyan-400" />
        </div>
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-lg font-bold tracking-tight text-white font-mono">SecureTrack<span className="text-cyan-400">.io</span></h1>
            <span className="text-[10px] font-bold bg-cyan-950/60 text-cyan-400 border border-cyan-800/50 px-2 py-0.5 rounded-full flex items-center gap-1">
              <span className="w-1.5 h-1.5 rounded-full bg-cyan-400 animate-pulse" />
              v1.2 ACTIVE
            </span>
          </div>
          <p className="text-xs text-slate-400">Zero-Trust Telemetry &amp; Anti-Theft Defense</p>
        </div>
      </div>

      <div className="flex items-center gap-4">
        <div className="hidden sm:flex items-center gap-2 text-xs bg-[#162032] text-slate-300 px-3 py-1.5 rounded-xl border border-slate-700/60">
          <User className="w-3.5 h-3.5 text-cyan-400" />
          <span className="font-medium">{user?.fullName || user?.email}</span>
        </div>
        <button
          onClick={logout}
          className="flex items-center gap-1.5 text-xs text-rose-300 hover:text-white transition-colors bg-rose-950/30 hover:bg-rose-900/50 px-3 py-1.5 rounded-xl border border-rose-800/40 font-medium"
        >
          <LogOut className="w-3.5 h-3.5" />
          Sign Out
        </button>
      </div>
    </header>
  );
};
