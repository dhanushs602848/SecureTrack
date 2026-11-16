import React, { useEffect, useState } from 'react';
import { Smartphone, Plus, Battery, Clock, Key } from 'lucide-react';
import { deviceService } from '../services/api';
import { Device } from '../types';

export const Devices: React.FC = () => {
  const [devices, setDevices] = useState<Device[]>([]);
  const [pairingCode, setPairingCode] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchDevices = async () => {
    try {
      const data = await deviceService.getDevices();
      setDevices(data);
    } catch (e) {
      console.error('Failed to load devices:', e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchDevices();
  }, []);

  const handleGeneratePairingCode = async () => {
    try {
      const res = await deviceService.createPairingCode();
      setPairingCode(res.code);
    } catch (e) {
      alert('Failed to generate pairing code');
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold text-[#152C4F]">My Registered Devices</h2>
          <p className="text-sm text-[#62666D]">Manage and authorize mobile endpoints</p>
        </div>
        <button
          onClick={handleGeneratePairingCode}
          className="flex items-center gap-2 px-4 py-2.5 bg-[#152C4F] text-white rounded-xl font-semibold hover:bg-[#1e3a66] transition-all shadow-md text-sm"
        >
          <Plus className="w-4 h-4" />
          Generate Pairing Code
        </button>
      </div>

      {pairingCode && (
        <div className="p-4 bg-[#D8E6FF] border border-[#255DA8] text-[#152C4F] rounded-2xl flex items-center justify-between shadow-sm">
          <div className="flex items-center gap-3">
            <Key className="w-6 h-6 text-[#255DA8]" />
            <div>
              <p className="font-bold text-sm">6-Digit Device Pairing Code</p>
              <p className="text-xs">Enter this code on target phone within 10 minutes</p>
            </div>
          </div>
          <span className="text-2xl font-mono font-bold tracking-widest bg-white px-4 py-1.5 rounded-xl border border-blue-300">
            {pairingCode}
          </span>
        </div>
      )}

      {loading ? (
        <p className="text-center text-sm py-12 text-[#62666D]">Loading devices...</p>
      ) : devices.length === 0 ? (
        <div className="bg-white p-12 text-center rounded-2xl border border-[#DFE2E7] space-y-3">
          <Smartphone className="w-12 h-12 text-[#62666D] mx-auto opacity-50" />
          <h3 className="text-lg font-bold text-[#152C4F]">No Devices Registered</h3>
          <p className="text-sm text-[#62666D] max-w-sm mx-auto">
            Install SecureTrack Android app on your mobile phone or click "Generate Pairing Code" above.
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {devices.map((device) => (
            <div key={device.id} className="bg-white p-6 rounded-2xl border border-[#DFE2E7] shadow-sm space-y-4">
              <div className="flex items-start justify-between">
                <div>
                  <h3 className="text-lg font-bold text-[#152C4F]">{device.deviceName}</h3>
                  <p className="text-xs text-[#62666D]">{device.model || 'Android Phone'}</p>
                </div>
                <span className={`px-2.5 py-1 rounded-full text-xs font-bold ${
                  device.status === 'ONLINE' ? 'bg-emerald-100 text-emerald-800' : 'bg-gray-100 text-gray-600'
                }`}>
                  {device.status}
                </span>
              </div>

              <div className="space-y-2 pt-2 border-t border-[#DFE2E7] text-xs text-[#1A1C1E]">
                <div className="flex items-center justify-between">
                  <span className="flex items-center gap-1.5 text-[#62666D]">
                    <Battery className="w-4 h-4 text-emerald-600" />
                    Battery Level
                  </span>
                  <span className="font-semibold">{device.batteryLevel}%</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="flex items-center gap-1.5 text-[#62666D]">
                    <Clock className="w-4 h-4 text-blue-600" />
                    Last Updated
                  </span>
                  <span className="font-semibold">{device.lastSeen}</span>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
