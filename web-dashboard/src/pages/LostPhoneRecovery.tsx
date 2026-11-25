import React, { useState, useEffect } from 'react';
import { deviceService } from '../services/api';
import { Device } from '../types';
import { EmailAuthProvider, GoogleAuthProvider, reauthenticateWithCredential, reauthenticateWithPopup } from 'firebase/auth';
import { auth } from '../services/firebase';
import { ShieldCheck, Lock, ExternalLink, AlertCircle, Eye, EyeOff, Smartphone, CheckCircle2, FileText, PhoneCall, HelpCircle } from 'lucide-react';
import { useToast } from '../context/ToastContext';
import { Skeleton } from '../components/ui/Skeleton';

export const LostPhoneRecovery: React.FC = () => {
  const toast = useToast();
  const [devices, setDevices] = useState<Device[]>([]);
  const [loading, setLoading] = useState(true);
  const [reauthPassword, setReauthPassword] = useState('');
  const [showReauthPassword, setShowReauthPassword] = useState(false);
  const [revealedDeviceId, setRevealedDeviceId] = useState<string | null>(null);
  const [showReauthModal, setShowReauthModal] = useState(false);
  const [targetDeviceId, setTargetDeviceId] = useState<string | null>(null);
  const [authError, setAuthError] = useState('');
  const [reauthLoading, setReauthLoading] = useState(false);
  const [recoveryInfo, setRecoveryInfo] = useState<{ imei1?: string; imei2?: string } | null>(null);

  useEffect(() => {
    fetchDevices();
    return () => {
      setRevealedDeviceId(null);
      setRecoveryInfo(null);
    };
  }, []);

  const fetchDevices = async () => {
    try {
      const data = await deviceService.getDevices();
      setDevices(data);
    } catch (error) {
      console.error('Failed to load devices', error);
      toast.error('Failed to load registered devices list.', 'Load Error');
    } finally {
      setLoading(false);
    }
  };

  const handleRevealRequest = (deviceId: string) => {
    setTargetDeviceId(deviceId);
    setShowReauthModal(true);
    setAuthError('');
    setReauthPassword('');
  };

  const handleReauthSuccess = async (deviceId: string) => {
    try {
      const info = await deviceService.getRecoveryInfo(deviceId);
      setRecoveryInfo(info);
      setRevealedDeviceId(deviceId);
      setShowReauthModal(false);
      setReauthPassword('');
      setShowReauthPassword(false);
      setTargetDeviceId(null);
      toast.success('Identity verified. IMEI recovery identifiers unlocked.', 'Access Granted');
    } catch (e: any) {
      toast.error(e?.message || 'Failed to retrieve recovery identifiers', 'Vault Error');
    }
  };

  const handleReauthSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setAuthError('');
    if (!reauthPassword.trim()) {
      setAuthError('Please enter your account password.');
      return;
    }

    setReauthLoading(true);
    try {
      const user = auth.currentUser;
      if (!user || !user.email) {
        setAuthError('User not properly authenticated.');
        return;
      }

      const credential = EmailAuthProvider.credential(user.email, reauthPassword);
      await reauthenticateWithCredential(user, credential);

      if (targetDeviceId) {
        await handleReauthSuccess(targetDeviceId);
      }
    } catch (error: any) {
      console.error('Re-authentication failed', error);
      setAuthError('Invalid password. Identity verification failed.');
    } finally {
      setReauthLoading(false);
    }
  };

  const handleGoogleReauth = async () => {
    setAuthError('');
    setReauthLoading(true);
    try {
      const user = auth.currentUser;
      if (!user) {
        setAuthError('User not properly authenticated.');
        return;
      }

      const provider = new GoogleAuthProvider();
      provider.setCustomParameters({ prompt: 'select_account' });
      await reauthenticateWithPopup(user, provider);

      if (targetDeviceId) {
        await handleReauthSuccess(targetDeviceId);
      }
    } catch (error: any) {
      console.error('Google re-authentication failed', error);
      setAuthError('Google identity verification failed. Please try again.');
    } finally {
      setReauthLoading(false);
    }
  };

  return (
    <div className="space-y-6 max-w-4xl mx-auto w-full min-w-0 transition-colors duration-200">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-slate-900 dark:text-white tracking-tight">
          Lost Phone Recovery &amp; IMEI Vault
        </h1>
        <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
          Guided emergency protocols, police FIR requirements, and authenticated hardware credentials
        </p>
      </div>

      {/* Guided 4-Step Recovery Workflow */}
      <div className="bg-white dark:bg-[#121927] p-6 rounded-3xl shadow-sm border border-slate-200 dark:border-[#223049] space-y-4">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-2xl bg-indigo-50 dark:bg-indigo-950/60 text-indigo-600 dark:text-cyan-400 border border-indigo-200 dark:border-indigo-800 flex items-center justify-center shrink-0">
            <ShieldCheck className="w-5 h-5" />
          </div>
          <div>
            <h2 className="text-base font-bold text-slate-900 dark:text-white font-mono">
              Official Stolen Phone Protocol (CEIR India &amp; Police FIR)
            </h2>
            <p className="text-xs text-slate-500 dark:text-slate-400">
              Prerequisites and government blacklisting procedures for permanent theft
            </p>
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 pt-2">
          {/* Step 1 */}
          <div className="p-4 rounded-2xl bg-slate-50 dark:bg-[#162032] border border-slate-200 dark:border-slate-800 space-y-2">
            <div className="flex items-center gap-2 text-xs font-bold text-indigo-600 dark:text-cyan-400 font-mono">
              <FileText className="w-4 h-4" />
              <span>Step 1: File Police FIR</span>
            </div>
            <p className="text-xs text-slate-600 dark:text-slate-300 leading-relaxed">
              File a Police Complaint (FIR or digital lost article report) immediately at your local police station or online portal. Retain your FIR copy and complaint number.
            </p>
          </div>

          {/* Step 2 */}
          <div className="p-4 rounded-2xl bg-slate-50 dark:bg-[#162032] border border-slate-200 dark:border-slate-800 space-y-2">
            <div className="flex items-center gap-2 text-xs font-bold text-indigo-600 dark:text-cyan-400 font-mono">
              <PhoneCall className="w-4 h-4" />
              <span>Step 2: Obtain Duplicate SIM</span>
            </div>
            <p className="text-xs text-slate-600 dark:text-slate-300 leading-relaxed">
              Visit your telecom carrier (Airtel, Jio, Vi) with your ID proof to block the old physical SIM and issue a replacement SIM for OTP delivery.
            </p>
          </div>

          {/* Step 3 */}
          <div className="p-4 rounded-2xl bg-slate-50 dark:bg-[#162032] border border-slate-200 dark:border-slate-800 space-y-2">
            <div className="flex items-center gap-2 text-xs font-bold text-indigo-600 dark:text-cyan-400 font-mono">
              <Lock className="w-4 h-4" />
              <span>Step 3: Unlock Verified IMEI</span>
            </div>
            <p className="text-xs text-slate-600 dark:text-slate-300 leading-relaxed">
              Re-authenticate below to retrieve the verified 15-digit IMEI credentials stored in your SecureTrack hardware vault.
            </p>
          </div>

          {/* Step 4 */}
          <div className="p-4 rounded-2xl bg-slate-50 dark:bg-[#162032] border border-slate-200 dark:border-slate-800 space-y-2">
            <div className="flex items-center gap-2 text-xs font-bold text-indigo-600 dark:text-cyan-400 font-mono">
              <ExternalLink className="w-4 h-4" />
              <span>Step 4: Submit CEIR Block Request</span>
            </div>
            <p className="text-xs text-slate-600 dark:text-slate-300 leading-relaxed">
              Submit the FIR copy and IMEI numbers to the Department of Telecommunications portal to blacklist the handset across all Indian telecom networks.
            </p>
            <a
              href="https://www.ceir.gov.in/"
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-1.5 text-xs text-indigo-600 dark:text-cyan-400 font-semibold hover:underline pt-1"
            >
              Open CEIR Official Portal <ExternalLink className="w-3 h-3" />
            </a>
          </div>
        </div>
      </div>

      {/* Google Find Hub Independent Fallback */}
      <div className="bg-white dark:bg-[#121927] p-6 rounded-3xl shadow-sm border border-slate-200 dark:border-[#223049] flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div className="space-y-1">
          <h2 className="text-base font-bold text-slate-900 dark:text-white font-mono">
            Google Find Hub (Backup Location)
          </h2>
          <p className="text-xs text-slate-500 dark:text-slate-400 max-w-lg">
            If the handset is still turned on and linked to your Google Account, you can utilize Google Find Hub as a secondary recovery fallback to ring, lock, or secure it.
          </p>
        </div>
        <a
          href="https://android.com/find"
          target="_blank"
          rel="noopener noreferrer"
          className="inline-flex items-center gap-2 bg-indigo-600 hover:bg-indigo-700 text-white px-4 py-2.5 rounded-xl font-semibold transition-colors text-xs shadow-xs shrink-0"
        >
          Open Google Find Hub <ExternalLink className="w-3.5 h-3.5" />
        </a>
      </div>

      {/* Enrolled Devices & Vaulted IMEI Numbers */}
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-bold text-slate-900 dark:text-white font-mono">
            Enrolled Device Identifiers
          </h2>
          <span className="text-xs text-slate-500 font-mono">
            AES-256 Vaulted Storage
          </span>
        </div>

        {loading ? (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <Skeleton className="w-full h-32 rounded-3xl" />
            <Skeleton className="w-full h-32 rounded-3xl" />
          </div>
        ) : devices.length === 0 ? (
          <div className="p-8 text-center bg-white dark:bg-[#121927] rounded-3xl border border-slate-200 dark:border-[#223049] text-slate-500 text-xs">
            No enrolled devices found under your account.
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {devices.map((device) => {
              const isRevealed = revealedDeviceId === device.id;

              return (
                <div
                  key={device.id}
                  className="bg-white dark:bg-[#121927] p-6 rounded-3xl border border-slate-200 dark:border-[#223049] shadow-xs space-y-4"
                >
                  <div className="flex items-start justify-between">
                    <div>
                      <h3 className="text-base font-bold text-slate-900 dark:text-white truncate">
                        {device.deviceName}
                      </h3>
                      <p className="text-xs text-slate-500 dark:text-slate-400">
                        {device.model || 'Android'} • Android {device.androidVersion || '14'}
                      </p>
                    </div>
                    <span
                      className={`text-[10px] font-mono font-bold px-2 py-0.5 rounded-full border ${
                        isRevealed
                          ? 'bg-emerald-100 dark:bg-emerald-950/60 text-emerald-800 dark:text-emerald-300 border-emerald-300 dark:border-emerald-800'
                          : 'bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-400 border-slate-300 dark:border-slate-700'
                      }`}
                    >
                      {isRevealed ? 'UNLOCKED' : 'LOCKED VAULT'}
                    </span>
                  </div>

                  {isRevealed ? (
                    <div className="bg-indigo-50/70 dark:bg-[#151f33] p-4 rounded-2xl border border-indigo-200 dark:border-indigo-800/80 space-y-2.5 animate-fade-in text-xs">
                      <div className="flex flex-col sm:flex-row sm:justify-between sm:items-center gap-1">
                        <span className="font-semibold text-slate-700 dark:text-slate-300">
                          IMEI Slot 1:
                        </span>
                        <span className="font-mono font-bold text-slate-900 dark:text-cyan-400 select-all bg-white dark:bg-[#0A0E17] px-2 py-0.5 rounded border border-indigo-100 dark:border-slate-800 break-all">
                          {recoveryInfo?.imei1 || 'Not registered on device'}
                        </span>
                      </div>
                      <div className="flex flex-col sm:flex-row sm:justify-between sm:items-center gap-1">
                        <span className="font-semibold text-slate-700 dark:text-slate-300">
                          IMEI Slot 2:
                        </span>
                        <span className="font-mono font-bold text-slate-900 dark:text-cyan-400 select-all bg-white dark:bg-[#0A0E17] px-2 py-0.5 rounded border border-indigo-100 dark:border-slate-800 break-all">
                          {recoveryInfo?.imei2 || 'Not registered on device'}
                        </span>
                      </div>
                      <div className="pt-2 flex justify-end">
                        <button
                          type="button"
                          onClick={() => {
                            setRevealedDeviceId(null);
                            setRecoveryInfo(null);
                          }}
                          className="text-xs text-indigo-600 dark:text-cyan-400 hover:underline font-semibold"
                        >
                          Lock Vault View
                        </button>
                      </div>
                    </div>
                  ) : (
                    <button
                      type="button"
                      onClick={() => handleRevealRequest(device.id)}
                      className="w-full flex items-center justify-center gap-2 bg-slate-50 dark:bg-[#162032] text-slate-800 dark:text-slate-200 font-semibold py-2.5 rounded-xl hover:bg-slate-100 dark:hover:bg-[#1E2B45] transition-colors text-xs border border-slate-200 dark:border-slate-700"
                    >
                      <Lock className="w-4 h-4 text-indigo-600 dark:text-cyan-400" />
                      Re-Authenticate to Reveal IMEI
                    </button>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* Accessible Re-Authentication Modal */}
      {showReauthModal && (
        <div
          role="dialog"
          aria-modal="true"
          className="fixed inset-0 bg-black/60 backdrop-blur-xs flex items-center justify-center z-50 p-4 animate-fade-in"
          onClick={(e) => {
            if (e.target === e.currentTarget && !reauthLoading) setShowReauthModal(false);
          }}
        >
          <div className="bg-white dark:bg-[#121927] p-6 rounded-3xl shadow-2xl max-w-md w-full border border-slate-200 dark:border-[#223049] space-y-4 text-left max-h-[90vh] overflow-y-auto">
            <div>
              <h2 className="text-lg font-bold text-slate-900 dark:text-white font-mono">
                Security Re-Authentication
              </h2>
              <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
                IMEI numbers are confidential hardware identifiers. Verify your account password to unlock this data.
              </p>
            </div>

            {authError && (
              <div className="p-3 bg-rose-50 dark:bg-rose-950/50 border border-rose-300 dark:border-rose-800 text-rose-800 dark:text-rose-300 text-xs rounded-xl flex items-center gap-2">
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span>{authError}</span>
              </div>
            )}

            {auth.currentUser?.providerData.some((p) => p.providerId === 'google.com') && (
              <div className="space-y-3">
                <button
                  type="button"
                  onClick={handleGoogleReauth}
                  disabled={reauthLoading}
                  className="w-full py-2.5 px-4 bg-white dark:bg-[#162032] border border-slate-300 dark:border-slate-700 text-slate-900 dark:text-white font-semibold rounded-xl hover:bg-slate-50 dark:hover:bg-[#1A263D] transition-all flex items-center justify-center gap-2 shadow-xs text-xs disabled:opacity-50"
                >
                  <svg className="w-4 h-4" viewBox="0 0 24 24">
                    <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" />
                    <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" />
                    <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z" />
                    <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z" />
                  </svg>
                  Verify with Google Account
                </button>
                <div className="flex items-center my-2">
                  <div className="flex-1 border-t border-slate-200 dark:border-slate-800" />
                  <span className="px-2 text-[10px] text-slate-400">or enter password</span>
                  <div className="flex-1 border-t border-slate-200 dark:border-slate-800" />
                </div>
              </div>
            )}

            <form onSubmit={handleReauthSubmit} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">
                  Account Password
                </label>
                <div className="relative">
                  <input
                    type={showReauthPassword ? 'text' : 'password'}
                    value={reauthPassword}
                    onChange={(e) => setReauthPassword(e.target.value)}
                    placeholder="Enter current password"
                    className="w-full pl-3.5 pr-10 py-2.5 rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-[#162032] text-slate-900 dark:text-white placeholder-slate-400 focus:outline-none focus:border-cyan-500 focus:ring-2 focus:ring-cyan-500/20 text-xs"
                    autoFocus
                    required
                  />
                  <button
                    type="button"
                    onClick={() => setShowReauthPassword(!showReauthPassword)}
                    aria-label={showReauthPassword ? 'Hide password' : 'Show password'}
                    className="absolute right-2.5 top-2.5 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 p-1 rounded-md"
                  >
                    {showReauthPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>
              </div>

              <div className="flex justify-end gap-3 pt-2">
                <button
                  type="button"
                  onClick={() => {
                    setShowReauthModal(false);
                    setReauthPassword('');
                    setAuthError('');
                  }}
                  disabled={reauthLoading}
                  className="px-4 py-2 text-xs text-slate-500 hover:text-slate-800 dark:hover:text-slate-200 rounded-xl"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={reauthLoading}
                  className="px-5 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-semibold shadow-xs disabled:opacity-50 flex items-center gap-1.5"
                >
                  {reauthLoading && (
                    <span className="w-3.5 h-3.5 border-2 border-white border-t-transparent rounded-full animate-spin" />
                  )}
                  <span>{reauthLoading ? 'Verifying...' : 'Verify & Unlock'}</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
