import React, { useEffect, useState } from 'react';
import { Smartphone, Plus, Battery, Clock, Key, ShieldCheck, MapPin, Trash2, QrCode, Copy, Check, CheckCircle2, XCircle } from 'lucide-react';
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
      `Are you sure you want to revoke "${deviceName}"? It will immediately lose access to tracking and commands.`
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
          onClick={handleGeneratePairingInvitation}
          disabled={actionLoading}
          className="flex items-center gap-2 px-4 py-2.5 bg-[#152C4F] text-white rounded-xl font-semibold hover:bg-[#1e3a66] transition-all shadow-md text-sm disabled:opacity-50"
        >
          <Plus className="w-4 h-4" />
          {actionLoading ? 'Generating...' : 'Pair New Phone (QR / Token)'}
        </button>
      </div>

      {/* 256-Bit Cryptographic Pairing Invitation Card */}
      {pairingToken && qrPayload && (
        <div className="p-6 bg-[#D8E6FF] border border-[#255DA8] text-[#152C4F] rounded-2xl shadow-md space-y-5">
          <div className="flex items-start justify-between">
            <div className="flex items-center gap-3">
              <QrCode className="w-7 h-7 text-[#255DA8] shrink-0" />
              <div>
                <p className="font-bold text-lg">Secure 256-Bit Device Pairing Invitation</p>
                <p className="text-xs text-[#334155]">
                  Scan this QR code from the second phone or enter the 256-bit token manually. Expires in{' '}
                  <span className="font-mono font-bold text-[#152C4F]">{formatCountdown(countdownSeconds)}</span>
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
              className="text-xs text-[#64748B] hover:text-[#152C4F] underline"
            >
              Close
            </button>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6 bg-white p-5 rounded-xl border border-blue-200">
            {/* QR Code Section */}
            <div className="flex flex-col items-center justify-center space-y-3 p-3 bg-gray-50 rounded-lg border border-gray-100">
              <div className="bg-white p-3 rounded-xl shadow-sm border border-gray-200">
                <QRCodeSVG value={qrPayload} size={180} level="H" includeMargin={true} />
              </div>
              <p className="text-xs text-[#64748B] font-medium text-center">
                Scan with SecureTrack app on the second phone
              </p>
            </div>

            {/* Manual Token Section */}
            <div className="flex flex-col justify-between space-y-4">
              <div>
                <label className="block text-xs font-semibold text-[#152C4F] mb-1">
                  Manual One-Time 256-Bit Pairing Token:
                </label>
                <div className="bg-gray-100 p-3 rounded-lg border border-gray-300 font-mono text-[11px] break-all select-all text-[#152C4F] leading-relaxed">
                  {pairingToken}
                </div>
              </div>

              <div className="flex items-center gap-3">
                <button
                  onClick={handleCopyToken}
                  className="flex items-center gap-2 px-4 py-2 bg-[#255DA8] text-white rounded-lg text-xs font-semibold hover:bg-[#1a447c] transition-all"
                >
                  {copied ? <Check className="w-4 h-4 text-emerald-300" /> : <Copy className="w-4 h-4" />}
                  {copied ? 'Copied to Clipboard!' : 'Copy Pairing Token'}
                </button>
                <span className="text-[11px] text-[#64748B]">Single-use cryptographic token</span>
              </div>

              {/* Pending Approval Panel */}
              {pendingInvitation && pendingInvitation.status === 'PENDING_APPROVAL' ? (
                <div className="p-4 bg-amber-50 border border-amber-300 rounded-xl space-y-3">
                  <div className="flex items-center gap-2 text-amber-900 font-bold text-sm">
                    <Clock className="w-4 h-4 text-amber-600 animate-spin" />
                    Incoming Pairing Request: Device Pending Approval!
                  </div>
                  <div className="text-xs text-amber-950 space-y-1">
                    <p><strong>Device:</strong> {pendingInvitation.deviceInfo?.deviceName || 'Android Device'}</p>
                    <p><strong>Model:</strong> {pendingInvitation.deviceInfo?.model || 'Generic'}</p>
                    <p><strong>Android Version:</strong> {pendingInvitation.deviceInfo?.androidVersion || '14'}</p>
                    <p><strong>Battery:</strong> {pendingInvitation.deviceInfo?.batteryLevel}%</p>
                  </div>
                  <div className="flex items-center gap-2 pt-2">
                    <button
                      onClick={handleApproveDevice}
                      disabled={actionLoading}
                      className="flex-1 py-2 bg-emerald-600 hover:bg-emerald-700 text-white rounded-lg text-xs font-bold transition-all shadow-sm"
                    >
                      Approve Device
                    </button>
                    <button
                      onClick={handleRejectDevice}
                      disabled={actionLoading}
                      className="flex-1 py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg text-xs font-bold transition-all shadow-sm"
                    >
                      Reject Device
                    </button>
                  </div>
                </div>
              ) : (
                <div className="p-3 bg-blue-50 border border-blue-200 rounded-lg text-xs text-[#255DA8] flex items-center gap-2">
                  <Clock className="w-4 h-4 text-[#255DA8] shrink-0" />
                  <span>Waiting for second phone to scan or enter token...</span>
                </div>
              )}
            </div>
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
