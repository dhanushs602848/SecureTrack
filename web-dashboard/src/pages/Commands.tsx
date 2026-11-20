import React, { useEffect, useState } from 'react';
import { Terminal, Shield, Lock, Trash2, MapPin, Volume2 } from 'lucide-react';
import { commandService, deviceService } from '../services/api';
import { Device, DeviceCommand } from '../types';

export const Commands: React.FC = () => {
  const [devices, setDevices] = useState<Device[]>([]);
  const [selectedDeviceId, setSelectedDeviceId] = useState<string>('');
  const [commands, setCommands] = useState<DeviceCommand[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const unsubDevices = deviceService.subscribeToDevices((data) => {
      setDevices(data);
      if (data.length > 0 && !selectedDeviceId) {
        setSelectedDeviceId(data[0].id);
      }
    });
    return () => unsubDevices();
  }, []);

  useEffect(() => {
    if (selectedDeviceId) {
      setLoading(true);
      const unsubCmds = commandService.subscribeToDeviceCommands(selectedDeviceId, (data) => {
        setCommands(data);
        setLoading(false);
      });
      return () => unsubCmds();
    }
  }, [selectedDeviceId]);

  const handleSendCommand = async (commandType: string) => {
    if (!selectedDeviceId) return;
    try {
      if (commandType === 'WIPE_DEVICE') {
        const step1 = window.confirm("CRITICAL WARNING: Factory resetting this device will permanently erase ALL user data, apps, photos, and accounts.\n\nDo you want to proceed to confirmation?");
        if (!step1) return;
        const step2 = window.prompt("Type 'ERASE' in capital letters to confirm remote factory reset:");
        if (step2 !== 'ERASE') {
          alert('Wipe cancelled: Confirmation word did not match.');
          return;
        }
      }
      await commandService.sendCommand(selectedDeviceId, commandType, 'Remote web trigger');
      alert(`Command ${commandType} dispatched.`);
    } catch (e: any) {
      console.error('Failed to send command', e);
      alert('Failed to send command: ' + (e?.message || e));
    }
  };

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'EXECUTED':
      case 'DELIVERED':
        return 'bg-emerald-50 text-emerald-700 border-emerald-200';
      case 'FAILED':
        return 'bg-red-50 text-red-700 border-red-200';
      case 'EXPIRED':
        return 'bg-gray-100 text-gray-600 border-gray-300';
      case 'EXECUTING':
        return 'bg-blue-50 text-blue-700 border-blue-200';
      default:
        return 'bg-amber-50 text-amber-700 border-amber-200';
    }
  };

  return (
    <div className="space-y-6 max-w-4xl mx-auto">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold text-[#152C4F]">Remote Command Center</h2>
          <p className="text-sm text-[#62666D]">Dispatch verified anti-theft and recovery instructions</p>
        </div>
        <select
          value={selectedDeviceId}
          onChange={(e) => setSelectedDeviceId(e.target.value)}
          className="w-full sm:w-64 bg-white border border-[#DFE2E7] text-[#152C4F] text-sm rounded-xl focus:ring-[#255DA8] focus:border-[#255DA8] p-2.5 shadow-sm"
        >
          <option value="" disabled>Select a device</option>
          {devices.map(d => (
            <option key={d.id} value={d.id}>{d.deviceName} ({d.model})</option>
          ))}
        </select>
      </div>

      {/* Action Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-4">
        {/* Locate Now */}
        <button
          onClick={() => handleSendCommand('LOCATE_NOW')}
          className="flex flex-col items-center justify-center gap-3 p-5 bg-white border-2 border-transparent hover:border-[#255DA8] rounded-2xl shadow-sm transition-all group"
        >
          <div className="w-11 h-11 bg-cyan-50 text-cyan-600 rounded-full flex items-center justify-center group-hover:scale-110 transition-transform">
            <MapPin className="w-5 h-5" />
          </div>
          <div className="text-center">
            <h3 className="font-bold text-sm text-[#152C4F]">Locate Now</h3>
            <p className="text-xs text-[#62666D] mt-0.5">Live GPS refresh</p>
          </div>
        </button>

        {/* Lock Device */}
        <button
          onClick={() => handleSendCommand('LOCK_DEVICE')}
          className="flex flex-col items-center justify-center gap-3 p-5 bg-white border-2 border-transparent hover:border-[#255DA8] rounded-2xl shadow-sm transition-all group"
        >
          <div className="w-11 h-11 bg-blue-50 text-[#255DA8] rounded-full flex items-center justify-center group-hover:scale-110 transition-transform">
            <Lock className="w-5 h-5" />
          </div>
          <div className="text-center">
            <h3 className="font-bold text-sm text-[#152C4F]">Lock Device</h3>
            <p className="text-xs text-[#62666D] mt-0.5">Force screen lock</p>
          </div>
        </button>

        {/* 5s Alarm */}
        <button
          onClick={() => handleSendCommand('ALARM_ALERT')}
          className="flex flex-col items-center justify-center gap-3 p-5 bg-white border-2 border-transparent hover:border-amber-500 rounded-2xl shadow-sm transition-all group"
        >
          <div className="w-11 h-11 bg-amber-50 text-amber-600 rounded-full flex items-center justify-center group-hover:scale-110 transition-transform">
            <Volume2 className="w-5 h-5" />
          </div>
          <div className="text-center">
            <h3 className="font-bold text-sm text-[#152C4F]">5s Siren Alarm</h3>
            <p className="text-xs text-[#62666D] mt-0.5">Auto-stop at 5 sec</p>
          </div>
        </button>

        {/* Wipe Data */}
        <button
          onClick={() => handleSendCommand('WIPE_DEVICE')}
          className="flex flex-col items-center justify-center gap-3 p-5 bg-white border-2 border-transparent hover:border-red-500 rounded-2xl shadow-sm transition-all group"
        >
          <div className="w-11 h-11 bg-red-50 text-red-600 rounded-full flex items-center justify-center group-hover:scale-110 transition-transform">
            <Trash2 className="w-5 h-5" />
          </div>
          <div className="text-center">
            <h3 className="font-bold text-sm text-[#152C4F]">Wipe Data</h3>
            <p className="text-xs text-[#62666D] mt-0.5">Multi-step factory reset</p>
          </div>
        </button>
      </div>

      {/* History Table */}
      <div className="bg-white rounded-2xl shadow-sm border border-[#DFE2E7] overflow-hidden">
        <div className="p-5 border-b border-[#DFE2E7] flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Terminal className="w-5 h-5 text-[#255DA8]" />
            <h3 className="font-bold text-[#152C4F]">Command Execution History</h3>
          </div>
          <span className="text-xs text-[#62666D]">Auto-expires stale commands after 15m</span>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm text-left text-[#1A1C1E]">
            <thead className="text-xs text-[#62666D] uppercase bg-gray-50 border-b border-[#DFE2E7]">
              <tr>
                <th className="px-6 py-4">Time</th>
                <th className="px-6 py-4">Command</th>
                <th className="px-6 py-4">Transport</th>
                <th className="px-6 py-4">Status</th>
                <th className="px-6 py-4">Result</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#DFE2E7]">
              {loading ? (
                <tr>
                  <td colSpan={5} className="px-6 py-8 text-center text-[#62666D]">Loading history...</td>
                </tr>
              ) : commands.length === 0 ? (
                <tr>
                  <td colSpan={5} className="px-6 py-12 text-center text-[#62666D]">No commands recorded for this device.</td>
                </tr>
              ) : (
                commands.map((cmd) => (
                  <tr key={cmd.id} className="hover:bg-gray-50 transition-colors">
                    <td className="px-6 py-4 font-mono text-xs text-gray-500 whitespace-nowrap">
                      {cmd.createdAt ? new Date(cmd.createdAt).toLocaleString() : 'Recent'}
                    </td>
                    <td className="px-6 py-4 font-bold text-[#152C4F]">
                      {cmd.commandType}
                    </td>
                    <td className="px-6 py-4">
                      <span className="px-2 py-0.5 rounded text-[11px] font-bold bg-purple-50 text-purple-700 border border-purple-200">
                        {cmd.transport || 'CLOUD'}
                      </span>
                    </td>
                    <td className="px-6 py-4">
                      <span className={`px-2.5 py-1 rounded-full text-xs font-bold border uppercase tracking-wider ${getStatusBadge(cmd.status)}`}>
                        {cmd.status}
                      </span>
                    </td>
                    <td className="px-6 py-4 text-xs text-[#62666D]">
                      {cmd.result || cmd.payload || '—'}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
