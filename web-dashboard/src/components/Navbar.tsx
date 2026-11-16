import React from 'react';
import { Shield, LogOut, User } from 'lucide-react';
import { useAuth } from '../context/AuthContext';

export const Navbar: React.FC = () => {
  const { user, logout } = useAuth();

  return (
    <header className="bg-[#152C4F] text-white border-b border-[#255DA8] px-6 py-4 flex items-center justify-between shadow-md">
      <div className="flex items-center gap-3">
        <Shield className="w-8 h-8 text-[#D8E6FF]" />
        <div>
          <h1 className="text-xl font-bold tracking-wide">SecureTrack</h1>
          <p className="text-xs text-[#D8E6FF] opacity-80">Smart Mobile Tracking &amp; Anti-Theft System</p>
        </div>
      </div>

      <div className="flex items-center gap-6">
        <div className="flex items-center gap-2 text-sm bg-[#1e3a66] px-3 py-1.5 rounded-full border border-blue-400/20">
          <User className="w-4 h-4 text-blue-300" />
          <span className="font-medium">{user?.fullName || user?.email}</span>
        </div>
        <button
          onClick={logout}
          className="flex items-center gap-2 text-sm text-red-300 hover:text-red-100 transition-colors bg-red-950/40 px-3 py-1.5 rounded-lg border border-red-800/30"
        >
          <LogOut className="w-4 h-4" />
          Sign Out
        </button>
      </div>
    </header>
  );
};
