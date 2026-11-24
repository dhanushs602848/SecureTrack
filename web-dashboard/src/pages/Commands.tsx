import React, { useEffect, useState, useMemo } from 'react';
import { Terminal, Lock, MapPin, Volume2, Smartphone, Clock, AlertTriangle, ShieldCheck, CheckCircle2, XCircle } from 'lucide-react';
import { commandService, deviceService } from '../services/api';
import { Device, DeviceCommand } from '../types';
import { useToast } from '../context/ToastContext';
import { ConfirmDialog } from '../components/ui/ConfirmDialog';
import { Skeleton } from '../components/ui/Skeleton';

export const Commands: React.FC = () => {
  const toast = useToast();
  const [devices, setDevices] = useState<Device[]>([]);
  const [selectedDeviceId, setSelectedDeviceId] = useState<string>('');
  const [commands, setCommands] = useState<DeviceCommand[]>([]);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);

  // Sensitive command confirmation dialog
  const [confirmModal, setConfirmModal] = useState<{
    isOpen: boolean;
    commandType: string;
    title: string;
    description: string;
    variant: 'danger' | 'warning' | 'primary';
  } | null>(null);

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

  const selectedDevice = useMemo(
    () => devices.find((d) => d.id === selectedDeviceId),
    [devices, selectedDeviceId]
  );

  const requestCommandDispatch = (commandType: string) => {
    if (!selectedDeviceId) return;

    if (commandType === 'LOCK_DEVICE') {
      setConfirmModal({
        isOpen: true,
        commandType: 'LOCK_DEVICE',
        title: `Lock Handset Screen?`,
        description: `This will send an immediate lock instruction to "${selectedDevice?.deviceName || 'Target Device'}". The screen will turn off and lock immediately, requiring the device owner's password or biometrics to unlock.`,
        variant: 'warning',
      });
      return;
    }

    if (commandType === 'ALARM_ALERT') {
      setConfirmModal({
        isOpen: true,
        commandType: 'ALARM_ALERT',
        title: `Sound Siren Alarm?`,
        description: `This will sound a high-decibel alarm on "${selectedDevice?.deviceName || 'Target Device'}" at maximum volume for exactly 5.0 seconds. Useful for pinpointing a lost phone in close proximity.`,
        variant: 'warning',
      });
      return;
    }

    // Direct dispatch for non-destructive actions like LOCATE_NOW
    executeSendCommand(commandType);
  };

  const executeSendCommand = async (commandType: string) => {
    if (!selectedDeviceId) return;
    setActionLoading(true);
    try {
      await commandService.sendCommand(selectedDeviceId, commandType, 'Console Dispatch');
      toast.success(
        `Command ${commandType} queued for "${selectedDevice?.deviceName || 'Device'}".`,
        'Command Dispatched'
      );
      setConfirmModal(null);
    } catch (e: any) {
      toast.error(e?.message || 'Failed to dispatch command', 'Command Error');
    } finally {
      setActionLoading(false);
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
    if (rawStatus === 'EXECUTING' || rawStatus === 'CLAIMED') {
      return rawStatus;
    }

    if (cmd.expiresAt) {
      const expiresMillis = typeof cmd.expiresAt === 'number' ? cmd.expiresAt : new Date(cmd.expiresAt).getTime();
      if (!isNaN(expiresMillis) && Date.now() > expiresMillis) {
        return 'EXPIRED';
      }
    }

    if (cmd.createdAt) {
      const createdMillis = typeof cmd.createdAt === 'number' ? cmd.createdAt : new Date(cmd.createdAt).getTime();
      if (!isNaN(createdMillis) && Date.now() - createdMillis > QUEUE_LIMIT_MS) {
        return 'EXPIRED';
      }
    }
    return rawStatus;
  };

  const getPlainEnglishExplanation = (cmd: any): string => {
    const status = getEffectiveCommandStatus(cmd);
    if (status === 'EXPIRED') {
      return 'Command timed out in queue (>15m). Handset may be powered off or disconnected from network.';
    }
    if (status === 'FAILED') {
      return cmd.result || 'Execution rejected by device security rules or missing permissions.';
    }
    if (status === 'EXECUTED') {
      return cmd.result || 'Executed successfully on device.';
    }
    if (status === 'EXECUTING') {
      return 'Dispatched to handset; currently running on Android client.';
    }
    return 'Queued in Google Cloud Firestore. Awaiting device sync.';
  };

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'EXECUTED':
      case 'COMPLETED':
      case 'DELIVERED':
        return 'bg-emerald-100 dark:bg-emerald-950/60 text-emerald-800 dark:text-emerald-300 border-emerald-300 dark:border-emerald-800';
      case 'FAILED':
        return 'bg-rose-100 dark:bg-rose-950/60 text-rose-800 dark:text-rose-300 border-rose-300 dark:border-rose-800';
      case 'EXPIRED':
        return 'bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-400 border-slate-300 dark:border-slate-700';
      case 'EXECUTING':
      case 'CLAIMED':
        return 'bg-blue-100 dark:bg-blue-950/60 text-blue-800 dark:text-blue-300 border-blue-300 dark:border-blue-800';
      case 'QUEUED':
      default:
        return 'bg-amber-100 dark:bg-amber-950/60 text-amber-800 dark:text-amber-300 border-amber-300 dark:border-amber-800';
    }
  };

  return (
    <div className="space-y-6 max-w-5xl mx-auto transition-colors duration-200">
      {/* Header and Controls */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 dark:text-white tracking-tight">
            Remote Device Commands
          </h1>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
            Dispatch authenticated anti-theft controls and inspect execution logs
          </p>
        </div>

        <div className="flex items-center gap-3">
          <label htmlFor="device-command-select" className="sr-only">
            Select Target Device
          </label>
          <select
            id="device-command-select"
            value={selectedDeviceId}
            onChange={(e) => setSelectedDeviceId(e.target.value)}
            className="w-full sm:w-64 bg-white dark:bg-[#121927] border border-slate-300 dark:border-[#223049] text-slate-900 dark:text-slate-200 text-xs rounded-xl focus:ring-2 focus:ring-cyan-500 p-2.5 shadow-xs"
          >
            <option value="" disabled>
              Select target device
            </option>
            {devices.map((d) => (
              <option key={d.id} value={d.id}>
                {d.deviceName} ({d.model || 'Android'}) • {d.status}
              </option>
            ))}
          </select>
        </div>
      </div>

      {/* Target Device Identification Banner */}
      {selectedDevice && (
        <div className="p-4 bg-white dark:bg-[#121927] rounded-2xl border border-slate-200 dark:border-[#223049] shadow-xs flex flex-wrap items-center justify-between gap-3 text-xs">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-indigo-50 dark:bg-indigo-950/60 text-indigo-600 dark:text-cyan-400 flex items-center justify-center border border-indigo-200 dark:border-indigo-800 shrink-0">
              <Smartphone className="w-5 h-5" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className="font-bold text-slate-900 dark:text-white text-sm">
                  {selectedDevice.deviceName}
                </span>
                <span
                  className={`text-[10px] font-bold px-2 py-0.5 rounded-full border ${
                    selectedDevice.status === 'ONLINE'
                      ? 'bg-emerald-100 dark:bg-emerald-950/60 text-emerald-800 dark:text-emerald-300 border-emerald-300 dark:border-emerald-800'
                      : 'bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-400 border-slate-300 dark:border-slate-700'
                  }`}
                >
                  {selectedDevice.status}
                </span>
              </div>
              <p className="text-slate-500 text-[11px] mt-0.5">
                Target Handset: {selectedDevice.model || 'Android'} • Android {selectedDevice.androidVersion || '14'}
              </p>
            </div>
          </div>

          <div className="text-right text-[11px] text-slate-500 font-mono">
            <span>Last Heartbeat: </span>
            <strong className="text-slate-700 dark:text-slate-300">
              {selectedDevice.lastSeen
                ? new Date(selectedDevice.lastSeen).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
                : 'Never'}
            </strong>
          </div>
        </div>
      )}

      {/* Redesigned Command Cards (Wipe Data card completely removed as factory wipe is disabled) */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        {/* Locate Now Card */}
        <button
          type="button"
          onClick={() => requestCommandDispatch('LOCATE_NOW')}
          disabled={!selectedDeviceId || actionLoading}
          className="p-5 bg-white dark:bg-[#121927] border border-slate-200 dark:border-[#223049] hover:border-cyan-500 rounded-2xl shadow-xs transition-all flex flex-col justify-between text-left group disabled:opacity-50"
        >
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <div className="w-10 h-10 rounded-xl bg-cyan-50 dark:bg-cyan-950/50 text-cyan-600 dark:text-cyan-400 border border-cyan-200 dark:border-cyan-800 flex items-center justify-center">
                <MapPin className="w-5 h-5" />
              </div>
              <span className="text-[10px] font-mono font-bold px-2 py-0.5 rounded bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300">
                LOW RISK
              </span>
            </div>
            <div>
              <h3 className="font-bold text-slate-900 dark:text-white text-sm">Locate Now</h3>
              <p className="text-xs text-slate-500 dark:text-slate-400 mt-1 leading-relaxed">
                Requests an immediate high-accuracy GPS fix from the target handset.
              </p>
            </div>
          </div>
          <div className="pt-3 mt-3 border-t border-slate-100 dark:border-slate-800 flex items-center justify-between text-[11px] font-semibold text-cyan-600 dark:text-cyan-400">
            <span>Dispatch GPS Fix</span>
            <span>→</span>
          </div>
        </button>

        {/* Lock Device Card */}
        <button
          type="button"
          onClick={() => requestCommandDispatch('LOCK_DEVICE')}
          disabled={!selectedDeviceId || actionLoading}
          className="p-5 bg-white dark:bg-[#121927] border border-slate-200 dark:border-[#223049] hover:border-indigo-500 rounded-2xl shadow-xs transition-all flex flex-col justify-between text-left group disabled:opacity-50"
        >
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <div className="w-10 h-10 rounded-xl bg-indigo-50 dark:bg-indigo-950/50 text-indigo-600 dark:text-indigo-400 border border-indigo-200 dark:border-indigo-800 flex items-center justify-center">
                <Lock className="w-5 h-5" />
              </div>
              <span className="text-[10px] font-mono font-bold px-2 py-0.5 rounded bg-amber-100 dark:bg-amber-950/60 text-amber-800 dark:text-amber-300 border border-amber-200 dark:border-amber-800">
                MEDIUM RISK
              </span>
            </div>
            <div>
              <h3 className="font-bold text-slate-900 dark:text-white text-sm">Lock Screen</h3>
              <p className="text-xs text-slate-500 dark:text-slate-400 mt-1 leading-relaxed">
                Forces immediate screen lock on the handset to prevent unauthorized physical access.
              </p>
            </div>
          </div>
          <div className="pt-3 mt-3 border-t border-slate-100 dark:border-slate-800 flex items-center justify-between text-[11px] font-semibold text-indigo-600 dark:text-indigo-400">
            <span>Requires Confirmation</span>
            <span>→</span>
          </div>
        </button>

        {/* Siren Alarm Card */}
        <button
          type="button"
          onClick={() => requestCommandDispatch('ALARM_ALERT')}
          disabled={!selectedDeviceId || actionLoading}
          className="p-5 bg-white dark:bg-[#121927] border border-slate-200 dark:border-[#223049] hover:border-amber-500 rounded-2xl shadow-xs transition-all flex flex-col justify-between text-left group disabled:opacity-50"
        >
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <div className="w-10 h-10 rounded-xl bg-amber-50 dark:bg-amber-950/50 text-amber-600 dark:text-amber-400 border border-amber-200 dark:border-amber-800 flex items-center justify-center">
                <Volume2 className="w-5 h-5" />
              </div>
              <span className="text-[10px] font-mono font-bold px-2 py-0.5 rounded bg-amber-100 dark:bg-amber-950/60 text-amber-800 dark:text-amber-300 border border-amber-200 dark:border-amber-800">
                AUDIBLE ALERT
              </span>
            </div>
            <div>
              <h3 className="font-bold text-slate-900 dark:text-white text-sm">5s Siren Alarm</h3>
              <p className="text-xs text-slate-500 dark:text-slate-400 mt-1 leading-relaxed">
                Sounds a loud 5.0-second siren to help recover a misplaced phone nearby.
              </p>
            </div>
          </div>
          <div className="pt-3 mt-3 border-t border-slate-100 dark:border-slate-800 flex items-center justify-between text-[11px] font-semibold text-amber-600 dark:text-amber-400">
            <span>Requires Confirmation</span>
            <span>→</span>
          </div>
        </button>
      </div>

      {/* Command Execution History Table */}
      <div className="bg-white dark:bg-[#121927] rounded-3xl shadow-sm border border-slate-200 dark:border-[#223049] overflow-hidden">
        <div className="p-4 border-b border-slate-200 dark:border-[#223049] flex items-center justify-between bg-slate-50 dark:bg-[#162032]">
          <div className="flex items-center gap-3">
            <Terminal className="w-4 h-4 text-indigo-600 dark:text-cyan-400" />
            <h2 className="font-bold text-slate-900 dark:text-white text-xs font-mono uppercase tracking-wider">
              Execution History &amp; Delivery Status
            </h2>
          </div>
          <span className="text-[11px] text-slate-500 font-mono">
            15m Expiry Guard • FCM + Firestore Synced
          </span>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-xs text-left text-slate-700 dark:text-slate-200">
            <thead className="text-[11px] text-slate-500 uppercase bg-slate-50 dark:bg-[#162032]/60 border-b border-slate-200 dark:border-[#223049]">
              <tr>
                <th className="px-5 py-3.5">Time</th>
                <th className="px-5 py-3.5">Command</th>
                <th className="px-5 py-3.5">Transport</th>
                <th className="px-5 py-3.5">Status</th>
                <th className="px-5 py-3.5">Explanation / Result</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-slate-800/80">
              {loading ? (
                <tr>
                  <td colSpan={5} className="px-5 py-8 text-center text-slate-400 font-mono">
                    Loading command records...
                  </td>
                </tr>
              ) : commands.length === 0 ? (
                <tr>
                  <td colSpan={5} className="px-5 py-12 text-center text-slate-400">
                    No commands dispatched to this device yet.
                  </td>
                </tr>
              ) : (
                commands.map((cmd) => {
                  const effectiveStatus = getEffectiveCommandStatus(cmd);
                  const explanation = getPlainEnglishExplanation(cmd);

                  return (
                    <tr key={cmd.id} className="hover:bg-slate-50 dark:hover:bg-[#162032] transition-colors">
                      <td className="px-5 py-3.5 font-mono text-[11px] text-slate-500 whitespace-nowrap">
                        {cmd.createdAt ? new Date(cmd.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }) : 'Recent'}
                      </td>
                      <td className="px-5 py-3.5 font-bold text-slate-900 dark:text-white font-mono">
                        {cmd.commandType}
                      </td>
                      <td className="px-5 py-3.5">
                        <span className="px-2 py-0.5 rounded text-[10px] font-bold font-mono bg-indigo-50 dark:bg-indigo-950/50 text-indigo-700 dark:text-indigo-400 border border-indigo-200 dark:border-indigo-800">
                          {cmd.transport || 'CLOUD'}
                        </span>
                      </td>
                      <td className="px-5 py-3.5">
                        <span
                          className={`px-2.5 py-1 rounded-full text-[10px] font-bold border uppercase tracking-wider font-mono ${getStatusBadge(
                            effectiveStatus
                          )}`}
                        >
                          {effectiveStatus}
                        </span>
                      </td>
                      <td className="px-5 py-3.5 text-xs text-slate-600 dark:text-slate-300 max-w-xs leading-relaxed">
                        {explanation}
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Confirmation Modal */}
      {confirmModal && (
        <ConfirmDialog
          isOpen={confirmModal.isOpen}
          title={confirmModal.title}
          description={confirmModal.description}
          confirmLabel="Dispatch Command"
          variant={confirmModal.variant}
          isLoading={actionLoading}
          onConfirm={() => executeSendCommand(confirmModal.commandType)}
          onCancel={() => setConfirmModal(null)}
        />
      )}
    </div>
  );
};
