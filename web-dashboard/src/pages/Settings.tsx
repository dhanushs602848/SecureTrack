import React from 'react';
import { useAuth } from '../context/AuthContext';

export const Settings: React.FC = () => {
  const { user } = useAuth();

  return (
    <div className="space-y-6 max-w-2xl">
      <div>
        <h2 className="text-2xl font-bold text-[#152C4F]">Account &amp; System Settings</h2>
        <p className="text-sm text-[#62666D]">Manage your SecureTrack web profile and preferences</p>
      </div>

      <div className="bg-white p-6 rounded-2xl border border-[#DFE2E7] shadow-sm space-y-4">
        <h3 className="text-lg font-bold text-[#152C4F]">User Profile Information</h3>
        <div className="space-y-3 text-sm">
          <div>
            <label className="text-xs text-[#62666D]">Full Name</label>
            <p className="font-semibold text-[#1A1C1E]">{user?.fullName}</p>
          </div>
          <div>
            <label className="text-xs text-[#62666D]">Email Address</label>
            <p className="font-semibold text-[#1A1C1E]">{user?.email}</p>
          </div>
          <div>
            <label className="text-xs text-[#62666D]">Phone Number</label>
            <p className="font-semibold text-[#1A1C1E]">{user?.phoneNumber || 'Not provided'}</p>
          </div>
        </div>
      </div>
    </div>
  );
};
