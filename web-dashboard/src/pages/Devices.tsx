import React, { useEffect, useState } from 'react';
import {
  Smartphone,
  Plus,
  Battery,
  Clock,
  ShieldCheck,
  MapPin,
  Trash2,
  QrCode,
  Copy,
  Check,
  Eye,
  EyeOff,
  AlertTriangle,
  Radio,
  CheckCircle2,
  XCircle,
  HelpCircle
} from 'lucide-react';
import { QRCodeSVG } from 'qrcode.react';
import { deviceService } from '../services/api';
import { Device } from '../types';
import { useToast } from '../context/ToastContext';
import { ConfirmDialog } from '../components/ui/ConfirmDialog';
import { EmptyState } from '../components/ui/EmptyState';
import { CardSkeleton } from '../components/ui/Skeleton';

export const Devices: React.FC = () => {
  const toast = useToast();
  const [devices, setDevices] = useState<Device[]>([]);
  const [pairingToken, setPairingToken] = useState<string | null>(null);
  const [tokenHash, setTokenHash] = useState<string | null>(null);
  const [qrPayload, setQrPayload] = useState<string | null>(null);
  const [pairingExpiresAt, setPairingExpiresAt] = useState<string | null>(null);
  const [countdownSeconds, setCountdownSeconds] = useState<number>(0);
  const [pendingInvitation, setPendingInvitation] = useState<any | null>(null);
  const [copied, setCopied] = useState(false);
  const [revealToken, setRevealToken] = useState(false);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);
  const [visibleDeviceIds, setVisibleDeviceIds] = useState<Record<string, boolean>>({});

  // Confirmation Modals State
  const [revokeTarget, setRevokeTarget] = useState<{ id: string; name: string } | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<{ id: string; name: string } | null>(null);

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
        toast.warning('Pairing invitation expired. Please generate a new invitation to pair.', 'Invitation Expired');
        clearInterval(interval);
      }
    }, 1000);
    return () => clearInterval(interval);
  }, [pairingExpiresAt, toast]);

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
      setRevealToken(false);
      toast.info('Secure 256-bit pairing invitation generated. Valid for 10 minutes.', 'Pairing Ready');
    } catch (e: any) {
      toast.error(e.message || 'Failed to generate pairing invitation', 'Pairing Error');
    } finally {
      setActionLoading(false);
    }
  };

  const handleCopyToken = () => {
    if (!pairingToken) return;
    navigator.clipboard.writeText(pairingToken);
    setCopied(true);
    toast.success('Pairing token copied to clipboard.', 'Copied');
    setTimeout(() => setCopied(false), 2500);
  };

  const handleApproveDevice = async () => {
    if (!tokenHash || !pendingInvitation) return;
    setActionLoading(true);
    try {
      const devName = pendingInvitation.deviceInfo?.deviceName || 'Android Device';
      await deviceService.approveDeviceClaim(tokenHash, pendingInvitation);
      toast.success(`Device "${devName}" enrolled and protected under your account!`, 'Pairing Approved');
      setPairingToken(null);
      setTokenHash(null);
      setQrPayload(null);
      setPendingInvitation(null);
    } catch (e: any) {
      toast.error(e.message || 'Failed to approve device pairing request', 'Approval Error');
    } finally {
      setActionLoading(false);
    }
  };

  const handleRejectDevice = async () => {
    if (!tokenHash) return;
    setActionLoading(true);
    try {
      await deviceService.rejectDeviceClaim(tokenHash);
      toast.info('Device pairing request was rejected.', 'Pairing Declined');
      setPendingInvitation(null);
    } catch (e: any) {
      toast.error(e.message || 'Failed to reject device pairing request', 'Rejection Error');
    } finally {
      setActionLoading(false);
    }
  };

  const executeRevokeDevice = async () => {
    if (!revokeTarget) return;
    setActionLoading(true);
    try {
      await deviceService.revokePairedDevice(revokeTarget.id);
      toast.success(
        `Device "${revokeTarget.name}" has been revoked. Handset access and command dispatch are disabled.`,
        'Access Revoked'
      );
      setRevokeTarget(null);
    } catch (e: any) {
      const errorText = e?.code
        ? `Revocation failed [${e.code}]: ${e.message || 'Operation was rejected by security rules.'}`
        : e?.message || 'Failed to revoke device.';
      toast.error(errorText, 'Revocation Error');
    } finally {
      setActionLoading(false);
    }
  };

  const executeDeleteDevice = async () => {
    if (!deleteTarget) return;
    setActionLoading(true);
    try {
      await deviceService.deleteDevice(deleteTarget.id);
      toast.success(`Device registration for "${deleteTarget.name}" removed from dashboard.`, 'Device Removed');
      setDeleteTarget(null);
    } catch (e: any) {
      const errorText = e?.code
        ? `Deletion failed [${e.code}]: ${e.message || 'Operation was rejected by security rules.'}`
        : e?.message || 'Failed to delete device record.';
      toast.error(errorText, 'Deletion Error');
    } finally {
      setActionLoading(false);
    }
  };

  const formatCountdown = (secs: number) => {
    const m = Math.floor(secs / 60);
    const s = secs % 60;
    return `${m}:${s < 10 ? '0' : ''}${s}`;
  };

  const toggleDeviceId = (id: string) => {
    setVisibleDeviceIds((prev) => ({ ...prev, [id]: !prev[id] }));
  };

  const currentPairingStep = pendingInvitation?.status === 'PENDING_APPROVAL' ? 4 : 2;

  return (
    <div className="space-y-6 max-w-7xl mx-auto transition-colors duration-200">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 dark:text-white tracking-tight">
            Registered Devices
          </h1>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
            Manage mobile endpoints, view protection health, and pair new handsets
          </p>
        </div>
        <button
          type="button"
          onClick={handleGeneratePairingInvitation}
          disabled={actionLoading}
          className="inline-flex items-center gap-2 px-4 py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl font-semibold transition-all shadow-sm text-xs disabled:opacity-50"
        >
          <Plus className="w-4 h-4" />
          {actionLoading ? 'Generating...' : 'Pair New Phone'}
        </button>
      </div>

      {/* Guided 6-Step Pairing Experience */}
      {pairingToken && qrPayload && (
        <div className="p-6 bg-white dark:bg-[#121927] border border-indigo-200 dark:border-indigo-900/60 rounded-3xl shadow-xl space-y-6 animate-fade-in">
          {/* Header & Expiration Notice */}
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-slate-100 dark:border-slate-800 pb-4">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-2xl bg-indigo-50 dark:bg-indigo-950/60 border border-indigo-200 dark:border-indigo-800 text-indigo-600 dark:text-cyan-400 flex items-center justify-center shrink-0">
                <QrCode className="w-5 h-5" />
              </div>
              <div>
                <h2 className="text-base font-bold text-slate-900 dark:text-white font-mono">
                  Pair New Handset (Step-by-Step)
                </h2>
                <p className="text-xs text-slate-500 dark:text-slate-400">
                  Follow the steps below to securely link a second Android device
                </p>
              </div>
            </div>

            <div className="flex items-center gap-3 self-end sm:self-center">
              <div className="flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-mono font-bold bg-amber-50 dark:bg-amber-950/50 text-amber-800 dark:text-amber-300 border border-amber-200 dark:border-amber-800">
                <Clock className="w-3.5 h-3.5" />
                <span>Expires in {formatCountdown(countdownSeconds)}</span>
              </div>
              <button
                type="button"
                onClick={() => {
                  setPairingToken(null);
                  setTokenHash(null);
                  setQrPayload(null);
                  setPendingInvitation(null);
                }}
                className="text-xs text-slate-500 hover:text-slate-800 dark:hover:text-white px-2 py-1"
              >
                Cancel
              </button>
            </div>
          </div>

          {/* Stepper Visualizer */}
          <div className="grid grid-cols-2 sm:grid-cols-6 gap-2 text-center text-[11px] font-medium text-slate-500">
            <div className="p-2 rounded-xl bg-emerald-50 dark:bg-emerald-950/40 text-emerald-800 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-800/60 font-semibold">
              1. Invite Ready ✓
            </div>
            <div
              className={`p-2 rounded-xl border ${
                currentPairingStep >= 2
                  ? 'bg-indigo-50 dark:bg-indigo-950/40 text-indigo-800 dark:text-cyan-300 border-indigo-200 dark:border-indigo-800 font-semibold'
                  : 'bg-slate-50 dark:bg-slate-800/40 border-slate-200 dark:border-slate-800'
              }`}
            >
              2. Scan QR Code
            </div>
            <div
              className={`p-2 rounded-xl border ${
                currentPairingStep >= 3
                  ? 'bg-indigo-50 dark:bg-indigo-950/40 text-indigo-800 dark:text-cyan-300 border-indigo-200 dark:border-indigo-800 font-semibold'
                  : 'bg-slate-50 dark:bg-slate-800/40 border-slate-200 dark:border-slate-800'
              }`}
            >
              3. Handset Request
            </div>
            <div
              className={`p-2 rounded-xl border ${
                currentPairingStep >= 4
                  ? 'bg-amber-50 dark:bg-amber-950/40 text-amber-800 dark:text-amber-300 border-amber-200 dark:border-amber-800 font-semibold'
                  : 'bg-slate-50 dark:bg-slate-800/40 border-slate-200 dark:border-slate-800'
              }`}
            >
              4. Review Device
            </div>
            <div className="p-2 rounded-xl bg-slate-50 dark:bg-slate-800/40 border border-slate-200 dark:border-slate-800">
              5. Approve / Deny
            </div>
            <div className="p-2 rounded-xl bg-slate-50 dark:bg-slate-800/40 border border-slate-200 dark:border-slate-800">
              6. Enrolled
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6 bg-slate-50 dark:bg-[#0A0E17] p-5 rounded-2xl border border-slate-200 dark:border-slate-800">
            {/* Step 2: QR Code */}
            <div className="flex flex-col items-center justify-center p-6 bg-white dark:bg-[#121927] rounded-2xl border border-slate-200 dark:border-[#223049] space-y-3">
              <div className="p-3 bg-white rounded-2xl border border-slate-200 shadow-xs">
                <QRCodeSVG value={qrPayload} size={180} level="H" includeMargin={true} />
              </div>
              <p className="text-xs text-slate-600 dark:text-slate-300 font-medium text-center max-w-xs">
                Open SecureTrack on the target phone and tap <strong>"Pair as Tracked Device"</strong> to scan this code.
              </p>
            </div>

            {/* Step 3 & 4: Token Masking & Pending Approval */}
            <div className="flex flex-col justify-between space-y-4">
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <label className="text-xs font-semibold text-slate-700 dark:text-slate-300">
                    Manual 256-Bit Pairing Token
                  </label>
                  <button
                    type="button"
                    onClick={() => setRevealToken(!revealToken)}
                    className="text-xs text-indigo-600 dark:text-cyan-400 hover:underline flex items-center gap-1 font-medium"
                  >
                    {revealToken ? <EyeOff className="w-3.5 h-3.5" /> : <Eye className="w-3.5 h-3.5" />}
                    <span>{revealToken ? 'Hide Token' : 'Reveal Token'}</span>
                  </button>
                </div>

                <div className="bg-white dark:bg-[#162032] p-3 rounded-xl border border-slate-200 dark:border-slate-700 font-mono text-[11px] select-all text-slate-800 dark:text-slate-200 break-all leading-relaxed">
                  {revealToken ? pairingToken : '••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••'}
                </div>

                <div className="flex items-center gap-3">
                  <button
                    type="button"
                    onClick={handleCopyToken}
                    className="flex items-center gap-2 px-3.5 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-semibold transition-all shadow-xs"
                  >
                    {copied ? <Check className="w-3.5 h-3.5 text-emerald-300" /> : <Copy className="w-3.5 h-3.5" />}
                    <span>{copied ? 'Copied!' : 'Copy Token'}</span>
                  </button>
                  <span className="text-[11px] text-slate-500">
                    Use if the device camera is unavailable
                  </span>
                </div>
              </div>

              {/* Step 4 & 5: Review & Approval Panel */}
              {pendingInvitation && pendingInvitation.status === 'PENDING_APPROVAL' ? (
                <div className="p-4 bg-amber-50 dark:bg-[#201912] border border-amber-300 dark:border-amber-700/80 rounded-2xl space-y-3 animate-fade-in">
                  <div className="flex items-center gap-2 text-amber-900 dark:text-amber-200 font-bold text-xs">
                    <Radio className="w-4 h-4 text-amber-600 dark:text-amber-400 animate-pulse" />
                    <span>Incoming Request: Review Device Credentials</span>
                  </div>

                  <div className="grid grid-cols-2 gap-2 text-xs bg-white/70 dark:bg-black/30 p-3 rounded-xl border border-amber-200 dark:border-amber-900/60">
                    <div>
                      <span className="text-slate-500 block text-[10px]">DEVICE NAME</span>
                      <strong className="text-slate-900 dark:text-white">
                        {pendingInvitation.deviceInfo?.deviceName || 'Android Device'}
                      </strong>
                    </div>
                    <div>
                      <span className="text-slate-500 block text-[10px]">MODEL</span>
                      <strong className="text-slate-900 dark:text-white">
                        {pendingInvitation.deviceInfo?.model || 'Generic Handset'}
                      </strong>
                    </div>
                    <div>
                      <span className="text-slate-500 block text-[10px]">OS VERSION</span>
                      <span className="font-mono text-slate-800 dark:text-slate-200">
                        Android {pendingInvitation.deviceInfo?.androidVersion || '14'}
                      </span>
                    </div>
                    <div>
                      <span className="text-slate-500 block text-[10px]">BATTERY</span>
                      <span className="font-mono text-slate-800 dark:text-slate-200">
                        {pendingInvitation.deviceInfo?.batteryLevel}%
                      </span>
                    </div>
                  </div>

                  <div className="flex items-center gap-3 pt-1">
                    <button
                      type="button"
                      onClick={handleApproveDevice}
                      disabled={actionLoading}
                      className="flex-1 py-2.5 bg-emerald-600 hover:bg-emerald-700 text-white rounded-xl text-xs font-bold transition-all shadow-sm flex items-center justify-center gap-1.5"
                    >
                      <CheckCircle2 className="w-4 h-4" />
                      Approve &amp; Enroll
                    </button>
                    <button
                      type="button"
                      onClick={handleRejectDevice}
                      disabled={actionLoading}
                      className="flex-1 py-2.5 bg-rose-600 hover:bg-rose-700 text-white rounded-xl text-xs font-bold transition-all shadow-sm flex items-center justify-center gap-1.5"
                    >
                      <XCircle className="w-4 h-4" />
                      Reject Request
                    </button>
                  </div>
                </div>
              ) : (
                <div className="p-3.5 bg-indigo-50/60 dark:bg-indigo-950/30 border border-indigo-200 dark:border-indigo-800/50 rounded-xl text-xs text-indigo-800 dark:text-indigo-300 flex items-center gap-2.5">
                  <Clock className="w-4 h-4 text-indigo-600 dark:text-indigo-400 shrink-0" />
                  <span>Waiting for second phone to scan QR code or submit token...</span>
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Device List or Skeletons or Empty State */}
      {loading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          <CardSkeleton />
          <CardSkeleton />
          <CardSkeleton />
        </div>
      ) : devices.length === 0 ? (
        <EmptyState
          icon={Smartphone}
          title="No Devices Registered"
          description="Your security console currently has no enrolled Android phones. Generate a pairing invitation above to link your handset."
          action={{
            label: 'Pair New Phone',
            icon: Plus,
            onClick: handleGeneratePairingInvitation,
          }}
        />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {devices.map((device) => {
            const isOnline = device.status === 'ONLINE';
            const isRevoked = device.status === 'REVOKED';
            const isLocationOff = device.locationStatus === 'DISABLED';
            const isIdVisible = visibleDeviceIds[device.id] || false;

            return (
              <div
                key={device.id}
                className="bg-white dark:bg-[#121927] p-6 rounded-3xl border border-slate-200 dark:border-[#223049] shadow-sm space-y-4 hover:shadow-md transition-shadow flex flex-col justify-between"
              >
                <div>
                  {/* Card Header */}
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <h2 className="text-base font-bold text-slate-900 dark:text-white truncate">
                        {device.deviceName}
                      </h2>
                      <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
                        {device.model || 'Android Phone'} • Android {device.androidVersion || '14'}
                      </p>
                    </div>
                    <span
                      className={`px-2.5 py-1 rounded-full text-[11px] font-bold border shrink-0 ${
                        isRevoked
                          ? 'bg-rose-100 dark:bg-rose-950/60 text-rose-800 dark:text-rose-300 border-rose-300 dark:border-rose-800'
                          : isOnline
                          ? 'bg-emerald-100 dark:bg-emerald-950/60 text-emerald-800 dark:text-emerald-300 border-emerald-300 dark:border-emerald-800'
                          : 'bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-400 border-slate-300 dark:border-slate-700'
                      }`}
                    >
                      {device.status}
                    </span>
                  </div>

                  {/* Telemetry Metrics */}
                  <div className="space-y-2.5 pt-4 mt-3 border-t border-slate-100 dark:border-slate-800 text-xs">
                    {/* Battery */}
                    <div className="flex items-center justify-between">
                      <span className="flex items-center gap-1.5 text-slate-500 dark:text-slate-400">
                        <Battery className="w-4 h-4 text-emerald-500" />
                        Battery Level
                      </span>
                      <span className="font-semibold text-slate-800 dark:text-slate-200 font-mono">
                        {device.batteryLevel ?? 'N/A'}%
                      </span>
                    </div>

                    {/* Location Status */}
                    <div className="flex items-center justify-between">
                      <span className="flex items-center gap-1.5 text-slate-500 dark:text-slate-400">
                        <MapPin className="w-4 h-4 text-cyan-500" />
                        Location Telemetry
                      </span>
                      <span
                        className={`font-semibold font-mono text-[11px] ${
                          isLocationOff ? 'text-amber-500' : 'text-emerald-500'
                        }`}
                      >
                        {isLocationOff ? 'DISABLED BY USER' : 'ENABLED'}
                      </span>
                    </div>

                    {/* Protection Health */}
                    <div className="flex items-center justify-between">
                      <span className="flex items-center gap-1.5 text-slate-500 dark:text-slate-400">
                        <ShieldCheck className="w-4 h-4 text-indigo-500" />
                        Protection Health
                      </span>
                      <span
                        title={device.protectionReason || 'Protection active'}
                        className={`text-[10px] font-mono font-bold px-2 py-0.5 rounded border uppercase ${
                          device.protectionStatus === 'ACTIVE' && !isLocationOff
                            ? 'bg-emerald-100 dark:bg-emerald-950/60 text-emerald-800 dark:text-emerald-300 border-emerald-300 dark:border-emerald-800'
                            : device.protectionStatus === 'DEGRADED' || isLocationOff
                            ? 'bg-amber-100 dark:bg-amber-950/60 text-amber-800 dark:text-amber-300 border-amber-300 dark:border-amber-800'
                            : 'bg-rose-100 dark:bg-rose-950/60 text-rose-800 dark:text-rose-300 border-rose-300 dark:border-rose-800'
                        }`}
                      >
                        {isLocationOff && device.protectionStatus === 'ACTIVE'
                          ? 'DEGRADED'
                          : device.protectionStatus || 'ACTIVE'}
                      </span>
                    </div>

                    {/* Last Heartbeat */}
                    <div className="flex items-center justify-between">
                      <span className="flex items-center gap-1.5 text-slate-500 dark:text-slate-400">
                        <Clock className="w-4 h-4 text-slate-400" />
                        Last Heartbeat
                      </span>
                      <span className="font-semibold text-slate-800 dark:text-slate-200 text-[11px]">
                        {device.lastSeen
                          ? new Date(device.lastSeen).toLocaleTimeString([], {
                              hour: '2-digit',
                              minute: '2-digit',
                            })
                          : 'Never'}
                      </span>
                    </div>

                    {/* Protection reason if degraded */}
                    {device.protectionReason &&
                      (device.protectionStatus !== 'ACTIVE' || isLocationOff) && (
                        <p className="text-[10px] text-amber-600 dark:text-amber-400 italic text-right truncate">
                          {device.protectionReason}
                        </p>
                      )}
                  </div>
                </div>

                {/* Footer Actions & ID Toggle */}
                <div className="pt-3 border-t border-slate-100 dark:border-slate-800 space-y-2">
                  <div className="flex items-center justify-between text-[11px]">
                    <button
                      type="button"
                      onClick={() => toggleDeviceId(device.id)}
                      className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 underline font-mono"
                    >
                      {isIdVisible ? 'Hide ID' : 'Show ID'}
                    </button>
                    {isIdVisible && (
                      <span className="font-mono text-slate-500 select-all truncate max-w-[140px]" title={device.id}>
                        {device.id}
                      </span>
                    )}
                  </div>

                  <div className="flex items-center justify-end gap-3 pt-1">
                    {!isRevoked && (
                      <button
                        type="button"
                        onClick={() => setRevokeTarget({ id: device.id, name: device.deviceName })}
                        disabled={actionLoading}
                        className="px-3 py-1.5 text-xs text-amber-700 dark:text-amber-400 hover:bg-amber-50 dark:hover:bg-amber-950/40 border border-amber-200 dark:border-amber-800/80 rounded-xl font-medium transition-colors"
                        title="Revoke device credentials and disable remote commands"
                      >
                        Revoke Access
                      </button>
                    )}
                    <button
                      type="button"
                      onClick={() => setDeleteTarget({ id: device.id, name: device.deviceName })}
                      disabled={actionLoading}
                      className="px-3 py-1.5 text-xs text-rose-700 dark:text-rose-400 hover:bg-rose-50 dark:hover:bg-rose-950/40 border border-rose-200 dark:border-rose-800/80 rounded-xl font-medium transition-colors flex items-center gap-1"
                      title="Delete registration record"
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

      {/* Revocation Confirmation Dialog */}
      <ConfirmDialog
        isOpen={!!revokeTarget}
        title="Revoke Device Access?"
        description={`Are you sure you want to revoke access for "${revokeTarget?.name}"? The phone will immediately lose background tracking permissions and will not accept remote security commands. Historical audit logs will be preserved.`}
        confirmLabel="Revoke Access"
        variant="warning"
        isLoading={actionLoading}
        onConfirm={executeRevokeDevice}
        onCancel={() => setRevokeTarget(null)}
      />

      {/* Deletion Confirmation Dialog */}
      <ConfirmDialog
        isOpen={!!deleteTarget}
        title="Remove Device Registration?"
        description={`Are you sure you want to permanently delete the registration record for "${deleteTarget?.name}"? Use this only for duplicate or obsolete handsets. This action cannot be undone.`}
        confirmLabel="Remove Record"
        variant="danger"
        isLoading={actionLoading}
        onConfirm={executeDeleteDevice}
        onCancel={() => setDeleteTarget(null)}
      />
    </div>
  );
};
