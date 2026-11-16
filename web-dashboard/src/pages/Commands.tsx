import React, { useEffect, useState } from 'react';
import { Terminal, Lock, Volume2, MapPin } from 'lucide-react';
import { commandService, deviceService } from '../services/api';
import { Device, DeviceCommand } from '../types';

export const Commands: React.FC = () => {
  const [devices, setDevices] = useState<Device[]>([]);
  const [selectedDeviceId, setSelectedDeviceId] = useState<number | null>(null);
  const [commands, setCommands] = useState<DeviceCommand[]>([]);
  const [sending, setSending] = useState(false);

  useEffect(() => {
    deviceService.getDevices().then((data) => {
      setDevices(data);
      if (data.length > 0) setSelectedDeviceId(data[0].id);
    });
  }, []);

  const fetchCommands = async () => {
    if (selectedDeviceId) {
      try {
        const data = await commandService.getDeviceCommands(selectedDeviceId);
        setCommands(data);
      } catch (e) {
        console.error('Failed to load commands:', e);
      }
    }
  };

  useEffect(() => {
    fetchCommands();
  }, [selectedDeviceId]);

  const handleSendCommand = async (commandType: string) => {
    if (!selectedDeviceId) return;
    setSending(true);
    try {
      await commandService.sendCommand(selectedDeviceId, commandType, 'Remote web trigger');
      fetchCommands();
    } catch (e) {
      alert('Failed to dispatch command');
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold text-[#152C4F]">Remote Command Dispatch Center</h2>
          <p className="text-sm text-[#62666D]">Authorized remote actions &amp; status lifecycle tracking</p>
        </div>

        <select
          value={selectedDeviceId || ''}
          onChange={(e) => setSelectedDeviceId(Number(e.target.value))}
          className="px-4 py-2 bg-white border border-[#DFE2E7] rounded-xl text-sm font-semibold text-[#152C4F]"
        >
          {devices.map((d) => (
            <option key={d.id} value={d.id}>
              {d.deviceName} ({d.model})
            </option>
          ))}
        </select>
      </div>

      {/* Quick Action Buttons */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <button
          disabled={sending || !selectedDeviceId}
          onClick={() => handleSendCommand('LOCK_DEVICE')}
          className="bg-white p-5 rounded-2xl border border-[#DFE2E7] hover:border-[#152C4F] transition-all flex items-center gap-4 text-left shadow-sm disabled:opacity-50"
        >
          <div className="p-3 bg-red-100 text-red-700 rounded-xl">
            <Lock className="w-6 h-6" />
          </div>
          <div>
            <p className="font-bold text-[#152C4F]">Remote Lock Device</p>
            <p className="text-xs text-[#62666D]">Triggers Device Admin screen lock</p>
          </div>
        </button>

        <button
          disabled={sending || !selectedDeviceId}
          onClick={() => handleSendCommand('ALARM_ALERT')}
          className="bg-white p-5 rounded-2xl border border-[#DFE2E7] hover:border-[#152C4F] transition-all flex items-center gap-4 text-left shadow-sm disabled:opacity-50"
        >
          <div className="p-3 bg-amber-100 text-amber-800 rounded-xl">
            <Volume2 className="w-6 h-6" />
          </div>
          <div>
            <p className="font-bold text-[#152C4F]">Sound High-Volume Alarm</p>
            <p className="text-xs text-[#62666D]">Plays loud alert sound on endpoint</p>
          </div>
        </button>

        <button
          disabled={sending || !selectedDeviceId}
          onClick={() => handleSendCommand('LOCATE_NOW')}
          className="bg-white p-5 rounded-2xl border border-[#DFE2E7] hover:border-[#152C4F] transition-all flex items-center gap-4 text-left shadow-sm disabled:opacity-50"
        >
          <div className="p-3 bg-blue-100 text-[#255DA8] rounded-xl">
            <MapPin className="w-6 h-6" />
          </div>
          <div>
            <p className="font-bold text-[#152C4F]">Request Instant Location</p>
            <p className="text-xs text-[#62666D]">Forces immediate location report</p>
          </div>
        </button>
      </div>

      {/* Command History */}
      <div className="bg-white rounded-2xl border border-[#DFE2E7] p-6 shadow-sm space-y-4">
        <h3 className="text-lg font-bold text-[#152C4F]">Dispatched Command Lifecycle Log</h3>
        {commands.length === 0 ? (
          <p className="text-sm text-[#62666D] py-8 text-center">No commands dispatched yet for selected device.</p>
        ) : (
          <div className="space-y-3">
            {commands.map((cmd) => (
              <div key={cmd.id} className="p-4 bg-[#F8F9FB] rounded-xl border border-[#DFE2E7] flex items-center justify-between">
                <div>
                  <p className="font-bold text-[#152C4F] text-sm">{cmd.commandType}</p>
                  <p className="text-xs text-[#62666D]">{cmd.result || 'Pending device execution...'}</p>
                  <p className="text-[10px] text-gray-400 mt-1">Sent at: {cmd.createdAt}</p>
                </div>
                <span className={`px-3 py-1 rounded-full text-xs font-bold ${
                  cmd.status === 'EXECUTED' ? 'bg-emerald-100 text-emerald-800' :
                  cmd.status === 'FAILED' ? 'bg-red-100 text-red-800' :
                  cmd.status === 'RECEIVED' ? 'bg-blue-100 text-blue-800' :
                  'bg-amber-100 text-amber-800'
                }`}>
                  {cmd.status}
                </span>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};
