import React, { useEffect, useState } from 'react';
import { Smartphone, Plus, Battery, Clock, Key, ShieldCheck, MapPin, Trash2 } from 'lucide-react';
import { deviceService } from '../services/api';
import { Device } from '../types';

export const Devices: React.FC = () => {
  const [devices, setDevices] = useState<Device[]>([]);
  const [pairingCode, setPairingCode] = useState<string | null>(null);
  const [pairingExpiresAt, setPairingExpiresAt] = useState<string | null>(null);
  const [countdownSeconds, setCountdownSeconds] = useState<number>(0);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);

  useEffect(() => {
    const unsubscribe = deviceService.subscribeToDevices((data) => {
      setDevices(data);
      setLoading(false);
    });
    return () => unsubscribe();
  }, []);

  useEffect(() => {
    if (!pairingExpiresAt) return;
    const interval = setInterval(() => {
      const remaining = Math.max(0, Math.floor((new Date(pairingExpiresAt).getTime() - Date.now()) / 1000));
      setCountdownSeconds(remaining);
      if (remaining <= 0) {
        setPairingCode(null);
        setPairingExpiresAt(null);
        clearInterval(interval);
      }
    }, 1000);
    return () => clearInterval(interval);
  }, [pairingExpiresAt]);

  const handleGeneratePairingCode = async () => {
    setActionLoading(true);
    try {
      const res = await deviceService.createPairingCode();
      setPairingCode(res.code);
      setPairingExpiresAt(res.expiresAt);
      setCountdownSeconds(600);
    } catch (e: any) {
      alert(e.message || 'Failed to generate pairing code');
    } finally {
      setActionLoading(false);
    }
  };

  const handleRevokeDevice = async (deviceId: string, deviceName: string) => {
    const confirmed = window.confirm(
      `Are you sure you want to revoke "${deviceName}"? It will lose access to tracking and commands.`
    );
    if (!confirmed) return;

    try {
      await deviceService.revokePairedDevice(deviceId);
      alert(`Device "${deviceName}" has been revoked.`);
    } catch (e: any) {
      alert(e.message || 'Failed to revoke device');
    }
  };

  const formatCountdown = (secs: number) => {
    const m = Math.floor(secs / 60);
    const s = secs % 60;
    return `${m}:${s < 10 ? '0' : ''}${s}`;
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold text-[#152C4F]">My Registered Devices</h2>
          <p className="text-sm text-[#64748B]">Manage all mobile devices enrolled under your account</p>
        </div>
        <button
          onClick={handleGeneratePairingCode}
          disabled={actionLoading}
          className="flex items-center gap-2 px-4 py-2.5 bg-[#152C4F] text-white rounded-xl font-semibold hover:bg-[#1e3a66] transition-all shadow-md text-sm disabled:opacity-50"
        >
          <Plus className="w-4 h-4" />
          {actionLoading ? 'Generating...' : 'Generate Pairing Code'}
        </button>
      </div>

      {pairingCode && (
        <div className="p-5 bg-[#D8E6FF] border border-[#255DA8] text-[#152C4F] rounded-2xl flex flex-col md:flex-row items-start md:items-center justify-between gap-4 shadow-sm">
          <div className="flex items-center gap-3">
            <Key className="w-8 h-8 text-[#255DA8] shrink-0" />
            <div>
              <p className="font-bold text-base">Secure Device Pairing Code (Single-Use)</p>
              <p className="text-xs text-[#334155]">
                Enter this code on the second phone in SecureTrack within{' '}
                <span className="font-mono font-bold text-[#152C4F]">{formatCountdown(countdownSeconds)}</span>
              </p>
            </div>
          </div>
          <div className="flex items-center gap-3">
            <span className="text-2xl font-mono font-bold tracking-widest bg-white px-5 py-2 rounded-xl border border-blue-300 shadow-inner">
              {pairingCode}
            </span>
            <button
              onClick={() => { setPairingCode(null); setPairingExpiresAt(null); }}
              className="text-xs text-[#64748B] hover:text-[#152C4F] underline"
            >
              Dismiss
            </button>
          </div>
        </div>
      )}

      {loading ? (
        <p className="text-center text-sm py-12 text-[#64748B]">Loading devices...</p>
      ) : devices.length === 0 ? (
        <div className="bg-white p-12 text-center rounded-2xl border border-[#CBD5E1] space-y-3">
          <Smartphone className="w-12 h-12 text-[#64748B] mx-auto opacity-50" />
          <h3 className="text-lg font-bold text-[#152C4F]">No Devices Registered</h3>
          <p className="text-sm text-[#64748B] max-w-sm mx-auto">
            Install the SecureTrack Android app on your phone and log in, or click "Generate Pairing Code" above.
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {devices.map((device) => {
            const isOnline = device.status === 'ONLINE';
            const isRevoked = device.status === 'REVOKED';
            const isLocationOff = device.locationStatus === 'DISABLED';

            return (
              <div key={device.id} className="bg-white p-6 rounded-2xl border border-[#CBD5E1] shadow-sm space-y-4 hover:shadow-md transition-shadow">
                <div className="flex items-start justify-between">
                  <div>
                    <h3 className="text-lg font-bold text-[#152C4F]">{device.deviceName}</h3>
                    <p className="text-xs text-[#64748B]">{device.model || 'Android Phone'} • Android {device.androidVersion}</p>
                  </div>
                  <span className={`px-2.5 py-1 rounded-full text-xs font-bold ${
                    isRevoked ? 'bg-red-100 text-red-800' :
                    isOnline ? 'bg-emerald-100 text-emerald-800' : 'bg-gray-100 text-gray-600'
                  }`}>
                    {device.status}
                  </span>
                </div>

                <div className="space-y-2 pt-2 border-t border-[#CBD5E1] text-xs">
                  <div className="flex items-center justify-between">
                    <span className="flex items-center gap-1.5 text-[#64748B]">
                      <Battery className="w-4 h-4 text-emerald-600" />
                      Battery Level
                    </span>
                    <span className="font-semibold text-[#152C4F]">{device.batteryLevel}%</span>
                  </div>

                  <div className="flex items-center justify-between">
                    <span className="flex items-center gap-1.5 text-[#64748B]">
                      <MapPin className="w-4 h-4 text-blue-600" />
                      Location Status
                    </span>
                    <span className={`font-semibold ${isLocationOff ? 'text-amber-600' : 'text-emerald-700'}`}>
                      {device.locationStatus || 'ENABLED'}
                    </span>
                  </div>

                  <div className="flex items-center justify-between">
                    <span className="flex items-center gap-1.5 text-[#64748B]">
                      <ShieldCheck className="w-4 h-4 text-indigo-600" />
                      Protection
                    </span>
                    <span className="font-semibold text-[#152C4F]">
                      {device.protectionEnabled ? 'Active' : 'Disabled'}
                    </span>
                  </div>

                  <div className="flex items-center justify-between">
                    <span className="flex items-center gap-1.5 text-[#64748B]">
                      <Clock className="w-4 h-4 text-blue-600" />
                      Last Heartbeat
                    </span>
                    <span className="font-semibold text-[#152C4F]">
                      {device.lastSeen ? new Date(device.lastSeen).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : 'Never'}
                    </span>
                  </div>
                </div>

                <div className="pt-3 border-t border-[#CBD5E1] flex items-center justify-between">
                  <span className="text-[11px] text-[#64748B] font-mono truncate max-w-[150px]">
                    ID: {device.id}
                  </span>
                  {!isRevoked && (
                    <button
                      onClick={() => handleRevokeDevice(device.id, device.deviceName)}
                      className="flex items-center gap-1 text-xs text-red-600 hover:text-red-800 font-medium"
                      title="Revoke device access"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                      Revoke
                    </button>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};
