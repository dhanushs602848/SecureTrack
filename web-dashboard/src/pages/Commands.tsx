import React, { useEffect, useState } from 'react';
import { Terminal, Lock, MapPin, Volume2 } from 'lucide-react';
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
        alert('Safety Lock: Remote factory wipe is disabled in this build to prevent accidental data loss on enrolled devices.');
        return;
      }
      await commandService.sendCommand(selectedDeviceId, commandType, 'Remote web trigger');
      alert(`Command ${commandType} dispatched.`);
    } catch (e: any) {
      console.error('Failed to send command', e);
      alert('Failed to send command: ' + (e?.message || e));
    }
  };

  const QUEUE_LIMIT_MS = 15 * 60 * 1000; // 15 minutes

  const getEffectiveCommandStatus = (cmd: any): string => {
    const rawStatus = (cmd.status || 'QUEUED').toUpperCase();
    if (rawStatus === 'EXECUTED' || rawStatus === 'COMPLETED' || rawStatus === 'DELIVERED') {
      return 'EXECUTED';
    }
    if (rawStatus === 'FAILED' || rawStatus === 'EXPIRED') {
      return rawStatus;
    }
    // Actively executing or claimed commands must NEVER be marked as expired
    if (rawStatus === 'EXECUTING' || rawStatus === 'CLAIMED') {
      return rawStatus;
    }

    // Explicit expiresAt check
    if (cmd.expiresAt) {
      const expiresMillis = typeof cmd.expiresAt === 'number'
        ? cmd.expiresAt
        : new Date(cmd.expiresAt).getTime();
      if (!isNaN(expiresMillis) && Date.now() > expiresMillis) {
        return 'EXPIRED';
      }
    }

    // Command is still in queue (QUEUED, PENDING, CREATED)
    if (cmd.createdAt) {
      const createdMillis = typeof cmd.createdAt === 'number'
        ? cmd.createdAt
        : new Date(cmd.createdAt).getTime();
      if (!isNaN(createdMillis) && (Date.now() - createdMillis > QUEUE_LIMIT_MS)) {
        return 'EXPIRED';
      }
    }
    return rawStatus;
  };

  const getEffectiveResult = (cmd: any): string => {
    const effectiveStatus = getEffectiveCommandStatus(cmd);
    if (effectiveStatus === 'EXPIRED') {
      return cmd.result || 'Command expired in queue (>15 min)';
    }
    if (effectiveStatus === 'EXECUTED') {
      return cmd.result || 'Executed successfully';
    }
    return cmd.result || cmd.payload || 'In Queue';
  };

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'COMPLETED':
      case 'EXECUTED':
      case 'DELIVERED':
        return 'bg-emerald-100 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-400 border-emerald-300 dark:border-emerald-800';
      case 'FAILED':
        return 'bg-rose-100 dark:bg-rose-950/40 text-rose-700 dark:text-rose-400 border-rose-300 dark:border-rose-800';
      case 'EXPIRED':
        return 'bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-400 border-slate-300 dark:border-slate-700';
      case 'EXECUTING':
      case 'CLAIMED':
        return 'bg-blue-100 dark:bg-blue-950/40 text-blue-700 dark:text-blue-400 border-blue-300 dark:border-blue-800';
      case 'QUEUED':
      case 'PENDING':
      case 'CREATED':
      default:
        return 'bg-amber-100 dark:bg-amber-950/40 text-amber-700 dark:text-amber-400 border-amber-300 dark:border-amber-800';
    }
  };

  return (
    <div className="space-y-6 max-w-4xl mx-auto transition-colors duration-200">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold text-slate-900 dark:text-white font-mono">Remote Command Center</h2>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">Dispatch verified anti-theft and recovery instructions</p>
        </div>
        <select
          value={selectedDeviceId}
          onChange={(e) => setSelectedDeviceId(e.target.value)}
          className="w-full sm:w-64 bg-white dark:bg-[#121927] border border-slate-200 dark:border-[#223049] text-slate-900 dark:text-slate-200 text-xs rounded-xl focus:ring-cyan-500 focus:border-cyan-500 p-2.5 shadow-sm"
        >
          <option value="" disabled>Select a device</option>
          {devices.map(d => (
            <option key={d.id} value={d.id}>{d.deviceName} ({d.model})</option>
          ))}
        </select>
      </div>

      {/* Action Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        {/* Locate Now */}
        <button
          onClick={() => handleSendCommand('LOCATE_NOW')}
          className="flex flex-col items-center justify-center gap-3 p-5 bg-white dark:bg-[#121927] border border-slate-200 dark:border-[#223049] hover:border-cyan-500 dark:hover:border-cyan-400 rounded-2xl shadow-sm dark:shadow-lg transition-all group"
        >
          <div className="w-11 h-11 bg-cyan-100 dark:bg-cyan-950/50 text-cyan-600 dark:text-cyan-400 border border-cyan-200 dark:border-cyan-800/50 rounded-full flex items-center justify-center group-hover:scale-110 transition-transform shadow-[0_0_10px_rgba(0,210,255,0.15)]">
            <MapPin className="w-5 h-5" />
          </div>
          <div className="text-center">
            <h3 className="font-bold text-sm text-slate-900 dark:text-white font-mono">Locate Now</h3>
            <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">Live GPS refresh</p>
          </div>
        </button>

        {/* Lock Device */}
        <button
          onClick={() => handleSendCommand('LOCK_DEVICE')}
          className="flex flex-col items-center justify-center gap-3 p-5 bg-white dark:bg-[#121927] border border-slate-200 dark:border-[#223049] hover:border-indigo-500 dark:hover:border-indigo-400 rounded-2xl shadow-sm dark:shadow-lg transition-all group"
        >
          <div className="w-11 h-11 bg-indigo-100 dark:bg-indigo-950/50 text-indigo-600 dark:text-indigo-400 border border-indigo-200 dark:border-indigo-800/50 rounded-full flex items-center justify-center group-hover:scale-110 transition-transform shadow-[0_0_10px_rgba(99,102,241,0.15)]">
            <Lock className="w-5 h-5" />
          </div>
          <div className="text-center">
            <h3 className="font-bold text-sm text-slate-900 dark:text-white font-mono">Lock Device</h3>
            <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">Force screen lock</p>
          </div>
        </button>

        {/* 5s Alarm */}
        <button
          onClick={() => handleSendCommand('ALARM_ALERT')}
          className="flex flex-col items-center justify-center gap-3 p-5 bg-white dark:bg-[#121927] border border-slate-200 dark:border-[#223049] hover:border-amber-500 dark:hover:border-amber-400 rounded-2xl shadow-sm dark:shadow-lg transition-all group"
        >
          <div className="w-11 h-11 bg-amber-100 dark:bg-amber-950/50 text-amber-600 dark:text-amber-400 border border-amber-200 dark:border-amber-800/50 rounded-full flex items-center justify-center group-hover:scale-110 transition-transform shadow-[0_0_10px_rgba(245,158,11,0.15)]">
            <Volume2 className="w-5 h-5" />
          </div>
          <div className="text-center">
            <h3 className="font-bold text-sm text-slate-900 dark:text-white font-mono">5s Siren Alarm</h3>
            <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">Auto-stop at 5 sec</p>
          </div>
        </button>
      </div>

      {/* History Table */}
      <div className="bg-white dark:bg-[#121927] rounded-2xl shadow-sm dark:shadow-xl border border-slate-200 dark:border-[#223049] overflow-hidden">
        <div className="p-5 border-b border-slate-200 dark:border-[#223049] flex items-center justify-between bg-slate-50 dark:bg-[#162032]">
          <div className="flex items-center gap-3">
            <Terminal className="w-5 h-5 text-indigo-600 dark:text-cyan-400" />
            <h3 className="font-bold text-slate-900 dark:text-white text-sm font-mono">Command Execution History</h3>
          </div>
          <span className="text-xs text-slate-500 dark:text-slate-400 font-mono">Queue limit: 15m • Auto-expires stale commands</span>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-xs text-left text-slate-700 dark:text-slate-200">
            <thead className="text-[11px] text-slate-500 dark:text-slate-400 uppercase bg-slate-50 dark:bg-[#162032]/60 border-b border-slate-200 dark:border-[#223049]">
              <tr>
                <th className="px-6 py-4">Time</th>
                <th className="px-6 py-4">Command</th>
                <th className="px-6 py-4">Transport</th>
                <th className="px-6 py-4">Status</th>
                <th className="px-6 py-4">Result</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200 dark:divide-[#223049]">
              {loading ? (
                <tr>
                  <td colSpan={5} className="px-6 py-8 text-center text-slate-400 font-mono">Loading history...</td>
                </tr>
              ) : commands.length === 0 ? (
                <tr>
                  <td colSpan={5} className="px-6 py-12 text-center text-slate-400 font-mono">No commands recorded for this device.</td>
                </tr>
              ) : (
                commands.map((cmd) => {
                  const effectiveStatus = getEffectiveCommandStatus(cmd);
                  return (
                    <tr key={cmd.id} className="hover:bg-slate-50 dark:hover:bg-[#162032] transition-colors">
                      <td className="px-6 py-4 font-mono text-[11px] text-slate-500 dark:text-slate-400 whitespace-nowrap">
                        {cmd.createdAt ? new Date(cmd.createdAt).toLocaleString() : 'Recent'}
                      </td>
                      <td className="px-6 py-4 font-bold text-slate-900 dark:text-white font-mono">
                        {cmd.commandType}
                      </td>
                      <td className="px-6 py-4">
                        <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-indigo-100 dark:bg-indigo-950/50 text-indigo-700 dark:text-indigo-400 border border-indigo-200 dark:border-indigo-800">
                          {cmd.transport || 'CLOUD'}
                        </span>
                      </td>
                      <td className="px-6 py-4">
                        <span className={`px-2.5 py-1 rounded-full text-[10px] font-bold border uppercase tracking-wider ${getStatusBadge(effectiveStatus)}`}>
                          {effectiveStatus}
                        </span>
                      </td>
                      <td className="px-6 py-4 text-xs text-slate-500 dark:text-slate-400">
                        {getEffectiveResult(cmd)}
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
