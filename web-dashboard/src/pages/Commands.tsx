import React, { useEffect, useState } from 'react';
import { Terminal, Shield, Lock, Trash2, Send } from 'lucide-react';
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
        const confirmed = window.confirm("WARNING: This will factory reset the target device, erasing all data permanently. Are you sure you want to proceed?");
        if (!confirmed) return;
      }
      await commandService.sendCommand(selectedDeviceId, commandType, 'Remote web trigger');
      alert(`Command ${commandType} sent to device.`);
    } catch (e) {
      console.error('Failed to send command', e);
      alert('Failed to send command');
    }
  };

  return (
    <div className="space-y-6 max-w-4xl mx-auto">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold text-[#152C4F]">Remote Command Center</h2>
          <p className="text-sm text-[#62666D]">Dispatch security commands to your endpoints</p>
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

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <button
          onClick={() => handleSendCommand('LOCK_DEVICE')}
          className="flex flex-col items-center justify-center gap-3 p-6 bg-white border-2 border-transparent hover:border-[#255DA8] rounded-2xl shadow-sm transition-all group"
        >
          <div className="w-12 h-12 bg-blue-50 text-[#255DA8] rounded-full flex items-center justify-center group-hover:scale-110 transition-transform">
            <Lock className="w-6 h-6" />
          </div>
          <div className="text-center">
            <h3 className="font-bold text-[#152C4F]">Lock Device</h3>
            <p className="text-xs text-[#62666D] mt-1">Force lock screen immediately</p>
          </div>
        </button>

        <button
          onClick={() => handleSendCommand('ALARM_ALERT')}
          className="flex flex-col items-center justify-center gap-3 p-6 bg-white border-2 border-transparent hover:border-orange-500 rounded-2xl shadow-sm transition-all group"
        >
          <div className="w-12 h-12 bg-orange-50 text-orange-600 rounded-full flex items-center justify-center group-hover:scale-110 transition-transform">
            <Shield className="w-6 h-6" />
          </div>
          <div className="text-center">
            <h3 className="font-bold text-[#152C4F]">Trigger Alarm</h3>
            <p className="text-xs text-[#62666D] mt-1">Play loud siren continuously</p>
          </div>
        </button>

        <button
          onClick={() => handleSendCommand('WIPE_DEVICE')}
          className="flex flex-col items-center justify-center gap-3 p-6 bg-white border-2 border-transparent hover:border-red-500 rounded-2xl shadow-sm transition-all group"
        >
          <div className="w-12 h-12 bg-red-50 text-red-600 rounded-full flex items-center justify-center group-hover:scale-110 transition-transform">
            <Trash2 className="w-6 h-6" />
          </div>
          <div className="text-center">
            <h3 className="font-bold text-[#152C4F]">Wipe Data</h3>
            <p className="text-xs text-[#62666D] mt-1">Factory reset device completely</p>
          </div>
        </button>
      </div>

      <div className="bg-white rounded-2xl shadow-sm border border-[#DFE2E7] overflow-hidden">
        <div className="p-5 border-b border-[#DFE2E7] flex items-center gap-3">
          <Terminal className="w-5 h-5 text-[#255DA8]" />
          <h3 className="font-bold text-[#152C4F]">Command Execution History</h3>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm text-left text-[#1A1C1E]">
            <thead className="text-xs text-[#62666D] uppercase bg-gray-50 border-b border-[#DFE2E7]">
              <tr>
                <th className="px-6 py-4">Time</th>
                <th className="px-6 py-4">Command</th>
                <th className="px-6 py-4">Status</th>
                <th className="px-6 py-4">Result</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan={4} className="px-6 py-8 text-center text-[#62666D]">Loading history...</td>
                </tr>
              ) : commands.length === 0 ? (
                <tr>
                  <td colSpan={4} className="px-6 py-8 text-center text-[#62666D]">No commands dispatched yet.</td>
                </tr>
              ) : (
                commands.map((cmd) => (
                  <tr key={cmd.id} className="bg-white border-b border-[#DFE2E7] hover:bg-gray-50 transition-colors">
                    <td className="px-6 py-4 whitespace-nowrap text-[#62666D]">{new Date(cmd.createdAt).toLocaleString()}</td>
                    <td className="px-6 py-4 font-semibold text-[#152C4F]">{cmd.commandType}</td>
                    <td className="px-6 py-4">
                      <span className={`px-2.5 py-1 rounded-full text-xs font-bold ${
                        cmd.status === 'EXECUTED' ? 'bg-emerald-100 text-emerald-800' :
                        cmd.status === 'FAILED' ? 'bg-red-100 text-red-800' :
                        'bg-blue-100 text-blue-800'
                      }`}>
                        {cmd.status}
                      </span>
                    </td>
                    <td className="px-6 py-4 text-[#62666D] max-w-xs truncate" title={cmd.result || cmd.payload}>
                      {cmd.result || cmd.payload || '-'}
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
