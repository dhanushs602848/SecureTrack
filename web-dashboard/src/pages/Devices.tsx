import React, { useEffect, useState } from 'react';
import { Smartphone, Plus, Battery, Clock, ShieldCheck, MapPin, Trash2, QrCode, Copy, Check } from 'lucide-react';
import { QRCodeSVG } from 'qrcode.react';
import { deviceService } from '../services/api';
import { Device } from '../types';

export const Devices: React.FC = () => {
  const [devices, setDevices] = useState<Device[]>([]);
  const [pairingToken, setPairingToken] = useState<string | null>(null);
  const [tokenHash, setTokenHash] = useState<string | null>(null);
  const [qrPayload, setQrPayload] = useState<string | null>(null);
  const [pairingExpiresAt, setPairingExpiresAt] = useState<string | null>(null);
  const [countdownSeconds, setCountdownSeconds] = useState<number>(0);
  const [pendingInvitation, setPendingInvitation] = useState<any | null>(null);
  const [copied, setCopied] = useState(false);
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
    if (!tokenHash) return;
    const unsub = deviceService.subscribeToInvitation(tokenHash, (inv) => {
      setPendingInvitation(inv);
    });
    return () => unsub();
  }, [tokenHash]);

  useEffect(() => {
    if (!pairingExpiresAt) return;
    const interval = setInterval(() => {
      const remaining = Math.max(0, Math.floor((new Date(pairingExpiresAt).getTime() - Date.now()) / 1000));
      setCountdownSeconds(remaining);
      if (remaining <= 0) {
        setPairingToken(null);
        setTokenHash(null);
        setQrPayload(null);
        setPairingExpiresAt(null);
        setPendingInvitation(null);
        clearInterval(interval);
      }
    }, 1000);
    return () => clearInterval(interval);
  }, [pairingExpiresAt]);

  const handleGeneratePairingInvitation = async () => {
    setActionLoading(true);
    try {
      const res = await deviceService.createPairingInvitation();
      setPairingToken(res.rawToken);
      setTokenHash(res.tokenHash);
      setQrPayload(res.qrPayload);
      setPairingExpiresAt(res.expiresAt);
      setCountdownSeconds(600);
      setCopied(false);
    } catch (e: any) {
      alert(e.message || 'Failed to generate pairing invitation');
    } finally {
      setActionLoading(false);
    }
  };

  const handleCopyToken = () => {
    if (!pairingToken) return;
    navigator.clipboard.writeText(pairingToken);
    setCopied(true);
    setTimeout(() => setCopied(false), 2500);
  };

  const handleApproveDevice = async () => {
    if (!tokenHash || !pendingInvitation) return;
    setActionLoading(true);
    try {
      await deviceService.approveDeviceClaim(tokenHash, pendingInvitation);
      alert(`Device "${pendingInvitation.deviceInfo?.deviceName || 'Android'}" approved and enrolled successfully!`);
      setPairingToken(null);
      setTokenHash(null);
      setQrPayload(null);
      setPendingInvitation(null);
    } catch (e: any) {
      alert(e.message || 'Failed to approve device');
    } finally {
      setActionLoading(false);
    }
  };

  const handleRejectDevice = async () => {
    if (!tokenHash) return;
    setActionLoading(true);
    try {
      await deviceService.rejectDeviceClaim(tokenHash);
      alert('Device pairing request was rejected.');
      setPendingInvitation(null);
    } catch (e: any) {
      alert(e.message || 'Failed to reject device');
    } finally {
      setActionLoading(false);
    }
  };

  const handleRevokeDevice = async (deviceId: string, deviceName: string) => {
    const confirmed = window.confirm(
      `Are you sure you want to revoke "${deviceName}" (${deviceId})? It will immediately lose access to tracking and commands.`
    );
    if (!confirmed) return;

    setActionLoading(true);
    try {
      await deviceService.revokePairedDevice(deviceId);
      alert(`Device "${deviceName}" has been successfully revoked.`);
    } catch (e: any) {
      console.error('Revoke device error:', {
        code: e?.code,
        message: e?.message,
        stack: e?.stack,
        deviceId,
        error: e
      });
      const errorText = e?.code
        ? `Revocation failed [${e.code}]: ${e.message || 'Operation was rejected by security rules.'}`
        : (e?.message || 'Failed to revoke device. Please check your network and account permissions.');
      alert(errorText);
    } finally {
      setActionLoading(false);
    }
  };

  const handleDeleteDevice = async (deviceId: string, deviceName: string) => {
    const confirmed = window.confirm(
      `Are you sure you want to remove "${deviceName}" (${deviceId}) from your dashboard? This will delete the duplicate registration record.`
    );
    if (!confirmed) return;

    setActionLoading(true);
    try {
      await deviceService.deleteDevice(deviceId);
      alert(`Device registration for "${deviceName}" has been removed.`);
    } catch (e: any) {
      console.error('Delete device error:', {
        code: e?.code,
        message: e?.message,
        stack: e?.stack,
        deviceId,
        error: e
      });
      const errorText = e?.code
        ? `Deletion failed [${e.code}]: ${e.message || 'Operation was rejected by security rules.'}`
        : (e?.message || 'Failed to delete device registration.');
      alert(errorText);
    } finally {
      setActionLoading(false);
    }
  };

  const formatCountdown = (secs: number) => {
    const m = Math.floor(secs / 60);
    const s = secs % 60;
    return `${m}:${s < 10 ? '0' : ''}${s}`;
  };

  return (
    <div className="space-y-6 max-w-7xl mx-auto transition-colors duration-200">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold text-slate-900 dark:text-white font-mono">My Registered Devices</h2>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">Manage and monitor mobile endpoints protected under your account</p>
        </div>
        <button
          onClick={handleGeneratePairingInvitation}
          disabled={actionLoading}
          className="flex items-center gap-2 px-4 py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl font-semibold transition-all shadow-md text-xs disabled:opacity-50"
        >
          <Plus className="w-4 h-4" />
          {actionLoading ? 'Generating...' : 'Pair New Phone (QR / Token)'}
        </button>
      </div>

      {/* 256-Bit Cryptographic Pairing Invitation Card */}
      {pairingToken && qrPayload && (
        <div className="p-6 bg-indigo-50 dark:bg-[#121927] border border-indigo-200 dark:border-indigo-800/80 rounded-2xl shadow-lg space-y-5">
          <div className="flex items-start justify-between">
            <div className="flex items-center gap-3">
              <QrCode className="w-7 h-7 text-indigo-600 dark:text-cyan-400 shrink-0" />
              <div>
                <p className="font-bold text-base text-slate-900 dark:text-white font-mono">Secure 256-Bit Device Pairing Invitation</p>
                <p className="text-xs text-slate-600 dark:text-slate-400">
                  Scan this QR code from the second phone or enter the 256-bit token manually. Expires in{' '}
                  <span className="font-mono font-bold text-indigo-600 dark:text-cyan-400">{formatCountdown(countdownSeconds)}</span>
                </p>
              </div>
            </div>
            <button
              onClick={() => {
                setPairingToken(null);
                setTokenHash(null);
                setQrPayload(null);
                setPendingInvitation(null);
              }}
              className="text-xs text-slate-500 hover:text-slate-800 dark:hover:text-white underline"
            >
              Close
            </button>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6 bg-white dark:bg-[#0A0E17] p-5 rounded-xl border border-indigo-100 dark:border-slate-800">
            {/* QR Code Section */}
            <div className="flex flex-col items-center justify-center space-y-3 p-4 bg-slate-50 dark:bg-[#121927] rounded-xl border border-slate-200 dark:border-[#223049]">
              <div className="bg-white p-3 rounded-xl shadow-sm border border-slate-200">
                <QRCodeSVG value={qrPayload} size={180} level="H" includeMargin={true} />
              </div>
              <p className="text-xs text-slate-500 dark:text-slate-400 font-medium text-center">
                Scan with SecureTrack app on the second phone
              </p>
            </div>

            {/* Manual Token Section */}
            <div className="flex flex-col justify-between space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">
                  Manual One-Time 256-Bit Pairing Token:
                </label>
                <div className="bg-slate-100 dark:bg-[#162032] p-3 rounded-xl border border-slate-200 dark:border-slate-700 font-mono text-[11px] break-all select-all text-slate-800 dark:text-slate-200 leading-relaxed">
                  {pairingToken}
                </div>
              </div>

              <div className="flex items-center gap-3">
                <button
                  onClick={handleCopyToken}
                  className="flex items-center gap-2 px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-semibold transition-all shadow-sm"
                >
                  {copied ? <Check className="w-4 h-4 text-emerald-300" /> : <Copy className="w-4 h-4" />}
                  {copied ? 'Copied to Clipboard!' : 'Copy Pairing Token'}
                </button>
                <span className="text-[11px] text-slate-500">Single-use cryptographic token</span>
              </div>

              {/* Pending Approval Panel */}
              {pendingInvitation && pendingInvitation.status === 'PENDING_APPROVAL' ? (
                <div className="p-4 bg-amber-50 dark:bg-amber-950/40 border border-amber-300 dark:border-amber-800 rounded-xl space-y-3">
                  <div className="flex items-center gap-2 text-amber-800 dark:text-amber-300 font-bold text-xs">
                    <Clock className="w-4 h-4 text-amber-600 animate-spin" />
                    Incoming Pairing Request: Device Pending Approval!
                  </div>
                  <div className="text-xs text-amber-900 dark:text-amber-200 space-y-1">
                    <p><strong>Device:</strong> {pendingInvitation.deviceInfo?.deviceName || 'Android Device'}</p>
                    <p><strong>Model:</strong> {pendingInvitation.deviceInfo?.model || 'Generic'}</p>
                    <p><strong>Android Version:</strong> {pendingInvitation.deviceInfo?.androidVersion || '14'}</p>
                    <p><strong>Battery:</strong> {pendingInvitation.deviceInfo?.batteryLevel}%</p>
                  </div>
                  <div className="flex items-center gap-2 pt-2">
                    <button
                      onClick={handleApproveDevice}
                      disabled={actionLoading}
                      className="flex-1 py-2 bg-emerald-600 hover:bg-emerald-700 text-white rounded-xl text-xs font-bold transition-all shadow-sm"
                    >
                      Approve Device
                    </button>
                    <button
                      onClick={handleRejectDevice}
                      disabled={actionLoading}
                      className="flex-1 py-2 bg-rose-600 hover:bg-rose-700 text-white rounded-xl text-xs font-bold transition-all shadow-sm"
                    >
                      Reject Device
                    </button>
                  </div>
                </div>
              ) : (
                <div className="p-3 bg-indigo-50 dark:bg-indigo-950/30 border border-indigo-200 dark:border-indigo-800/50 rounded-xl text-xs text-indigo-700 dark:text-indigo-300 flex items-center gap-2">
                  <Clock className="w-4 h-4 text-indigo-600 dark:text-indigo-400 shrink-0" />
                  <span>Waiting for second phone to scan or enter token...</span>
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {loading ? (
        <p className="text-center text-xs py-12 text-slate-500 font-mono">Loading enrolled devices...</p>
      ) : devices.length === 0 ? (
        <div className="bg-white dark:bg-[#121927] p-12 text-center rounded-2xl border border-slate-200 dark:border-[#223049] shadow-sm space-y-3">
          <Smartphone className="w-12 h-12 text-slate-400 mx-auto opacity-50" />
          <h3 className="text-base font-bold text-slate-900 dark:text-white font-mono">No Devices Registered</h3>
          <p className="text-xs text-slate-500 max-w-sm mx-auto">
            Install the SecureTrack Android app on your phone and log in, or click "Pair New Phone" above.
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {devices.map((device) => {
            const isOnline = device.status === 'ONLINE';
            const isRevoked = device.status === 'REVOKED';
            const isLocationOff = device.locationStatus === 'DISABLED';

            return (
              <div key={device.id} className="bg-white dark:bg-[#121927] p-6 rounded-2xl border border-slate-200 dark:border-[#223049] shadow-sm dark:shadow-lg space-y-4 hover:shadow-md transition-shadow">
                <div className="flex items-start justify-between">
                  <div>
                    <h3 className="text-base font-bold text-slate-900 dark:text-white">{device.deviceName}</h3>
                    <p className="text-xs text-slate-500 dark:text-slate-400">{device.model || 'Android Phone'} • Android {device.androidVersion}</p>
                  </div>
                  <span className={`px-2.5 py-1 rounded-full text-[11px] font-bold border ${
                    isRevoked
                      ? 'bg-rose-100 dark:bg-rose-950/40 text-rose-700 dark:text-rose-400 border-rose-300 dark:border-rose-800'
                      : isOnline
                      ? 'bg-emerald-100 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-400 border-emerald-300 dark:border-emerald-800'
                      : 'bg-slate-100 dark:bg-slate-800/80 text-slate-600 dark:text-slate-400 border-slate-300 dark:border-slate-700'
                  }`}>
                    {device.status}
                  </span>
                </div>

                <div className="space-y-2 pt-2 border-t border-slate-200 dark:border-slate-800 text-xs">
                  <div className="flex items-center justify-between">
                    <span className="flex items-center gap-1.5 text-slate-500 dark:text-slate-400">
                      <Battery className="w-4 h-4 text-emerald-500" />
                      Battery Level
                    </span>
                    <span className="font-semibold text-slate-800 dark:text-slate-200">{device.batteryLevel}%</span>
                  </div>

                  <div className="flex items-center justify-between">
                    <span className="flex items-center gap-1.5 text-slate-500 dark:text-slate-400">
                      <MapPin className="w-4 h-4 text-cyan-500" />
                      Location Status
                    </span>
                    <span className={`font-semibold ${isLocationOff ? 'text-amber-500' : 'text-emerald-500'}`}>
                      {device.locationStatus || 'ENABLED'}
                    </span>
                  </div>

                  <div className="flex items-center justify-between">
                    <span className="flex items-center gap-1.5 text-slate-500 dark:text-slate-400">
                      <ShieldCheck className="w-4 h-4 text-indigo-500" />
                      Protection Health
                    </span>
                    <span
                      title={device.protectionReason || 'All protection systems active'}
                      className={`text-[10px] font-mono font-bold px-2 py-0.5 rounded border uppercase ${
                        (device.protectionStatus === 'ACTIVE' && !isLocationOff)
                          ? 'bg-emerald-100 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-400 border-emerald-300 dark:border-emerald-800'
                          : (device.protectionStatus === 'DEGRADED' || isLocationOff)
                          ? 'bg-amber-100 dark:bg-amber-950/40 text-amber-700 dark:text-amber-400 border-amber-300 dark:border-amber-800'
                          : device.protectionStatus === 'NEEDS_ATTENTION'
                          ? 'bg-rose-100 dark:bg-rose-950/40 text-rose-700 dark:text-rose-400 border-rose-300 dark:border-rose-800'
                          : 'bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-400 border-slate-300 dark:border-slate-700'
                      }`}
                    >
                      {isLocationOff && device.protectionStatus === 'ACTIVE' ? 'DEGRADED' : (device.protectionStatus || 'ACTIVE')}
                    </span>
                  </div>
                  {device.protectionReason && (device.protectionStatus !== 'ACTIVE' || isLocationOff) && (
                    <p className="text-[10px] text-amber-600 dark:text-amber-400 italic text-right truncate" title={device.protectionReason}>
                      {device.protectionReason}
                    </p>
                  )}

                  <div className="flex items-center justify-between">
                    <span className="flex items-center gap-1.5 text-slate-500 dark:text-slate-400">
                      <Clock className="w-4 h-4 text-indigo-500" />
                      Last Heartbeat
                    </span>
                    <span className="font-semibold text-slate-800 dark:text-slate-200">
                      {device.lastSeen ? new Date(device.lastSeen).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : 'Never'}
                    </span>
                  </div>
                </div>

                <div className="pt-3 border-t border-slate-200 dark:border-slate-800 flex items-center justify-between">
                  <span className="text-[11px] text-slate-400 dark:text-slate-500 font-mono truncate max-w-[130px]" title={device.id}>
                    ID: {device.id}
                  </span>
                  <div className="flex items-center gap-3">
                    {!isRevoked && (
                      <button
                        onClick={() => handleRevokeDevice(device.id, device.deviceName)}
                        disabled={actionLoading}
                        className="flex items-center gap-1 text-xs text-amber-600 dark:text-amber-400 hover:underline font-medium disabled:opacity-50"
                        title="Revoke device tracking and credentials"
                      >
                        <ShieldCheck className="w-3.5 h-3.5" />
                        Revoke
                      </button>
                    )}
                    <button
                      onClick={() => handleDeleteDevice(device.id, device.deviceName)}
                      disabled={actionLoading}
                      className="flex items-center gap-1 text-xs text-rose-600 dark:text-rose-400 hover:underline font-medium disabled:opacity-50"
                      title="Remove device registration"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                      Remove
                    </button>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};
